package dev.aalman.budstikke.trash

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import dev.aalman.budstikke.data.Trash
import kotlin.concurrent.thread

/**
 * Empties the trash of deletions older than 30 days once a day, whether or not the app is
 * opened. [dev.aalman.budstikke.system.BootReceiver] sets the alarm again after a restart.
 */
class TrashPurgeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PURGE) return
        val pending = goAsync()
        thread(name = "trash-purge") {
            try {
                Trash.get(context).purgeExpired()
            } catch (e: Exception) {
                Log.e(TAG, "Could not empty expired trash", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TrashPurge"
        private const val ACTION_PURGE = "dev.aalman.budstikke.PURGE_TRASH"
    }
}

/**
 * Sets the daily purge alarm. With [onlyIfMissing], an alarm already set is left alone:
 * the app starts often, and resetting each time would keep pushing the first run back.
 */
fun schedulePurge(context: Context, onlyIfMissing: Boolean = false) {
    val intent = Intent(context, TrashPurgeReceiver::class.java).setAction("dev.aalman.budstikke.PURGE_TRASH")
    if (onlyIfMissing) {
        val existing = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
        if (existing != null) return
    }
    val pi = PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    // Inexact: the exact hour does not matter, and Android can batch it with other wakeups.
    context.getSystemService(AlarmManager::class.java).setInexactRepeating(
        AlarmManager.ELAPSED_REALTIME,
        SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HOUR,
        AlarmManager.INTERVAL_DAY,
        pi,
    )
}
