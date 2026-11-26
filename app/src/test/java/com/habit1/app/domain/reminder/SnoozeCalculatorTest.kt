package com.habit1.app.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class SnoozeCalculatorTest {

    private val calculator = SnoozeCalculator()
    private val zoneId = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 25)

    @Test
    fun testSnooze_moreThanOneHourBeforeMidnight_adds60Minutes() {
        val now = LocalDateTime.of(today, LocalTime.of(14, 0))
        val result = calculator.calculateSnooze(now, zoneId)

        assertTrue(result is SnoozeResult.Scheduled)
        val scheduled = result as SnoozeResult.Scheduled
        assertEquals(LocalTime.of(15, 0), scheduled.triggerTime)
    }

    @Test
    fun testSnooze_lateEveningBeforeClamp_adds60Minutes() {
        val now = LocalDateTime.of(today, LocalTime.of(22, 45))
        val result = calculator.calculateSnooze(now, zoneId)

        assertTrue(result is SnoozeResult.Scheduled)
        val scheduled = result as SnoozeResult.Scheduled
        assertEquals(LocalTime.of(23, 45), scheduled.triggerTime)
    }

    @Test
    fun testSnooze_lessThanOneHourBeforeMidnight_clampsTo2350() {
        val now = LocalDateTime.of(today, LocalTime.of(23, 15))
        val result = calculator.calculateSnooze(now, zoneId)

        assertTrue(result is SnoozeResult.Scheduled)
        val scheduled = result as SnoozeResult.Scheduled
        assertEquals(LocalTime.of(23, 50), scheduled.triggerTime)
    }

    @Test
    fun testSnooze_60MinutesWouldCrossBoundary_clampsTo2350() {
        val now = LocalDateTime.of(today, LocalTime.of(22, 55))
        val result = calculator.calculateSnooze(now, zoneId)

        assertTrue(result is SnoozeResult.Scheduled)
        val scheduled = result as SnoozeResult.Scheduled
        assertEquals(LocalTime.of(23, 50), scheduled.triggerTime)
    }

    @Test
    fun testSnooze_at2350_disallowed() {
        val now = LocalDateTime.of(today, LocalTime.of(23, 50))
        val result = calculator.calculateSnooze(now, zoneId)

        assertTrue(result is SnoozeResult.DisallowedPastBoundary)
    }

    @Test
    fun testSnooze_after2350_disallowed() {
        val now = LocalDateTime.of(today, LocalTime.of(23, 55))
        val result = calculator.calculateSnooze(now, zoneId)

        assertTrue(result is SnoozeResult.DisallowedPastBoundary)
    }
}
