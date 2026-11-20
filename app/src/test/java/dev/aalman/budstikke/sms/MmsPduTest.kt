package dev.aalman.budstikke.sms

import android.content.ContextWrapper
import dev.aalman.budstikke.mms.ContentType
import dev.aalman.budstikke.mms.pdu.CharacterSets
import dev.aalman.budstikke.mms.pdu.EncodedStringValue
import dev.aalman.budstikke.mms.pdu.PduBody
import dev.aalman.budstikke.mms.pdu.PduParser
import dev.aalman.budstikke.mms.pdu.PduPart
import dev.aalman.budstikke.mms.pdu.SendReq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class MmsPduTest {

    // ContextWrapper(null) stands in for a Context: the composer only needs one to read part
    // data from content URIs, and these parts carry their bytes.
    private val context = ContextWrapper(null)

    /** An M-Notification.ind as the fake MMS centre sends it, from +12135550104. */
    private val notificationHex =
        "8C8298616263008D928918802B31323133353535303130342F545950453D504C4D4E008A808E01AA" +
            "88058103093A8083687474703A2F2F31302E302E322E323A383038302F6D2F626261636435633000"

    @Test
    fun readsANotification() {
        val n = assertNotNull(MmsPdu.notification(hex(notificationHex)))
        assertEquals("+12135550104", n.from.string)
        assertEquals("http://10.0.2.2:8080/m/bbacd5c0", String(n.contentLocation))
        assertEquals(170L, n.messageSize)
        assertEquals("abc", String(n.transactionId))
    }

    @Test
    fun composedMessageReadsBack() {
        val bytes = assertNotNull(MmsPdu.compose(context, sendReq(parts = 2)))
        val read = assertNotNull(MmsPdu.parse(bytes) as? SendReq)
        assertEquals(listOf("+12135550101"), read.to.map { it.string })
        // The parser may move a part to the front (checkPartPosition), so order is not kept.
        val texts = (0 until read.body.partsNum).map { String(read.body.getPart(it).data) }.sorted()
        assertEquals(listOf("part 0", "part 1"), texts)
    }

    @Test
    fun refusesWhatIsTooLarge() {
        val bytes = assertNotNull(MmsPdu.compose(context, sendReq(parts = 1)))
        assertNull(MmsPdu.parse(bytes, maxBytes = bytes.size - 1))
        assertNull(MmsPdu.parse(ByteArray(0)))
        assertNull(MmsPdu.parse(assertNotNull(MmsPdu.compose(context, sendReq(parts = MmsPdu.MAX_PARTS + 1)))))
        assertNotNull(MmsPdu.parse(assertNotNull(MmsPdu.compose(context, sendReq(parts = MmsPdu.MAX_PARTS)))))
    }

    @Test(timeout = 20_000)
    fun brokenInputNeverEscapes() {
        val random = Random(42)
        val valid = listOf(hex(notificationHex), assertNotNull(MmsPdu.compose(context, sendReq(parts = 3))))
        for (pdu in valid) {
            // Every truncation, and every single byte changed to a few values.
            for (n in 1 until pdu.size) MmsPdu.parse(pdu.copyOf(n))
            for (i in pdu.indices) {
                for (b in intArrayOf(0x00, 0x1F, 0x7F, 0x80, 0xFF, random.nextInt(256))) {
                    MmsPdu.parse(pdu.copyOf().also { it[i] = b.toByte() })
                }
            }
        }
        // Random bytes, some starting like a real header.
        repeat(20_000) {
            val bytes = random.nextBytes(random.nextInt(1, 600))
            if (random.nextBoolean()) {
                bytes[0] = 0x8C.toByte()
                if (bytes.size > 1) bytes[1] = (0x80 + random.nextInt(8)).toByte()
            }
            MmsPdu.parse(bytes)
        }
    }

    /** mmspdu/bounds.patch: a part claiming far more data than the message holds. */
    @Test
    fun aForgedPartLengthIsRefusedBeforeAllocating() {
        val pdu = assertNotNull(MmsPdu.compose(context, sendReq(parts = 1)))
        val data = "part 0".toByteArray()
        val at = (0..pdu.size - data.size).first { i -> data.indices.all { pdu[i + it] == data[it] } }
        // The part is: header length, data length (6), headers, data. Find the data length byte.
        val lengthAt = (at - 2 downTo 1).first { k -> pdu[k] == 6.toByte() && pdu[k - 1].toInt() == at - k - 1 }
        val forged = pdu.copyOfRange(0, lengthAt) + uintvar(2_000_000_000) + pdu.copyOfRange(lengthAt + 1, pdu.size)
        // The parser itself, not the wrapper, so an OutOfMemoryError would fail the test.
        assertNull(PduParser(forged, true).parse())
    }

    private fun uintvar(value: Int): ByteArray {
        val out = ArrayList<Byte>()
        var n = value
        out.add((n and 0x7F).toByte())
        n = n ushr 7
        while (n != 0) {
            out.add(0, ((n and 0x7F) or 0x80).toByte())
            n = n ushr 7
        }
        return out.toByteArray()
    }

    private fun sendReq(parts: Int): SendReq = SendReq().apply {
        addTo(EncodedStringValue("+12135550101/TYPE=PLMN"))
        body = PduBody().apply {
            repeat(parts) { i ->
                addPart(
                    PduPart().apply {
                        contentType = ContentType.TEXT_PLAIN.toByteArray()
                        charset = CharacterSets.UTF_8
                        contentLocation = "text$i.txt".toByteArray()
                        data = "part $i".toByteArray()
                    },
                )
            }
        }
        contentType = ContentType.MULTIPART_MIXED.toByteArray()
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun <T> assertNotNull(value: T?): T {
        org.junit.Assert.assertNotNull(value)
        return value!!
    }
}
