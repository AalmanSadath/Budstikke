package dev.aalman.budstikke.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTimeTest {

    private val sydney = ZoneId.of("Australia/Sydney")

    @Test
    fun picksTheMomentOnTheLocalClock() {
        val expected = ZonedDateTime.of(2026, 11, 12, 23, 0, 0, 0, sydney).toInstant().toEpochMilli()
        assertEquals(expected, scheduleMillis(LocalDate.of(2026, 11, 12), 23, 0, sydney))
    }

    @Test
    fun nextFullHourRoundsUpOnTheLocalClock() {
        val now = ZonedDateTime.of(2026, 11, 12, 10, 37, 12, 0, sydney).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 11, 12, 11, 0, 0, 0, sydney).toInstant().toEpochMilli()
        assertEquals(expected, nextFullHour(now, sydney))
    }

    @Test
    fun nextFullHourFollowsHalfHourZones() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.of(2026, 11, 12, 10, 5, 0, 0, kolkata).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 11, 12, 11, 0, 0, 0, kolkata).toInstant().toEpochMilli()
        assertEquals(expected, nextFullHour(now, kolkata))
    }

    @Test
    fun nextFullHourCrossesMidnight() {
        val now = ZonedDateTime.of(2026, 11, 12, 23, 30, 0, 0, sydney).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 11, 13, 0, 0, 0, 0, sydney).toInstant().toEpochMilli()
        assertEquals(expected, nextFullHour(now, sydney))
    }
}
