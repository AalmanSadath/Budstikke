package dev.aalman.budstikke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InitialsTest {

    @Test
    fun usesFirstAndLastWord() {
        assertEquals("CD", initialsOf("Carlos Diaz"))
        assertEquals("AS", initialsOf("Aalman Mohammed Sadath"))
    }

    @Test
    fun singleWordGivesOneLetter() {
        assertEquals("D", initialsOf("Dad"))
    }

    @Test
    fun uppercasesAndTrims() {
        assertEquals("EM", initialsOf("  emma   mills "))
    }

    @Test
    fun skipsWordsNotStartingWithLetterOrDigit() {
        assertEquals("JS", initialsOf("John Smith (Work)"))
    }

    @Test
    fun keepsNonLatinLetters() {
        assertEquals("ÆØ", initialsOf("æsa østby"))
    }

    @Test
    fun nothingUsableGivesNull() {
        assertNull(initialsOf(""))
        assertNull(initialsOf("(—)"))
    }
}
