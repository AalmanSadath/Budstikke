package dev.aalman.budstikke.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingSmsTest {

    @Test
    fun joinsPartsInOrderAndKeepsFirstSenderAndTime() {
        val sms = joinParts(
            listOf(
                SmsPart("+4712345678", "Hello, ", 1_000),
                SmsPart("+4712345678", "this is ", 1_001),
                SmsPart("+4712345678", "one message", 1_002),
            ),
        )
        assertEquals(IncomingSms("+4712345678", "Hello, this is one message", 1_000), sms)
    }

    @Test
    fun treatsMissingBodyAsEmpty() {
        val sms = joinParts(listOf(SmsPart("BANK", null, 5)))
        assertEquals("", sms?.body)
    }

    @Test
    fun returnsNullWithoutParts() {
        assertNull(joinParts(emptyList()))
    }

    @Test
    fun returnsNullWithoutSender() {
        assertNull(joinParts(listOf(SmsPart(" ", "text", 0))))
    }
}
