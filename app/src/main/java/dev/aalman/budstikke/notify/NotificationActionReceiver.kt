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
import dev.aalman.budstikke.data.MessageKey
import dev.aalman.budstikke.data.MmsReader
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
                        copyCode(context, threadId, intent.getLongExtra(EXTRA_MESSAGE_ID, NO_MESSAGE))
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
        if (messageId == NO_MESSAGE || Locks.get(context).state().isLocked(threadId, messageId)) return
        val body = if (MessageKey.isMms(messageId)) {
            MmsReader(context.contentResolver)
                .summaries("${Telephony.Mms._ID} = ? AND ${Telephony.Mms.THREAD_ID} = ?", arrayOf(MessageKey.mmsRowId(messageId).toString(), threadId.toString()))
                .firstOrNull()?.text
        } else {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.BODY),
                "${Telephony.Sms._ID} = ? AND ${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(messageId.toString(), threadId.toString()),
                null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } ?: return
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
        context.contentResolver.update(
            Telephony.Mms.CONTENT_URI,
            ContentValues().apply {
                put(Telephony.Mms.READ, 1)
                put(Telephony.Mms.SEEN, 1)
            },
            "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0",
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

        /** No message: a [MessageKey] can be negative, for an MMS, so -1 cannot stand for none. */
        private const val NO_MESSAGE = Long.MIN_VALUE
        const val KEY_REPLY = "reply"
        private const val TAG = "NotificationAction"
    }
}
