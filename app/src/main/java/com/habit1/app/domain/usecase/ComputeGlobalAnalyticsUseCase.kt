package com.habit1.app.domain.usecase

import com.habit1.app.domain.model.AnalyticsRange
import com.habit1.app.domain.model.GlobalAnalyticsSummary
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitPerformanceItem
import com.habit1.app.domain.model.HabitRecord
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.model.StreakLeaderItem
import java.time.LocalDate
import java.time.ZoneId

/**
 * Authoritative domain usecase that computes aggregate cross-habit analytics
 * across a set of active habits for a requested [AnalyticsRange].
 *
 * Core Semantics:
 * 1. Reuses [ComputeHabitAnalyticsUseCase] for each individual habit to guarantee zero mathematical discrepancy.
 * 2. Global Completion Rate is the weighted sum of completed scheduled days divided by eligible scheduled days.
 * 3. Recent trend delta is the percentage point difference between the last 7 days and the prior 7 days.
 * 4. Deterministic and explainable: no synthetic scores or arbitrary productivity numbers.
 */
class ComputeGlobalAnalyticsUseCase(
    private val computeHabitAnalyticsUseCase: ComputeHabitAnalyticsUseCase = ComputeHabitAnalyticsUseCase()
) {

    fun execute(
        habits: List<Habit>,
        recordsByHabit: Map<String, List<HabitRecord>>,
        range: AnalyticsRange,
        todayDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): GlobalAnalyticsSummary {
        val activeHabits = habits.filter { !it.isArchived }

        if (activeHabits.isEmpty()) {
            return GlobalAnalyticsSummary(
                range = range,
                globalCompletionRate = 0.0f,
                totalCompletedDays = 0,
                totalScheduledDays = 0,
                activeHabitsCount = 0,
                streakLeaders = emptyList(),
                habitPerformances = emptyList()
            )
        }

        val current7dRange = AnalyticsRange.ofDaysEndingAt(todayDate, 7)
        val prior7dRange = AnalyticsRange(todayDate.minusDays(13), todayDate.minusDays(7))

        var totalCompleted = 0
        var totalScheduled = 0
        var totalEligibleScheduled = 0

        val performanceItems = mutableListOf<HabitPerformanceItem>()
        val streakItems = mutableListOf<StreakLeaderItem>()

        for (habit in activeHabits) {
            val records = recordsByHabit[habit.id] ?: emptyList()
            val summary = computeHabitAnalyticsUseCase.execute(
                habit = habit,
                records = records,
                range = range,
                todayDate = todayDate,
                zoneId = zoneId
            )

            totalCompleted += summary.completedDays
            totalScheduled += summary.scheduledDays
            totalEligibleScheduled += summary.eligibleScheduledDays

            // Calculate 7-day trend delta
            val curr7dSummary = computeHabitAnalyticsUseCase.execute(
                habit = habit,
                records = records,
                range = current7dRange,
                todayDate = todayDate,
                zoneId = zoneId
            )
            val prior7dSummary = computeHabitAnalyticsUseCase.execute(
                habit = habit,
                records = records,
                range = prior7dRange,
                todayDate = todayDate,
                zoneId = zoneId
            )

            val trendDelta = if (prior7dSummary.scheduledDays > 0) {
                curr7dSummary.completionRate - prior7dSummary.completionRate
            } else {
                null
            }

            val targetFormatted = when (val m = habit.measurement) {
                is MeasurementType.BooleanChoice -> "Daily check"
                is MeasurementType.Count -> "${m.target} ${m.unit ?: "times"}"
                is MeasurementType.Duration -> "${m.targetMinutes} mins"
                is MeasurementType.Quantity -> {
                    val unitStr = if (m.unit.isNotBlank()) " ${m.unit}" else ""
                    "${m.target}$unitStr"
                }
            }

            performanceItems.add(
                HabitPerformanceItem(
                    habitId = habit.id,
                    habitName = habit.name,
                    measurementType = habit.measurement,
                    completionRate = summary.completionRate,
                    currentStreak = summary.currentStreak,
                    longestStreak = summary.longestStreak,
                    scheduledDays = summary.scheduledDays,
                    completedDays = summary.completedDays,
                    recentTrendDelta = trendDelta,
                    isPaused = habit.isPaused,
                    targetFormatted = targetFormatted
                )
            )

            if (summary.currentStreak > 0 || summary.longestStreak > 0) {
                streakItems.add(
                    StreakLeaderItem(
                        habitId = habit.id,
                        habitName = habit.name,
                        currentStreak = summary.currentStreak,
                        longestStreak = summary.longestStreak
                    )
                )
            }
        }

        // Global Consistency Formula (Product Invariant):
        // total completed eligible occurrences / total eligible scheduled occurrences
        val globalRate = if (totalEligibleScheduled > 0) {
            ((totalCompleted.toDouble() / totalEligibleScheduled.toDouble()) * 100.0)
                .toFloat()
                .coerceIn(0.0f, 100.0f)
        } else if (totalCompleted > 0) {
            100.0f
        } else {
            0.0f
        }

        val topStreaks = streakItems
            .sortedWith(compareByDescending<StreakLeaderItem> { it.currentStreak }.thenByDescending { it.longestStreak })
            .take(5)

        return GlobalAnalyticsSummary(
            range = range,
            globalCompletionRate = globalRate,
            totalCompletedDays = totalCompleted,
            totalScheduledDays = totalScheduled,
            activeHabitsCount = activeHabits.size,
            streakLeaders = topStreaks,
            habitPerformances = performanceItems
        )
    }
}
