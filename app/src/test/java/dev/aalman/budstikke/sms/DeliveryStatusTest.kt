package dev.aalman.budstikke.sms

import android.provider.Telephony
import dev.aalman.budstikke.data.MessageState
import dev.aalman.budstikke.data.stateOf
import org.junit.Assert.assertEquals
import org.junit.Test

class DeliveryStatusTest {

    @Test
    fun mapsTpStatusRangesToDeliveryStates() {
        assertEquals(Telephony.Sms.STATUS_COMPLETE, deliveryStatusOf(0x00))
        assertEquals(Telephony.Sms.STATUS_COMPLETE, deliveryStatusOf(0x1F))
        assertEquals(Telephony.Sms.STATUS_PENDING, deliveryStatusOf(0x20))
        assertEquals(Telephony.Sms.STATUS_PENDING, deliveryStatusOf(0x3F))
        assertEquals(Telephony.Sms.STATUS_FAILED, deliveryStatusOf(0x40))
        assertEquals(Telephony.Sms.STATUS_FAILED, deliveryStatusOf(0x65))
    }

    @Test
    fun mapsStoreRowsToMessageStates() {
        assertEquals(MessageState.Received, stateOf(Telephony.Sms.MESSAGE_TYPE_INBOX, Telephony.Sms.STATUS_NONE))
        assertEquals(MessageState.Sending, stateOf(Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.STATUS_NONE))
        assertEquals(MessageState.Sending, stateOf(Telephony.Sms.MESSAGE_TYPE_QUEUED, Telephony.Sms.STATUS_NONE))
        assertEquals(MessageState.Sent, stateOf(Telephony.Sms.MESSAGE_TYPE_SENT, Telephony.Sms.STATUS_NONE))
        assertEquals(MessageState.Sent, stateOf(Telephony.Sms.MESSAGE_TYPE_SENT, Telephony.Sms.STATUS_PENDING))
        assertEquals(MessageState.Delivered, stateOf(Telephony.Sms.MESSAGE_TYPE_SENT, Telephony.Sms.STATUS_COMPLETE))
        assertEquals(MessageState.Failed, stateOf(Telephony.Sms.MESSAGE_TYPE_SENT, Telephony.Sms.STATUS_FAILED))
        assertEquals(MessageState.Failed, stateOf(Telephony.Sms.MESSAGE_TYPE_FAILED, Telephony.Sms.STATUS_NONE))
    }
}
