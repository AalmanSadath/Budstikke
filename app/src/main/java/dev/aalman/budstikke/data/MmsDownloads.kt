package dev.aalman.budstikke.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.TimeUnit

/** Where a waiting MMS stands. One nobody has asked for yet is [Waiting]. */
enum class DownloadState { Waiting, Downloading, Failed }

/**
 * Download states of waiting MMS, by their row id in the system store. The store has no
 * column for this, so the app keeps it.
 */
class MmsDownloads private constructor(context: Context) {

    private val db = AppDatabase.get(context)
    private val version = MutableStateFlow(0)

    /** Emits at once, then after every change. */
    fun changes(): Flow<Unit> = version.map { }

    fun all(now: Long = System.currentTimeMillis()): Map<Long, DownloadState> =
        db.readableDatabase.query("mms_downloads", arrayOf("mms_id", "state", "updated_at"), null, null, null, null, null)
            .use { c ->
                buildMap {
                    while (c.moveToNext()) put(c.getLong(0), stateOf(c.getInt(1), c.getLong(2), now))
                }
            }

    fun state(mmsId: Long): DownloadState = all()[mmsId] ?: DownloadState.Waiting

    fun set(mmsId: Long, state: DownloadState) {
        db.writableDatabase.insertWithOnConflict(
            "mms_downloads", null,
            ContentValues().apply {
                put("mms_id", mmsId)
                put("state", state.ordinal)
                put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        version.update { it + 1 }
    }

    /** Forgets a waiting MMS once its message is downloaded or it is deleted. */
    fun forget(mmsIds: Collection<Long>) {
        if (mmsIds.isEmpty()) return
        mmsIds.forEach { db.writableDatabase.delete("mms_downloads", "mms_id = ?", arrayOf(it.toString())) }
        version.update { it + 1 }
    }

    companion object {
        /**
         * A download that has said nothing for this long is taken as failed: the app was
         * stopped while it ran, and its result will never come.
         */
        val STALE_MILLIS: Long = TimeUnit.MINUTES.toMillis(5)

        internal fun stateOf(stored: Int, updatedAt: Long, now: Long): DownloadState {
            val state = DownloadState.entries.getOrElse(stored) { DownloadState.Waiting }
            return if (state == DownloadState.Downloading && now - updatedAt > STALE_MILLIS) DownloadState.Failed else state
        }

        @Volatile
        private var instance: MmsDownloads? = null

        fun get(context: Context): MmsDownloads =
            instance ?: synchronized(this) {
                instance ?: MmsDownloads(context).also { instance = it }
            }
    }
}
