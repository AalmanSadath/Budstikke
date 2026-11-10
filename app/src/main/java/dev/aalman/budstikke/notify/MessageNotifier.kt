package dev.aalman.budstikke.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.net.toUri
import dev.aalman.budstikke.MainActivity
import dev.aalman.budstikke.R

/**
 * One notification per conversation, listing its unread messages. It is rebuilt from the
 * message store every time, so it always matches what is unread.
 *
 * Reads the store, so call it off the main thread.
 */
class MessageNotifier(context: Context) {

    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    /** Shows or updates the thread's notification, or removes it when nothing is unread. */
    fun update(threadId: Long) {
        if (ActiveConversation.threadId == threadId) {
            cancel(threadId)
            return
        }
        val unread = unreadMessages(threadId)
        if (unread.isEmpty()) {
            cancel(threadId)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel()

        val address = unread.last().address
        val sender = Person.Builder().setName(address).setKey(address).build()
        val me = Person.Builder().setName(context.getString(R.string.notification_you)).build()
        val style = NotificationCompat.MessagingStyle(me)
        unread.forEach { style.addMessage(it.body, it.dateMillis, sender) }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(unread.last().dateMillis)
            .setShowWhen(true)
            .setNumber(unread.size)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(openIntent(threadId, address))
            .addAction(replyAction(threadId, address))
            .addAction(markReadAction(threadId))
            .build()
        manager.notify(notificationId(threadId), notification)
    }

    fun cancel(threadId: Long) {
        manager.cancel(notificationId(threadId))
    }

    /**
     * Marks the thread's notification as answered. Android shows a spinner on an inline
     * reply until the notification changes, and a sent reply leaves nothing unread.
     */
    fun replied(threadId: Long) = cancel(threadId)

    private fun ensureChannel() {
        val system = context.getSystemService(NotificationManager::class.java)
        if (system.getNotificationChannel(CHANNEL_ID) != null) return
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_messages),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    private fun openIntent(threadId: Long, address: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            notificationId(threadId),
            MainActivity.openConversation(context, threadId, address),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * Inline reply. The intent is mutable because Android adds the typed text to it; it
     * names an unexported receiver in this app.
     */
    private fun replyAction(threadId: Long, address: String): NotificationCompat.Action {
        val input = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY)
            .setLabel(context.getString(R.string.notification_reply_hint))
            .build()
        val intent = actionIntent(NotificationActionReceiver.ACTION_REPLY, threadId)
            .putExtra(NotificationActionReceiver.EXTRA_ADDRESS, address)
        val pending = PendingIntent.getBroadcast(
            context,
            notificationId(threadId),
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_send, context.getString(R.string.notification_reply), pending)
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    private fun markReadAction(threadId: Long): NotificationCompat.Action {
        val pending = PendingIntent.getBroadcast(
            context,
            notificationId(threadId),
            actionIntent(NotificationActionReceiver.ACTION_MARK_READ, threadId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(0, context.getString(R.string.notification_mark_read), pending)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }

    /** The thread id goes into the data URI so each conversation's intents stay distinct. */
    private fun actionIntent(action: String, threadId: Long): Intent =
        Intent(action, "budstikke://thread/$threadId".toUri())
            .setClass(context, NotificationActionReceiver::class.java)
            .putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)

    private data class Unread(val address: String, val body: String, val dateMillis: Long)

    /** The thread's unread received messages, oldest first, at most the last [MAX_LINES]. */
    private fun unreadMessages(threadId: Long): List<Unread> {
        val rows = ArrayList<Unread>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC LIMIT $MAX_LINES",
        )?.use { c ->
            while (c.moveToNext()) {
                rows += Unread(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getLong(2))
            }
        }
        return rows.asReversed()
    }

    companion object {
        private const val CHANNEL_ID = "messages"
        private const val MAX_LINES = 8

        /** Thread ids are small row ids in practice; folding keeps them distinct as ints. */
        fun notificationId(threadId: Long): Int = (threadId xor (threadId ushr 32)).toInt()
    }
}
