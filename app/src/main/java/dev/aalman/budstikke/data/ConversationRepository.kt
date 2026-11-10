package dev.aalman.budstikke.data

import android.content.Context
import android.provider.Telephony
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

    /**
     * Emits one folder's conversations now, and again whenever the SMS store, contacts,
     * pins, the archive or the block list change. The main list has pinned conversations
     * first; within each part the newest is first.
     */
    fun conversations(folder: Folder): Flow<List<Conversation>> =
        merge(resolver.smsChanges(), contacts.changes(), flags.changes(), blocks.changes())
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
        )
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
            while (c.moveToNext()) {
                val threadId = c.getLong(thread)
                val isInbox = c.getInt(type) == Telephony.Sms.MESSAGE_TYPE_INBOX
                if (isInbox && c.getInt(read) == 0) {
                    unread[threadId] = (unread[threadId] ?: 0) + 1
                }
                if (threadId !in latest) {
                    latest[threadId] = Conversation(
                        threadId = threadId,
                        address = c.getString(address).orEmpty(),
                        snippet = c.getString(body).orEmpty(),
                        dateMillis = c.getLong(date),
                        lastFromMe = !isInbox,
                        unreadCount = 0,
                    )
                }
            }
        }
        flags.forgetAllBut(latest.keys)
        val allFlags = flags.all()
        // Rows come newest first, so insertion order is already date order.
        return latest.values.map {
            it.copy(
                unreadCount = unread[it.threadId] ?: 0,
                contact = contacts.lookup(it.address),
                flags = allFlags[it.threadId] ?: Flags(),
                blocked = blocks.isBlocked(it.address),
            )
        }
    }
}
