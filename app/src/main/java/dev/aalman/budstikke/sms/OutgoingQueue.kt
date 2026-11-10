package dev.aalman.budstikke.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dev.aalman.budstikke.data.Outgoing
import dev.aalman.budstikke.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

/**
 * Sends messages from [Outgoing] when their time comes. One alarm is kept for the soonest
 * message; [Outgoing] sets it again after every change, so the app need not be running.
 *
 * Every function does disk and radio work and must run off the main thread.
 */
object OutgoingQueue {

    private const val TAG = "OutgoingQueue"
    const val ACTION_SEND_DUE = "dev.aalman.budstikke.SEND_DUE"

    /**
     * Sends a message typed in the app: at once, or held back for the send delay set in
     * Settings, during which the conversation shows a countdown and Cancel.
     */
    fun sendOrHold(context: Context, threadId: Long, address: String, body: String, subscriptionId: Int) {
        val delaySeconds = SettingsRepository.get(context).settings.value.sendDelaySeconds
        if (delaySeconds <= 0) {
            SmsSender(context).send(address, body, subscriptionId)
            return
        }
        val sendAt = System.currentTimeMillis() + delaySeconds * 1000L
        val id = Outgoing.get(context).add(threadId, address, body, subscriptionId, sendAt, delayed = true)
        sendWhenDue(context, id, sendAt)
    }

    /** Sends one message now, if nothing else has sent or cancelled it. */
    fun sendNow(context: Context, id: Long) {
        val message = Outgoing.get(context).claim(id) ?: return
        SmsSender(context).send(message.address, message.body, message.subscriptionId)
    }

    private val timers = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Sends a held-back message when its delay ends, from inside the app so it goes on the
     * second the countdown shows. The alarm stays set as a fallback if the app is closed
     * first; whichever claims it first sends it.
     */
    fun sendWhenDue(context: Context, id: Long, sendAtMillis: Long) {
        val app = context.applicationContext
        timers.launch {
            delay((sendAtMillis - System.currentTimeMillis()).coerceAtLeast(0))
            sendNow(app, id)
        }
    }

    /** Sends everything whose time has come, including what fell due while the phone was off. */
    fun sendDue(context: Context) {
        val outgoing = Outgoing.get(context)
        val sender = SmsSender(context)
        for (due in outgoing.due()) {
            val message = outgoing.claim(due.id) ?: continue
            try {
                sender.send(message.address, message.body, message.subscriptionId)
            } catch (e: Exception) {
                Log.e(TAG, "Could not send a scheduled message", e)
            }
        }
        // Claiming moves the alarm on; this covers a run that found nothing due.
        arm(context)
    }

    /**
     * Sets the alarm for the soonest message, or clears it when nothing waits. Exact when
     * Android allows it, which it does on install for this app (USE_EXACT_ALARM); otherwise
     * Android may send it a few minutes late rather than not at all.
     */
    fun arm(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, OutgoingReceiver::class.java).setAction(ACTION_SEND_DUE)
        val pi = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val next = Outgoing.get(context).nextSendAt()
        if (next == null) {
            alarms.cancel(pi)
            return
        }
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
    }
}

/** The alarm for scheduled messages. Only this app's own PendingIntent reaches it. */
class OutgoingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != OutgoingQueue.ACTION_SEND_DUE) return
        val pending = goAsync()
        thread(name = "send-due") {
            try {
                OutgoingQueue.sendDue(context)
            } finally {
                pending.finish()
            }
        }
    }
}
