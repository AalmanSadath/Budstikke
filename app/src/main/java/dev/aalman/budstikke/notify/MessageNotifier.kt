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
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import dev.aalman.budstikke.MainActivity
import dev.aalman.budstikke.R
import dev.aalman.budstikke.data.AddressList
import dev.aalman.budstikke.data.AttachmentKind
import dev.aalman.budstikke.data.ContactRepository
import dev.aalman.budstikke.data.Member
import dev.aalman.budstikke.data.membersTitle
import dev.aalman.budstikke.data.Locks
import dev.aalman.budstikke.data.MessageKey
import dev.aalman.budstikke.data.MmsReader
import dev.aalman.budstikke.data.attachmentLabel

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

        // A group's notification names each message's sender; one person's, just them.
        val reader = MmsReader(context.contentResolver)
        val recipients = reader.recipientsOf(threadId)
        val group = recipients.size > 1
        val addresses = if (group) recipients else listOf(unread.last().address)
        val contacts = ContactRepository.get(context)
        val people = HashMap<String, Person>()
        fun personFor(address: String): Person = people.getOrPut(address) {
            val contact = contacts.lookup(address)
            Person.Builder()
                .setName(contact?.name ?: address)
                .setKey(address)
                .apply { contact?.photoUri?.let(contacts::photo)?.let { setIcon(IconCompat.createWithBitmap(it)) } }
                .build()
        }
        val me = Person.Builder().setName(context.getString(R.string.notification_you)).build()
        val title = if (group) membersTitle(addresses.map { Member(it, contacts.lookup(it)) }) else null
        // A locked conversation's or message's text stays off the lock screen and the shade,
        // and so does its picture.
        val locks = Locks.get(context).state()
        val lockedText = context.getString(R.string.locked_message)
        val photoLabel = context.getString(R.string.attachment_photo)
        // A received picture shows in the notification, scaled down; never a locked one.
        val pictures = unread.filter { it.image && !locks.isLocked(threadId, it.id) }.mapNotNull { m ->
            val rowId = MessageKey.mmsRowId(m.id)
            reader.firstImage(rowId)?.let { NotificationImages.forMessage(context, threadId, rowId, it) }?.let { m.id to it }
        }.toMap()
        NotificationImages.keepOnly(context, threadId, pictures.keys.mapTo(HashSet(), MessageKey::mmsRowId))
        // MessagingStyle has an apply() of its own, so this builds it with also.
        fun style(withPictures: Boolean) = NotificationCompat.MessagingStyle(me).also { style ->
            if (title != null) {
                style.isGroupConversation = true
                style.conversationTitle = title
            }
            unread.forEach {
                val body = if (locks.isLocked(threadId, it.id)) lockedText else it.body
                val sender = personFor(it.address.ifEmpty { addresses.first() })
                val picture = if (withPictures) pictures[it.id] else null
                if (picture != null) {
                    // Android shows a message with a picture as the picture alone, so its
                    // text follows as a message of its own.
                    style.addMessage(
                        NotificationCompat.MessagingStyle.Message(photoLabel, it.dateMillis, sender).setData("image/jpeg", picture),
                    )
                    if (it.hasText) style.addMessage(body, it.dateMillis, sender)
                } else {
                    style.addMessage(body, it.dateMillis, sender)
                }
            }
        }
        val address = AddressList.join(addresses)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style(withPictures = true))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(unread.last().dateMillis)
            .setShowWhen(true)
            .setNumber(unread.size)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(openIntent(threadId, address))
        // A code is something to copy, not answer: its notification offers Copy code alone,
        // and the system is kept from adding a second copy button.
        val code = codeToCopy(unread.map { it.id to it.body }) { locks.isLocked(threadId, it) }
        if (code != null) {
            builder.addAction(copyCodeAction(threadId, code.messageId))
            builder.setAllowSystemGeneratedContextualActions(false)
        } else {
            builder.addAction(replyAction(threadId, address))
            builder.addAction(markReadAction(threadId))
        }
        // On a lock screen that hides private content the text-only version stands in,
        // with no pictures.
        if (pictures.isNotEmpty()) {
            builder.setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setStyle(style(withPictures = false))
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setWhen(unread.last().dateMillis)
                    .setShowWhen(true)
                    .build(),
            )
        }
        val notification = builder.build()
        manager.notify(notificationId(threadId), notification)
    }

    fun cancel(threadId: Long) {
        manager.cancel(notificationId(threadId))
        NotificationImages.clear(context, threadId)
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

    /**
     * Copy code. Only the message id travels in the intent; the receiver reads the code
     * from the message again, so it is never copied from a message locked since. It is a
     * contextual action, one drawn from the message text, which Android shows as a chip
     * with its icon; plain notification buttons show text only.
     */
    private fun copyCodeAction(threadId: Long, messageId: Long): NotificationCompat.Action {
        val pending = PendingIntent.getBroadcast(
            context,
            notificationId(threadId),
            actionIntent(NotificationActionReceiver.ACTION_COPY_CODE, threadId)
                .putExtra(NotificationActionReceiver.EXTRA_MESSAGE_ID, messageId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_copy, context.getString(R.string.notification_copy_code), pending)
            .setContextual(true)
            .setShowsUserInterface(false)
            .build()
    }

    /** The thread id goes into the data URI so each conversation's intents stay distinct. */
    private fun actionIntent(action: String, threadId: Long): Intent =
        Intent(action, "budstikke://thread/$threadId".toUri())
            .setClass(context, NotificationActionReceiver::class.java)
            .putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)

    private data class Unread(
        val id: Long,
        val address: String,
        val body: String,
        val dateMillis: Long,
        /** An MMS whose first attachment is a picture. */
        val image: Boolean = false,
        /** Whether [body] is the message's own text, rather than what it carries. */
        val hasText: Boolean = true,
    )

    /** The thread's unread received messages, oldest first, at most the last [MAX_LINES]. */
    private fun unreadMessages(threadId: Long): List<Unread> {
        val rows = ArrayList<Unread>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms._ID),
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC LIMIT $MAX_LINES",
        )?.use { c ->
            while (c.moveToNext()) {
                rows += Unread(c.getLong(3), c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getLong(2))
            }
        }
        // MMS have no address of their own row; who sent each is in its address table.
        val reader = MmsReader(context.contentResolver)
        val mms = reader.summaries(
            "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0 AND ${Telephony.Mms.MESSAGE_BOX} = ${Telephony.Mms.MESSAGE_BOX_INBOX}",
            arrayOf(threadId.toString()),
        ).take(MAX_LINES).map { m ->
            val body = when {
                m.waiting -> context.getString(R.string.mms_waiting)
                m.text.isNotEmpty() -> m.text
                else -> context.getString(attachmentLabel(m.attachment))
            }
            Unread(
                m.key, reader.sender(MessageKey.mmsRowId(m.key)) ?: rows.firstOrNull()?.address.orEmpty(), body, m.dateMillis,
                image = !m.waiting && m.attachment == AttachmentKind.Image,
                hasText = !m.waiting && m.text.isNotEmpty(),
            )
        }
        return (rows + mms).sortedBy { it.dateMillis }.takeLast(MAX_LINES)
    }

    companion object {
        private const val CHANNEL_ID = "messages"
        private const val MAX_LINES = 8

        /** Thread ids are small row ids in practice; folding keeps them distinct as ints. */
        fun notificationId(threadId: Long): Int = (threadId xor (threadId ushr 32)).toInt()
    }
}
