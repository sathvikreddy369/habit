package com.habit1.app.domain.model

import java.time.LocalDate

/**
 * Distinct, explainable classification of a calendar day in a habit's history.
 *
 * Explicitly separates factual recorded data from schedule-derived projections:
 * - Completed & RecordedIncomplete are immutable factual records in SQLite.
 * - ProjectedMissed & ProjectedRest are projections based on the habit's current schedule,
 *   as historical schedule definition changes are not logged in the database.
 */
sealed interface CalendarDayStatus {

    /**
     * Date is strictly before the habit's civil creation date.
     * No expectation existed.
     */
    data object PreCreation : CalendarDayStatus

    /**
     * Date is in the future (> today).
     */
    data object Future : CalendarDayStatus

    /**
     * The habit is currently paused. No expectation existed on this date.
     * Does not count as missed, does not break streaks.
     */
    data object Paused : CalendarDayStatus

    /**
     * Factually recorded completion on this calendar date.
     */
    data class Completed(
        val actualValue: Double,
        val targetValue: Double,
        val unit: String?,
        val measurementType: String
    ) : CalendarDayStatus

    /**
     * Factually recorded incomplete/partial attempt on this calendar date (e.g. 10/20 reps).
     * Distinct from a day with no record at all.
     */
    data class RecordedIncomplete(
        val actualValue: Double,
        val targetValue: Double,
        val unit: String?,
        val measurementType: String
    ) : CalendarDayStatus

    /**
     * Past date (< today) where no record exists, and the date was expected
     * based on the habit's current schedule projection.
     */
    data object ProjectedMissed : CalendarDayStatus

    /**
     * Past date (< today) where no record exists, and the date was an off-duty / rest day
     * based on the habit's current schedule projection.
     */
    data object ProjectedRest : CalendarDayStatus
}

/**
 * Represents a specific calendar day in a habit's history.
 */
data class HabitHistoryDay(
    val date: LocalDate,
    val status: CalendarDayStatus
)
