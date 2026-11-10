package dev.aalman.budstikke.notify

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.RemoteInput
import dev.aalman.budstikke.sms.SmsSender
import kotlin.concurrent.thread

/** Reply and Mark as read from a message notification. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        if (threadId < 0) return
        val action = intent.action
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.trim()
        val address = intent.getStringExtra(EXTRA_ADDRESS)

        val pending = goAsync()
        thread(name = "notification-action") {
            try {
                val notifier = MessageNotifier(context)
                when (action) {
                    ACTION_REPLY -> {
                        if (!reply.isNullOrEmpty() && address != null) {
                            SmsSender(context).send(address, reply, lastSubscription(context, threadId))
                            markRead(context, threadId)
                        }
                        notifier.replied(threadId)
                    }
                    ACTION_MARK_READ -> {
                        markRead(context, threadId)
                        notifier.cancel(threadId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Notification action failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun markRead(context: Context, threadId: Long) {
        context.contentResolver.update(
            Telephony.Sms.CONTENT_URI,
            ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            },
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
            arrayOf(threadId.toString()),
        )
    }

    /** A reply goes out on the SIM the conversation's last message came in on. */
    private fun lastSubscription(context: Context, threadId: Long): Int =
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.SUBSCRIPTION_ID),
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC LIMIT 1",
        )?.use { c -> if (c.moveToFirst()) c.getInt(0) else null }
            ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID

    companion object {
        const val ACTION_REPLY = "dev.aalman.budstikke.NOTIFICATION_REPLY"
        const val ACTION_MARK_READ = "dev.aalman.budstikke.NOTIFICATION_MARK_READ"
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_ADDRESS = "address"
        const val KEY_REPLY = "reply"
        private const val TAG = "NotificationAction"
    }
}
