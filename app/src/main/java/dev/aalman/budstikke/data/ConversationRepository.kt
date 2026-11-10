package dev.aalman.budstikke.data

import android.content.Context
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** The newest message of one SMS thread, as the conversation list shows it. */
data class Conversation(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val dateMillis: Long,
    val lastFromMe: Boolean,
    val unreadCount: Int,
)

/**
 * Reads conversations from the system message store. Only the SMS table is queried, so
 * MMS already on the phone never shows up.
 */
class ConversationRepository(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    /** Emits the conversation list now and again whenever the SMS store changes. */
    fun conversations(): Flow<List<Conversation>> = resolver.smsChanges()
        .conflate()
        .map { load() }
        .flowOn(Dispatchers.IO)

    private fun load(): List<Conversation> {
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
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
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
        // Rows come newest first, so insertion order is already the list order.
        return latest.values.map { it.copy(unreadCount = unread[it.threadId] ?: 0) }
    }
}
