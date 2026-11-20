package dev.aalman.budstikke.data

import android.content.Context
import android.provider.Telephony
import dev.aalman.budstikke.R
import dev.aalman.budstikke.sms.isSentReaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** The newest message of one thread, as the conversation list shows it. */
data class Conversation(
    val threadId: Long,
    /** Who it is with besides this phone: one person, or the members of a group. */
    val members: List<Member>,
    val snippet: String,
    val dateMillis: Long,
    val lastFromMe: Boolean,
    val unreadCount: Int,
    val flags: Flags = Flags(),
    val blocked: Boolean = false,
    /** Text typed in the conversation and not sent, if any. */
    val draft: String? = null,
    /** The conversation is locked, or its newest message is: the snippet stays hidden. */
    val snippetLocked: Boolean = false,
    /** The conversation is locked, or holds a locked message: deleting it asks to unlock. */
    val hasLocked: Boolean = false,
    /** The SIM the newest message came in on or went out from, as a subscription id. */
    val subscriptionId: Int = -1,
) {
    val folder: Folder
        get() = when {
            blocked -> Folder.Blocked
            flags.archived -> Folder.Archive
            else -> Folder.Main
        }

    val isGroup: Boolean get() = members.size > 1

    val addresses: List<String> get() = members.map { it.address }

    /** The contact of the one person it is with; a group has none of its own. */
    val contact: Contact? get() = members.singleOrNull()?.contact

    /** The contact's name, the number or sender name, or a group's first names. */
    val title: String get() = membersTitle(members)
}

/** A blocked address, and its conversation when there is one. */
data class BlockedEntry(val address: String, val conversation: Conversation?, val contact: Contact?) {
    val title: String get() = contact?.name ?: conversation?.title ?: address
}

/** Which conversations a list shows. Blocked ones show only in [Blocked], wherever they were. */
enum class Folder { Main, Archive, Blocked }

/**
 * Reads conversations from the system message store: SMS and MMS, which it keeps in two
 * tables filed into the same threads.
 */
class ConversationRepository(context: Context) {

    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val mms = MmsReader(resolver)
    private val contacts = ContactRepository.get(context)
    private val flags = ConversationFlags.get(context)
    private val blocks = BlockList.get(context)
    private val drafts = Drafts.get(context)
    private val locks = Locks.get(context)

    /**
     * Emits one folder's conversations now, and again whenever the SMS store, contacts,
     * pins, the archive or the block list change. The main list has pinned conversations
     * first; within each part the newest is first.
     */
    fun conversations(folder: Folder): Flow<List<Conversation>> =
        merge(resolver.smsChanges(), contacts.changes(), flags.changes(), blocks.changes(), drafts.changes(), locks.changes())
            .conflate()
            .map { load()?.filter { it.folder == folder }?.sortedByDescending { it.flags.pinned } }
            .flowOn(Dispatchers.IO)
            .filterNotNull()

    /**
     * Every blocked address with its conversations, one entry per conversation, or one
     * without a conversation when the number has sent nothing since.
     */
    fun blocked(): Flow<List<BlockedEntry>> = conversations(Folder.Blocked)
        .map { conversations ->
            blocks.all().flatMap { address ->
                val matching = conversations.filter { c -> !c.isGroup && c.addresses.any { sameSender(it, address) } }
                if (matching.isEmpty()) {
                    listOf(BlockedEntry(address, null, contacts.lookup(address)))
                } else {
                    matching.map { BlockedEntry(address, it, it.contact) }
                }
            }
        }
        .flowOn(Dispatchers.IO)

    /**
     * Conversations in every folder that match [query] by contact name, number or sender
     * name, newest first, then those where only a message's text matches, by that message's
     * date. Locked text is searched only when [includeLocked], so a search cannot show what
     * a lock hides. Call off the main thread.
     */
    fun search(query: String, includeLocked: Boolean): List<SearchResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val all = load() ?: return emptyList()
        val messages = matchingMessages(q, if (includeLocked) null else locks.state())
        val byName = all.filter { c ->
            matchesConversation(c.title, "", q) || c.members.any { matchesConversation(it.name, it.address, q) }
        }
        val named = byName.mapTo(HashSet()) { it.threadId }
        val byText = all
            .filter { it.threadId !in named && it.threadId in messages }
            .map { SearchResult(it, messages.getValue(it.threadId)) }
            .sortedByDescending { it.message!!.dateMillis }
        return byName.map { SearchResult(it, null) } + byText
    }

    /** The newest message in each thread whose text holds [query], case-insensitively. */
    private fun matchingMessages(query: String, hidden: LockState?): Map<Long, MatchedMessage> {
        val found = HashMap<Long, MatchedMessage>()
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms._ID),
            "${Telephony.Sms.BODY} LIKE ? ESCAPE '\\'",
            arrayOf("%${escapeLike(query)}%"),
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val thread = c.getLong(0)
                if (thread in found) continue
                if (hidden != null && hidden.isLocked(thread, c.getLong(4))) continue
                found[thread] = MatchedMessage(
                    id = c.getLong(4),
                    body = c.getString(1).orEmpty(),
                    dateMillis = c.getLong(2),
                    fromMe = c.getInt(3) != Telephony.Sms.MESSAGE_TYPE_INBOX,
                )
            }
        }
        // MMS text lives in their parts; the newest match per thread stands beside the SMS one.
        mms.matching(query, escapeLike(query)).forEach { m ->
            if (hidden != null && hidden.isLocked(m.threadId, m.key)) return@forEach
            val current = found[m.threadId]
            if (current == null || current.dateMillis < m.dateMillis) {
                found[m.threadId] = MatchedMessage(id = m.key, body = m.text, dateMillis = m.dateMillis, fromMe = !m.inbox)
            }
        }
        return found
    }

    /** One message of either table, reduced to what choosing a thread's row needs. */
    private class StoreRow(
        val key: Long,
        val threadId: Long,
        val address: String?,
        val snippet: String,
        val rawText: String,
        val dateMillis: Long,
        val inbox: Boolean,
        val unread: Boolean,
        val failed: Boolean,
        val subscriptionId: Int,
    )

    private fun load(): List<Conversation>? {
        val rows = ArrayList<StoreRow>()
        val cursor = resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE,
                Telephony.Sms.TYPE, Telephony.Sms.READ, Telephony.Sms._ID, Telephony.Sms.SUBSCRIPTION_ID,
            ),
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        ) ?: return null
        cursor.use { c ->
            while (c.moveToNext()) {
                val type = c.getInt(4)
                val text = c.getString(2).orEmpty()
                rows += StoreRow(
                    key = c.getLong(6),
                    threadId = c.getLong(0),
                    address = c.getString(1),
                    snippet = text,
                    rawText = text,
                    dateMillis = c.getLong(3),
                    inbox = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                    unread = type == Telephony.Sms.MESSAGE_TYPE_INBOX && c.getInt(5) == 0,
                    failed = type == Telephony.Sms.MESSAGE_TYPE_FAILED,
                    subscriptionId = c.getInt(7),
                )
            }
        }
        mms.summaries().forEach { m ->
            rows += StoreRow(
                key = m.key,
                threadId = m.threadId,
                address = null,
                snippet = mmsSnippet(m),
                rawText = m.text,
                dateMillis = m.dateMillis,
                inbox = m.inbox,
                unread = m.inbox && !m.read,
                failed = m.failed,
                subscriptionId = m.subscriptionId,
            )
        }
        // SMS and MMS come from two tables; one order, newest first, for both.
        rows.sortByDescending { it.dateMillis }
        val recipients by lazy { mms.threadRecipients() }

        val latest = LinkedHashMap<Long, Conversation>()
        val unread = HashMap<Long, Int>()
        val lockState = locks.state()
        val withLocked = HashSet<Long>()
        // A thread's newest sent reaction, for a thread that holds nothing else.
        val fallback = HashMap<Long, Conversation>()
        for (r in rows) {
            val threadId = r.threadId
            val messageLocked = r.key in lockState.messages
            if (messageLocked) withLocked += threadId
            if (r.unread) unread[threadId] = (unread[threadId] ?: 0) + 1
            if (threadId in latest) continue
            // An MMS row has no address of its own; the thread's record says who it is with.
            val addresses = r.address?.let(::listOf) ?: recipients[threadId].orEmpty().ifEmpty { listOf("") }
            val row = Conversation(
                threadId = threadId,
                members = addresses.map { Member(it) },
                snippet = r.snippet,
                dateMillis = r.dateMillis,
                lastFromMe = !r.inbox,
                unreadCount = 0,
                snippetLocked = messageLocked,
                subscriptionId = r.subscriptionId,
            )
            // A reaction sent from here is folded into its message in the conversation,
            // so the list shows the message before it instead.
            if (isSentReaction(r.rawText, outgoing = !r.inbox, failed = r.failed)) {
                fallback.putIfAbsent(threadId, row)
            } else {
                latest[threadId] = row
            }
        }
        fallback.forEach { (threadId, row) -> latest.putIfAbsent(threadId, row) }
        flags.forgetAllBut(latest.keys)
        drafts.forgetAllBut(latest.keys)
        locks.forgetThreadsBut(latest.keys)
        val allFlags = flags.all()
        val allDrafts = drafts.all()
        // Skipped reactions can leave a thread dated before others, so sort, newest first.
        return latest.values.sortedByDescending { it.dateMillis }.map {
            it.copy(
                unreadCount = unread[it.threadId] ?: 0,
                members = it.members.map { m -> m.copy(contact = contacts.lookup(m.address)) },
                flags = allFlags[it.threadId] ?: Flags(),
                // A group is never blocked as a whole; its blocked members' messages are dropped.
                blocked = !it.isGroup && blocks.isBlocked(it.members.single().address),
                draft = allDrafts[it.threadId],
                snippetLocked = it.snippetLocked || it.threadId in lockState.threads,
                hasLocked = it.threadId in lockState.threads || it.threadId in withLocked,
            )
        }
    }

    /** An MMS in the list: its text, or what it carries when it has none. */
    private fun mmsSnippet(m: MmsSummary): String = when {
        m.waiting -> app.getString(R.string.mms_waiting)
        m.text.isNotEmpty() -> m.text
        else -> app.getString(attachmentLabel(m.attachment))
    }
}
