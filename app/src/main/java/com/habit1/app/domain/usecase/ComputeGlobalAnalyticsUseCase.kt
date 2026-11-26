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
 * across habits for a requested [AnalyticsRange].
 *
 * Core Semantics (Decision 5.3):
 * 1. Reuses [ComputeHabitAnalyticsUseCase] for each individual habit to guarantee zero mathematical discrepancy.
 * 2. Macro-Average Global Completion Rate:
 *    sum(completed days) / sum(eligible scheduled days excluding SKIPPED) * 100.
 * 3. Archived habits: fully included in historical Global Analytics (30d, 90d, 365d, All Time)
 *    for their applicable active periods. Archival date acts as an immutable boundary.
 * 4. Current Streak Leaders and Active Habits Count strictly exclude archived habits.
 * 5. Deterministic and explainable: no synthetic scores or arbitrary productivity numbers.
 */
class ComputeGlobalAnalyticsUseCase(
    private val computeHabitAnalyticsUseCase: ComputeHabitAnalyticsUseCase = ComputeHabitAnalyticsUseCase()
) {

    fun execute(
        habits: List<Habit>,
        recordsByHabit: Map<String, List<HabitRecord>>,
        range: AnalyticsRange,
        todayDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        includeArchived: Boolean = false
    ): GlobalAnalyticsSummary {
        val activeHabits = habits.filter { !it.isArchived }
        val habitsToEvaluate = if (includeArchived) habits else activeHabits

        if (habitsToEvaluate.isEmpty()) {
            return GlobalAnalyticsSummary(
                range = range,
                globalCompletionRate = 0.0f,
                totalCompletedDays = 0,
                totalScheduledDays = 0,
                activeHabitsCount = activeHabits.size,
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

        for (habit in habitsToEvaluate) {
            val records = recordsByHabit[habit.id] ?: emptyList()
            val summary = computeHabitAnalyticsUseCase.execute(
                habit = habit,
                records = records,
                range = range,
                todayDate = todayDate,
                zoneId = zoneId
            )

            // If an archived habit was not active/scheduled in this range, skip it
            if (habit.isArchived && summary.scheduledDays == 0 && summary.completedDays == 0) {
                continue
            }

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
                    targetFormatted = targetFormatted,
                    isArchived = habit.isArchived
                )
            )

            // Current streak leaders exclude archived habits per Decision 5.3.2
            if (!habit.isArchived && (summary.currentStreak > 0 || summary.longestStreak > 0)) {
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

        // Global Consistency Formula (Decision 5.3.1):
        // Macro-Average = sum(completed days) / sum(eligible scheduled days - SKIPPED)
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

        // 1. Build Daily Completion Trend & Daily breakdown
        val dailyMap = mutableMapOf<LocalDate, Pair<Int, Int>>() // date -> (completed, scheduled)
        var walkDate = range.startDate
        while (!walkDate.isAfter(range.endDate)) {
            dailyMap[walkDate] = Pair(0, 0)
            walkDate = walkDate.plusDays(1)
        }

        var totalPartial = 0
        var totalSkipped = 0
        var totalMissed = 0
        var totalUnrecorded = 0
        var totalRest = 0
        var totalPaused = 0
        var totalRecordedInstances = 0

        // Aggregate daily breakdown across all evaluated habits
        for (habit in habitsToEvaluate) {
            val records = recordsByHabit[habit.id] ?: emptyList()
            val summary = computeHabitAnalyticsUseCase.execute(
                habit = habit,
                records = records,
                range = range,
                todayDate = todayDate,
                zoneId = zoneId
            )

            totalPartial += summary.recordedIncompleteDays
            totalMissed += summary.missedDays
            totalRest += summary.restDays

            for (day in summary.dailyBreakdown) {
                val current = dailyMap[day.date] ?: Pair(0, 0)
                val isCompleted = day.status is com.habit1.app.domain.model.CalendarDayStatus.Completed
                val isScheduled = day.status !is com.habit1.app.domain.model.CalendarDayStatus.ProjectedRest &&
                        day.status !is com.habit1.app.domain.model.CalendarDayStatus.PreCreation &&
                        day.status !is com.habit1.app.domain.model.CalendarDayStatus.Future &&
                        day.status !is com.habit1.app.domain.model.CalendarDayStatus.Paused

                val newCompleted = current.first + if (isCompleted) 1 else 0
                val newScheduled = current.second + if (isScheduled) 1 else 0
                dailyMap[day.date] = Pair(newCompleted, newScheduled)

                when (day.status) {
                    is com.habit1.app.domain.model.CalendarDayStatus.Skipped -> totalSkipped++
                    is com.habit1.app.domain.model.CalendarDayStatus.Paused -> totalPaused++
                    else -> {}
                }
            }
        }

        val dailyTrend = dailyMap.entries.sortedBy { it.key }.map { (date, counts) ->
            val comp = counts.first
            val sched = counts.second
            val rate = if (sched > 0) ((comp.toDouble() / sched.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f)
            else if (comp > 0) 100f else 0f
            com.habit1.app.domain.model.DailyCompletionPoint(
                date = date,
                scheduledHabitsCount = sched,
                completedHabitsCount = comp,
                completionRate = rate,
                isEligible = sched > 0
            )
        }

        // 2. Global Weekday Adherence (Monday through Sunday)
        val weekdayMap = mutableMapOf<java.time.DayOfWeek, Pair<Int, Int>>() // DayOfWeek -> (completed, scheduled)
        java.time.DayOfWeek.entries.forEach { weekdayMap[it] = Pair(0, 0) }
        dailyMap.forEach { (date, counts) ->
            if (!date.isAfter(todayDate)) {
                val dow = date.dayOfWeek
                val prev = weekdayMap[dow] ?: Pair(0, 0)
                weekdayMap[dow] = Pair(prev.first + counts.first, prev.second + counts.second)
            }
        }

        val globalWeekdayAdherence = java.time.DayOfWeek.entries.map { dow ->
            val counts = weekdayMap[dow] ?: Pair(0, 0)
            val comp = counts.first
            val sched = counts.second
            val rate = if (sched > 0) ((comp.toDouble() / sched.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f) else null
            com.habit1.app.domain.model.WeekdayAdherence(
                dayOfWeek = dow,
                completedCount = comp,
                eligibleScheduledCount = sched,
                adherenceRate = rate
            )
        }

        // 3. Global Period Comparison
        val dayCount = range.dayCount
        val prevRange = AnalyticsRange(range.startDate.minusDays(dayCount.toLong()), range.startDate.minusDays(1))
        var prevTotalCompleted = 0
        var prevTotalEligibleScheduled = 0
        for (habit in habitsToEvaluate) {
            val records = recordsByHabit[habit.id] ?: emptyList()
            val prevSummary = computeHabitAnalyticsUseCase.execute(
                habit = habit,
                records = records,
                range = prevRange,
                todayDate = todayDate,
                zoneId = zoneId
            )
            prevTotalCompleted += prevSummary.completedDays
            prevTotalEligibleScheduled += prevSummary.eligibleScheduledDays
        }

        val prevGlobalRate = if (prevTotalEligibleScheduled > 0) {
            ((prevTotalCompleted.toDouble() / prevTotalEligibleScheduled.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f)
        } else if (prevTotalCompleted > 0) 100f else 0f

        val earliestCreation = habitsToEvaluate.minOfOrNull { it.startDate } ?: todayDate
        val hasSufficientData = prevTotalEligibleScheduled > 0 && !prevRange.endDate.isBefore(earliestCreation)

        val periodComparison = com.habit1.app.domain.model.PeriodComparison(
            previousRange = prevRange,
            previousCompletionRate = prevGlobalRate,
            currentCompletionRate = globalRate,
            rateDelta = globalRate - prevGlobalRate,
            previousCompletedCount = prevTotalCompleted,
            currentCompletedCount = totalCompleted,
            completedDelta = totalCompleted - prevTotalCompleted,
            isSufficientData = hasSufficientData
        )

        val recordBreakdown = com.habit1.app.domain.model.RecordBreakdown(
            completedCount = totalCompleted,
            partialCount = totalPartial,
            skippedCount = totalSkipped,
            missedCount = totalMissed,
            unrecordedCount = totalUnrecorded,
            restCount = totalRest,
            pausedCount = totalPaused,
            totalScheduledInstances = totalScheduled,
            totalRecordedInstances = totalCompleted + totalPartial + totalSkipped
        )

        val baseSummary = GlobalAnalyticsSummary(
            range = range,
            globalCompletionRate = globalRate,
            totalCompletedDays = totalCompleted,
            totalScheduledDays = totalScheduled,
            activeHabitsCount = activeHabits.size,
            streakLeaders = topStreaks,
            habitPerformances = performanceItems,
            recordBreakdown = recordBreakdown,
            dailyTrend = dailyTrend,
            weekdayAdherence = globalWeekdayAdherence,
            periodComparison = periodComparison
        )

        val insights = GenerateAnalyticsInsightsUseCase().generateGlobalInsights(baseSummary)
        return baseSummary.copy(insights = insights)
    }
}
