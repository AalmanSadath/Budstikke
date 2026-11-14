package dev.aalman.budstikke.data

import android.content.Context
import android.provider.Telephony
import dev.aalman.budstikke.sms.isSentReaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** The newest message of one SMS thread, as the conversation list shows it. */
data class Conversation(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val dateMillis: Long,
    val lastFromMe: Boolean,
    val unreadCount: Int,
    val contact: Contact? = null,
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

    /** The contact's name, or the number or sender name when there is no saved contact. */
    val title: String get() = contact?.name ?: address
}

/** A blocked address, and its conversation when there is one. */
data class BlockedEntry(val address: String, val conversation: Conversation?, val contact: Contact?) {
    val title: String get() = contact?.name ?: conversation?.address ?: address
}

/** Which conversations a list shows. Blocked ones show only in [Blocked], wherever they were. */
enum class Folder { Main, Archive, Blocked }

/**
 * Reads conversations from the system message store. Only the SMS table is queried, so
 * MMS already on the phone never shows up.
 */
class ConversationRepository(context: Context) {

    private val resolver = context.applicationContext.contentResolver
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
                val matching = conversations.filter { sameSender(it.address, address) }
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
        val byName = all.filter { matchesConversation(it.title, it.address, q) }
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
        return found
    }

    private fun load(): List<Conversation>? {
        val latest = LinkedHashMap<Long, Conversation>()
        val unread = HashMap<Long, Int>()
        val projection = arrayOf(
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms._ID,
            Telephony.Sms.SUBSCRIPTION_ID,
        )
        val lockState = locks.state()
        val withLocked = HashSet<Long>()
        // A thread's newest sent reaction, for a thread that holds nothing else.
        val fallback = HashMap<Long, Conversation>()
        val cursor = resolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        ) ?: return null
        cursor.use { c ->
            val thread = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val address = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val type = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val read = c.getColumnIndexOrThrow(Telephony.Sms.READ)
            val id = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val sub = c.getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID)
            while (c.moveToNext()) {
                val threadId = c.getLong(thread)
                val messageLocked = c.getLong(id) in lockState.messages
                if (messageLocked) withLocked += threadId
                val isInbox = c.getInt(type) == Telephony.Sms.MESSAGE_TYPE_INBOX
                if (isInbox && c.getInt(read) == 0) {
                    unread[threadId] = (unread[threadId] ?: 0) + 1
                }
                if (threadId in latest) continue
                val text = c.getString(body).orEmpty()
                val row = Conversation(
                    threadId = threadId,
                    address = c.getString(address).orEmpty(),
                    snippet = text,
                    dateMillis = c.getLong(date),
                    lastFromMe = !isInbox,
                    unreadCount = 0,
                    snippetLocked = messageLocked,
                    subscriptionId = c.getInt(sub),
                )
                // A reaction sent from here is folded into its message in the conversation,
                // so the list shows the message before it instead.
                val failed = c.getInt(type) == Telephony.Sms.MESSAGE_TYPE_FAILED
                if (isSentReaction(text, outgoing = !isInbox, failed = failed)) {
                    fallback.putIfAbsent(threadId, row)
                } else {
                    latest[threadId] = row
                }
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
                contact = contacts.lookup(it.address),
                flags = allFlags[it.threadId] ?: Flags(),
                blocked = blocks.isBlocked(it.address),
                draft = allDrafts[it.threadId],
                snippetLocked = it.snippetLocked || it.threadId in lockState.threads,
                hasLocked = it.threadId in lockState.threads || it.threadId in withLocked,
            )
        }
    }
}
