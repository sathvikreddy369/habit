package com.habit1.app.domain.usecase

import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.AnalyticsRange
import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitAnalyticsSummary
import com.habit1.app.domain.model.HabitHistoryDay
import com.habit1.app.domain.model.HabitRecord
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.model.QuantitativeAnalyticsStats
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure domain use case that evaluates consistency and quantitative performance
 * metrics for a habit over an [AnalyticsRange].
 *
 * Core Semantics:
 * 1. Historical Truth: Factual records in SQLite are immutable facts. A record with isCompleted=true
 *    is factually completed; a record with isCompleted=false is recorded incomplete/partial.
 * 2. Schedule Projections: For unrecorded past dates (< todayDate), expected dates derived from the
 *    habit's current schedule are classified as ProjectedMissed; non-expected dates are ProjectedRest.
 * 3. Completion Denominator: Future dates, pre-creation dates, paused dates, and non-scheduled rest
 *    days are never counted as missed.
 * 4. Current-Day Grace: If today is scheduled but not yet completed, it is excluded from the completion
 *    rate denominator so morning checks do not artificially depress consistency.
 * 5. Streak Reuse: Streaks are delegated authoritatively to [CalculateStreaksUseCase].
 * 6. Quantitative Precision: Exact recorded values are preserved without artificial clamping.
 */
class ComputeHabitAnalyticsUseCase(
    private val evaluateSchedule: EvaluateScheduleUseCase = EvaluateScheduleUseCase(),
    private val calculateStreaks: CalculateStreaksUseCase = CalculateStreaksUseCase()
) {

    /**
     * Executes the analytics evaluation using domain models.
     */
    fun execute(
        habit: Habit,
        records: List<HabitRecord>,
        range: AnalyticsRange,
        todayDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): HabitAnalyticsSummary {
        val creationDate = DateTimeUtils.toLocalDate(habit.createdAt, zoneId)
        val recordsInRange = records.filter { it.date in range }
        val recordsMap = recordsInRange.associateBy { it.date }

        val habitForSchedule = if (habit.isArchived) habit.copy(isArchived = false) else habit
        val scheduledDates = evaluateSchedule.getScheduledDatesInRange(
            habit = habitForSchedule,
            startDate = range.startDate,
            endDate = range.endDate,
            zoneId = zoneId
        )

        // 1. Build Day-by-Day Historical Breakdown
        val dailyBreakdown = mutableListOf<HabitHistoryDay>()
        var currentDate = range.startDate

        while (!currentDate.isAfter(range.endDate)) {
            val status: CalendarDayStatus = when {
                currentDate.isAfter(todayDate) -> CalendarDayStatus.Future
                currentDate.isBefore(creationDate) -> CalendarDayStatus.PreCreation
                habit.isPaused -> CalendarDayStatus.Paused
                else -> {
                    val record = recordsMap[currentDate]
                    if (record != null) {
                        if (record.isCompleted) {
                            CalendarDayStatus.Completed(
                                actualValue = record.actualValue,
                                targetValue = record.targetValue,
                                unit = record.unit,
                                measurementType = record.measurementType
                            )
                        } else {
                            CalendarDayStatus.RecordedIncomplete(
                                actualValue = record.actualValue,
                                targetValue = record.targetValue,
                                unit = record.unit,
                                measurementType = record.measurementType
                            )
                        }
                    } else {
                        val isScheduled = evaluateSchedule.isScheduledOn(habitForSchedule, currentDate, zoneId)
                        if (isScheduled) {
                            CalendarDayStatus.ProjectedMissed
                        } else {
                            CalendarDayStatus.ProjectedRest
                        }
                    }
                }
            }

            dailyBreakdown.add(HabitHistoryDay(date = currentDate, status = status))
            currentDate = currentDate.plusDays(1)
        }

        // 2. Aggregate Days Counts
        val completedDaysCount = dailyBreakdown.count { it.status is CalendarDayStatus.Completed }
        val recordedIncompleteCount = dailyBreakdown.count { it.status is CalendarDayStatus.RecordedIncomplete }
        val missedDaysCount = dailyBreakdown.count {
            it.status is CalendarDayStatus.ProjectedMissed && it.date.isBefore(todayDate)
        }
        val restDaysCount = dailyBreakdown.count { it.status is CalendarDayStatus.ProjectedRest }

        // 3. Evaluate Consistency / Completion Rate
        // Eligible scheduled dates: scheduled dates on or before todayDate.
        // If today is scheduled but not yet completed, exclude today from the denominator so
        // the rate is not depressed prematurely before midnight.
        val todayScheduledAndIncomplete = scheduledDates.contains(todayDate) && recordsMap[todayDate]?.isCompleted != true
        val eligibleScheduledDays = if (todayScheduledAndIncomplete) {
            scheduledDates.count { it.isBefore(todayDate) }
        } else {
            scheduledDates.count { !it.isAfter(todayDate) }
        }

        val completionRate = if (eligibleScheduledDays > 0) {
            ((completedDaysCount.toDouble() / eligibleScheduledDays.toDouble()) * 100.0)
                .toFloat()
                .coerceIn(0.0f, 100.0f)
        } else if (completedDaysCount > 0) {
            100.0f
        } else {
            0.0f
        }

        // 4. Calculate Streaks using authoritative use case
        val streakResult = calculateStreaks.execute(
            habit = habit,
            records = records,
            todayDate = todayDate,
            zoneId = zoneId,
            startDate = range.startDate,
            endDate = range.endDate
        )

        // 5. Quantitative Analytics
        val quantitativeStats = if (habit.measurement is MeasurementType.BooleanChoice) {
            null
        } else {
            val totalActual = recordsInRange.sumOf { it.actualValue }
            val completedRecords = recordsInRange.filter { it.isCompleted }

            val avgCompleted = if (completedRecords.isNotEmpty()) {
                completedRecords.map { it.actualValue }.average()
            } else null

            val avgRecorded = if (recordsInRange.isNotEmpty()) {
                recordsInRange.map { it.actualValue }.average()
            } else null

            val successRateOnRecorded = if (recordsInRange.isNotEmpty()) {
                ((completedRecords.size.toDouble() / recordsInRange.size.toDouble()) * 100.0)
                    .toFloat()
                    .coerceIn(0.0f, 100.0f)
            } else null

            QuantitativeAnalyticsStats(
                totalActualValue = totalActual,
                averageOnCompletedDays = avgCompleted,
                averageOnRecordedDays = avgRecorded,
                recordedDaysCount = recordsInRange.size,
                completedDaysCount = completedRecords.size,
                targetAchievementRate = completionRate,
                successRateOnRecordedDays = successRateOnRecorded
            )
        }

        return HabitAnalyticsSummary(
            habit = habit,
            range = range,
            scheduledDays = scheduledDates.size,
            completedDays = completedDaysCount,
            recordedIncompleteDays = recordedIncompleteCount,
            missedDays = missedDaysCount,
            restDays = restDaysCount,
            completionRate = completionRate,
            currentStreak = streakResult.currentStreak,
            longestStreak = streakResult.longestStreak,
            quantitativeStats = quantitativeStats,
            dailyBreakdown = dailyBreakdown
        )
    }

    /**
     * Overload to compute analytics directly from [HabitRecordEntity] rows.
     */
    fun executeWithEntities(
        habit: Habit,
        recordEntities: List<HabitRecordEntity>,
        range: AnalyticsRange,
        todayDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): HabitAnalyticsSummary {
        return execute(
            habit = habit,
            records = recordEntities.map { it.toDomain() },
            range = range,
            todayDate = todayDate,
            zoneId = zoneId
        )
    }
}
