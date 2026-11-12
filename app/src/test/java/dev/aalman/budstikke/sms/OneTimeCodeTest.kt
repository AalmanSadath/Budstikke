package dev.aalman.budstikke.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OneTimeCodeTest {

    private fun code(text: String) = findOneTimeCode(text)?.code

    @Test
    fun findsCommonCodeMessages() {
        assertEquals("482913", code("Your code is 482913"))
        assertEquals("482913", code("G-482913 is your Google verification code."))
        assertEquals("123456", code("123456 is your Instagram code. Don't share it."))
        assertEquals("7731", code("Your OTP for login is 7731. Valid for 10 minutes."))
        assertEquals("9921", code("PIN: 9921"))
        assertEquals("55443322", code("Use 55443322 to sign in"))
    }

    @Test
    fun findsGroupedAndLetterCodes() {
        assertEquals("384112", code("Use verification code 384-112 to sign in"))
        assertEquals("384112", code("Your code: 384 112"))
        assertEquals("A7K2Q9", code("Your security code: A7K2Q9"))
    }

    @Test
    fun findsCodesInOtherLanguages() {
        assertEquals("554433", code("Din engangskode er 554433"))
        assertEquals("8812", code("Tu código es 8812"))
    }

    @Test
    fun marksWhereTheCodeSits() {
        val text = "Your code is 482913."
        val found = findOneTimeCode(text)!!
        assertEquals("482913", text.substring(found.range))
    }

    @Test
    fun ignoresMessagesThatDoNotMentionACode() {
        assertNull(code("Pay 4500 by Friday"))
        assertNull(code("See you at 1930"))
        assertNull(code("Room 4021 on the second floor"))
    }

    @Test
    fun ignoresAmounts() {
        assertNull(code("Your code for the refund: $1500"))
        assertNull(code("OTP sent. Rs 2500 debited from your account"))
        assertNull(code("Verification fee 1250 NOK... kr 1250"))
        assertNull(code("Code for 20% off: 2000%"))
        assertEquals("482913", code("Your code is 482913. Amount: $1500.00"))
    }

    @Test
    fun ignoresDatesTimesAndDecimals() {
        assertNull(code("Your verification code expires on 12/11/2026"))
        assertNull(code("Code valid until 2026.11.12"))
        assertNull(code("Meeting at 12:30, code red"))
        assertNull(code("Your PIN changed: 1,250 points left"))
    }

    @Test
    fun ignoresPhoneAndOrderNumbers() {
        assertNull(code("Call 0412 345 678 to verify your account"))
        assertNull(code("+4712345678 sent you a code"))
        assertNull(code("Order #48213 shipped, see the code on the box"))
    }

    @Test
    fun picksTheCandidateNearestAKeyword() {
        assertEquals("661204", code("Order 4021 is ready. Your pickup code is 661204"))
    }
}
