package com.habit1.app.domain

import com.habit1.app.domain.model.AnalyticsRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalyticsRangeTest {

    @Test
    fun testSingleDayRange() {
        val date = LocalDate.of(2026, 9, 21)
        val range = AnalyticsRange(date, date)

        assertEquals(1, range.dayCount)
        assertTrue(range.contains(date))
        assertFalse(range.contains(date.minusDays(1)))
        assertFalse(range.contains(date.plusDays(1)))
    }

    @Test
    fun testInvalidRange_throwsException() {
        val start = LocalDate.of(2026, 9, 21)
        val end = LocalDate.of(2026, 9, 20)

        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsRange(start, end)
        }
    }

    @Test
    fun testOfDaysEndingAt_sevenDays() {
        val end = LocalDate.of(2026, 9, 21)
        val range = AnalyticsRange.ofDaysEndingAt(end, 7)

        assertEquals(LocalDate.of(2026, 9, 15), range.startDate)
        assertEquals(end, range.endDate)
        assertEquals(7, range.dayCount)
        assertTrue(range.contains(LocalDate.of(2026, 9, 15)))
        assertTrue(range.contains(LocalDate.of(2026, 9, 21)))
        assertFalse(range.contains(LocalDate.of(2026, 9, 14)))
        assertFalse(range.contains(LocalDate.of(2026, 9, 22)))
    }

    @Test
    fun testOfDaysEndingAt_thirtyDays() {
        val end = LocalDate.of(2026, 9, 21)
        val range = AnalyticsRange.ofDaysEndingAt(end, 30)

        assertEquals(30, range.dayCount)
        assertEquals(LocalDate.of(2026, 8, 23), range.startDate)
        assertEquals(end, range.endDate)
    }

    @Test
    fun testOfMonthsEndingAt() {
        val end = LocalDate.of(2026, 9, 21)
        val range = AnalyticsRange.ofMonthsEndingAt(end, 1)

        assertEquals(end, range.endDate)
        assertEquals(LocalDate.of(2026, 8, 22), range.startDate)
        assertTrue(range.dayCount >= 30)
    }

    @Test
    fun testOfYearsEndingAt_leapYearBoundary() {
        // 2024 was a leap year
        val end = LocalDate.of(2024, 3, 1)
        val range = AnalyticsRange(LocalDate.of(2024, 2, 28), end)

        assertEquals(3, range.dayCount) // Feb 28, Feb 29, Mar 1
        assertTrue(range.contains(LocalDate.of(2024, 2, 29)))
    }

    @Test
    fun testYearBoundaryCrossing() {
        val start = LocalDate.of(2025, 12, 25)
        val end = LocalDate.of(2026, 1, 5)
        val range = AnalyticsRange(start, end)

        assertEquals(12, range.dayCount)
        assertTrue(range.contains(LocalDate.of(2025, 12, 31)))
        assertTrue(range.contains(LocalDate.of(2026, 1, 1)))
        assertFalse(range.contains(LocalDate.of(2025, 12, 24)))
        assertFalse(range.contains(LocalDate.of(2026, 1, 6)))
    }

    @Test
    fun testInvalidDaysCount_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsRange.ofDaysEndingAt(LocalDate.now(), 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsRange.ofMonthsEndingAt(LocalDate.now(), -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsRange.ofYearsEndingAt(LocalDate.now(), 0)
        }
    }
}
