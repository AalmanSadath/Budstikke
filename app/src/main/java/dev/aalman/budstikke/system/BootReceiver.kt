package dev.aalman.budstikke.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.aalman.budstikke.sms.OutgoingQueue
import dev.aalman.budstikke.trash.schedulePurge
import kotlin.concurrent.thread

/**
 * Alarms do not survive a restart or an app update. After either, this sets them again:
 * the daily trash clean-up, and scheduled messages, sending at once any that fell due
 * while the phone was off.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        schedulePurge(context)
        val pending = goAsync()
        thread(name = "boot") {
            try {
                OutgoingQueue.sendDue(context)
            } catch (e: Exception) {
                Log.e("BootReceiver", "Could not send due messages", e)
            } finally {
                pending.finish()
            }
        }
    }
}
