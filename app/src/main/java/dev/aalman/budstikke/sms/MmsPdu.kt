package dev.aalman.budstikke.sms

import android.content.Context
import dev.aalman.budstikke.mms.pdu.GenericPdu
import dev.aalman.budstikke.mms.pdu.MultimediaMessagePdu
import dev.aalman.budstikke.mms.pdu.NotificationInd
import dev.aalman.budstikke.mms.pdu.PduComposer
import dev.aalman.budstikke.mms.pdu.PduParser
import dev.aalman.budstikke.mms.pdu.RetrieveConf

/**
 * The only way the app reads or writes MMS PDUs. The bytes come from the carrier, or from
 * anyone who knows the phone's number, so the AOSP parser in `mmspdu` runs behind these limits
 * and never throws into the app: anything it cannot read, or chokes on, is null.
 */
object MmsPdu {

    /** Larger than any carrier sends (most cap MMS at 300 KB to 1 MB), small enough to hold. */
    const val MAX_PDU_BYTES = 5 * 1024 * 1024

    /** More parts than any real message has; a PDU claiming more is treated as unreadable. */
    const val MAX_PARTS = 50

    fun parse(bytes: ByteArray, maxBytes: Int = MAX_PDU_BYTES): GenericPdu? {
        if (bytes.isEmpty() || bytes.size > maxBytes) return null
        val pdu = try {
            PduParser(bytes, true).parse()
        } catch (_: Exception) {
            null
        } catch (_: StackOverflowError) {
            null
        } catch (_: OutOfMemoryError) {
            // mmspdu/bounds.patch stops the allocations a forged length asks for; this is the
            // backstop if one is missed. A single refused allocation leaves the heap as it was.
            null
        } ?: return null
        val parts = (pdu as? MultimediaMessagePdu)?.body?.partsNum ?: 0
        return if (parts > MAX_PARTS) null else pdu
    }

    /** The notification an MMS is waiting, as a WAP push delivers it. */
    fun notification(bytes: ByteArray): NotificationInd? = parse(bytes) as? NotificationInd

    /** A downloaded message. */
    fun retrieveConf(bytes: ByteArray, maxBytes: Int = MAX_PDU_BYTES): RetrieveConf? =
        parse(bytes, maxBytes) as? RetrieveConf

    /** Encodes [pdu] for sending, or null when the composer cannot. */
    fun compose(context: Context, pdu: GenericPdu): ByteArray? = try {
        PduComposer(context, pdu).make()
    } catch (_: Exception) {
        null
    }
}
