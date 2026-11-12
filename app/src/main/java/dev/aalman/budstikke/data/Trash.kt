package dev.aalman.budstikke.data

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import java.util.concurrent.TimeUnit

/** One deletion in the trash: a whole conversation, or messages picked out of one. */
data class TrashItem(
    val id: Long,
    val wholeConversation: Boolean,
    val address: String,
    val deletedAtMillis: Long,
    val messageCount: Int,
    val lastBody: String,
    val contact: Contact? = null,
    /** Holds a locked conversation or message: its text stays hidden until unlocked. */
    val locked: Boolean = false,
) {
    val title: String get() = contact?.name ?: address
    val expiresAtMillis: Long get() = deletedAtMillis + KEEP_MILLIS
}

/** How long a deletion stays in the trash before it is removed for good. */
val KEEP_MILLIS: Long = TimeUnit.DAYS.toMillis(30)

/**
 * Deleting takes SMS out of the system store and keeps copies here, so other apps no longer
 * see them while they can still be restored for [KEEP_MILLIS]. Restoring writes them back
 * with their original dates; the store files them into a thread by address again.
 */
class Trash private constructor(context: Context) {

    private val resolver = context.applicationContext.contentResolver
    private val db = AppDatabase.get(context)
    private val flags = ConversationFlags.get(context)
    private val contacts = ContactRepository.get(context)
    private val locks = Locks.get(context)
    private val marks = Marks.get(context)
    private val version = MutableStateFlow(0)

    /** The trash, newest deletion first, reloaded after every change. */
    fun items(): Flow<List<TrashItem>> = merge(version.map { }, contacts.changes())
        .map { load() }
        .flowOn(Dispatchers.IO)

    /** Moves whole conversations to the trash, one item each. Returns the new items' ids. */
    fun deleteConversations(threads: Map<Long, String>): List<Long> {
        val before = flags.all()
        val lockState = locks.state()
        val markState = marks.state()
        val ids = threads.mapNotNull { (threadId, address) ->
            val f = before[threadId] ?: Flags()
            move(
                address, "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), true, f,
                threadLocked = threadId in lockState.threads, lockedMessages = lockState.messages, marks = markState,
            )?.also { locks.setThreadLocked(threadId, false) }
        }
        version.update { it + 1 }
        return ids
    }

    /** Moves the given messages of one conversation to the trash as one item. */
    fun deleteMessages(address: String, messageIds: Collection<Long>): Long? {
        if (messageIds.isEmpty()) return null
        val id = move(
            address,
            "${Telephony.Sms._ID} IN (${messageIds.joinToString(",") { "?" }})",
            messageIds.map { it.toString() }.toTypedArray(),
            false,
            Flags(),
            threadLocked = false,
            lockedMessages = locks.state().messages,
            marks = marks.state(),
        )
        version.update { it + 1 }
        return id
    }

    /**
     * Writes the items' messages back into the system store and drops them from the trash.
     * A whole conversation gets its pin and archive state back.
     */
    fun restore(itemIds: Collection<Long>) {
        val sql = db.writableDatabase
        for (itemId in itemIds) {
            val item = sql.query(
                "trash_items", arrayOf("whole_conversation", "pinned_at", "archived", "thread_locked"),
                "id = ?", arrayOf(itemId.toString()), null, null, null,
            ).use { c ->
                if (!c.moveToFirst()) null
                else Triple(c.getInt(0) != 0, if (c.isNull(1)) null else c.getLong(1), c.getInt(2) != 0) to (c.getInt(3) != 0)
            } ?: continue
            var threadId: Long? = null
            // Restored rows get new ids, so their locks, stars and reactions are set again on those.
            val relock = ArrayList<Long>()
            val restar = ArrayList<Long>()
            val react = HashMap<Long, String>()
            sql.query(
                "trash_messages",
                arrayOf("address", "body", "date", "date_sent", "type", "read", "seen", "status", "sub_id", "locked", "starred", "reaction"),
                "item_id = ?", arrayOf(itemId.toString()), null, null, "date ASC",
            ).use { c ->
                while (c.moveToNext()) {
                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, c.getString(0))
                        put(Telephony.Sms.BODY, c.getString(1))
                        put(Telephony.Sms.DATE, c.getLong(2))
                        put(Telephony.Sms.DATE_SENT, c.getLong(3))
                        put(Telephony.Sms.TYPE, restoredType(c.getInt(4)))
                        put(Telephony.Sms.READ, c.getInt(5))
                        put(Telephony.Sms.SEEN, c.getInt(6))
                        put(Telephony.Sms.STATUS, c.getInt(7))
                        put(Telephony.Sms.SUBSCRIPTION_ID, c.getInt(8))
                    }
                    val uri = resolver.insert(Telephony.Sms.CONTENT_URI, values) ?: continue
                    val newId = uri.lastPathSegment?.toLongOrNull()
                    if (newId != null) {
                        if (c.getInt(9) != 0) relock += newId
                        if (c.getInt(10) != 0) restar += newId
                        if (!c.isNull(11)) react[newId] = c.getString(11)
                    }
                    if (threadId == null) {
                        threadId = resolver.query(uri, arrayOf(Telephony.Sms.THREAD_ID), null, null, null)
                            ?.use { t -> if (t.moveToFirst()) t.getLong(0) else null }
                    }
                }
            }
            val (state, threadLocked) = item
            val (whole, pinnedAt, archived) = state
            val restoredThread = threadId
            if (whole && restoredThread != null && (pinnedAt != null || archived)) {
                flags.restore(mapOf(restoredThread to Flags(pinnedAt, archived)))
            }
            if (threadLocked && restoredThread != null) locks.setThreadLocked(restoredThread, true)
            locks.setMessagesLocked(relock, true)
            marks.setStarred(restar, true)
            react.forEach { (id, emoji) -> marks.setReaction(id, emoji) }
            sql.delete("trash_items", "id = ?", arrayOf(itemId.toString()))
        }
        version.update { it + 1 }
    }

    fun deleteForever(itemIds: Collection<Long>) {
        val sql = db.writableDatabase
        itemIds.forEach { sql.delete("trash_items", "id = ?", arrayOf(it.toString())) }
        version.update { it + 1 }
    }

    fun empty() {
        db.writableDatabase.delete("trash_items", null, null)
        version.update { it + 1 }
    }

    /** Removes deletions older than [KEEP_MILLIS]. */
    fun purgeExpired(now: Long = System.currentTimeMillis()) {
        val removed = db.writableDatabase.delete(
            "trash_items", "deleted_at < ?", arrayOf((now - KEEP_MILLIS).toString()),
        )
        if (removed > 0) version.update { it + 1 }
    }

    /**
     * Copies the matching SMS into a new trash item, then deletes exactly the copied rows
     * from the system store, so a message arriving in between is never lost.
     */
    private fun move(
        address: String,
        selection: String,
        args: Array<String>,
        wholeConversation: Boolean,
        flags: Flags,
        threadLocked: Boolean,
        lockedMessages: Set<Long>,
        marks: MarkState,
    ): Long? {
        val sql = db.writableDatabase
        val copied = ArrayList<Long>()
        sql.beginTransaction()
        val itemId: Long
        try {
            itemId = sql.insertOrThrow("trash_items", null, ContentValues().apply {
                put("whole_conversation", if (wholeConversation) 1 else 0)
                put("address", address)
                put("deleted_at", System.currentTimeMillis())
                put("pinned_at", flags.pinnedAt)
                put("archived", if (flags.archived) 1 else 0)
                put("thread_locked", if (threadLocked) 1 else 0)
            })
            resolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE,
                    Telephony.Sms.DATE_SENT, Telephony.Sms.TYPE, Telephony.Sms.READ, Telephony.Sms.SEEN,
                    Telephony.Sms.STATUS, Telephony.Sms.SUBSCRIPTION_ID,
                ),
                selection, args, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    copied += id
                    sql.insertOrThrow("trash_messages", null, ContentValues().apply {
                        put("item_id", itemId)
                        put("address", c.getString(1))
                        put("body", c.getString(2))
                        put("date", c.getLong(3))
                        put("date_sent", c.getLong(4))
                        put("type", c.getInt(5))
                        put("read", c.getInt(6))
                        put("seen", c.getInt(7))
                        put("status", c.getInt(8))
                        put("sub_id", c.getInt(9))
                        put("locked", if (id in lockedMessages) 1 else 0)
                        put("starred", if (id in marks.starred) 1 else 0)
                        put("reaction", marks.reactions[id])
                    })
                }
            }
            if (copied.isEmpty()) return null
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        // The copies, with their locks and marks, are committed before anything leaves the
        // system store; the locks and marks on the old ids go with them.
        locks.setMessagesLocked(copied.filter { it in lockedMessages }, false)
        this.marks.forget(copied.filter { it in marks.starred || it in marks.reactions })
        copied.chunked(500).forEach { chunk ->
            resolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms._ID} IN (${chunk.joinToString(",") { "?" }})",
                chunk.map { it.toString() }.toTypedArray(),
            )
        }
        return itemId
    }

    private fun load(): List<TrashItem> = db.readableDatabase.rawQuery(
        """
        SELECT i.id, i.whole_conversation, i.address, i.deleted_at, COUNT(m.item_id),
               (SELECT body FROM trash_messages WHERE item_id = i.id ORDER BY date DESC LIMIT 1),
               i.thread_locked OR MAX(m.locked)
        FROM trash_items i LEFT JOIN trash_messages m ON m.item_id = i.id
        GROUP BY i.id ORDER BY i.deleted_at DESC, i.id DESC
        """.trimIndent(),
        null,
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val address = c.getString(2)
                add(
                    TrashItem(
                        id = c.getLong(0),
                        wholeConversation = c.getInt(1) != 0,
                        address = address,
                        deletedAtMillis = c.getLong(3),
                        messageCount = c.getInt(4),
                        lastBody = c.getString(5).orEmpty(),
                        contact = contacts.lookup(address),
                        locked = c.getInt(6) != 0,
                    ),
                )
            }
        }
    }

    companion object {
        @Volatile
        private var instance: Trash? = null

        fun get(context: Context): Trash =
            instance ?: synchronized(this) {
                instance ?: Trash(context).also { instance = it }
            }
    }
}

/**
 * The type a message is written back with. One that was still waiting to go out comes back
 * as failed, so it can be retried instead of showing as sending forever.
 */
internal fun restoredType(type: Int): Int = when (type) {
    Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> Telephony.Sms.MESSAGE_TYPE_FAILED
    else -> type
}
