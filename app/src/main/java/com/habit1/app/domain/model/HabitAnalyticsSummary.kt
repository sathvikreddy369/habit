package com.habit1.app.domain.model

import java.time.DayOfWeek

/**
 * Day-of-week adherence breakdown (Monday through Sunday) per Decision 5.1.3.
 */
data class WeekdayAdherence(
    val dayOfWeek: DayOfWeek,
    val completedCount: Int,
    val eligibleScheduledCount: Int,
    val adherenceRate: Float? // null if eligibleScheduledCount == 0 (displayed as '—')
)

/**
 * Authoritative, read-only analytics summary for a habit over an [AnalyticsRange].
 *
 * Preserves Historical Truth (Product Principle #7):
 * - Factual records are never rewritten or fabricated.
 * - Schedules determine expected/missed days for unrecorded past dates, while recorded completions
 *   and values remain immutable fact.
 * - Missed days and recorded incomplete days are strictly separated.
 * - Current-day pending state does not depress completion rate prematurely before midnight.
 */
data class HabitAnalyticsSummary(
    val habit: Habit,
    val range: AnalyticsRange,

    // Schedule & Completion Aggregates within range
    val scheduledDays: Int,
    val eligibleScheduledDays: Int = scheduledDays,
    val completedDays: Int,
    val recordedIncompleteDays: Int,
    val missedDays: Int,
    val restDays: Int,
    val completionRate: Float,

    // Streaks (authoritative from CalculateStreaksUseCase)
    val currentStreak: Int,
    val longestStreak: Int,

    // Quantitative Statistics (non-null for Count, Duration, Quantity; null for BooleanChoice)
    val quantitativeStats: QuantitativeAnalyticsStats?,

    // Daily breakdown across the range (for heatmaps and trend graphs)
    val dailyBreakdown: List<HabitHistoryDay>,

    // Monday-to-Sunday 7-day adherence breakdown (Decision 5.1.3)
    val weekdayAdherence: List<WeekdayAdherence> = emptyList(),

    // Period comparison with preceding equivalent period
    val periodComparison: PeriodComparison? = null,

    // Specific deterministic insights for this habit
    val insights: List<HabitInsight> = emptyList()
) {
    val totalActualValue: Double
        get() = quantitativeStats?.totalActualValue ?: 0.0

    val averageActualValue: Double?
        get() = quantitativeStats?.averageOnRecordedDays

    val targetAchievementRate: Float?
        get() = quantitativeStats?.targetAchievementRate
}

/**
 * Quantitative metrics for habits with Count, Duration, or Quantity measurements (Decision 5.2.2).
 *
 * All numbers preserve their domain meaning without artificial score normalizations.
 */
data class QuantitativeAnalyticsStats(
    /**
     * Total recorded value across all records in the evaluated range.
     * Preserves exact recorded amounts (e.g. 90 minutes remains 90, not clamped to 60 target).
     */
    val totalActualValue: Double,

    /**
     * Average actual value on days where the habit was completed (actualValue >= target).
     * Null if no completed records exist in the range.
     */
    val averageOnCompletedDays: Double?,

    /**
     * Average actual value on all days where a record was logged (completed or incomplete).
     * Null if no records were logged in the range.
     */
    val averageOnRecordedDays: Double?,

    /**
     * Total count of distinct calendar days with a logged record in the range.
     */
    val recordedDaysCount: Int,

    /**
     * Total count of distinct calendar days where target was achieved in the range.
     */
    val completedDaysCount: Int,

    /**
     * Percentage of eligible scheduled days on which the target was achieved.
     * Equivalent to completionRate for quantitative habits.
     */
    val targetAchievementRate: Float,

    /**
     * Percentage of recorded attempts on which the target was achieved (completedDaysCount / recordedDaysCount).
     * Null if no records were logged in the range.
     */
    val successRateOnRecordedDays: Float?,

    /**
     * Decision 5.2.2: Active Day Average = Total Volume / Days with actual_value > 0.
     */
    val activeDaysCount: Int = recordedDaysCount,
    val activeDayAverage: Double? = averageOnRecordedDays,

    /**
     * Decision 5.2.2: Scheduled Daily Average = Volume on Scheduled Days / (Eligible Scheduled Days - Count(SKIPPED)).
     * Strictly excludes bonus-day volume. Displays null ('—') when denominator == 0.
     */
    val scheduledDailyAverage: Double? = null,
    val scheduledVolume: Double = totalActualValue,

    /**
     * Decision 5.2.3: Disclosed bonus contributions (activity on unscheduled/rest days).
     */
    val bonusDaysCount: Int = 0,
    val bonusVolume: Double = 0.0
)
