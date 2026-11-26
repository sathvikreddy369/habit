package com.habit1.app.domain.model

import java.time.LocalDate

/**
 * Distinct, explainable classification of a calendar day in a habit's history.
 *
 * Explicitly separates factual recorded data from schedule-derived projections:
 * - Completed, RecordedIncomplete, Skipped, ExplicitMissed are immutable factual records in SQLite.
 * - ProjectedMissed & ProjectedRest are projections based on the habit's applicable schedule.
 */
sealed interface CalendarDayStatus {

    /**
     * Date is strictly before the habit's effective startDate.
     * No expectation existed.
     */
    data object PreCreation : CalendarDayStatus

    /**
     * Date is in the future (> today).
     */
    data object Future : CalendarDayStatus

    /**
     * The habit is paused on this date via a temporal pause interval.
     * Does not count as missed, preserves streak without breaking.
     */
    data object Paused : CalendarDayStatus

    /**
     * Factually recorded completion on this calendar date (status == DONE).
     */
    data class Completed(
        val actualValue: Double,
        val targetValue: Double,
        val unit: String?,
        val measurementType: String
    ) : CalendarDayStatus

    /**
     * Factually recorded incomplete/partial attempt on this calendar date (status == PARTIAL).
     * Non-zero progress that did not reach the target.
     */
    data class RecordedIncomplete(
        val actualValue: Double,
        val targetValue: Double,
        val unit: String?,
        val measurementType: String
    ) : CalendarDayStatus

    /**
     * Factually recorded deliberate skip / rest on this calendar date (status == SKIPPED).
     * Acts as a neutral streak bridge; excluded from completion rate denominator.
     */
    data class Skipped(
        val actualValue: Double = 0.0,
        val targetValue: Double = 0.0,
        val unit: String? = null,
        val measurementType: String = ""
    ) : CalendarDayStatus

    /**
     * Factually recorded explicit missed acknowledgment by the user (status == MISSED).
     * Breaks streak and revokes morning grace.
     */
    data class ExplicitMissed(
        val notes: String? = null
    ) : CalendarDayStatus

    /**
     * Past date (< today) where no record exists, and the date was expected
     * based on the habit's schedule version applicable on that date.
     */
    data object ProjectedMissed : CalendarDayStatus

    /**
     * Past date (< today) where no record exists, and the date was an off-duty / rest day
     * based on the habit's schedule version applicable on that date.
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
