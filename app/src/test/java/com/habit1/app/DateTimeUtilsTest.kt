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
    fun testEpochToLocalDate() {
        val instant = Instant.parse("2026-09-20T12:00:00Z")
        val dateUtc = DateTimeUtils.toLocalDate(instant, ZoneId.of("UTC"))
        assertEquals(LocalDate.of(2026, 9, 20), dateUtc)
    }

    @Test
    fun testFormatLocalizedTime12Hour() {
        assertEquals("12:00 AM", DateTimeUtils.formatLocalizedTime(LocalTime.of(0, 0), is24Hour = false))
        assertEquals("12:30 AM", DateTimeUtils.formatLocalizedTime(LocalTime.of(0, 30), is24Hour = false))
        assertEquals("1:00 AM", DateTimeUtils.formatLocalizedTime(LocalTime.of(1, 0), is24Hour = false))
        assertEquals("8:30 AM", DateTimeUtils.formatLocalizedTime(LocalTime.of(8, 30), is24Hour = false))
        assertEquals("11:59 AM", DateTimeUtils.formatLocalizedTime(LocalTime.of(11, 59), is24Hour = false))
        assertEquals("12:00 PM", DateTimeUtils.formatLocalizedTime(LocalTime.of(12, 0), is24Hour = false))
        assertEquals("1:00 PM", DateTimeUtils.formatLocalizedTime(LocalTime.of(13, 0), is24Hour = false))
        assertEquals("8:30 PM", DateTimeUtils.formatLocalizedTime(LocalTime.of(20, 30), is24Hour = false))
        assertEquals("11:59 PM", DateTimeUtils.formatLocalizedTime(LocalTime.of(23, 59), is24Hour = false))
    }

    @Test
    fun testFormatLocalizedTime24Hour() {
        assertEquals("00:00", DateTimeUtils.formatLocalizedTime(LocalTime.of(0, 0), is24Hour = true))
        assertEquals("01:00", DateTimeUtils.formatLocalizedTime(LocalTime.of(1, 0), is24Hour = true))
        assertEquals("08:30", DateTimeUtils.formatLocalizedTime(LocalTime.of(8, 30), is24Hour = true))
        assertEquals("12:00", DateTimeUtils.formatLocalizedTime(LocalTime.of(12, 0), is24Hour = true))
        assertEquals("13:00", DateTimeUtils.formatLocalizedTime(LocalTime.of(13, 0), is24Hour = true))
        assertEquals("20:30", DateTimeUtils.formatLocalizedTime(LocalTime.of(20, 30), is24Hour = true))
        assertEquals("23:59", DateTimeUtils.formatLocalizedTime(LocalTime.of(23, 59), is24Hour = true))
    }

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
