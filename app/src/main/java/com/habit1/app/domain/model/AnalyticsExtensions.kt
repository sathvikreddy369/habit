package com.habit1.app.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Breakdown of record instances in a given period.
 * Strictly separates scheduled, unscheduled, and unrecorded instances.
 */
data class RecordBreakdown(
    val completedCount: Int,
    val partialCount: Int,
    val skippedCount: Int,
    val missedCount: Int,
    val unrecordedCount: Int,
    val restCount: Int,
    val pausedCount: Int,
    val totalScheduledInstances: Int,
    val totalRecordedInstances: Int
)

/**
 * Period comparison between current range and preceding equivalent period.
 * Does not emit comparisons when insufficient data exists.
 */
data class PeriodComparison(
    val previousRange: AnalyticsRange,
    val previousCompletionRate: Float,
    val currentCompletionRate: Float,
    val rateDelta: Float, // currentCompletionRate - previousCompletionRate
    val previousCompletedCount: Int,
    val currentCompletedCount: Int,
    val completedDelta: Int,
    val isSufficientData: Boolean
)

/**
 * Daily aggregate completion point for global trend charts.
 */
data class DailyCompletionPoint(
    val date: LocalDate,
    val scheduledHabitsCount: Int,
    val completedHabitsCount: Int,
    val completionRate: Float, // 0..100%
    val isEligible: Boolean // false if scheduledHabitsCount == 0
)

/**
 * Deterministic insight derived from factual habit tracking data.
 * Purely mathematical, explainable, and free of gamification/AI fluff.
 */
data class HabitInsight(
    val id: String,
    val title: String,
    val description: String,
    val type: InsightType,
    val habitId: String? = null,
    val metricValue: String? = null
) {
    enum class InsightType {
        CONSISTENCY_HIGH,
        CONSISTENCY_IMPROVEMENT,
        CONSISTENCY_DROP,
        BEST_WEEKDAY,
        STREAK_MILESTONE,
        QUANTITATIVE_INCREASE,
        NEEDS_ATTENTION
    }
}
