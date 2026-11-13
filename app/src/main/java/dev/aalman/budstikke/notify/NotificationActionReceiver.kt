package dev.aalman.budstikke.notify

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.RemoteInput
import dev.aalman.budstikke.R
import dev.aalman.budstikke.data.Locks
import dev.aalman.budstikke.sms.findOneTimeCode
import dev.aalman.budstikke.sms.SmsSender
import kotlin.concurrent.thread

/** Reply, Mark as read and Copy code from a message notification. */
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
                    ACTION_COPY_CODE -> {
                        copyCode(context, threadId, intent.getLongExtra(EXTRA_MESSAGE_ID, -1L))
                        // The code has been taken; it does not stay in the shade.
                        markRead(context, threadId)
                        notifier.cancel(threadId)
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

    /**
     * Reads the message again and copies its code, unless it has been locked or deleted
     * since the notification was posted.
     */
    private fun copyCode(context: Context, threadId: Long, messageId: Long) {
        if (messageId < 0 || Locks.get(context).state().isLocked(threadId, messageId)) return
        val body = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY),
            "${Telephony.Sms._ID} = ? AND ${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(messageId.toString(), threadId.toString()),
            null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: return
        copyCode(context, findOneTimeCode(body)?.code ?: return)
        // Android 13 and later confirm a copy themselves.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, R.string.code_copied, Toast.LENGTH_SHORT).show()
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
        const val ACTION_COPY_CODE = "dev.aalman.budstikke.NOTIFICATION_COPY_CODE"
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val KEY_REPLY = "reply"
        private const val TAG = "NotificationAction"
    }
}
