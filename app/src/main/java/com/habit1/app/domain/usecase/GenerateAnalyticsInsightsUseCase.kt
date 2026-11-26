package com.habit1.app.domain.usecase

import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.GlobalAnalyticsSummary
import com.habit1.app.domain.model.HabitAnalyticsSummary
import com.habit1.app.domain.model.HabitInsight
import com.habit1.app.domain.model.MeasurementType
import java.time.format.TextStyle
import java.util.Locale

/**
 * Deterministic engine for generating factual, mathematical insights from habit analytics data.
 * Adheres strictly to Product Principle #7 (Preserve Historical Truth) and Decision 5.3:
 * - No AI hallucinations or synthetic interpretations.
 * - Mathematically verified deltas, streak thresholds, and weekday patterns.
 */
class GenerateAnalyticsInsightsUseCase {

    /**
     * Generates global insights across all tracked habits.
     */
    fun generateGlobalInsights(
        summary: GlobalAnalyticsSummary
    ): List<HabitInsight> {
        val insights = mutableListOf<HabitInsight>()

        // 1. Period comparison insight
        summary.periodComparison?.let { comp ->
            if (comp.isSufficientData && Math.abs(comp.rateDelta) >= 5f) {
                if (comp.rateDelta > 0) {
                    insights.add(
                        HabitInsight(
                            id = "global_consistency_improvement",
                            title = "Consistency improved",
                            description = "Your overall completion rate is up by +${comp.rateDelta.toInt()}% compared to the prior ${summary.range.dayCount}-day period (${comp.previousCompletionRate.toInt()}% → ${comp.currentCompletionRate.toInt()}%).",
                            type = HabitInsight.InsightType.CONSISTENCY_IMPROVEMENT,
                            metricValue = "+${comp.rateDelta.toInt()}%"
                        )
                    )
                } else {
                    insights.add(
                        HabitInsight(
                            id = "global_consistency_drop",
                            title = "Consistency dipped",
                            description = "Your overall completion rate dropped by ${comp.rateDelta.toInt()}% compared to the prior ${summary.range.dayCount}-day period (${comp.previousCompletionRate.toInt()}% → ${comp.currentCompletionRate.toInt()}%).",
                            type = HabitInsight.InsightType.CONSISTENCY_DROP,
                            metricValue = "${comp.rateDelta.toInt()}%"
                        )
                    )
                }
            }
        }

        // 2. Best weekday insight
        val eligibleWeekdays = summary.weekdayAdherence.filter { (it.eligibleScheduledCount) >= 2 && it.adherenceRate != null }
        if (eligibleWeekdays.size >= 3) {
            val bestWeekday = eligibleWeekdays.maxByOrNull { it.adherenceRate ?: 0f }
            val worstWeekday = eligibleWeekdays.minByOrNull { it.adherenceRate ?: 0f }
            if (bestWeekday != null && (bestWeekday.adherenceRate ?: 0f) >= 70f &&
                worstWeekday != null && ((bestWeekday.adherenceRate ?: 0f) - (worstWeekday.adherenceRate ?: 0f) >= 20f)) {
                val dayName = bestWeekday.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                insights.add(
                    HabitInsight(
                        id = "global_best_weekday",
                        title = "$dayName is your strongest day",
                        description = "You achieve ${bestWeekday.adherenceRate?.toInt()}% completion on ${dayName}s, significantly higher than other days.",
                        type = HabitInsight.InsightType.BEST_WEEKDAY,
                        metricValue = "${bestWeekday.adherenceRate?.toInt()}%"
                    )
                )
            }
        }

        // 3. Significant streak milestones
        val highStreaks = summary.streakLeaders.filter { it.currentStreak in listOf(7, 14, 21, 30, 50, 60, 90, 100, 365) || it.currentStreak % 30 == 0 && it.currentStreak > 0 }
        highStreaks.firstOrNull()?.let { leader ->
            insights.add(
                HabitInsight(
                    id = "global_streak_milestone_${leader.habitId}",
                    title = "${leader.habitName} reached ${leader.currentStreak} days",
                    description = "Active uninterrupted streak of ${leader.currentStreak} days.",
                    type = HabitInsight.InsightType.STREAK_MILESTONE,
                    habitId = leader.habitId,
                    metricValue = "${leader.currentStreak}d"
                )
            )
        }

        // 4. Habits that need attention (scheduled but unrecorded/missed consecutively in past 3 scheduled occurrences)
        val lowAdherence = summary.habitPerformances.filter { !it.isPaused && !it.isArchived && it.scheduledDays >= 5 && it.completionRate <= 25f }
        lowAdherence.firstOrNull()?.let { habit ->
            insights.add(
                HabitInsight(
                    id = "needs_attention_${habit.habitId}",
                    title = "${habit.habitName} consistency is low",
                    description = "Completed only ${habit.completedDays} of ${habit.scheduledDays} scheduled days (${habit.completionRate.toInt()}%) in this period.",
                    type = HabitInsight.InsightType.NEEDS_ATTENTION,
                    habitId = habit.habitId,
                    metricValue = "${habit.completionRate.toInt()}%"
                )
            )
        }

        return insights
    }

    /**
     * Generates habit-specific insights for an individual habit.
     */
    fun generateHabitInsights(
        summary: HabitAnalyticsSummary
    ): List<HabitInsight> {
        val insights = mutableListOf<HabitInsight>()
        val habit = summary.habit

        // 1. Period comparison
        summary.periodComparison?.let { comp ->
            if (comp.isSufficientData && Math.abs(comp.rateDelta) >= 5f) {
                if (comp.rateDelta > 0) {
                    insights.add(
                        HabitInsight(
                            id = "habit_consistency_improvement",
                            title = "Improved consistency",
                            description = "Adherence increased by +${comp.rateDelta.toInt()}% (${comp.previousCompletionRate.toInt()}% → ${comp.currentCompletionRate.toInt()}%) compared to previous period.",
                            type = HabitInsight.InsightType.CONSISTENCY_IMPROVEMENT,
                            metricValue = "+${comp.rateDelta.toInt()}%"
                        )
                    )
                } else {
                    insights.add(
                        HabitInsight(
                            id = "habit_consistency_drop",
                            title = "Lower consistency",
                            description = "Adherence dropped by ${comp.rateDelta.toInt()}% (${comp.previousCompletionRate.toInt()}% → ${comp.currentCompletionRate.toInt()}%) compared to previous period.",
                            type = HabitInsight.InsightType.CONSISTENCY_DROP,
                            metricValue = "${comp.rateDelta.toInt()}%"
                        )
                    )
                }
            }
        }

        // 2. Weekday pattern
        val eligibleWeekdays = summary.weekdayAdherence.filter { it.eligibleScheduledCount >= 2 && it.adherenceRate != null }
        if (eligibleWeekdays.size >= 2) {
            val bestWeekday = eligibleWeekdays.maxByOrNull { it.adherenceRate ?: 0f }
            if (bestWeekday != null && (bestWeekday.adherenceRate ?: 0f) >= 80f) {
                val dayName = bestWeekday.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                insights.add(
                    HabitInsight(
                        id = "habit_best_weekday",
                        title = "Strong on ${dayName}s",
                        description = "${bestWeekday.adherenceRate?.toInt()}% completion across scheduled ${dayName}s.",
                        type = HabitInsight.InsightType.BEST_WEEKDAY,
                        metricValue = "${bestWeekday.adherenceRate?.toInt()}%"
                    )
                )
            }
        }

        // 3. Quantitative volume progression
        if (habit.measurement !is MeasurementType.BooleanChoice && summary.quantitativeStats != null) {
            val stats = summary.quantitativeStats
            if (stats.recordedDaysCount >= 3 && stats.averageOnRecordedDays != null) {
                val unitStr = when (val m = habit.measurement) {
                    is MeasurementType.Count -> m.unit ?: "units"
                    is MeasurementType.Duration -> "mins"
                    is MeasurementType.Quantity -> m.unit.ifBlank { "units" }
                    else -> ""
                }
                val avgFormatted = if ((stats.averageOnRecordedDays ?: 0.0) % 1.0 == 0.0) {
                    (stats.averageOnRecordedDays ?: 0.0).toInt().toString()
                } else {
                    String.format(Locale.US, "%.1f", stats.averageOnRecordedDays)
                }
                insights.add(
                    HabitInsight(
                        id = "habit_avg_volume",
                        title = "Daily average",
                        description = "Averaging $avgFormatted $unitStr per active logging day (${stats.recordedDaysCount} recorded days).",
                        type = HabitInsight.InsightType.QUANTITATIVE_INCREASE,
                        metricValue = "$avgFormatted $unitStr"
                    )
                )
            }
        }

        // 4. Streak milestones
        if (summary.currentStreak >= 7) {
            insights.add(
                HabitInsight(
                    id = "habit_streak_active",
                    title = "Active streak: ${summary.currentStreak} days",
                    description = if (summary.currentStreak >= summary.longestStreak && summary.longestStreak > 0) {
                        "You are currently at your all-time best streak of ${summary.currentStreak} days!"
                    } else {
                        "Current run of ${summary.currentStreak} days. Your best is ${summary.longestStreak} days."
                    },
                    type = HabitInsight.InsightType.STREAK_MILESTONE,
                    metricValue = "${summary.currentStreak}d"
                )
            )
        }

        return insights
    }
}
