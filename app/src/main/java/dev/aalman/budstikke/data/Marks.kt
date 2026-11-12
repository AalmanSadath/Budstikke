package dev.aalman.budstikke.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.provider.Telephony
import androidx.core.database.sqlite.transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update

/** This phone's own reactions, one emoji per message, and its starred messages, by SMS id. */
data class MarkState(val reactions: Map<Long, String> = emptyMap(), val starred: Set<Long> = emptySet())

/** A starred message, as the Starred screen lists it. */
data class StarredMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val dateMillis: Long,
    val fromMe: Boolean,
    val contact: Contact?,
) {
    val title: String get() = contact?.name ?: address
}

/**
 * Reactions and stars put on single messages. Both are keyed by the system store's SMS id,
 * so the trash keeps them with a deleted message's copy and sets them again on the id it
 * gets back when restored.
 */
class Marks private constructor(context: Context) {

    private val db = AppDatabase.get(context)
    private val resolver = context.applicationContext.contentResolver
    private val contacts = ContactRepository.get(context)
    private val version = MutableStateFlow(0)

    /** Emits at once, then after every change. */
    fun changes(): Flow<Unit> = version.map { }

    fun state(): MarkState {
        val sql = db.readableDatabase
        val reactions = sql.query("reactions", arrayOf("message_id", "emoji"), null, null, null, null, null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getLong(0), c.getString(1)) }
        }
        val starred = sql.query("starred", arrayOf("message_id"), null, null, null, null, null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getLong(0)) }
        }
        return MarkState(reactions, starred)
    }

    /** Puts this phone's reaction on a message, replacing any it had, or takes it off for null. */
    fun setReaction(messageId: Long, emoji: String?) {
        val sql = db.writableDatabase
        if (emoji == null) {
            sql.delete("reactions", "message_id = ?", arrayOf(messageId.toString()))
        } else {
            sql.insertWithOnConflict(
                "reactions", null,
                ContentValues().apply {
                    put("message_id", messageId)
                    put("emoji", emoji)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
        version.update { it + 1 }
    }

    fun setStarred(messageIds: Collection<Long>, starred: Boolean) {
        if (messageIds.isEmpty()) return
        val now = System.currentTimeMillis()
        db.writableDatabase.transaction {
            messageIds.forEach { id ->
                if (starred) {
                    insertWithOnConflict(
                        "starred", null,
                        ContentValues().apply {
                            put("message_id", id)
                            put("starred_at", now)
                        },
                        SQLiteDatabase.CONFLICT_IGNORE,
                    )
                } else {
                    delete("starred", "message_id = ?", arrayOf(id.toString()))
                }
            }
        }
        version.update { it + 1 }
    }

    /** Drops every mark on these messages, as they leave the system store. */
    fun forget(messageIds: Collection<Long>) {
        if (messageIds.isEmpty()) return
        db.writableDatabase.transaction {
            messageIds.forEach { id ->
                delete("reactions", "message_id = ?", arrayOf(id.toString()))
                delete("starred", "message_id = ?", arrayOf(id.toString()))
            }
        }
        version.update { it + 1 }
    }

    /** Starred messages, newest star first, reloaded when stars, messages or contacts change. */
    fun starredMessages(): Flow<List<StarredMessage>> = merge(version.map { }, resolver.smsChanges(), contacts.changes())
        .conflate()
        .map { loadStarred() }
        .flowOn(Dispatchers.IO)

    private fun loadStarred(): List<StarredMessage> {
        val order = db.readableDatabase.query(
            "starred", arrayOf("message_id"), null, null, null, null, "starred_at DESC, message_id DESC",
        ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
        if (order.isEmpty()) return emptyList()
        val found = HashMap<Long, StarredMessage>()
        order.chunked(500).forEach { chunk ->
            resolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE,
                ),
                "${Telephony.Sms._ID} IN (${chunk.joinToString(",") { "?" }})",
                chunk.map { it.toString() }.toTypedArray(),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val address = c.getString(2).orEmpty()
                    found[c.getLong(0)] = StarredMessage(
                        id = c.getLong(0),
                        threadId = c.getLong(1),
                        address = address,
                        body = c.getString(3).orEmpty(),
                        dateMillis = c.getLong(4),
                        fromMe = c.getInt(5) != Telephony.Sms.MESSAGE_TYPE_INBOX,
                        contact = contacts.lookup(address),
                    )
                }
            }
        }
        return order.mapNotNull(found::get)
    }

    companion object {
        @Volatile
        private var instance: Marks? = null

        fun get(context: Context): Marks =
            instance ?: synchronized(this) {
                instance ?: Marks(context).also { instance = it }
            }
    }
}
