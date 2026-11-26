package com.habit1.app.domain.usecase

import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitPause
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.HabitScheduleVersion
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Pure domain logic to evaluate whether a Habit is expected on a given civil calendar date.
 * Handles DAILY, SPECIFIC_DAYS, and INTERVAL schedules across leap years and date boundaries,
 * strictly honoring temporal pauses (HabitPause) and schedule versioning (HabitScheduleVersion).
 */
class EvaluateScheduleUseCase {

    /**
     * Determines whether the habit is scheduled on [targetDate].
     *
     * @param habit The habit to evaluate.
     * @param targetDate The civil calendar date to check.
     * @param zoneId The zoneId used to determine civil dates from timestamps.
     * @param pauses Optional list of temporal pause intervals for this habit.
     * @param scheduleVersions Optional list of temporal schedule versions for this habit.
     * @return true if the habit is expected on [targetDate], false otherwise.
     */
    fun isScheduledOn(
        habit: Habit,
        targetDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        pauses: List<HabitPause> = emptyList(),
        scheduleVersions: List<HabitScheduleVersion> = emptyList()
    ): Boolean {
        // 1. Boundary: Before effective start date
        val effectiveStart = habit.startDate
        if (targetDate.isBefore(effectiveStart)) {
            return false
        }

        // 2. Boundary: After archival date (immutable temporal boundary)
        if (habit.isArchived) {
            val archivedAt = habit.archivedAt
            if (archivedAt != null) {
                val archiveCivilDate = DateTimeUtils.toLocalDate(archivedAt, zoneId)
                if (targetDate.isAfter(archiveCivilDate)) {
                    return false
                }
            } else {
                return false
            }
        }

        // 3. Temporal Pauses
        if (pauses.isNotEmpty()) {
            if (pauses.any { it.covers(targetDate) }) {
                return false
            }
        } else if (habit.isCurrentlyPaused || habit.isPaused) {
            return false
        }

        // 4. Resolve applicable schedule version for targetDate
        val applicableSchedule = if (scheduleVersions.isNotEmpty()) {
            scheduleVersions.find { it.isEffectiveOn(targetDate) }?.schedule ?: habit.schedule
        } else {
            habit.schedule
        }

        // 5. Evaluate schedule rule
        return when (applicableSchedule) {
            is HabitSchedule.Daily -> true

            is HabitSchedule.SpecificDays -> {
                targetDate.dayOfWeek in applicableSchedule.days
            }

            is HabitSchedule.Interval -> {
                val daysDiff = ChronoUnit.DAYS.between(applicableSchedule.anchorDate, targetDate)
                daysDiff >= 0 && (daysDiff % applicableSchedule.everyNDays == 0L)
            }
        }
    }

    /**
     * Determines whether the habit is paused on [date].
     */
    fun isDayPaused(
        habit: Habit,
        date: LocalDate,
        pauses: List<HabitPause> = emptyList()
    ): Boolean {
        if (pauses.isNotEmpty()) {
            return pauses.any { it.covers(date) }
        }
        return habit.isCurrentlyPaused || habit.isPaused
    }

    /**
     * Returns all expected scheduled civil dates for [habit] within the closed date range [startDate, endDate].
     */
    fun getScheduledDatesInRange(
        habit: Habit,
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        pauses: List<HabitPause> = emptyList(),
        scheduleVersions: List<HabitScheduleVersion> = emptyList()
    ): List<LocalDate> {
        if (startDate.isAfter(endDate)) return emptyList()

        val scheduledDates = mutableListOf<LocalDate>()
        var current = startDate
        while (!current.isAfter(endDate)) {
            if (isScheduledOn(habit, current, zoneId, pauses, scheduleVersions)) {
                scheduledDates.add(current)
            }
            current = current.plusDays(1)
        }
        return scheduledDates
    }
}
