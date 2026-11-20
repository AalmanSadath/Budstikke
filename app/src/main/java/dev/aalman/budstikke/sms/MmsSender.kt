package dev.aalman.budstikke.sms

import android.content.Context
import android.provider.Telephony.Mms
import android.util.Log
import dev.aalman.budstikke.mms.ContentType
import dev.aalman.budstikke.mms.pdu.CharacterSets
import dev.aalman.budstikke.mms.pdu.EncodedStringValue
import dev.aalman.budstikke.mms.pdu.PduBody
import dev.aalman.budstikke.mms.pdu.PduHeaders
import dev.aalman.budstikke.mms.pdu.PduPart
import dev.aalman.budstikke.mms.pdu.SendReq
import java.util.concurrent.TimeUnit

/**
 * Sends MMS: writes the message to the store's outbox, so the conversation shows it at once,
 * composes it, and hands it to Android's MMS service through [MmsSendQueue]. Its result
 * moves the message to sent or failed. Call off the main thread.
 */
object MmsSender {

    /**
     * Sends [text] and [images] (JPEGs, already shrunk to fit) to [addresses]. Returns the
     * new message's row id, or null when it could not be stored.
     */
    fun send(context: Context, addresses: List<String>, text: String, images: List<ByteArray>, subscriptionId: Int): Long? {
        val record = MmsRecord(
            box = Mms.MESSAGE_BOX_OUTBOX,
            messageType = PduHeaders.MESSAGE_TYPE_SEND_REQ,
            dateSeconds = System.currentTimeMillis() / 1000,
            read = true,
            seen = true,
            subscriptionId = subscriptionId,
            from = null,
            to = addresses,
            contentType = ContentType.MULTIPART_RELATED,
            parts = parts(text, images),
        )
        val store = MmsStore(context)
        val id = store.insert(record) ?: return null
        dispatch(context, id, record)
        return id
    }

    /** Sends a failed MMS again, from what the store kept of it. */
    fun retry(context: Context, rowId: Long) {
        val store = MmsStore(context)
        val record = store.read(rowId) ?: return
        if (record.box != Mms.MESSAGE_BOX_FAILED && record.box != Mms.MESSAGE_BOX_OUTBOX) return
        store.setBox(rowId, Mms.MESSAGE_BOX_OUTBOX)
        dispatch(context, rowId, record)
    }

    private fun dispatch(context: Context, rowId: Long, record: MmsRecord) {
        val limits = MmsLimits.of(context, record.subscriptionId)
        val bytes = MmsPdu.compose(context, sendReq(record))
        if (bytes == null || bytes.size > limits.maxMessageBytes) {
            Log.w(TAG, "MMS not sent: ${bytes?.size ?: "no"} bytes, limit ${limits.maxMessageBytes}")
            MmsStore(context).setBox(rowId, Mms.MESSAGE_BOX_FAILED)
            return
        }
        MmsSendQueue.sendRaw(context, bytes, record.subscriptionId, rowId)
    }

    /**
     * The message's parts: a SMIL layout first, as the composer names the first part as the
     * message's start, then each picture, then the text. The layout shows each picture in
     * turn with the text under the last, which is how phones without a layout of their own
     * arrange it.
     */
    internal fun parts(text: String, images: List<ByteArray>): List<MmsPartRecord> {
        val pictures = images.mapIndexed { i, data ->
            MmsPartRecord(ContentType.IMAGE_JPEG, name = "image$i.jpg", contentLocation = "image$i.jpg", contentId = "<image$i>", data = data)
        }
        val words = text.takeIf { it.isNotBlank() }?.let {
            MmsPartRecord(MmsStore.TEXT_PLAIN, name = "text0.txt", contentLocation = "text0.txt", contentId = "<text0>", text = it)
        }
        val layout = MmsPartRecord(
            ContentType.APP_SMIL, contentLocation = "smil.xml", contentId = "<smil>",
            data = smil(pictures.mapNotNull { it.contentLocation }, words?.contentLocation).toByteArray(),
        )
        return listOf(layout) + pictures + listOfNotNull(words)
    }

    internal fun smil(pictures: List<String>, text: String?): String {
        val textRef = text?.let { """<text src="$it" region="Text"/>""" }.orEmpty()
        val slides = if (pictures.isEmpty()) {
            listOf("""<par dur="5000ms">$textRef</par>""")
        } else {
            pictures.mapIndexed { i, src ->
                """<par dur="5000ms"><img src="$src" region="Image"/>${if (i == pictures.lastIndex) textRef else ""}</par>"""
            }
        }
        return "<smil><head><layout><root-layout/>" +
            """<region id="Image" fit="meet" top="0" left="0" height="80%" width="100%"/>""" +
            """<region id="Text" top="80%" left="0" height="20%" width="100%"/>""" +
            "</layout></head><body>${slides.joinToString("")}</body></smil>"
    }

    /** The PDU to send for [record]: an M-Send.req addressed from this phone. */
    internal fun sendReq(record: MmsRecord): SendReq = SendReq().apply {
        // The carrier fills in this phone's number in place of the token.
        from = EncodedStringValue(PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR)
        setTo(record.to.map { EncodedStringValue(it) }.toTypedArray())
        record.cc.forEach { addCc(EncodedStringValue(it)) }
        date = record.dateSeconds
        messageClass = PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray()
        expiry = TimeUnit.DAYS.toSeconds(7)
        priority = PduHeaders.PRIORITY_NORMAL
        deliveryReport = PduHeaders.VALUE_NO
        readReport = PduHeaders.VALUE_NO
        contentType = (record.contentType ?: ContentType.MULTIPART_RELATED).toByteArray()
        body = PduBody().apply {
            record.parts.forEach { p ->
                addPart(
                    PduPart().apply {
                        contentType = p.contentType.toByteArray()
                        p.name?.let { name = it.toByteArray() }
                        p.contentLocation?.let { contentLocation = it.toByteArray() }
                        p.contentId?.let { contentId = it.toByteArray() }
                        if (p.text != null) {
                            charset = CharacterSets.UTF_8
                            data = p.text.toByteArray(Charsets.UTF_8)
                        } else {
                            data = p.data ?: ByteArray(0)
                        }
                    },
                )
            }
        }
        messageSize = record.parts.sumOf { (it.data?.size ?: 0) + (it.text?.toByteArray()?.size ?: 0) }.toLong()
    }

    private const val TAG = "MmsSender"
}
