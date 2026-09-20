package com.habit1.app.domain.usecase

import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitSchedule
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Pure domain logic to evaluate whether a Habit is expected on a given civil calendar date.
 * Handles DAILY, SPECIFIC_DAYS, and INTERVAL schedules across leap years and date boundaries.
 */
class EvaluateScheduleUseCase {

    /**
     * Determines whether the habit is scheduled on [targetDate].
     *
     * @param habit The habit to evaluate.
     * @param targetDate The civil calendar date to check.
     * @param zoneId The zoneId used to determine the habit's civil creation date.
     * @return true if the habit is expected on [targetDate], false otherwise.
     */
    fun isScheduledOn(
        habit: Habit,
        targetDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (habit.isPaused || habit.isArchived) {
            return false
        }

        val creationCivilDate = habit.createdAt.atZone(zoneId).toLocalDate()
        if (targetDate.isBefore(creationCivilDate)) {
            return false
        }

        return when (val schedule = habit.schedule) {
            is HabitSchedule.Daily -> true

            is HabitSchedule.SpecificDays -> {
                targetDate.dayOfWeek in schedule.days
            }

            is HabitSchedule.Interval -> {
                val daysDiff = ChronoUnit.DAYS.between(schedule.anchorDate, targetDate)
                daysDiff >= 0 && (daysDiff % schedule.everyNDays == 0L)
            }
        }
    }

    /**
     * Returns all expected scheduled civil dates for [habit] within the closed date range [startDate, endDate].
     */
    fun getScheduledDatesInRange(
        habit: Habit,
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<LocalDate> {
        if (startDate.isAfter(endDate)) return emptyList()

        val scheduledDates = mutableListOf<LocalDate>()
        var current = startDate
        while (!current.isAfter(endDate)) {
            if (isScheduledOn(habit, current, zoneId)) {
                scheduledDates.add(current)
            }
            current = current.plusDays(1)
        }
        return scheduledDates
    }
}
