package dev.aalman.budstikke.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Unsent text per conversation, keyed by the system store's thread id. Writes run one at a
 * time on their own scope, so a save asked for as a screen closes still happens, and in
 * the order asked.
 */
class Drafts private constructor(context: Context) {

    private val db = AppDatabase.get(context)
    private val version = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** Emits at once, then after every change. */
    fun changes(): Flow<Unit> = version.map { }

    fun get(threadId: Long): String? = db.readableDatabase.query(
        TABLE, arrayOf(BODY), "$THREAD_ID = ?", arrayOf(threadId.toString()), null, null, null,
    ).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun all(): Map<Long, String> = db.readableDatabase.query(
        TABLE, arrayOf(THREAD_ID, BODY), null, null, null, null, null,
    ).use { c -> buildMap { while (c.moveToNext()) put(c.getLong(0), c.getString(1)) } }

    /** Saves [body] as the thread's draft, or removes the draft when it is blank. */
    fun put(threadId: Long, body: String) {
        writes.launch {
            val sql = db.writableDatabase
            val current = get(threadId)
            if (body.isBlank()) {
                if (current == null) return@launch
                sql.delete(TABLE, "$THREAD_ID = ?", arrayOf(threadId.toString()))
            } else {
                if (current == body) return@launch
                sql.insertWithOnConflict(
                    TABLE, null,
                    ContentValues().apply {
                        put(THREAD_ID, threadId)
                        put(BODY, body)
                        put(UPDATED_AT, System.currentTimeMillis())
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            version.update { it + 1 }
        }
    }

    /** Drops drafts of threads that no longer exist, whose ids the store may give out again. */
    fun forgetAllBut(existing: Set<Long>) {
        val stale = all().keys - existing
        if (stale.isEmpty()) return
        val sql = db.writableDatabase
        stale.forEach { sql.delete(TABLE, "$THREAD_ID = ?", arrayOf(it.toString())) }
        version.update { it + 1 }
    }

    companion object {
        private const val TABLE = "drafts"
        private const val THREAD_ID = "thread_id"
        private const val BODY = "body"
        private const val UPDATED_AT = "updated_at"

        @Volatile
        private var instance: Drafts? = null

        fun get(context: Context): Drafts =
            instance ?: synchronized(this) {
                instance ?: Drafts(context).also { instance = it }
            }
    }
}
