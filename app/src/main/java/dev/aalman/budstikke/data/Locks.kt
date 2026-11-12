package dev.aalman.budstikke.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import dev.aalman.budstikke.data.AppDatabase.Companion.LOCK_MESSAGE
import dev.aalman.budstikke.data.AppDatabase.Companion.LOCK_THREAD
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** What is locked: whole conversations by thread id, and single messages by SMS id. */
data class LockState(val threads: Set<Long> = emptySet(), val messages: Set<Long> = emptySet()) {
    fun isLocked(threadId: Long, messageId: Long): Boolean = threadId in threads || messageId in messages
}

/**
 * Conversations and messages whose text stays hidden until the screen lock is confirmed.
 * Message locks are keyed by the system store's SMS id, conversation locks by thread id;
 * neither survives the row being deleted, except through the trash, which keeps them.
 */
class Locks private constructor(context: Context) {

    private val db = AppDatabase.get(context)
    private val version = MutableStateFlow(0)

    /** Emits at once, then after every change. */
    fun changes(): Flow<Unit> = version.map { }

    fun state(): LockState = db.readableDatabase.query("locks", arrayOf("kind", "id"), null, null, null, null, null)
        .use { c ->
            val threads = HashSet<Long>()
            val messages = HashSet<Long>()
            while (c.moveToNext()) {
                if (c.getInt(0) == LOCK_THREAD) threads += c.getLong(1) else messages += c.getLong(1)
            }
            LockState(threads, messages)
        }

    fun isThreadLocked(threadId: Long): Boolean = threadId in state().threads

    fun setThreadLocked(threadId: Long, locked: Boolean) = set(LOCK_THREAD, listOf(threadId), locked)

    fun setMessagesLocked(messageIds: Collection<Long>, locked: Boolean) = set(LOCK_MESSAGE, messageIds, locked)

    /**
     * Drops conversation locks for threads that no longer exist, since the system store can
     * give a deleted thread's id to a new conversation, which would arrive locked.
     */
    fun forgetThreadsBut(threadIds: Set<Long>) {
        val stale = state().threads - threadIds
        if (stale.isNotEmpty()) set(LOCK_THREAD, stale, false)
    }

    private fun set(kind: Int, ids: Collection<Long>, locked: Boolean) {
        if (ids.isEmpty()) return
        db.writableDatabase.transaction {
            ids.forEach { id ->
                if (locked) {
                    insertWithOnConflict(
                        "locks", null,
                        ContentValues().apply {
                            put("kind", kind)
                            put("id", id)
                        },
                        SQLiteDatabase.CONFLICT_IGNORE,
                    )
                } else {
                    delete("locks", "kind = ? AND id = ?", arrayOf(kind.toString(), id.toString()))
                }
            }
        }
        version.update { it + 1 }
    }

    companion object {
        @Volatile
        private var instance: Locks? = null

        fun get(context: Context): Locks =
            instance ?: synchronized(this) {
                instance ?: Locks(context).also { instance = it }
            }
    }
}
