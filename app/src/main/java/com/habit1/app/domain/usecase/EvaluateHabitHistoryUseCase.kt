package com.habit1.app.domain.usecase

import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitHistoryDay
import com.habit1.app.domain.model.HabitHistorySummary
import com.habit1.app.domain.model.HabitRecord
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure domain use case that composes existing canonical domain logic
 * (EvaluateScheduleUseCase, CalculateStreaksUseCase) to evaluate a habit's history
 * across a specified date window.
 */
class EvaluateHabitHistoryUseCase(
    private val evaluateSchedule: EvaluateScheduleUseCase = EvaluateScheduleUseCase(),
    private val calculateStreaks: CalculateStreaksUseCase = CalculateStreaksUseCase()
) {

    fun execute(
        habit: Habit,
        records: List<HabitRecord>,
        startDate: LocalDate,
        endDate: LocalDate,
        todayDate: LocalDate,
        zoneId: ZoneId
    ): HabitHistorySummary {
        val creationDate = DateTimeUtils.toLocalDate(habit.createdAt, zoneId)
        val recordsMap = records.associateBy { it.date }

        // 1. Single source of truth for streaks & overall consistency
        val streakResult = calculateStreaks.execute(habit, records, todayDate, zoneId)

        val historyDays = mutableListOf<HabitHistoryDay>()
        var completedCount = 0
        var recordedIncompleteCount = 0
        var projectedMissedCount = 0
        var projectedRestCount = 0

        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            val record = recordsMap[currentDate]
            val status: CalendarDayStatus = when {
                currentDate.isAfter(todayDate) -> CalendarDayStatus.Future
                currentDate.isBefore(creationDate) -> CalendarDayStatus.PreCreation
                record != null -> {
                    if (record.isCompleted) {
                        completedCount++
                        CalendarDayStatus.Completed(
                            actualValue = record.actualValue,
                            targetValue = record.targetValue,
                            unit = record.unit,
                            measurementType = record.measurementType
                        )
                    } else {
                        recordedIncompleteCount++
                        CalendarDayStatus.RecordedIncomplete(
                            actualValue = record.actualValue,
                            targetValue = record.targetValue,
                            unit = record.unit,
                            measurementType = record.measurementType
                        )
                    }
                }
                habit.isPaused -> CalendarDayStatus.Paused
                currentDate == todayDate -> CalendarDayStatus.Future
                else -> {
                    val habitForSchedule = if (habit.isArchived) habit.copy(isArchived = false) else habit
                    val isScheduled = evaluateSchedule.isScheduledOn(habitForSchedule, currentDate, zoneId)
                    if (isScheduled) {
                        projectedMissedCount++
                        CalendarDayStatus.ProjectedMissed
                    } else {
                        projectedRestCount++
                        CalendarDayStatus.ProjectedRest
                    }
                }
            }

            historyDays.add(HabitHistoryDay(date = currentDate, status = status))
            currentDate = currentDate.plusDays(1)
        }

        // Quantitative history in window
        val windowRecords = records.filter { it.date in startDate..endDate }
        val completedWindowRecords = windowRecords.filter { it.isCompleted }
        val totalVolume = windowRecords.sumOf { it.actualValue }

        val avgCompleted = if (completedWindowRecords.isNotEmpty()) {
            completedWindowRecords.map { it.actualValue }.average()
        } else null

        val avgRecorded = if (windowRecords.isNotEmpty()) {
            windowRecords.map { it.actualValue }.average()
        } else null

        return HabitHistorySummary(
            habit = habit,
            streakResult = streakResult,
            totalRecordedDays = windowRecords.size,
            completedDaysCount = completedCount,
            recordedIncompleteDaysCount = recordedIncompleteCount,
            projectedMissedDaysCount = projectedMissedCount,
            projectedRestDaysCount = projectedRestCount,
            totalRecordedVolume = totalVolume,
            averageOnCompletedDays = avgCompleted,
            averageOnRecordedDays = avgRecorded,
            historyDays = historyDays
        )
    }
}
