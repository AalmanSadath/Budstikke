package dev.aalman.budstikke.ui

import dev.aalman.budstikke.ui.theme.INK
import dev.aalman.budstikke.ui.theme.PAPER
import dev.aalman.budstikke.ui.theme.accentShade
import dev.aalman.budstikke.ui.theme.contrast
import dev.aalman.budstikke.ui.theme.onAccent
import dev.aalman.budstikke.ui.theme.parseHex
import dev.aalman.budstikke.ui.theme.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentTest {

    @Test
    fun parsesShortAndLongHex() {
        assertEquals(0x58A6FF, parseHex("#58a6ff"))
        assertEquals(0x58A6FF, parseHex("58A6FF"))
        assertEquals(0xFFAA00, parseHex("#fa0"))
        assertEquals(0xFFAA00, parseHex(" #FA0 "))
    }

    @Test
    fun rejectsAnythingElse() {
        assertNull(parseHex(""))
        assertNull(parseHex("#12345"))
        assertNull(parseHex("#ggg"))
        assertNull(parseHex("blue"))
    }

    @Test
    fun formatsHex() {
        assertEquals("#58a6ff", toHex(0x58A6FF))
        assertEquals("#000000", toHex(0))
    }

    @Test
    fun contrastMatchesKnownValues() {
        assertEquals(21.0, contrast(0x000000, 0xFFFFFF), 0.01)
        assertEquals(1.0, contrast(0x777777, 0x777777), 0.001)
    }

    @Test
    fun picksTheMoreReadableTextColour() {
        // BifroSSH's own pairs: its dark accent takes near-black, its light accent white.
        assertEquals(INK, onAccent(0x58A6FF))
        assertEquals(PAPER, onAccent(0x155397))
        assertEquals(INK, onAccent(0xFFEB3B))
        assertEquals(PAPER, onAccent(0x1A237E))
    }

    @Test
    fun chosenTextColourIsNeverTheWorseOfTheTwo() {
        for (accent in listOf(0x58A6FF, 0xA78BFA, 0x10B981, 0xEF4444, 0x777777, 0x00FF00)) {
            val chosen = contrast(accent, onAccent(accent))
            val other = contrast(accent, if (onAccent(accent) == INK) PAPER else INK)
            assertTrue("$accent", chosen >= other)
        }
    }

    @Test
    fun shadeMovesAwayFromTheTextColour() {
        // Near-black text: the shade moves towards white, so it gets lighter.
        assertTrue(luma(accentShade(0x58A6FF)) > luma(0x58A6FF))
        // White text: the shade moves towards near-black, so it gets darker.
        assertTrue(luma(accentShade(0x155397)) < luma(0x155397))
    }

    private fun luma(rgb: Int) = dev.aalman.budstikke.ui.theme.luminance(rgb)
}
