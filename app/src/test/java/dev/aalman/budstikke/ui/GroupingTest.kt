package dev.aalman.budstikke.ui

import dev.aalman.budstikke.data.Message
import dev.aalman.budstikke.data.MessageState
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupingTest {

    private val minute = 60_000L

    private fun msg(id: Long, atMinute: Long, mine: Boolean) =
        Message(id, "m$id", atMinute * minute, if (mine) MessageState.Sent else MessageState.Received, 1)

    @Test
    fun joinsRunsFromTheSameSide() {
        val places = bubblePlaces(listOf(msg(1, 0, false), msg(2, 1, false), msg(3, 2, true), msg(4, 3, true), msg(5, 4, true)))
        assertEquals(listOf(false, true, false, true, true), places.map { it.joinedAbove })
        assertEquals(listOf(true, false, true, true, false), places.map { it.joinedBelow })
        assertEquals(listOf(true, false, false, false, false), places.map { it.timeLabel })
    }

    @Test
    fun aLongPauseStartsANewGroupUnderALabel() {
        val places = bubblePlaces(listOf(msg(1, 0, true), msg(2, 59, true), msg(3, 59 + 60, true)))
        assertEquals(listOf(true, false, true), places.map { it.timeLabel })
        assertEquals(listOf(false, true, false), places.map { it.joinedAbove })
        assertEquals(listOf(true, false, false), places.map { it.joinedBelow })
    }

    @Test
    fun handlesNoMessages() {
        assertEquals(emptyList<BubblePlace>(), bubblePlaces(emptyList()))
    }
}
