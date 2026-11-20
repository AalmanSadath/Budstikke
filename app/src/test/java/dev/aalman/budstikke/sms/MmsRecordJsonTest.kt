package dev.aalman.budstikke.sms

import android.provider.Telephony.Mms
import dev.aalman.budstikke.mms.pdu.CharacterSets
import dev.aalman.budstikke.mms.pdu.PduHeaders
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MmsRecordJsonTest {

    @Test
    fun aRecordSurvivesTheRoundTrip() {
        val picture = byteArrayOf(-1, -40, -1, -32, 0, 16, 74, 70)
        val record = MmsRecord(
            box = Mms.MESSAGE_BOX_INBOX,
            messageType = PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF,
            dateSeconds = 1_700_000_000,
            dateSentSeconds = 1_699_999_990,
            read = false,
            seen = true,
            subscriptionId = 2,
            from = "+12135550101",
            to = listOf("+12135550199", "+12135550102"),
            cc = listOf("+12135550103"),
            subject = "Trip",
            parts = listOf(
                MmsPartRecord("image/jpeg", name = "view.jpg", contentLocation = "view.jpg", data = picture),
                MmsPartRecord("text/plain", text = "Look at the view ✨", charset = CharacterSets.UTF_8),
            ),
        )
        val back = MmsRecordJson.read(JSONObject(MmsRecordJson.write(record).toString()))!!
        assertEquals(record.box, back.box)
        assertEquals(record.dateSeconds, back.dateSeconds)
        assertEquals(record.dateSentSeconds, back.dateSentSeconds)
        assertEquals(record.read, back.read)
        assertEquals(record.from, back.from)
        assertEquals(record.to, back.to)
        assertEquals(record.cc, back.cc)
        assertEquals(record.subject, back.subject)
        assertEquals(2, back.parts.size)
        assertArrayEquals(picture, back.parts[0].data)
        assertEquals("view.jpg", back.parts[0].name)
        assertEquals("Look at the view ✨", back.parts[1].text)
    }

    @Test
    fun aWaitingMessageKeepsWhereToDownloadIt() {
        val record = MmsRecord(
            box = Mms.MESSAGE_BOX_INBOX,
            messageType = PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND,
            dateSeconds = 1,
            read = true,
            seen = true,
            subscriptionId = 1,
            from = "+12135550101",
            to = emptyList(),
            contentLocation = "http://mmsc.example/m/1",
            messageSize = 2048,
            expirySeconds = 604800,
        )
        val back = MmsRecordJson.read(MmsRecordJson.write(record))!!
        assertEquals("http://mmsc.example/m/1", back.contentLocation)
        assertEquals(2048L, back.messageSize)
        assertEquals(604800L, back.expirySeconds)
    }

    @Test
    fun somethingElseIsNotARecord() {
        assertNull(MmsRecordJson.read(JSONObject("""{"body":"hi"}""")))
    }

    @Test
    fun othersAreWhoTheConversationIsWith() {
        val inbox = MmsRecord(Mms.MESSAGE_BOX_INBOX, 132, 1, read = true, seen = true, subscriptionId = 1, from = "A", to = listOf("me"))
        val sent = MmsRecord(Mms.MESSAGE_BOX_SENT, 128, 1, read = true, seen = true, subscriptionId = 1, from = null, to = listOf("B"), cc = listOf("C"))
        assertEquals(listOf("A"), inbox.others)
        assertEquals(listOf("B", "C"), sent.others)
    }

    @Test
    fun addressesLoseTheirTypeSuffix() {
        assertEquals("+12135550101", plainAddress("+12135550101/TYPE=PLMN"))
        assertEquals("someone@example.com", plainAddress("someone@example.com"))
    }

    @Test
    fun textIsDecodedInItsCharset() {
        assertEquals("Grüß", MmsStore.decode("Grüß".toByteArray(Charsets.ISO_8859_1), CharacterSets.ISO_8859_1))
        assertEquals("✨", MmsStore.decode("✨".toByteArray(), CharacterSets.UTF_8))
        assertEquals("", MmsStore.decode(null, CharacterSets.UTF_8))
    }
}
