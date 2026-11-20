package dev.aalman.budstikke.sms

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.util.Log
import androidx.core.net.toUri
import dev.aalman.budstikke.data.sameSender
import dev.aalman.budstikke.mms.pdu.CharacterSets
import dev.aalman.budstikke.mms.pdu.EncodedStringValue
import dev.aalman.budstikke.mms.pdu.NotificationInd
import dev.aalman.budstikke.mms.pdu.PduHeaders
import dev.aalman.budstikke.mms.pdu.RetrieveConf

/** One part of an MMS: its text, or the bytes of a picture, video, sound or file. */
class MmsPartRecord(
    val contentType: String,
    val name: String? = null,
    val contentLocation: String? = null,
    val contentId: String? = null,
    val charset: Int = CharacterSets.UTF_8,
    /** The text of a text/plain part. */
    val text: String? = null,
    /** The bytes of any other part. */
    val data: ByteArray? = null,
)

/**
 * A whole MMS as the app moves it between the system store, the network, the trash and
 * backups. Dates are in seconds, as the store keeps them for MMS.
 */
class MmsRecord(
    val box: Int,
    val messageType: Int,
    val dateSeconds: Long,
    val dateSentSeconds: Long = 0,
    val read: Boolean,
    val seen: Boolean,
    val subscriptionId: Int,
    val from: String?,
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val subject: String? = null,
    val messageId: String? = null,
    val transactionId: String? = null,
    val contentType: String? = null,
    val parts: List<MmsPartRecord> = emptyList(),
    /** Where a message still waiting at the carrier can be downloaded from. */
    val contentLocation: String? = null,
    val messageSize: Long = 0,
    val expirySeconds: Long = 0,
) {
    /** The people in the conversation besides this phone: who sent it, or who it went to. */
    val others: List<String>
        get() = if (box == Mms.MESSAGE_BOX_INBOX) listOfNotNull(from) else to + cc

    /**
     * Who the conversation is with, given this phone's [own] numbers. A received message
     * sent to several people is a group: its sender and everyone else it went to. This
     * phone is left out by its number, or, when the SIM does not know its number or knows
     * an old one, by being the only one the message went to.
     */
    fun conversationWith(own: List<String>): List<String> {
        if (box != Mms.MESSAGE_BOX_INBOX) return distinctPeople(to + cc)
        val recipients = to + cc
        val others = recipients.filterNot { r -> own.any { sameSender(it, r) } }
        val rest = if (others.size == recipients.size && recipients.size <= 1) emptyList() else others
        return distinctPeople(listOfNotNull(from) + rest)
    }
}

/** [addresses] with blanks dropped, each person once however their number is written. */
internal fun distinctPeople(addresses: List<String>): List<String> {
    val out = ArrayList<String>()
    addresses.map { it.trim() }.filter { it.isNotEmpty() }.forEach { a -> if (out.none { sameSender(it, a) }) out += a }
    return out
}

/**
 * Reads and writes MMS in the system message store: the message row, its addresses and its
 * parts, each in their own table. The app's own code, in place of AOSP's PduPersister.
 */
class MmsStore(context: Context) {

    private val context = context.applicationContext
    private val resolver = this.context.contentResolver

    /**
     * Writes [record] into the store, filed into the thread of [MmsRecord.conversationWith],
     * and returns the new row's id. A failed part write leaves no half-written message.
     */
    fun insert(record: MmsRecord): Long? {
        val others = record.conversationWith(ownNumbers(context))
        if (others.isEmpty()) return null
        val threadId = runCatching { Telephony.Threads.getOrCreateThreadId(context, others.toSet()) }.getOrNull()
            ?: return null
        val values = ContentValues().apply {
            put(Mms.THREAD_ID, threadId)
            put(Mms.MESSAGE_BOX, record.box)
            put(Mms.MESSAGE_TYPE, record.messageType)
            put(Mms.DATE, record.dateSeconds)
            put(Mms.DATE_SENT, record.dateSentSeconds)
            put(Mms.READ, if (record.read) 1 else 0)
            put(Mms.SEEN, if (record.seen) 1 else 0)
            put(Mms.SUBSCRIPTION_ID, record.subscriptionId)
            put(Mms.MMS_VERSION, PduHeaders.CURRENT_MMS_VERSION)
            record.subject?.let {
                put(Mms.SUBJECT, it)
                put(Mms.SUBJECT_CHARSET, CharacterSets.UTF_8)
            }
            record.messageId?.let { put(Mms.MESSAGE_ID, it) }
            record.transactionId?.let { put(Mms.TRANSACTION_ID, it) }
            put(Mms.CONTENT_TYPE, record.contentType ?: "application/vnd.wap.multipart.related")
            put(Mms.TEXT_ONLY, if (record.parts.all { it.text != null || it.contentType == SMIL }) 1 else 0)
            record.contentLocation?.let { put(Mms.CONTENT_LOCATION, it) }
            if (record.messageSize > 0) put(Mms.MESSAGE_SIZE, record.messageSize)
            if (record.expirySeconds > 0) put(Mms.EXPIRY, record.expirySeconds)
        }
        val uri = resolver.insert(Mms.CONTENT_URI, values) ?: return null
        val id = uri.lastPathSegment?.toLongOrNull() ?: return null
        try {
            writeAddresses(id, record)
            record.parts.forEach { writePart(id, it) }
        } catch (e: Exception) {
            Log.e(TAG, "Could not write the MMS's addresses or parts", e)
            delete(listOf(id))
            return null
        }
        return id
    }

    /** The whole message with [id], parts included, or null when it is gone. */
    fun read(id: Long): MmsRecord? {
        val row = resolver.query(
            ContentUris.mms(id),
            arrayOf(
                Mms.MESSAGE_BOX, Mms.MESSAGE_TYPE, Mms.DATE, Mms.DATE_SENT, Mms.READ, Mms.SEEN,
                Mms.SUBSCRIPTION_ID, Mms.SUBJECT, Mms.MESSAGE_ID, Mms.TRANSACTION_ID, Mms.CONTENT_TYPE,
                Mms.CONTENT_LOCATION, Mms.MESSAGE_SIZE, Mms.EXPIRY,
            ),
            null, null, null,
        )?.use { c -> if (c.moveToFirst()) RowFields(c) else null } ?: return null
        val addresses = addresses(id)
        return MmsRecord(
            box = row.box,
            messageType = row.messageType,
            dateSeconds = row.date,
            dateSentSeconds = row.dateSent,
            read = row.read,
            seen = row.seen,
            subscriptionId = row.subscriptionId,
            from = addresses.firstOrNull { it.second == PduHeaders.FROM }?.first,
            to = addresses.filter { it.second == PduHeaders.TO }.map { it.first },
            cc = addresses.filter { it.second == PduHeaders.CC }.map { it.first },
            subject = row.subject,
            messageId = row.messageId,
            transactionId = row.transactionId,
            contentType = row.contentType,
            parts = readParts(id),
            contentLocation = row.contentLocation,
            messageSize = row.messageSize,
            expirySeconds = row.expiry,
        )
    }

    /** Takes these messages, with their addresses and parts, out of the store. */
    fun delete(ids: Collection<Long>) {
        ids.chunked(500).forEach { chunk ->
            resolver.delete(
                Mms.CONTENT_URI,
                "${Mms._ID} IN (${chunk.joinToString(",") { "?" }})",
                chunk.map { it.toString() }.toTypedArray(),
            )
        }
    }

    /**
     * Stores the notification that an MMS is waiting, so it shows in its conversation with a
     * Download button until the message itself is fetched. Returns the row's id.
     */
    fun insertNotification(ind: NotificationInd, subscriptionId: Int, read: Boolean): Long? {
        val from = ind.from?.string?.let(::plainAddress) ?: return null
        val threadId = runCatching { Telephony.Threads.getOrCreateThreadId(context, from) }.getOrNull() ?: return null
        val values = ContentValues().apply {
            put(Mms.THREAD_ID, threadId)
            put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_INBOX)
            put(Mms.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND)
            put(Mms.DATE, System.currentTimeMillis() / 1000)
            put(Mms.READ, if (read) 1 else 0)
            put(Mms.SEEN, if (read) 1 else 0)
            put(Mms.SUBSCRIPTION_ID, subscriptionId)
            put(Mms.MMS_VERSION, ind.mmsVersion)
            put(Mms.CONTENT_LOCATION, String(ind.contentLocation))
            put(Mms.MESSAGE_SIZE, ind.messageSize)
            put(Mms.EXPIRY, ind.expiry)
            ind.transactionId?.let { put(Mms.TRANSACTION_ID, String(it)) }
            ind.subject?.string?.let { put(Mms.SUBJECT, it) }
        }
        val id = resolver.insert(Mms.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull() ?: return null
        resolver.insert(
            "content://mms/$id/addr".toUri(),
            ContentValues().apply {
                put(Mms.Addr.ADDRESS, from)
                put(Mms.Addr.TYPE, PduHeaders.FROM)
                put(Mms.Addr.CHARSET, CharacterSets.UTF_8)
            },
        )
        return id
    }

    /** What a waiting notification row needs to download its message. */
    data class Waiting(val id: Long, val threadId: Long, val location: String, val transactionId: String?, val subscriptionId: Int)

    fun waiting(id: Long): Waiting? = resolver.query(
        ContentUris.mms(id),
        arrayOf(Mms.THREAD_ID, Mms.CONTENT_LOCATION, Mms.TRANSACTION_ID, Mms.SUBSCRIPTION_ID, Mms.MESSAGE_TYPE),
        null, null, null,
    )?.use { c ->
        if (!c.moveToFirst() || c.getInt(4) != PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) null
        else Waiting(id, c.getLong(0), c.getString(1) ?: return null, c.getString(2), c.getInt(3))
    }

    fun threadOf(id: Long): Long? = resolver.query(ContentUris.mms(id), arrayOf(Mms.THREAD_ID), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }

    /** Moves an outgoing message to [box]: sent, or failed. */
    fun setBox(id: Long, box: Int) {
        resolver.update(ContentUris.mms(id), ContentValues().apply { put(Mms.MESSAGE_BOX, box) }, null, null)
    }

    private fun writeAddresses(id: Long, record: MmsRecord) {
        val uri = "content://mms/$id/addr".toUri()
        val rows = buildList {
            add((record.from ?: INSERT_ADDRESS_TOKEN) to PduHeaders.FROM)
            record.to.forEach { add(it to PduHeaders.TO) }
            record.cc.forEach { add(it to PduHeaders.CC) }
        }
        rows.forEach { (address, type) ->
            resolver.insert(
                uri,
                ContentValues().apply {
                    put(Mms.Addr.ADDRESS, address)
                    put(Mms.Addr.TYPE, type)
                    put(Mms.Addr.CHARSET, CharacterSets.UTF_8)
                },
            ) ?: error("address not written")
        }
    }

    private fun writePart(id: Long, part: MmsPartRecord) {
        // The store keeps text and SMIL layout parts in its text column, with no file; only
        // other parts get a file to write their bytes into.
        val asText = part.text ?: if (part.contentType == SMIL) part.data?.toString(Charsets.UTF_8).orEmpty() else null
        val values = ContentValues().apply {
            put(Mms.Part.CONTENT_TYPE, part.contentType)
            part.name?.let { put(Mms.Part.NAME, it) }
            part.contentLocation?.let { put(Mms.Part.CONTENT_LOCATION, it) }
            part.contentId?.let { put(Mms.Part.CONTENT_ID, it) }
            put(Mms.Part.CHARSET, part.charset)
            asText?.let { put(Mms.Part.TEXT, it) }
        }
        val uri = resolver.insert("content://mms/$id/part".toUri(), values) ?: error("part not written")
        if (asText != null) return
        val data = part.data ?: return
        resolver.openOutputStream(uri)?.use { it.write(data) } ?: error("part data not written")
    }

    private fun addresses(id: Long): List<Pair<String, Int>> = resolver.query(
        "content://mms/$id/addr".toUri(),
        arrayOf(Mms.Addr.ADDRESS, Mms.Addr.TYPE),
        null, null, null,
    )?.use { c ->
        buildList {
            while (c.moveToNext()) {
                val address = c.getString(0) ?: continue
                if (address != INSERT_ADDRESS_TOKEN) add(address to c.getInt(1))
            }
        }
    } ?: emptyList()

    private fun readParts(id: Long): List<MmsPartRecord> = resolver.query(
        Mms.Part.CONTENT_URI,
        arrayOf(
            Mms.Part._ID, Mms.Part.CONTENT_TYPE, Mms.Part.NAME, Mms.Part.CONTENT_LOCATION,
            Mms.Part.CONTENT_ID, Mms.Part.CHARSET, Mms.Part.TEXT,
        ),
        "${Mms.Part.MSG_ID} = ?", arrayOf(id.toString()), "${Mms.Part.SEQ} ASC",
    )?.use { c ->
        buildList {
            while (c.moveToNext()) {
                val type = c.getString(1) ?: continue
                val isText = type == TEXT_PLAIN
                // A SMIL layout is kept as text, like text parts, and has no file to open.
                val isSmil = type == SMIL
                add(
                    MmsPartRecord(
                        contentType = type,
                        name = c.getString(2),
                        contentLocation = c.getString(3),
                        contentId = c.getString(4),
                        charset = if (c.isNull(5)) CharacterSets.UTF_8 else c.getInt(5),
                        text = if (isText) c.getString(6).orEmpty() else null,
                        data = when {
                            isText -> null
                            isSmil -> c.getString(6).orEmpty().toByteArray()
                            else -> resolver.openInputStream(ContentUris.part(c.getLong(0)))?.use { it.readBytes() }
                        },
                    ),
                )
            }
        }
    } ?: emptyList()

    private class RowFields(c: android.database.Cursor) {
        val box = c.getInt(0)
        val messageType = c.getInt(1)
        val date = c.getLong(2)
        val dateSent = c.getLong(3)
        val read = c.getInt(4) != 0
        val seen = c.getInt(5) != 0
        val subscriptionId = c.getInt(6)
        val subject: String? = c.getString(7)
        val messageId: String? = c.getString(8)
        val transactionId: String? = c.getString(9)
        val contentType: String? = c.getString(10)
        val contentLocation: String? = c.getString(11)
        val messageSize = c.getLong(12)
        val expiry = c.getLong(13)
    }

    companion object {
        private const val TAG = "MmsStore"
        const val TEXT_PLAIN = "text/plain"
        const val SMIL = "application/smil"

        /** Stands in for this phone's own number in an outgoing message's From address. */
        const val INSERT_ADDRESS_TOKEN = "insert-address-token"

        /**
         * Turns a downloaded message into a record for the store. Text parts are decoded to
         * strings in their charset; everything else keeps its bytes.
         */
        fun fromRetrieveConf(conf: RetrieveConf, subscriptionId: Int, read: Boolean): MmsRecord {
            val body = conf.body
            val parts = (0 until (body?.partsNum ?: 0)).map { i ->
                val p = body.getPart(i)
                val type = p.contentType?.let { String(it) }?.lowercase() ?: "application/octet-stream"
                val charset = if (p.charset == 0) CharacterSets.UTF_8 else p.charset
                MmsPartRecord(
                    contentType = type,
                    name = (p.name ?: p.filename)?.let { String(it) },
                    contentLocation = p.contentLocation?.let { String(it) },
                    contentId = p.contentId?.let { String(it) },
                    charset = charset,
                    text = if (type == TEXT_PLAIN) decode(p.data, charset) else null,
                    data = if (type == TEXT_PLAIN) null else p.data,
                )
            }
            return MmsRecord(
                box = Mms.MESSAGE_BOX_INBOX,
                messageType = PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF,
                dateSeconds = System.currentTimeMillis() / 1000,
                dateSentSeconds = conf.date,
                read = read,
                seen = read,
                subscriptionId = subscriptionId,
                from = conf.from?.string?.let(::plainAddress),
                to = conf.to.addresses(),
                cc = conf.cc.addresses(),
                subject = conf.subject?.string,
                messageId = conf.messageId?.let { String(it) },
                transactionId = conf.transactionId?.let { String(it) },
                contentType = conf.contentType?.let { String(it) },
                parts = parts,
            )
        }

        private fun Array<EncodedStringValue>?.addresses(): List<String> =
            this?.mapNotNull { it.string?.let(::plainAddress) } ?: emptyList()

        internal fun decode(data: ByteArray?, charset: Int): String {
            if (data == null) return ""
            val name = runCatching { CharacterSets.getMimeName(charset) }.getOrNull() ?: "UTF-8"
            return runCatching { String(data, charset(name)) }.getOrElse { String(data, Charsets.UTF_8) }
        }
    }
}

/** A number as the PDU carries it, without the `/TYPE=PLMN` suffix some carriers keep. */
internal fun plainAddress(raw: String): String = raw.substringBefore("/TYPE=").trim()

/** URIs of single MMS rows and parts in the system store. */
internal object ContentUris {
    fun mms(id: Long): Uri = android.content.ContentUris.withAppendedId(Mms.CONTENT_URI, id)
    fun part(partId: Long): Uri = "content://mms/part/$partId".toUri()
}
