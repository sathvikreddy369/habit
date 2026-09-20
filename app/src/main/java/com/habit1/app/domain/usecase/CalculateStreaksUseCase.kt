package com.habit1.app.domain.usecase

import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitRecord
import com.habit1.app.domain.model.StreakResult
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure domain logic to calculate streak and consistency metrics for a habit.
 *
 * Canonical Streak Rules (Product Principles #7, #8 & ADR-08):
 * 1. Only scheduled days count towards streaks.
 * 2. Non-scheduled days neither increment nor break a streak.
 * 3. Today's Pending Scheduled Day:
 *    - Mon ✅, Tue ✅, Wed ⏳ (today pending): The current streak remains 2 until Wednesday is completed.
 *    - When Wednesday is completed (✅), the current streak becomes 3.
 *    - If Wednesday ends without completion, then on Thursday morning, Wednesday was missed, and the current streak resets to 0.
 *    - In short: Today does NOT prematurely break an active streak from yesterday.
 * 4. Missing records remain unrecorded; historical records are never fabricated.
 * 5. All statistics are explainable and mathematically transparent without synthetic scores.
 */
class CalculateStreaksUseCase(
    private val evaluateSchedule: EvaluateScheduleUseCase = EvaluateScheduleUseCase()
) {

    /**
     * Calculates streak and consistency metrics for [habit] based on authoritative [records] up to [todayDate].
     */
    fun execute(
        habit: Habit,
        records: List<HabitRecord>,
        todayDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): StreakResult {
        val creationCivilDate = habit.createdAt.atZone(zoneId).toLocalDate()
        val earliestRecordDate = records.minOfOrNull { it.date } ?: creationCivilDate
        val startDate = if (earliestRecordDate.isBefore(creationCivilDate)) earliestRecordDate else creationCivilDate

        // If habit is in the future or no valid dates exist
        if (startDate.isAfter(todayDate)) {
            val totalDone = records.count { it.isCompleted }
            return StreakResult(
                currentStreak = 0,
                longestStreak = 0,
                totalCompletions = totalDone,
                totalScheduledDays = 0,
                completionRate = 0.0f
            )
        }

        val recordMap = records.associateBy { it.date }
        val habitForSchedule = if (habit.isArchived) habit.copy(isArchived = false) else habit
        val scheduledDates = evaluateSchedule.getScheduledDatesInRange(habitForSchedule, startDate, todayDate, zoneId)

        if (scheduledDates.isEmpty()) {
            val totalDone = records.count { it.isCompleted }
            return StreakResult(
                currentStreak = 0,
                longestStreak = 0,
                totalCompletions = totalDone,
                totalScheduledDays = 0,
                completionRate = 0.0f
            )
        }

        // 1. Calculate Longest Streak & Total Completions on scheduled days
        var runningStreak = 0
        var longestStreak = 0
        var totalCompletedScheduled = 0

        for (date in scheduledDates) {
            val isDone = recordMap[date]?.isCompleted == true
            if (isDone) {
                runningStreak++
                totalCompletedScheduled++
                if (runningStreak > longestStreak) {
                    longestStreak = runningStreak
                }
            } else {
                if (date != todayDate) {
                    runningStreak = 0
                }
            }
        }

        // 2. Calculate Current Streak (stepping backwards from todayDate)
        var currentStreak = 0
        val scheduledReversed = scheduledDates.reversed()

        for (date in scheduledReversed) {
            val isDone = recordMap[date]?.isCompleted == true
            if (date == todayDate && !isDone) {
                // Today is scheduled but not completed yet; give user until midnight without breaking active streak
                continue
            }
            if (isDone) {
                currentStreak++
            } else {
                // Encountered first missed scheduled day; streak stops here
                break
            }
        }

        // 3. Calculate Completion Percentage
        // If today is scheduled but not yet completed, evaluate percentage against past scheduled days
        // so morning visits don't artificially depress the completion rate.
        val todayScheduledAndIncomplete = scheduledDates.contains(todayDate) && recordMap[todayDate]?.isCompleted != true
        val denominatorDays = if (todayScheduledAndIncomplete) {
            scheduledDates.count { it.isBefore(todayDate) }
        } else {
            scheduledDates.size
        }

        val completionRate = if (denominatorDays > 0) {
            ((totalCompletedScheduled.toDouble() / denominatorDays.toDouble()) * 100.0)
                .toFloat()
                .coerceIn(0.0f, 100.0f)
        } else if (totalCompletedScheduled > 0) {
            100.0f
        } else {
            0.0f
        }

        return StreakResult(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            totalCompletions = totalCompletedScheduled,
            totalScheduledDays = scheduledDates.size,
            completionRate = completionRate
        )
    }
}
