package dev.aalman.budstikke.sms

import org.junit.Assert.assertEquals
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
}
