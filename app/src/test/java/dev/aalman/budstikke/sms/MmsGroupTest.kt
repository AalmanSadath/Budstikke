package dev.aalman.budstikke.sms

import android.provider.Telephony.Mms
import dev.aalman.budstikke.mms.pdu.PduHeaders
import org.junit.Assert.assertEquals
import org.junit.Test

// Phone numbers are compared by Android's PhoneNumberUtils, which plain unit tests cannot
// run, so these use sender names where this phone's own number has to be matched.
class MmsGroupTest {

    private fun received(from: String, to: List<String>, cc: List<String> = emptyList()) = MmsRecord(
        box = Mms.MESSAGE_BOX_INBOX,
        messageType = PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF,
        dateSeconds = 0,
        read = false,
        seen = false,
        subscriptionId = 1,
        from = from,
        to = to,
        cc = cc,
    )

    @Test
    fun aMessageToThisPhoneAloneIsWithItsSender() {
        assertEquals(listOf("+111"), received("+111", listOf("+999")).conversationWith(own = emptyList()))
    }

    @Test
    fun aMessageToOneOtherNumberIsStillToThisPhone() {
        // The SIM can hold an old number, or the carrier write this one differently.
        assertEquals(listOf("Ingrid"), received("Ingrid", listOf("Sam")).conversationWith(own = listOf("Me")))
    }

    @Test
    fun aMessageToSeveralIsAGroupWithoutThisPhone() {
        val record = received("Ingrid", listOf("Me", "Sam"), cc = listOf("Kim"))
        assertEquals(listOf("Ingrid", "Sam", "Kim"), record.conversationWith(own = listOf("me")))
    }

    @Test
    fun withoutItsOwnNumberThePhoneCannotTellItselfApart() {
        val record = received("+111", listOf("+222", "+333"))
        assertEquals(listOf("+111", "+222", "+333"), record.conversationWith(own = emptyList()))
    }

    @Test
    fun aSentMessageIsWithEveryoneItWentTo() {
        val record = MmsRecord(
            box = Mms.MESSAGE_BOX_OUTBOX,
            messageType = PduHeaders.MESSAGE_TYPE_SEND_REQ,
            dateSeconds = 0,
            read = true,
            seen = true,
            subscriptionId = 1,
            from = null,
            to = listOf("Ingrid", " ", "Sam"),
            cc = listOf("ingrid"),
        )
        assertEquals(listOf("Ingrid", "Sam"), record.conversationWith(own = listOf("Ingrid")))
    }
}
