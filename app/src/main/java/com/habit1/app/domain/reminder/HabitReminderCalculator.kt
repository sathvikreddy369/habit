package com.habit1.app.domain.reminder

import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.usecase.EvaluateScheduleUseCase
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure domain calculator for determining the next upcoming reminder instant for a habit.
 * Reuses the canonical [EvaluateScheduleUseCase] exclusively without duplicating schedule logic.
 */
class HabitReminderCalculator(
    private val evaluateSchedule: EvaluateScheduleUseCase = EvaluateScheduleUseCase()
) {

    /**
     * Calculates the next upcoming scheduled trigger time for [habit] strictly after [fromInstant].
     *
     * @param habit The habit to evaluate.
     * @param fromInstant The reference point in time (usually Instant.now()).
     * @param zoneId The local device timezone.
     * @return The next [ZonedDateTime] the reminder should fire, or null if the habit is not eligible.
     */
    fun calculateNextReminder(
        habit: Habit,
        fromInstant: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ZonedDateTime? {
        val reminderTime = habit.reminderTime ?: return null
        if (habit.isPaused || habit.isArchived) {
            return null
        }

        val currentDateTime = fromInstant.atZone(zoneId).toLocalDateTime()
        val today = currentDateTime.toLocalDate()

        // 1. Check if today is scheduled and the reminder time hasn't passed yet
        if (evaluateSchedule.isScheduledOn(habit, today, zoneId)) {
            val todayTrigger = LocalDateTime.of(today, reminderTime)
            if (todayTrigger.isAfter(currentDateTime)) {
                return todayTrigger.atZone(zoneId)
            }
        }

        // 2. Iterate future dates to find the next scheduled civil date
        var candidateDate = today.plusDays(1)
        val maxSearchDays = 366 * 2 // Horizon of 2 years (covers any interval schedule)
        var count = 0

        while (count < maxSearchDays) {
            if (evaluateSchedule.isScheduledOn(habit, candidateDate, zoneId)) {
                val candidateTrigger = LocalDateTime.of(candidateDate, reminderTime)
                return candidateTrigger.atZone(zoneId)
            }
            candidateDate = candidateDate.plusDays(1)
            count++
        }

        return null
    }
}
