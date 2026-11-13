package dev.aalman.budstikke.ui

import dev.aalman.budstikke.sms.Sim
import org.junit.Assert.assertEquals
import org.junit.Test

class SimBadgeTest {

    private val first = Sim(subscriptionId = 3, slotLabel = "SIM 1", name = "Telia", slot = 1)
    private val second = Sim(subscriptionId = 7, slotLabel = "SIM 2", name = "Telenor", slot = 2)

    @Test
    fun noBadgesWhenTurnedOff() {
        assertEquals(emptyMap<Int, Int>(), simSlots(listOf(first, second), enabled = false))
    }

    @Test
    fun noBadgesWithOneSim() {
        assertEquals(emptyMap<Int, Int>(), simSlots(listOf(second), enabled = true))
        assertEquals(emptyMap<Int, Int>(), simSlots(emptyList(), enabled = true))
    }

    @Test
    fun mapsEachSimToItsSlot() {
        assertEquals(mapOf(3 to 1, 7 to 2), simSlots(listOf(first, second), enabled = true))
    }
}
