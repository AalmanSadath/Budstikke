package dev.aalman.budstikke.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BarItemsTest {

    private fun item(n: Int, inMenu: Boolean = false) = BarItem(n, n, inMenu) {}

    private fun split(items: List<BarItem>, slots: Int) = splitBarItems(items, slots).let { (a, b) -> a.map { it.icon } to b.map { it.icon } }

    @Test
    fun showsEveryButtonThatFits() {
        assertEquals(listOf(1, 2, 3) to emptyList<Int>(), split(listOf(item(1), item(2), item(3)), 3))
    }

    @Test
    fun movesTheLastIntoTheMenuWhenShort() {
        // The menu takes one of the three slots.
        assertEquals(listOf(1, 2) to listOf(3, 4, 5), split((1..5).map(::item), 3))
        assertEquals(emptyList<Int>() to listOf(1, 2), split(listOf(item(1), item(2)), 1))
        assertEquals(emptyList<Int>() to listOf(1, 2), split(listOf(item(1), item(2)), 0))
    }

    @Test
    fun keepsMenuOnlyItemsInTheMenu() {
        assertEquals(listOf(1, 2) to listOf(9), split(listOf(item(1), item(2), item(9, inMenu = true)), 5))
        assertEquals(listOf(1) to listOf(2, 9), split(listOf(item(1), item(2), item(9, inMenu = true)), 2))
    }
}
