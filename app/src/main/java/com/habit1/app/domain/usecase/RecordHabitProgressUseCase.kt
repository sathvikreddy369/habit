package com.habit1.app.domain.usecase

import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.MeasurementType
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Pure domain UseCase for recording and updating habit progress.
 *
 * Enforces unified business logic across both UI interactions (TodayViewModel)
 * and platform notification actions (HabitActionReceiver).
 *
 * Core Guarantees:
 * 1. Single Source of Truth: Re-reads habit from repository to prevent operating on stale data.
 * 2. Defense in Depth: Rejects archived or paused habits.
 * 3. Explicit Target Date: Respects the civil target date passed in rather than assuming today.
 * 4. Idempotency: Reuses existing record ID for (habitId, date) to prevent duplicate records.
 * 5. Value Preservation: Quantitative DONE never downgrades an existing actual value that exceeds target.
 */
class RecordHabitProgressUseCase(
    private val habitRepository: HabitRepository,
    private val habitRecordRepository: HabitRecordRepository,
    private val evaluateMeasurement: EvaluateMeasurementUseCase = EvaluateMeasurementUseCase(),
    private val evaluateSchedule: EvaluateScheduleUseCase = EvaluateScheduleUseCase()
) {

    /**
     * Marks a habit as completed for [targetDate].
     * For Boolean habits: actualValue = 1.0, isCompleted = true.
     * For Quantitative habits: if existing actual > target, preserves actual; otherwise sets actual = target.
     */
    suspend fun markCompleted(
        habitId: String,
        targetDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        enforceSchedule: Boolean = false
    ): Result<HabitRecordEntity> {
        return updateRecord(habitId, targetDate, zoneId, enforceSchedule) { habit, existing, targetVal ->
            val currentActual = existing?.actualValue ?: 0.0
            val actual = if (currentActual > targetVal && existing?.isCompleted == true) {
                currentActual
            } else {
                targetVal
            }
            Pair(actual, true)
        }
    }

    /**
     * Marks a habit as incomplete for [targetDate].
     * Sets actualValue = 0.0, isCompleted = false.
     */
    suspend fun markIncomplete(
        habitId: String,
        targetDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        enforceSchedule: Boolean = false
    ): Result<HabitRecordEntity> {
        return updateRecord(habitId, targetDate, zoneId, enforceSchedule) { _, _, _ ->
            Pair(0.0, false)
        }
    }

    /**
     * Sets an explicit numeric actual value for [targetDate].
     */
    suspend fun setActualValue(
        habitId: String,
        targetDate: LocalDate,
        value: Double,
        zoneId: ZoneId = ZoneId.systemDefault(),
        enforceSchedule: Boolean = false
    ): Result<HabitRecordEntity> {
        return updateRecord(habitId, targetDate, zoneId, enforceSchedule) { habit, _, _ ->
            val clampedVal = value.coerceAtLeast(0.0)
            val isCompleted = evaluateMeasurement.isCompleted(habit.measurement, clampedVal)
            Pair(clampedVal, isCompleted)
        }
    }

    /**
     * Toggles completion status (used by TodayScreen direct tap).
     */
    suspend fun toggleHabit(
        habitId: String,
        targetDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Result<HabitRecordEntity> {
        return updateRecord(habitId, targetDate, zoneId, enforceSchedule = false) { habit, existing, targetVal ->
            val isCurrentlyCompleted = existing?.isCompleted ?: false
            if (isCurrentlyCompleted) {
                Pair(0.0, false)
            } else {
                Pair(targetVal, true)
            }
        }
    }

    /**
     * Adjusts numeric value by default step (used by +/- buttons).
     */
    suspend fun adjustHabit(
        habitId: String,
        targetDate: LocalDate,
        increment: Boolean,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Result<HabitRecordEntity> {
        return updateRecord(habitId, targetDate, zoneId, enforceSchedule = false) { habit, existing, _ ->
            val currentVal = existing?.actualValue ?: 0.0
            val step = evaluateMeasurement.defaultStep(habit.measurement)
            val newVal = if (increment) {
                currentVal + step
            } else {
                (currentVal - step).coerceAtLeast(0.0)
            }
            val isCompleted = evaluateMeasurement.isCompleted(habit.measurement, newVal)
            Pair(newVal, isCompleted)
        }
    }

    private suspend fun updateRecord(
        habitId: String,
        targetDate: LocalDate,
        zoneId: ZoneId,
        enforceSchedule: Boolean,
        calculateNewValues: (habit: com.habit1.app.domain.model.Habit, existing: HabitRecordEntity?, targetVal: Double) -> Pair<Double, Boolean>
    ): Result<HabitRecordEntity> {
        val habitEntity = habitRepository.getHabitById(habitId)
            ?: return Result.failure(IllegalArgumentException("Habit not found: $habitId"))

        if (habitEntity.isArchived) {
            return Result.failure(IllegalStateException("Cannot record progress on archived habit: $habitId"))
        }

        if (habitEntity.isPaused) {
            return Result.failure(IllegalStateException("Cannot record progress on paused habit: $habitId"))
        }

        val habit = habitEntity.toDomain()

        if (enforceSchedule && !evaluateSchedule.isScheduledOn(habit, targetDate, zoneId)) {
            return Result.failure(IllegalStateException("Habit is not scheduled on $targetDate: $habitId"))
        }

        val targetVal = when (val m = habit.measurement) {
            is MeasurementType.BooleanChoice -> 1.0
            is MeasurementType.Count -> m.target.toDouble()
            is MeasurementType.Duration -> m.targetMinutes.toDouble()
            is MeasurementType.Quantity -> m.target
        }

        val dateString = DateTimeUtils.formatDate(targetDate)
        val existingRecord = habitRecordRepository.getRecord(habitId, dateString)

        val (newActualValue, newIsCompleted) = calculateNewValues(habit, existingRecord, targetVal)

        val recordToSave = HabitRecordEntity(
            id = existingRecord?.id ?: UUID.randomUUID().toString(),
            habitId = habitId,
            date = dateString,
            actualValue = newActualValue,
            targetValue = targetVal,
            measurementType = habit.measurement.typeName,
            unit = habitEntity.unit,
            isCompleted = newIsCompleted,
            recordedAt = System.currentTimeMillis()
        )

        habitRecordRepository.recordProgress(recordToSave)
        return Result.success(recordToSave)
    }
}
