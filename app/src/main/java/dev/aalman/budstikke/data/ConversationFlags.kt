package dev.aalman.budstikke.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** What the app keeps about one conversation beyond its messages. */
data class Flags(val pinnedAt: Long? = null, val archived: Boolean = false) {
    val pinned: Boolean get() = pinnedAt != null
}

/**
 * Pins and the archive, keyed by the thread id the system message store gives each
 * conversation. Every change bumps a counter so open screens load again.
 */
class ConversationFlags private constructor(context: Context) {

    private val db = AppDatabase.get(context)
    private val version = MutableStateFlow(0)

    /** Emits at once, then after every change. */
    fun changes(): Flow<Unit> = version.map { }

    fun all(): Map<Long, Flags> = db.readableDatabase.query(
        TABLE, arrayOf(THREAD_ID, PINNED_AT, ARCHIVED), null, null, null, null, null,
    ).use { c ->
        buildMap {
            while (c.moveToNext()) {
                put(c.getLong(0), Flags(if (c.isNull(1)) null else c.getLong(1), c.getInt(2) != 0))
            }
        }
    }

    fun of(threadId: Long): Flags = all()[threadId] ?: Flags()

    /** Pins or unpins; returns what the threads were before, for undo. */
    fun setPinned(threadIds: Collection<Long>, pinned: Boolean): Map<Long, Flags> {
        val now = System.currentTimeMillis()
        return change(threadIds) { it.copy(pinnedAt = if (pinned) it.pinnedAt ?: now else null) }
    }

    /**
     * Archives or unarchives; returns what the threads were before, for undo. Archiving
     * unpins, since pins order only the main list.
     */
    fun setArchived(threadIds: Collection<Long>, archived: Boolean): Map<Long, Flags> =
        change(threadIds) { if (archived) Flags(pinnedAt = null, archived = true) else it.copy(archived = false) }

    /** Puts threads back the way [setPinned] or [setArchived] found them. */
    fun restore(before: Map<Long, Flags>) {
        write(before)
    }

    /**
     * Drops flags for threads that no longer exist. The system store can give a deleted
     * thread's id to a new conversation, which would otherwise arrive pinned or archived.
     */
    fun forgetAllBut(existing: Set<Long>) {
        val stale = all().keys - existing
        if (stale.isEmpty()) return
        db.writableDatabase.run {
            beginTransaction()
            try {
                stale.forEach { delete(TABLE, "$THREAD_ID = ?", arrayOf(it.toString())) }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
        version.update { it + 1 }
    }

    private fun change(threadIds: Collection<Long>, transform: (Flags) -> Flags): Map<Long, Flags> {
        val current = all()
        val before = threadIds.associateWith { current[it] ?: Flags() }
        write(before.mapValues { transform(it.value) })
        return before
    }

    private fun write(flags: Map<Long, Flags>) {
        db.writableDatabase.run {
            beginTransaction()
            try {
                flags.forEach { (threadId, f) ->
                    if (f == Flags()) {
                        delete(TABLE, "$THREAD_ID = ?", arrayOf(threadId.toString()))
                    } else {
                        val values = ContentValues().apply {
                            put(THREAD_ID, threadId)
                            put(PINNED_AT, f.pinnedAt)
                            put(ARCHIVED, if (f.archived) 1 else 0)
                        }
                        insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                    }
                }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
        version.update { it + 1 }
    }

    companion object {
        private const val TABLE = "conversation_flags"
        private const val THREAD_ID = "thread_id"
        private const val PINNED_AT = "pinned_at"
        private const val ARCHIVED = "archived"

        @Volatile
        private var instance: ConversationFlags? = null

        fun get(context: Context): ConversationFlags =
            instance ?: synchronized(this) {
                instance ?: ConversationFlags(context).also { instance = it }
            }
    }
}
