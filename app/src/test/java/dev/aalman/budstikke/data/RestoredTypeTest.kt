package dev.aalman.budstikke.data

import android.provider.Telephony.Sms
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoredTypeTest {

    @Test
    fun messagesStillGoingOutComeBackAsFailed() {
        assertEquals(Sms.MESSAGE_TYPE_FAILED, restoredType(Sms.MESSAGE_TYPE_OUTBOX))
        assertEquals(Sms.MESSAGE_TYPE_FAILED, restoredType(Sms.MESSAGE_TYPE_QUEUED))
    }

    @Test
    fun otherMessagesKeepTheirType() {
        for (type in listOf(Sms.MESSAGE_TYPE_INBOX, Sms.MESSAGE_TYPE_SENT, Sms.MESSAGE_TYPE_FAILED)) {
            assertEquals(type, restoredType(type))
        }
    }
}
