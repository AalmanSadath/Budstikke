package dev.aalman.budstikke.sms

import android.content.ContextWrapper
import android.provider.Telephony.Mms
import dev.aalman.budstikke.mms.pdu.PduHeaders
import dev.aalman.budstikke.mms.pdu.SendReq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsSendTest {

    @Test
    fun shrinkingTriesEveryQualityThenAThreeQuarterSize() {
        val steps = MmsImages.steps(1600)
        assertEquals(ShrinkStep(1600, 85), steps.first())
        assertEquals(ShrinkStep(1600, 40), steps[3])
        assertEquals(ShrinkStep(1200, 85), steps[4])
        assertEquals(MmsImages.SMALLEST_SIDE, steps.last().longestSide)
        assertTrue(steps.zipWithNext().all { (a, b) -> b.longestSide <= a.longestSide })
        // A limit below the smallest size still gets the smallest attempts.
        assertEquals(MmsImages.SMALLEST_SIDE, MmsImages.steps(100).first().longestSide)
    }

    @Test
    fun picturesShareWhatTheTextLeaves() {
        val limits = MmsLimits(maxMessageBytes = 300 * 1024, maxWidth = 1600, maxHeight = 1200)
        assertEquals((300 * 1024 - MmsImages.OVERHEAD_BYTES - 1000) / 2, MmsImages.budgetPerImage(limits, 1000, 2))
        assertEquals(0, MmsImages.budgetPerImage(limits, 0, 0))
        assertEquals(0, MmsImages.budgetPerImage(MmsLimits(1024, 1, 1), 0, 1))
    }

    @Test
    fun theLayoutComesFirstAndNamesEveryPart() {
        val parts = MmsSender.parts("Hi there", listOf(byteArrayOf(1), byteArrayOf(2)))
        assertEquals(listOf("application/smil", "image/jpeg", "image/jpeg", "text/plain"), parts.map { it.contentType })
        val smil = String(parts[0].data!!)
        assertTrue(smil.contains("""<img src="image0.jpg""""))
        assertTrue(smil.contains("""<img src="image1.jpg""""))
        assertTrue(smil.contains("""<text src="text0.txt""""))
        assertEquals("<smil>", parts[0].contentId)
        // Pictures alone carry no text part.
        assertEquals(2, MmsSender.parts(" ", listOf(byteArrayOf(1))).size)
    }

    @Test
    fun aComposedMessageReadsBackWithItsAddressesAndParts() {
        val record = MmsRecord(
            box = Mms.MESSAGE_BOX_OUTBOX,
            messageType = PduHeaders.MESSAGE_TYPE_SEND_REQ,
            dateSeconds = 1_700_000_000,
            read = true,
            seen = true,
            subscriptionId = 1,
            from = null,
            to = listOf("+12135550101", "+12135550102"),
            parts = MmsSender.parts("Look", listOf(byteArrayOf(9, 8, 7))),
        )
        val bytes = MmsPdu.compose(ContextWrapper(null), MmsSender.sendReq(record))!!
        val read = MmsPdu.parse(bytes) as SendReq
        assertEquals(listOf("+12135550101", "+12135550102"), read.to.map { it.string })
        assertEquals(3, read.body.partsNum)
        val types = (0 until read.body.partsNum).map { String(read.body.getPart(it).contentType) }.toSet()
        assertEquals(setOf("application/smil", "image/jpeg", "text/plain"), types)
    }
}
