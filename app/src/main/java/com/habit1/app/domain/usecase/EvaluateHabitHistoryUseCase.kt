package com.habit1.app.domain.usecase

import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitHistoryDay
import com.habit1.app.domain.model.HabitHistorySummary
import com.habit1.app.domain.model.HabitPause
import com.habit1.app.domain.model.HabitRecord
import com.habit1.app.domain.model.HabitRecordStatus
import com.habit1.app.domain.model.HabitScheduleVersion
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
        zoneId: ZoneId,
        pauses: List<HabitPause> = emptyList(),
        scheduleVersions: List<HabitScheduleVersion> = emptyList()
    ): HabitHistorySummary {
        val creationDate = habit.startDate
        val recordsMap = records.associateBy { it.date }

        // 1. Single source of truth for streaks & overall consistency
        val streakResult = calculateStreaks.execute(
            habit = habit,
            records = records,
            todayDate = todayDate,
            zoneId = zoneId,
            pauses = pauses,
            scheduleVersions = scheduleVersions
        )

        val historyDays = mutableListOf<HabitHistoryDay>()
        var completedCount = 0
        var recordedIncompleteCount = 0
        var projectedMissedCount = 0
        var projectedRestCount = 0

        var currentDate = startDate
        val habitForSchedule = if (habit.isArchived) habit.copy(isArchived = false) else habit
        val archiveCivilDate = habit.archivedAt?.let { DateTimeUtils.toLocalDate(it, zoneId) }

        while (!currentDate.isAfter(endDate)) {
            val record = recordsMap[currentDate]
            val status: CalendarDayStatus = when {
                currentDate.isAfter(todayDate) -> CalendarDayStatus.Future
                currentDate.isBefore(creationDate) -> CalendarDayStatus.PreCreation
                archiveCivilDate != null && currentDate.isAfter(archiveCivilDate) -> CalendarDayStatus.ProjectedRest
                record != null -> {
                    when (record.status) {
                        HabitRecordStatus.DONE -> {
                            completedCount++
                            CalendarDayStatus.Completed(
                                actualValue = record.actualValue,
                                targetValue = record.targetValue,
                                unit = record.unit,
                                measurementType = record.measurementType
                            )
                        }
                        HabitRecordStatus.PARTIAL -> {
                            recordedIncompleteCount++
                            CalendarDayStatus.RecordedIncomplete(
                                actualValue = record.actualValue,
                                targetValue = record.targetValue,
                                unit = record.unit,
                                measurementType = record.measurementType
                            )
                        }
                        HabitRecordStatus.SKIPPED -> {
                            CalendarDayStatus.Skipped(
                                actualValue = record.actualValue,
                                targetValue = record.targetValue,
                                unit = record.unit,
                                measurementType = record.measurementType
                            )
                        }
                        HabitRecordStatus.MISSED -> {
                            CalendarDayStatus.ExplicitMissed(
                                notes = record.notes
                            )
                        }
                        HabitRecordStatus.UNRECORDED -> {
                            CalendarDayStatus.ProjectedMissed
                        }
                    }
                }
                pauses.any { it.covers(currentDate) } || habit.isCurrentlyPaused || habit.isPaused -> CalendarDayStatus.Paused
                currentDate == todayDate -> CalendarDayStatus.Future
                else -> {
                    val isScheduled = evaluateSchedule.isScheduledOn(habitForSchedule, currentDate, zoneId, pauses, scheduleVersions)
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
        val completedWindowRecords = windowRecords.filter { it.status == HabitRecordStatus.DONE }
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
