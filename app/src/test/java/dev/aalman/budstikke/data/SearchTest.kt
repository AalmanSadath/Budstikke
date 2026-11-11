package dev.aalman.budstikke.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {

    @Test
    fun escapesLikeWildcards() {
        assertEquals("50\\% off", escapeLike("50% off"))
        assertEquals("a\\_b", escapeLike("a_b"))
        assertEquals("c:\\\\x", escapeLike("c:\\x"))
        assertEquals("plain", escapeLike("plain"))
    }

    @Test
    fun matchesNameAndSenderIgnoringCase() {
        assertTrue(matchesConversation("Dad", "+61412345678", "dad"))
        assertTrue(matchesConversation("BANK", "BANK", "ban"))
        assertFalse(matchesConversation("Dad", "+61412345678", "mum"))
    }

    @Test
    fun matchesNumbersIgnoringSpacing() {
        assertTrue(matchesConversation("Dad", "+61412345678", "412 345"))
        assertTrue(matchesConversation("+61412345678", "+61412345678", "(412) 345"))
        assertTrue(matchesConversation("Dad", "0412-345-678", "0412345"))
    }

    @Test
    fun digitsInsideWordsAreNotANumberSearch() {
        assertFalse(matchesConversation("Dad", "+61412345678", "room 412"))
    }

    @Test
    fun excerptKeepsShortLeadsWhole() {
        assertEquals("See you at 5", excerpt("See you at 5", "you"))
    }

    @Test
    fun excerptStartsNearALateMatchAtAWordBoundary() {
        val body = "Thanks for dinner last night, it was lovely. The code for the gate is 4821."
        val shown = excerpt(body, "gate")
        assertTrue(shown, shown.startsWith("…"))
        assertTrue(shown, shown.contains("gate is 4821."))
        assertFalse(shown, shown.removePrefix("…").startsWith(" "))
        assertTrue(shown.length < body.length)
    }

    @Test
    fun excerptFlattensLineBreaks() {
        assertEquals("one two", excerpt("one\ntwo", "two"))
    }

    @Test
    fun findsEveryMatchIgnoringCase() {
        assertEquals(listOf(0..2, 8..10), matchRanges("Ana and ana", "ana").let { listOf(it[0], it[1]) })
        assertEquals(listOf(0..1, 2..3), matchRanges("aaaa", "aa"))
        assertEquals(emptyList<IntRange>(), matchRanges("abc", ""))
        assertEquals(emptyList<IntRange>(), matchRanges("abc", "x"))
    }
}
