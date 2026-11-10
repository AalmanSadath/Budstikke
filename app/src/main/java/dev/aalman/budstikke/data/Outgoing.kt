package dev.aalman.budstikke.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import dev.aalman.budstikke.sms.OutgoingQueue
import androidx.core.database.sqlite.transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** A message waiting to be sent at [sendAtMillis]. */
data class PendingMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val subscriptionId: Int,
    val sendAtMillis: Long,
    /** Held back by the send delay rather than scheduled by hand. */
    val delayed: Boolean,
)

/**
 * Messages that go out later: scheduled ones and ones held back by the send delay. Each is
 * sent by whoever [claim]s it first, the app's own timer or the alarm, and never twice.
 */
class Outgoing private constructor(context: Context) {

    private val context = context.applicationContext
    private val db = AppDatabase.get(context)
    private val version = MutableStateFlow(0)

    /** Every pending message, soonest first, now and after every change. */
    fun all(): Flow<List<PendingMessage>> = version.map { query(null, null) }.flowOn(Dispatchers.IO)

    /** One conversation's pending messages, soonest first. */
    fun forThread(threadId: Long): Flow<List<PendingMessage>> =
        version.map { query("$THREAD_ID = ?", arrayOf(threadId.toString())) }.flowOn(Dispatchers.IO)

    fun add(threadId: Long, address: String, body: String, subscriptionId: Int, sendAtMillis: Long, delayed: Boolean): Long {
        val id = db.writableDatabase.insertOrThrow(
            TABLE, null,
            ContentValues().apply {
                put(THREAD_ID, threadId)
                put(ADDRESS, address)
                put(BODY, body)
                put(SUB_ID, subscriptionId)
                put(SEND_AT, sendAtMillis)
                put(DELAYED, if (delayed) 1 else 0)
            },
        )
        changed()
        return id
    }

    /** Puts back a message taken out by [claim], keeping its id. */
    fun restore(message: PendingMessage) {
        db.writableDatabase.insertOrThrow(
            TABLE, null,
            ContentValues().apply {
                put(ID, message.id)
                put(THREAD_ID, message.threadId)
                put(ADDRESS, message.address)
                put(BODY, message.body)
                put(SUB_ID, message.subscriptionId)
                put(SEND_AT, message.sendAtMillis)
                put(DELAYED, if (message.delayed) 1 else 0)
            },
        )
        changed()
    }

    fun reschedule(id: Long, sendAtMillis: Long) {
        db.writableDatabase.update(
            TABLE, ContentValues().apply { put(SEND_AT, sendAtMillis) }, "$ID = ?", arrayOf(id.toString()),
        )
        changed()
    }

    /**
     * Takes a message out of the queue, to send or to drop it. Returns it only to the one
     * caller whose delete removed the row, so a message can never go out twice.
     */
    fun claim(id: Long): PendingMessage? {
        val claimed = db.writableDatabase.transaction {
            val message = query("$ID = ?", arrayOf(id.toString())).firstOrNull()
            if (message != null && delete(TABLE, "$ID = ?", arrayOf(id.toString())) == 1) message else null
        }
        if (claimed != null) changed()
        return claimed
    }

    /** Messages whose time has come, soonest first. */
    fun due(now: Long = System.currentTimeMillis()): List<PendingMessage> =
        query("$SEND_AT <= ?", arrayOf(now.toString()))

    /** When the soonest message is due, or null when nothing is waiting. */
    fun nextSendAt(): Long? = db.readableDatabase.rawQuery("SELECT MIN($SEND_AT) FROM $TABLE", null)
        .use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }

    private fun query(selection: String?, args: Array<String>?): List<PendingMessage> =
        db.readableDatabase.query(
            TABLE, arrayOf(ID, THREAD_ID, ADDRESS, BODY, SUB_ID, SEND_AT, DELAYED),
            selection, args, null, null, "$SEND_AT ASC, $ID ASC",
        ).use { c -> buildList { while (c.moveToNext()) add(c.toMessage()) } }

    private fun Cursor.toMessage() = PendingMessage(
        id = getLong(0),
        threadId = getLong(1),
        address = getString(2),
        body = getString(3),
        subscriptionId = getInt(4),
        sendAtMillis = getLong(5),
        delayed = getInt(6) != 0,
    )

    /** Tells open screens, and moves the alarm to whatever is now soonest. */
    private fun changed() {
        version.update { it + 1 }
        OutgoingQueue.arm(context)
    }

    companion object {
        private const val TABLE = "scheduled_messages"
        private const val ID = "id"
        private const val THREAD_ID = "thread_id"
        private const val ADDRESS = "address"
        private const val BODY = "body"
        private const val SUB_ID = "sub_id"
        private const val SEND_AT = "send_at"
        private const val DELAYED = "delayed"

        // Holds only the application context, which lives as long as the process anyway.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: Outgoing? = null

        fun get(context: Context): Outgoing =
            instance ?: synchronized(this) {
                instance ?: Outgoing(context).also { instance = it }
            }
    }
}
