package dev.aalman.budstikke.data

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Where an SMS stands, as the conversation screen shows it. */
enum class MessageState { Received, Sending, Sent, Delivered, Failed }

data class Message(
    val id: Long,
    val body: String,
    val dateMillis: Long,
    val state: MessageState,
    val subscriptionId: Int,
) {
    val isOutgoing: Boolean get() = state != MessageState.Received
}

/** Reads and updates the SMS of one thread in the system message store. */
class MessageRepository(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    /** The thread's messages, oldest first, reloaded whenever the SMS store changes. */
    fun messages(threadId: Long): Flow<List<Message>> = resolver.smsChanges()
        .conflate()
        .map { load(threadId) }
        .flowOn(Dispatchers.IO)

    /** Marks every unread message in the thread as read and seen. */
    suspend fun markRead(threadId: Long) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        resolver.update(
            Telephony.Sms.CONTENT_URI,
            values,
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
            arrayOf(threadId.toString()),
        )
    }

    private fun load(threadId: Long): List<Message> {
        val messages = ArrayList<Message>()
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.STATUS,
                Telephony.Sms.SUBSCRIPTION_ID,
            ),
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val type = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val status = c.getColumnIndexOrThrow(Telephony.Sms.STATUS)
            val sub = c.getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID)
            while (c.moveToNext()) {
                messages += Message(
                    id = c.getLong(id),
                    body = c.getString(body).orEmpty(),
                    dateMillis = c.getLong(date),
                    state = stateOf(c.getInt(type), c.getInt(status)),
                    subscriptionId = c.getInt(sub),
                )
            }
        }
        return messages
    }
}

/** Maps the store's type and delivery-status columns to one state. */
internal fun stateOf(type: Int, status: Int): MessageState = when (type) {
    Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageState.Received
    Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> MessageState.Sending
    Telephony.Sms.MESSAGE_TYPE_FAILED -> MessageState.Failed
    else -> when (status) {
        Telephony.Sms.STATUS_COMPLETE -> MessageState.Delivered
        Telephony.Sms.STATUS_FAILED -> MessageState.Failed
        else -> MessageState.Sent
    }
}
