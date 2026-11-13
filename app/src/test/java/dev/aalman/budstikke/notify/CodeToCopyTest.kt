package dev.aalman.budstikke.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodeToCopyTest {

    private val none: (Long) -> Boolean = { false }

    @Test
    fun offersTheNewestCode() {
        val messages = listOf(
            1L to "Your verification code is 111111",
            2L to "Your verification code is 482913",
            3L to "Thanks!",
        )
        assertEquals(CodeToCopy(2, "482913"), codeToCopy(messages, none))
    }

    @Test
    fun skipsLockedMessages() {
        val messages = listOf(
            1L to "Your verification code is 111111",
            2L to "Your verification code is 482913",
        )
        assertEquals(CodeToCopy(1, "111111"), codeToCopy(messages, isLocked = { it == 2L }))
        assertNull(codeToCopy(messages, isLocked = { true }))
    }

    @Test
    fun offersNothingWithoutACode() {
        assertNull(codeToCopy(listOf(1L to "See you at 482913 Main St"), none))
        assertNull(codeToCopy(emptyList(), none))
    }
}
