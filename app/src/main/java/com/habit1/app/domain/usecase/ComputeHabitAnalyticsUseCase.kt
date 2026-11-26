package com.habit1.app.domain.usecase

import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.AnalyticsRange
import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitAnalyticsSummary
import com.habit1.app.domain.model.HabitHistoryDay
import com.habit1.app.domain.model.HabitPause
import com.habit1.app.domain.model.HabitRecord
import com.habit1.app.domain.model.HabitRecordStatus
import com.habit1.app.domain.model.HabitScheduleVersion
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.model.QuantitativeAnalyticsStats
import com.habit1.app.domain.model.WeekdayAdherence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure domain use case that evaluates consistency and quantitative performance
 * metrics for a habit over an [AnalyticsRange].
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
        zoneId: ZoneId = ZoneId.systemDefault(),
        pauses: List<HabitPause> = emptyList(),
        scheduleVersions: List<HabitScheduleVersion> = emptyList()
    ): HabitAnalyticsSummary {
        val creationDate = habit.startDate
        val recordsInRange = records.filter { it.date in range }
        val recordsMap = recordsInRange.associateBy { it.date }

        val archiveCivilDate = habit.archivedAt?.let { DateTimeUtils.toLocalDate(it, zoneId) }
        val habitForSchedule = if (habit.isArchived && habit.archivedAt == null) {
            habit.copy(isArchived = false)
        } else {
            habit
        }

        val rawScheduledDates = evaluateSchedule.getScheduledDatesInRange(
            habit = habitForSchedule,
            startDate = range.startDate,
            endDate = range.endDate,
            zoneId = zoneId,
            pauses = pauses,
            scheduleVersions = scheduleVersions
        )
        // Decision 5.3.2: Archival date is an immutable boundary; no scheduled occurrences after archival
        val scheduledDates = if (archiveCivilDate != null) {
            rawScheduledDates.filter { !it.isAfter(archiveCivilDate) }
        } else {
            rawScheduledDates
        }

        // 1. Build Day-by-Day Historical Breakdown
        val dailyBreakdown = mutableListOf<HabitHistoryDay>()
        var currentDate = range.startDate

        while (!currentDate.isAfter(range.endDate)) {
            val record = recordsMap[currentDate]
            val status: CalendarDayStatus = when {
                currentDate.isAfter(todayDate) -> CalendarDayStatus.Future
                currentDate.isBefore(creationDate) -> CalendarDayStatus.PreCreation
                record != null -> {
                    when (record.status) {
                        HabitRecordStatus.DONE -> CalendarDayStatus.Completed(
                            actualValue = record.actualValue,
                            targetValue = record.targetValue,
                            unit = record.unit,
                            measurementType = record.measurementType
                        )
                        HabitRecordStatus.PARTIAL -> CalendarDayStatus.RecordedIncomplete(
                            actualValue = record.actualValue,
                            targetValue = record.targetValue,
                            unit = record.unit,
                            measurementType = record.measurementType
                        )
                        HabitRecordStatus.SKIPPED -> CalendarDayStatus.Skipped(
                            actualValue = record.actualValue,
                            targetValue = record.targetValue,
                            unit = record.unit,
                            measurementType = record.measurementType
                        )
                        HabitRecordStatus.MISSED -> CalendarDayStatus.ExplicitMissed(
                            notes = record.notes
                        )
                        HabitRecordStatus.UNRECORDED -> CalendarDayStatus.ProjectedMissed
                    }
                }
                archiveCivilDate != null && currentDate.isAfter(archiveCivilDate) -> CalendarDayStatus.ProjectedRest
                pauses.any { it.covers(currentDate) } || habit.isCurrentlyPaused || habit.isPaused -> CalendarDayStatus.Paused
                currentDate == todayDate -> CalendarDayStatus.Future
                else -> {
                    val isScheduled = evaluateSchedule.isScheduledOn(habitForSchedule, currentDate, zoneId, pauses, scheduleVersions)
                    if (isScheduled) {
                        CalendarDayStatus.ProjectedMissed
                    } else {
                        CalendarDayStatus.ProjectedRest
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
            (it.status is CalendarDayStatus.ProjectedMissed || it.status is CalendarDayStatus.ExplicitMissed) && it.date.isBefore(todayDate)
        }
        val restDaysCount = dailyBreakdown.count { it.status is CalendarDayStatus.ProjectedRest }

        // 3. Evaluate Consistency / Completion Rate
        // Formula: DONE / (Eligible Scheduled - SKIPPED) * 100
        val isTodayScheduled = scheduledDates.contains(todayDate)
        val todayRecord = recordsMap[todayDate]
        val todayScheduledAndIncomplete = isTodayScheduled && (todayRecord == null || todayRecord.status != HabitRecordStatus.DONE)

        val eligibleDates = (if (todayScheduledAndIncomplete) {
            scheduledDates.filter { it.isBefore(todayDate) }
        } else {
            scheduledDates.filter { !it.isAfter(todayDate) }
        }).filter { date ->
            archiveCivilDate == null || !date.isAfter(archiveCivilDate)
        }

        val eligibleSkippedCount = eligibleDates.count { recordsMap[it]?.status == HabitRecordStatus.SKIPPED }
        val eligibleScheduledDays = (eligibleDates.size - eligibleSkippedCount).coerceAtLeast(0)

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
            startDate = null,
            endDate = if (range.endDate.isBefore(todayDate)) range.endDate else todayDate,
            pauses = pauses,
            scheduleVersions = scheduleVersions
        )

        // 5. Weekday Adherence Breakdown (Monday through Sunday per Decision 5.1.3)
        val weekdays = listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY
        )
        val weekdayAdherence = weekdays.map { dayOfWeek ->
            val datesForDay = eligibleDates.filter { it.dayOfWeek == dayOfWeek }
            val skippedForDay = datesForDay.count { recordsMap[it]?.status == HabitRecordStatus.SKIPPED }
            val eligibleDayCount = (datesForDay.size - skippedForDay).coerceAtLeast(0)
            val completedForDay = datesForDay.count { recordsMap[it]?.status == HabitRecordStatus.DONE }
            val adherenceRate = if (eligibleDayCount > 0) {
                ((completedForDay.toDouble() / eligibleDayCount.toDouble()) * 100.0)
                    .toFloat()
                    .coerceIn(0.0f, 100.0f)
            } else {
                null
            }
            WeekdayAdherence(
                dayOfWeek = dayOfWeek,
                completedCount = completedForDay,
                eligibleScheduledCount = eligibleDayCount,
                adherenceRate = adherenceRate
            )
        }

        // 6. Quantitative Analytics (Decision 5.2.2 & 5.2.3)
        val quantitativeStats = if (habit.measurement is MeasurementType.BooleanChoice) {
            null
        } else {
            val totalActual = recordsInRange.sumOf { it.actualValue }
            val completedRecords = recordsInRange.filter { it.status == HabitRecordStatus.DONE }

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

            // Decision 5.2.2: Active Day Average = Total Volume / Days with actual_value > 0
            val activeRecords = recordsInRange.filter { it.actualValue > 0.0 }
            val activeDaysCount = activeRecords.size
            val activeDayAvg = if (activeDaysCount > 0) {
                totalActual / activeDaysCount.toDouble()
            } else null

            // Decision 5.2.2: Scheduled Daily Average = Volume on Scheduled Days / (Eligible Scheduled Days - Count(SKIPPED))
            val scheduledRecords = recordsInRange.filter { it.date in eligibleDates }
            val scheduledVolume = scheduledRecords.sumOf { it.actualValue }
            val scheduledDailyAvg = if (eligibleScheduledDays > 0) {
                scheduledVolume / eligibleScheduledDays.toDouble()
            } else null

            // Decision 5.2.3: Bonus Activity (activity on unscheduled/rest days)
            val bonusRecords = recordsInRange.filter { it.date !in eligibleDates && it.actualValue > 0.0 }
            val bonusDaysCount = bonusRecords.size
            val bonusVolume = bonusRecords.sumOf { it.actualValue }

            QuantitativeAnalyticsStats(
                totalActualValue = totalActual,
                averageOnCompletedDays = avgCompleted,
                averageOnRecordedDays = avgRecorded,
                recordedDaysCount = recordsInRange.size,
                completedDaysCount = completedRecords.size,
                targetAchievementRate = completionRate,
                successRateOnRecordedDays = successRateOnRecorded,
                activeDaysCount = activeDaysCount,
                activeDayAverage = activeDayAvg,
                scheduledDailyAverage = scheduledDailyAvg,
                scheduledVolume = scheduledVolume,
                bonusDaysCount = bonusDaysCount,
                bonusVolume = bonusVolume
            )
        }

        // 6. Period Comparison with preceding equivalent period
        val dayCount = range.dayCount
        val prevRange = AnalyticsRange(range.startDate.minusDays(dayCount.toLong()), range.startDate.minusDays(1))
        val prevRawScheduled = evaluateSchedule.getScheduledDatesInRange(
            habit = habitForSchedule,
            startDate = prevRange.startDate,
            endDate = prevRange.endDate,
            zoneId = zoneId,
            pauses = pauses,
            scheduleVersions = scheduleVersions
        )
        val prevScheduled = if (archiveCivilDate != null) {
            prevRawScheduled.filter { !it.isAfter(archiveCivilDate) }
        } else {
            prevRawScheduled
        }

        val prevRecordsMap = records.filter { it.date in prevRange }.associateBy { it.date }
        val prevSkipped = prevScheduled.count { prevRecordsMap[it]?.status == HabitRecordStatus.SKIPPED }
        val prevEligible = (prevScheduled.size - prevSkipped).coerceAtLeast(0)
        val prevCompleted = prevRecordsMap.values.count { it.status == HabitRecordStatus.DONE }
        val prevRate = if (prevEligible > 0) {
            ((prevCompleted.toDouble() / prevEligible.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f)
        } else if (prevCompleted > 0) 100f else 0f

        val hasSufficientData = prevEligible > 0 && !prevRange.endDate.isBefore(creationDate)
        val periodComparison = com.habit1.app.domain.model.PeriodComparison(
            previousRange = prevRange,
            previousCompletionRate = prevRate,
            currentCompletionRate = completionRate,
            rateDelta = completionRate - prevRate,
            previousCompletedCount = prevCompleted,
            currentCompletedCount = completedDaysCount,
            completedDelta = completedDaysCount - prevCompleted,
            isSufficientData = hasSufficientData
        )

        val baseSummary = HabitAnalyticsSummary(
            habit = habit,
            range = range,
            scheduledDays = scheduledDates.size,
            eligibleScheduledDays = eligibleScheduledDays,
            completedDays = completedDaysCount,
            recordedIncompleteDays = recordedIncompleteCount,
            missedDays = missedDaysCount,
            restDays = restDaysCount,
            completionRate = completionRate,
            currentStreak = streakResult.currentStreak,
            longestStreak = streakResult.longestStreak,
            quantitativeStats = quantitativeStats,
            dailyBreakdown = dailyBreakdown,
            weekdayAdherence = weekdayAdherence,
            periodComparison = periodComparison
        )

        val insights = GenerateAnalyticsInsightsUseCase().generateHabitInsights(baseSummary)
        return baseSummary.copy(insights = insights)
    }

    /**
     * Overload to compute analytics directly from [HabitRecordEntity] rows.
     */
    fun executeWithEntities(
        habit: Habit,
        recordEntities: List<HabitRecordEntity>,
        range: AnalyticsRange,
        todayDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        pauses: List<HabitPause> = emptyList(),
        scheduleVersions: List<HabitScheduleVersion> = emptyList()
    ): HabitAnalyticsSummary {
        return execute(
            habit = habit,
            records = recordEntities.map { it.toDomain() },
            range = range,
            todayDate = todayDate,
            zoneId = zoneId,
            pauses = pauses,
            scheduleVersions = scheduleVersions
        )
    }
}
