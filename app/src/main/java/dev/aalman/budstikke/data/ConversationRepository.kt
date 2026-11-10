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
) {
    /** The contact's name, or the number or sender name when there is no saved contact. */
    val title: String get() = contact?.name ?: address
}

/**
 * Reads conversations from the system message store. Only the SMS table is queried, so
 * MMS already on the phone never shows up.
 */
class ConversationRepository(context: Context) {

    private val resolver = context.applicationContext.contentResolver
    private val contacts = ContactRepository.get(context)
    private val flags = ConversationFlags.get(context)

    /**
     * Emits the main list, or the archive when [archived] is true, now and again whenever the
     * SMS store, contacts, pins or the archive change. The main list has pinned
     * conversations first; within each part the newest is first.
     */
    fun conversations(archived: Boolean): Flow<List<Conversation>> =
        merge(resolver.smsChanges(), contacts.changes(), flags.changes())
            .conflate()
            .map { load()?.filter { it.flags.archived == archived }?.sortedByDescending { it.flags.pinned } }
            .flowOn(Dispatchers.IO)
            .filterNotNull()

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
            )
        }
    }
}
