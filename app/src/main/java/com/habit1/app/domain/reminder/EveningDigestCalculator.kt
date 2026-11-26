package com.habit1.app.domain.reminder

import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitPauseEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.HabitRecordStatus
import com.habit1.app.domain.usecase.EvaluateScheduleUseCase
import java.time.LocalDate
import java.time.ZoneId

data class EveningDigestSummary(
    val completedCount: Int,
    val pendingCount: Int,
    val totalScheduledCount: Int,
    val date: LocalDate
)

class EveningDigestCalculator(
    private val evaluateSchedule: EvaluateScheduleUseCase = EvaluateScheduleUseCase()
) {

    /**
     * Calculates the summary for the evening digest on [date].
     *
     * Only considers eligible scheduled habits:
     * - Not pre-creation (date is not before habit.startDate)
     * - Not archived (or archived after date)
     * - Not paused on date (checks both isPaused and habit_pauses)
     * - Scheduled on date
     *
     * Habits marked SKIPPED are excluded from pending and total.
     * Returns null if there are no eligible scheduled habits for the date.
     */
    fun calculateDigest(
        habits: List<HabitEntity>,
        records: List<HabitRecordEntity>,
        pausesByHabit: Map<String, List<HabitPauseEntity>>,
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): EveningDigestSummary? {
        val recordsByHabit = records.associateBy { it.habitId }

        var completed = 0
        var pending = 0

        for (habitEntity in habits) {
            val domainHabit = habitEntity.toDomain(zoneId)

            // 1. Check startDate pre-creation boundary
            if (habitEntity.startDate.isNotBlank()) {
                val start = try { DateTimeUtils.parseDate(habitEntity.startDate) } catch (_: Exception) { null }
                if (start != null && date.isBefore(start)) {
                    continue
                }
            }

            // 2. Check archival
            if (habitEntity.isArchived) {
                continue
            }

            // 3. Check pauses
            val pauses = pausesByHabit[habitEntity.id] ?: emptyList()
            val isPausedOnDate = habitEntity.isPaused || pauses.any { pause ->
                val pStart = try { DateTimeUtils.parseDate(pause.startDate) } catch (_: Exception) { null }
                val pEnd = pause.endDate?.let { try { DateTimeUtils.parseDate(it) } catch (_: Exception) { null } }
                if (pStart != null) {
                    if (pEnd != null) {
                        !date.isBefore(pStart) && !date.isAfter(pEnd)
                    } else {
                        !date.isBefore(pStart)
                    }
                } else false
            }
            if (isPausedOnDate) {
                continue
            }

            // 4. Check schedule
            if (!evaluateSchedule.isScheduledOn(domainHabit, date, zoneId)) {
                continue
            }

            // 5. Evaluate record
            val record = recordsByHabit[habitEntity.id]
            if (record != null) {
                when (record.status) {
                    HabitRecordStatus.DONE.name -> completed++
                    HabitRecordStatus.SKIPPED.name -> {
                        // Excused rest - does not count as pending or completed
                    }
                    else -> {
                        if (record.isCompleted) completed++ else pending++
                    }
                }
            } else {
                pending++
            }
        }

        val totalScheduled = completed + pending
        if (totalScheduled == 0) {
            return null
        }

        return EveningDigestSummary(
            completedCount = completed,
            pendingCount = pending,
            totalScheduledCount = totalScheduled,
            date = date
        )
    }
}
