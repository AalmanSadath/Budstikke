package dev.aalman.budstikke.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Phone numbers go through Android's PhoneNumberUtils, which plain unit tests cannot run;
// those are checked on a device.
class SameSenderTest {

    @Test
    fun senderNamesMatchIgnoringCaseAndSpaces() {
        assertTrue(sameSender("MyBank", "mybank"))
        assertTrue(sameSender(" MyBank", "MyBank "))
    }

    @Test
    fun differentSenderNamesDoNotMatch() {
        assertFalse(sameSender("MyBank", "MyBank2"))
    }

    @Test
    fun aSenderNameNeverMatchesANumber() {
        assertFalse(sameSender("MyBank", "+61400000000"))
    }
}
