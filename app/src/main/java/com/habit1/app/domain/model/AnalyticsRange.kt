package com.habit1.app.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * An immutable, closed date range [startDate, endDate] (both inclusive) used for analytics queries.
 *
 * Uses civil calendar dates exclusively (LocalDate) to prevent timezone drift, DST slippage,
 * or time-of-day offsets.
 */
data class AnalyticsRange(
    val startDate: LocalDate,
    val endDate: LocalDate
) {
    init {
        require(!startDate.isAfter(endDate)) {
            "startDate ($startDate) must not be after endDate ($endDate)"
        }
    }

    /**
     * Total number of calendar days in this closed range.
     */
    val dayCount: Int
        get() = (ChronoUnit.DAYS.between(startDate, endDate) + 1).toInt()

    /**
     * Checks if [date] falls within this inclusive range.
     */
    operator fun contains(date: LocalDate): Boolean =
        !date.isBefore(startDate) && !date.isAfter(endDate)

    companion object {
        /**
         * Creates a range covering the last [days] calendar days ending on [endDate] (inclusive).
         * Example: days = 7 ending on 2026-09-21 produces [2026-09-15, 2026-09-21].
         */
        fun ofDaysEndingAt(endDate: LocalDate, days: Int): AnalyticsRange {
            require(days >= 1) { "days must be at least 1, but was $days" }
            return AnalyticsRange(
                startDate = endDate.minusDays(days.toLong() - 1),
                endDate = endDate
            )
        }

        /**
         * Creates a range covering [months] months ending on [endDate] (inclusive).
         * Example: months = 1 ending on 2026-09-21 produces [2026-08-22, 2026-09-21].
         */
        fun ofMonthsEndingAt(endDate: LocalDate, months: Long): AnalyticsRange {
            require(months >= 1) { "months must be at least 1, but was $months" }
            return AnalyticsRange(
                startDate = endDate.minusMonths(months).plusDays(1),
                endDate = endDate
            )
        }

        /**
         * Creates a range covering [years] years ending on [endDate] (inclusive).
         */
        fun ofYearsEndingAt(endDate: LocalDate, years: Long): AnalyticsRange {
            require(years >= 1) { "years must be at least 1, but was $years" }
            return AnalyticsRange(
                startDate = endDate.minusYears(years).plusDays(1),
                endDate = endDate
            )
        }
    }
}
