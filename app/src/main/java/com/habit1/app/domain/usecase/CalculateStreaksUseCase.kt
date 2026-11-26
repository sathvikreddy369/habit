package com.habit1.app.domain.usecase

import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitPause
import com.habit1.app.domain.model.HabitRecord
import com.habit1.app.domain.model.HabitRecordStatus
import com.habit1.app.domain.model.HabitScheduleVersion
import com.habit1.app.domain.model.StreakResult
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure domain logic to calculate streak and consistency metrics for a habit.
 *
 * Canonical Streak Rules (Product Decisions Area 1 & ADR-08):
 * 1. Only scheduled days count towards streaks.
 * 2. DONE extends the streak (+1).
 * 3. SKIPPED preserves an established streak without incrementing it (neutral bridge).
 *    Skips alone without preceding completions do not create a streak.
 * 4. PARTIAL breaks the streak (resets to 0).
 * 5. MISSED breaks the streak (resets to 0).
 * 6. Past scheduled UNRECORDED breaks the streak (resets to 0).
 * 7. Today's Pending Scheduled Day:
 *    - Mon DONE, Tue DONE, Wed pending UNRECORDED: streak remains 2 (morning grace).
 *    - When Wed is DONE, streak becomes 3.
 *    - If Wed ends without completion, on Thursday morning Wed breaks the streak.
 *    - If Wed is explicitly marked MISSED, morning grace is revoked immediately and streak drops to 0.
 * 8. Completion Rate:
 *    Rate = DONE / (Eligible Scheduled Days - SKIPPED) * 100
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
        zoneId: ZoneId = ZoneId.systemDefault(),
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        pauses: List<HabitPause> = emptyList(),
        scheduleVersions: List<HabitScheduleVersion> = emptyList()
    ): StreakResult {
        val creationCivilDate = habit.startDate
        val earliestRecordDate = records.minOfOrNull { it.date } ?: creationCivilDate
        val defaultStartDate = if (earliestRecordDate.isBefore(creationCivilDate)) earliestRecordDate else creationCivilDate
        val effectiveStartDate = startDate ?: defaultStartDate
        val effectiveEndDate = endDate ?: todayDate

        // If habit is in the future or no valid dates exist
        if (effectiveStartDate.isAfter(effectiveEndDate)) {
            val totalDone = records.count { it.status == HabitRecordStatus.DONE }
            return StreakResult(
                currentStreak = 0,
                longestStreak = 0,
                totalCompletions = totalDone,
                totalScheduledDays = 0,
                completionRate = 0.0f,
                isFrozen = false,
                totalSkippedDays = 0
            )
        }

        val recordMap = records.associateBy { it.date }
        val habitForSchedule = if (habit.isArchived) habit.copy(isArchived = false) else habit
        val scheduledDates = evaluateSchedule.getScheduledDatesInRange(
            habit = habitForSchedule,
            startDate = effectiveStartDate,
            endDate = effectiveEndDate,
            zoneId = zoneId,
            pauses = pauses,
            scheduleVersions = scheduleVersions
        )

        if (scheduledDates.isEmpty()) {
            val totalDone = records.count { it.status == HabitRecordStatus.DONE }
            return StreakResult(
                currentStreak = 0,
                longestStreak = 0,
                totalCompletions = totalDone,
                totalScheduledDays = 0,
                completionRate = 0.0f,
                isFrozen = false,
                totalSkippedDays = 0
            )
        }

        // 1. Calculate Longest Streak & Scheduled Counts
        var runningStreak = 0
        var longestStreak = 0
        var totalCompletedScheduled = 0
        var totalSkippedScheduled = 0

        for (date in scheduledDates) {
            val record = recordMap[date]
            val status = record?.status

            when (status) {
                HabitRecordStatus.DONE -> {
                    runningStreak++
                    totalCompletedScheduled++
                    if (runningStreak > longestStreak) {
                        longestStreak = runningStreak
                    }
                }
                HabitRecordStatus.SKIPPED -> {
                    totalSkippedScheduled++
                    // Neutral bridge: streak neither increments nor resets
                }
                HabitRecordStatus.PARTIAL, HabitRecordStatus.MISSED, HabitRecordStatus.UNRECORDED -> {
                    runningStreak = 0
                }
                null -> {
                    // Unrecorded
                    if (date != todayDate) {
                        runningStreak = 0
                    }
                }
            }
        }

        // 2. Calculate Current Streak (stepping backwards from todayDate)
        var currentStreak = 0
        var isFrozen = false
        var seenDoneInChain = false
        val scheduledReversed = scheduledDates.reversed()

        for (date in scheduledReversed) {
            val record = recordMap[date]
            val status = record?.status

            if (date == todayDate) {
                when (status) {
                    HabitRecordStatus.DONE -> {
                        currentStreak++
                        seenDoneInChain = true
                    }
                    HabitRecordStatus.SKIPPED -> {
                        // Today is skipped: preserves streak from yesterday in frozen state
                        isFrozen = true
                    }
                    HabitRecordStatus.MISSED, HabitRecordStatus.PARTIAL -> {
                        // Explicitly missed or partial today revokes morning grace immediately
                        currentStreak = 0
                        break
                    }
                    HabitRecordStatus.UNRECORDED, null -> {
                        // Pending unrecorded today: morning grace applies, continue backward
                        continue
                    }
                }
                continue
            }

            // Historical dates (< todayDate)
            when (status) {
                HabitRecordStatus.DONE -> {
                    currentStreak++
                    seenDoneInChain = true
                }
                HabitRecordStatus.SKIPPED -> {
                    // Only frozen if no subsequent completion was logged (i.e. streak leading edge is a skip)
                    if (!seenDoneInChain && currentStreak == 0) {
                        isFrozen = true
                    }
                    // Neutral bridge: keep looking back
                }
                HabitRecordStatus.PARTIAL, HabitRecordStatus.MISSED, HabitRecordStatus.UNRECORDED, null -> {
                    // First failure or unrecorded past scheduled day stops the active streak
                    break
                }
            }
        }

        // If no actual completion was seen, skips alone cannot display a streak or frozen status
        if (!seenDoneInChain) {
            currentStreak = 0
            isFrozen = false
        }

        // 3. Calculate Completion Percentage
        // Formula: DONE / (Eligible Scheduled - SKIPPED) * 100
        val isTodayScheduled = scheduledDates.contains(todayDate)
        val todayRecord = recordMap[todayDate]
        val isTodayPending = isTodayScheduled && (todayRecord == null)

        // Eligible scheduled days: all scheduled dates on or before todayDate, excluding today if pending
        val eligibleDates = if (isTodayPending) {
            scheduledDates.filter { it.isBefore(todayDate) }
        } else {
            scheduledDates.filter { !it.isAfter(todayDate) }
        }

        val eligibleSkippedCount = eligibleDates.count { recordMap[it]?.status == HabitRecordStatus.SKIPPED }
        val denominatorDays = eligibleDates.size - eligibleSkippedCount

        val completionRate = if (denominatorDays > 0) {
            ((totalCompletedScheduled.toDouble() / denominatorDays.toDouble()) * 100.0)
                .toFloat()
                .coerceIn(0.0f, 100.0f)
        } else if (totalCompletedScheduled > 0) {
            100.0f
        } else {
            0.0f
        }

        val finalCurrentStreak = if (habit.isArchived) 0 else currentStreak
        val finalIsFrozen = if (habit.isArchived) false else isFrozen

        return StreakResult(
            currentStreak = finalCurrentStreak,
            longestStreak = longestStreak,
            totalCompletions = totalCompletedScheduled,
            totalScheduledDays = scheduledDates.size,
            completionRate = completionRate,
            isFrozen = finalIsFrozen,
            totalSkippedDays = totalSkippedScheduled
        )
    }
}
