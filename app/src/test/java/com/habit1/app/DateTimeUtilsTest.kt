package com.habit1.app

import com.habit1.app.core.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DateTimeUtilsTest {

    @Test
    fun testDateFormattingAndParsing() {
        val date = LocalDate.of(2026, 9, 20)
        val formatted = DateTimeUtils.formatDate(date)
        assertEquals("2026-09-20", formatted)

        val parsed = DateTimeUtils.parseDate("2026-09-20")
        assertEquals(date, parsed)
    }

    @Test
    fun testLeapYearHandling() {
        // 2024 is a leap year; 2026 is not.
        val leapDay = LocalDate.of(2024, 2, 29)
        val formatted = DateTimeUtils.formatDate(leapDay)
        assertEquals("2024-02-29", formatted)

        val parsed = DateTimeUtils.parseDate("2024-02-29")
        assertEquals(leapDay, parsed)
        assertTrue(parsed.isLeapYear)
    }

    @Test
    fun testTimeFormattingAndParsing() {
        val time = LocalTime.of(8, 30)
        val formatted = DateTimeUtils.formatTime(time)
        assertEquals("08:30", formatted)

        val parsed = DateTimeUtils.parseTime("08:30")
        assertEquals(time, parsed)
    }

    @Test
    fun testDaysBetween() {
        val date1 = LocalDate.of(2026, 1, 1)
        val date2 = LocalDate.of(2026, 1, 10)
        assertEquals(9L, DateTimeUtils.daysBetween(date1, date2))
    }

    @Test
    fun testEpochConversion() {
        val epochMillis = 1726850000000L
        val instant = DateTimeUtils.fromEpochMillis(epochMillis)
        assertEquals(epochMillis, DateTimeUtils.toEpochMillis(instant))
    }

    @Test
    fun testPastAndFutureChecks() {
        val testZone = ZoneId.of("UTC")
        val today = DateTimeUtils.today(testZone)
        val yesterday = today.minusDays(1)
        val tomorrow = today.plusDays(1)

        assertTrue(DateTimeUtils.isToday(today, testZone))
        assertTrue(DateTimeUtils.isPast(yesterday, testZone))
        assertTrue(DateTimeUtils.isFuture(tomorrow, testZone))
        assertFalse(DateTimeUtils.isPast(tomorrow, testZone))
        assertFalse(DateTimeUtils.isFuture(yesterday, testZone))
    }
}
