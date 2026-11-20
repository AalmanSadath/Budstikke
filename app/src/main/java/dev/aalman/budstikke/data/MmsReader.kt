package dev.aalman.budstikke.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.Telephony.Mms
import androidx.annotation.StringRes
import androidx.core.net.toUri
import dev.aalman.budstikke.R
import dev.aalman.budstikke.mms.pdu.PduHeaders
import dev.aalman.budstikke.sms.MmsStore

/**
 * One number naming a message in either table of the system store: an SMS by its row id,
 * an MMS by its row id negated. Row ids are positive, so the two never meet, and every key
 * the app saved before MMS (locks, stars, reactions) is an SMS id that still means the same.
 */
object MessageKey {
    fun mms(rowId: Long): Long = -rowId
    fun isMms(key: Long): Boolean = key < 0
    fun mmsRowId(key: Long): Long = -key
}

/**
 * A picture, video, sound or file in an MMS, read from the store when shown or opened. The
 * part's URI is kept as text, so plain JVM tests can make one.
 */
data class Attachment(val uriString: String, val contentType: String, val name: String?) {
    val uri: Uri get() = uriString.toUri()
    val kind: AttachmentKind get() = attachmentKind(contentType)
}

/** What kind of attachment a part of [contentType] is. */
fun attachmentKind(contentType: String): AttachmentKind = when {
    contentType.startsWith("image/") -> AttachmentKind.Image
    contentType.startsWith("video/") -> AttachmentKind.Video
    contentType.startsWith("audio/") -> AttachmentKind.Audio
    contentType.equals("text/x-vcard", true) || contentType.equals("text/vcard", true) -> AttachmentKind.Contact
    else -> AttachmentKind.File
}

enum class AttachmentKind { Image, Video, Audio, Contact, File }

/** What an attachment is called where only a word fits: the list, notifications, search. */
@StringRes
fun attachmentLabel(kind: AttachmentKind?): Int = when (kind) {
    AttachmentKind.Image -> R.string.attachment_photo
    AttachmentKind.Video -> R.string.attachment_video
    AttachmentKind.Audio -> R.string.attachment_audio
    AttachmentKind.Contact -> R.string.attachment_contact
    AttachmentKind.File, null -> R.string.attachment_file
}

/** What an MMS waiting at the carrier shows until it is downloaded. */
data class WaitingMms(val rowId: Long, val sizeBytes: Long, val download: DownloadState)

/** An MMS reduced to what the conversation list and notifications need. */
internal data class MmsSummary(
    val key: Long,
    val threadId: Long,
    val dateMillis: Long,
    val inbox: Boolean,
    val read: Boolean,
    val failed: Boolean,
    val subscriptionId: Int,
    val text: String,
    val attachment: AttachmentKind?,
    val waiting: Boolean,
)

/** Reads MMS from the system store in the shapes the app's screens use. */
internal class MmsReader(private val resolver: ContentResolver) {

    /**
     * The thread's MMS as conversation messages, oldest first. In a [group], each received
     * message names who sent it.
     */
    fun threadMessages(threadId: Long, downloads: Map<Long, DownloadState>, group: Boolean = false): List<Message> {
        val rows = rows("${Mms.THREAD_ID} = ?", arrayOf(threadId.toString()))
        if (rows.isEmpty()) return emptyList()
        val parts = parts(rows.map { it.id })
        return rows.map { r ->
            val own = parts[r.id].orEmpty()
            Message(
                id = MessageKey.mms(r.id),
                body = own.filter { it.isText }.joinToString("\n") { it.text },
                dateMillis = r.dateSeconds * 1000,
                state = stateOf(r.box),
                subscriptionId = r.subscriptionId,
                attachments = own.filter { it.isAttachment }.map { Attachment(it.uri.toString(), it.contentType, it.name) },
                waiting = if (r.waiting) WaitingMms(r.id, r.size, downloads[r.id] ?: DownloadState.Waiting) else null,
                sender = if (group && r.box == Mms.MESSAGE_BOX_INBOX) cachedSender(r.id) else null,
            )
        }
    }

    /** Who sent a message never changes, so each is looked up once per process. */
    private fun cachedSender(rowId: Long): String? = senders[rowId] ?: sender(rowId)?.also { senders[rowId] = it }

    /** Every MMS, newest first, with its text and the kind of its first attachment. */
    fun summaries(selection: String? = null, args: Array<String>? = null): List<MmsSummary> {
        val rows = rows(selection, args)
        if (rows.isEmpty()) return emptyList()
        val parts = parts(rows.map { it.id }, withUris = false)
        return rows.map { r ->
            val own = parts[r.id].orEmpty()
            MmsSummary(
                key = MessageKey.mms(r.id),
                threadId = r.threadId,
                dateMillis = r.dateSeconds * 1000,
                inbox = r.box == Mms.MESSAGE_BOX_INBOX,
                read = r.read,
                failed = r.box == Mms.MESSAGE_BOX_FAILED,
                subscriptionId = r.subscriptionId,
                text = own.filter { it.isText }.joinToString(" ") { it.text }.trim(),
                attachment = own.firstOrNull { it.isAttachment }?.let { attachmentKind(it.contentType) },
                waiting = r.waiting,
            )
        }.sortedByDescending { it.dateMillis }
    }

    /** Thread ids of MMS whose text holds [query], newest first, as summaries. */
    fun matching(query: String, escapedLike: String): List<MmsSummary> {
        val ids = resolver.query(
            Mms.Part.CONTENT_URI, arrayOf(Mms.Part.MSG_ID),
            "${Mms.Part.CONTENT_TYPE} = ? AND ${Mms.Part.TEXT} LIKE ? ESCAPE '\\'",
            arrayOf(MmsStore.TEXT_PLAIN, "%$escapedLike%"), null,
        )?.use { c -> buildSet<Long> { while (c.moveToNext()) add(c.getLong(0)) } } ?: return emptyList()
        if (ids.isEmpty()) return emptyList()
        return ids.toList().chunked(500).flatMap { chunk ->
            summaries("${Mms._ID} IN (${chunk.joinToString(",")})", null)
        }.filter { it.text.contains(query, ignoreCase = true) }.sortedByDescending { it.dateMillis }
    }

    /**
     * Each thread's people besides this phone, from the store's own record of who a thread
     * is with. Covers threads that hold only MMS, whose rows carry no address.
     */
    fun threadRecipients(): Map<Long, List<String>> {
        val canonical = resolver.query(CANONICAL_ADDRESSES, arrayOf("_id", "address"), null, null, null)?.use { c ->
            buildMap<Long, String> { while (c.moveToNext()) put(c.getLong(0), c.getString(1).orEmpty()) }
        } ?: return emptyMap()
        return resolver.query(SIMPLE_THREADS, arrayOf("_id", "recipient_ids"), null, null, null)?.use { c ->
            buildMap<Long, List<String>> {
                while (c.moveToNext()) {
                    val ids = c.getString(1).orEmpty().split(' ').mapNotNull { it.toLongOrNull() }
                    put(c.getLong(0), ids.mapNotNull { canonical[it]?.takeIf(String::isNotBlank) })
                }
            }
        } ?: emptyMap()
    }

    /**
     * The people of one thread besides this phone, from the store's record of it, or an
     * empty list when the thread is unknown.
     */
    fun recipientsOf(threadId: Long): List<String> {
        val ids = resolver.query(SIMPLE_THREADS, arrayOf("recipient_ids"), "_id = ?", arrayOf(threadId.toString()), null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0).orEmpty().split(' ').mapNotNull { it.toLongOrNull() } else null }
            ?: return emptyList()
        if (ids.isEmpty()) return emptyList()
        val byId = resolver.query(CANONICAL_ADDRESSES, arrayOf("_id", "address"), "_id IN (${ids.joinToString(",")})", null, null)
            ?.use { c -> buildMap<Long, String> { while (c.moveToNext()) put(c.getLong(0), c.getString(1).orEmpty()) } }
            ?: return emptyList()
        return ids.mapNotNull { byId[it]?.takeIf(String::isNotBlank) }
    }

    /** Who sent the MMS with row [rowId], or null for none on record. */
    fun sender(rowId: Long): String? = resolver.query(
        "content://mms/$rowId/addr".toUri(), arrayOf(Mms.Addr.ADDRESS),
        "${Mms.Addr.TYPE} = ${PduHeaders.FROM}", null, null,
    )?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it != MmsStore.INSERT_ADDRESS_TOKEN } else null }

    private class Row(
        val id: Long,
        val threadId: Long,
        val dateSeconds: Long,
        val box: Int,
        val read: Boolean,
        val subscriptionId: Int,
        val waiting: Boolean,
        val size: Long,
    )

    private fun rows(selection: String?, args: Array<String>?): List<Row> {
        // Only messages: delivery and read reports are rows too, and drafts are not shown.
        val kinds = "${Mms.MESSAGE_TYPE} IN (${PduHeaders.MESSAGE_TYPE_SEND_REQ}, " +
            "${PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND}, ${PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF}) " +
            "AND ${Mms.MESSAGE_BOX} != ${Mms.MESSAGE_BOX_DRAFTS}"
        return resolver.query(
            Mms.CONTENT_URI,
            arrayOf(Mms._ID, Mms.THREAD_ID, Mms.DATE, Mms.MESSAGE_BOX, Mms.READ, Mms.SUBSCRIPTION_ID, Mms.MESSAGE_TYPE, Mms.MESSAGE_SIZE),
            if (selection == null) kinds else "($selection) AND $kinds",
            args,
            "${Mms.DATE} ASC",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Row(
                            id = c.getLong(0),
                            threadId = c.getLong(1),
                            dateSeconds = c.getLong(2),
                            box = c.getInt(3),
                            read = c.getInt(4) != 0,
                            subscriptionId = c.getInt(5),
                            waiting = c.getInt(6) == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND,
                            size = c.getLong(7),
                        ),
                    )
                }
            }
        } ?: emptyList()
    }

    private class Part(val uri: Uri, val contentType: String, val text: String, val name: String?) {
        val isText get() = contentType == MmsStore.TEXT_PLAIN
        val isAttachment get() = !isText && contentType != MmsStore.SMIL
    }

    private fun parts(ids: List<Long>, withUris: Boolean = true): Map<Long, List<Part>> {
        val out = HashMap<Long, MutableList<Part>>()
        ids.chunked(500).forEach { chunk ->
            resolver.query(
                Mms.Part.CONTENT_URI,
                arrayOf(Mms.Part._ID, Mms.Part.MSG_ID, Mms.Part.CONTENT_TYPE, Mms.Part.TEXT, Mms.Part.NAME, Mms.Part.FILENAME, Mms.Part.CONTENT_LOCATION),
                "${Mms.Part.MSG_ID} IN (${chunk.joinToString(",")})",
                null,
                "${Mms.Part.SEQ} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getString(2)?.lowercase() ?: continue
                    out.getOrPut(c.getLong(1)) { ArrayList() } += Part(
                        uri = if (withUris) "content://mms/part/${c.getLong(0)}".toUri() else Uri.EMPTY,
                        contentType = type,
                        text = c.getString(3).orEmpty(),
                        name = c.getString(4) ?: c.getString(5) ?: c.getString(6),
                    )
                }
            }
        }
        return out
    }

    private fun stateOf(box: Int): MessageState = when (box) {
        Mms.MESSAGE_BOX_INBOX -> MessageState.Received
        Mms.MESSAGE_BOX_OUTBOX -> MessageState.Sending
        Mms.MESSAGE_BOX_FAILED -> MessageState.Failed
        else -> MessageState.Sent
    }

    private companion object {
        val senders = java.util.concurrent.ConcurrentHashMap<Long, String>()
        val CANONICAL_ADDRESSES: Uri = "content://mms-sms/canonical-addresses".toUri()
        val SIMPLE_THREADS: Uri = "content://mms-sms/conversations?simple=true".toUri()
    }
}
