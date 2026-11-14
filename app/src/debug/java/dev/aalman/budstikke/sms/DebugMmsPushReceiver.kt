package dev.aalman.budstikke.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SubscriptionManager
import android.util.Log
import kotlin.concurrent.thread

/**
 * Debug builds only. The emulator's modem cannot deliver a WAP push, so this stands in for
 * it: the fake MMS centre in the emulator toolkit sends the push's bytes as a hex string,
 * and they go through the same [MmsIncoming] the real push uses. Not exported, so only root
 * (`adb root`, then `am broadcast -n`) can reach it.
 */
class DebugMmsPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hex = intent.getStringExtra("pdu") ?: return
        val pdu = runCatching { hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull() ?: return
        val pending = goAsync()
        thread(name = "mms-debug-push") {
            try {
                MmsIncoming.handle(context, pdu, SubscriptionManager.getDefaultSmsSubscriptionId())
            } catch (e: Exception) {
                Log.e("DebugMmsPushReceiver", "Could not handle the test push", e)
            } finally {
                pending.finish()
            }
        }
    }
}
