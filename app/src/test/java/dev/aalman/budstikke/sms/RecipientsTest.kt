package dev.aalman.budstikke.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipientsTest {

    @Test
    fun splitsOnCommasAndSemicolonsAndDropsTheQuery() {
        assertEquals(
            listOf("+4712345678", "+4787654321", "98765"),
            parseRecipients("+4712345678; +4787654321,98765?body=hi"),
        )
    }

    @Test
    fun ignoresEmptyEntries() {
        assertEquals(listOf("12345"), parseRecipients(" , 12345 ;; "))
    }

    @Test
    fun noRecipientsGivesEmptyList() {
        assertEquals(emptyList<String>(), parseRecipients(""))
    }

    @Test
    fun typedRecipientIsTheTextAfterTheLastSeparator() {
        assertEquals("", typedRecipient(""))
        assertEquals("mu", typedRecipient("mu"))
        assertEquals("da", typedRecipient("+4712345678, da"))
        assertEquals("", typedRecipient("+4712345678, "))
        assertEquals("04", typedRecipient("1;2; 04"))
    }

    @Test
    fun pickingReplacesOnlyTheTypedRecipient() {
        assertEquals("+4712345678, ", withRecipient("mu", "+4712345678"))
        assertEquals("111, 222, ", withRecipient("111, da", "222"))
        assertEquals("111, 222, ", withRecipient("111,da", "222"))
        assertEquals("111; 222, ", withRecipient("111; ", "222"))
    }

    @Test
    fun onlyNumbersCanBeSentToAsTyped() {
        assertTrue(looksLikeNumber("+47 123 45 678"))
        assertTrue(looksLikeNumber("(04) 1234-5678"))
        assertFalse(looksLikeNumber("Mum"))
        assertFalse(looksLikeNumber("Mum 2"))
        assertFalse(looksLikeNumber("+"))
    }
}
