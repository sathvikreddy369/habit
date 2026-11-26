package com.habit1.app.domain.usecase

import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.HabitRecordStatus
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
 * 6. Status Derivation: Correctly tags records with DONE, PARTIAL, SKIPPED, or MISSED.
 */
class RecordHabitProgressUseCase(
    private val habitRepository: HabitRepository,
    private val habitRecordRepository: HabitRecordRepository,
    private val evaluateMeasurement: EvaluateMeasurementUseCase = EvaluateMeasurementUseCase(),
    private val evaluateSchedule: EvaluateScheduleUseCase = EvaluateScheduleUseCase(),
    private val reminderCoordinator: com.habit1.app.domain.reminder.HabitReminderCoordinator? = null
) {

    /**
     * Marks a habit as completed for [targetDate].
     * For Boolean habits: actualValue = 1.0, status = DONE.
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
            Triple(actual, true, HabitRecordStatus.DONE.name)
        }
    }

    /**
     * Marks a habit as incomplete for [targetDate].
     * Sets actualValue = 0.0, isCompleted = false.
     * Boolean habits revert to UNRECORDED; quantitative habits to PARTIAL.
     */
    suspend fun markIncomplete(
        habitId: String,
        targetDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        enforceSchedule: Boolean = false
    ): Result<HabitRecordEntity> {
        return updateRecord(habitId, targetDate, zoneId, enforceSchedule) { habit, _, _ ->
            val status = if (habit.measurement is MeasurementType.BooleanChoice) {
                HabitRecordStatus.UNRECORDED.name
            } else {
                HabitRecordStatus.PARTIAL.name
            }
            Triple(0.0, false, status)
        }
    }

    /**
     * Explicitly marks a habit as SKIPPED for [targetDate].
     * Preserves any existing non-zero actual volume.
     */
    suspend fun markSkipped(
        habitId: String,
        targetDate: LocalDate,
        notes: String? = null,
        zoneId: ZoneId = ZoneId.systemDefault(),
        enforceSchedule: Boolean = false
    ): Result<HabitRecordEntity> {
        return updateRecord(habitId, targetDate, zoneId, enforceSchedule, customNotes = notes) { _, existing, _ ->
            val retainedActual = existing?.actualValue ?: 0.0
            Triple(retainedActual, false, HabitRecordStatus.SKIPPED.name)
        }
    }

    /**
     * Explicitly marks a habit as MISSED for [targetDate].
     * Breaks streak and revokes morning grace.
     */
    suspend fun markMissed(
        habitId: String,
        targetDate: LocalDate,
        notes: String? = null,
        zoneId: ZoneId = ZoneId.systemDefault(),
        enforceSchedule: Boolean = false
    ): Result<HabitRecordEntity> {
        return updateRecord(habitId, targetDate, zoneId, enforceSchedule, customNotes = notes) { _, _, _ ->
            Triple(0.0, false, HabitRecordStatus.MISSED.name)
        }
    }

    /**
     * Clears (deletes) a record for [targetDate], cleanly reverting it to virtual UNRECORDED.
     */
    suspend fun clearRecord(
        habitId: String,
        targetDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Result<Unit> {
        val today = DateTimeUtils.today(zoneId)
        if (targetDate.isAfter(today)) {
            return Result.failure(IllegalStateException("Cannot clear record for future date: $targetDate"))
        }
        val dateString = DateTimeUtils.formatDate(targetDate)
        habitRecordRepository.deleteRecord(habitId, dateString)
        return Result.success(Unit)
    }

    /**
     * Sets an explicit numeric actual value for [targetDate].
     */
    suspend fun setActualValue(
        habitId: String,
        targetDate: LocalDate,
        value: Double,
        zoneId: ZoneId = ZoneId.systemDefault(),
        enforceSchedule: Boolean = false,
        customTargetValue: Double? = null,
        customNotes: String? = null
    ): Result<HabitRecordEntity> {
        return updateRecord(habitId, targetDate, zoneId, enforceSchedule, customTargetValue = customTargetValue, customNotes = customNotes) { habit, existing, effectiveTarget ->
            val clampedVal = value.coerceAtLeast(0.0)
            val isCompleted = clampedVal >= effectiveTarget
            val status = when {
                habit.measurement is MeasurementType.BooleanChoice -> {
                    if (isCompleted) HabitRecordStatus.DONE.name else HabitRecordStatus.UNRECORDED.name
                }
                isCompleted -> HabitRecordStatus.DONE.name
                existing?.status == HabitRecordStatus.SKIPPED.name -> HabitRecordStatus.SKIPPED.name
                clampedVal > 0.0 -> HabitRecordStatus.PARTIAL.name
                else -> HabitRecordStatus.PARTIAL.name
            }
            Triple(clampedVal, isCompleted, status)
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
                val revertedStatus = if (habit.measurement is MeasurementType.BooleanChoice) {
                    HabitRecordStatus.UNRECORDED.name
                } else {
                    HabitRecordStatus.PARTIAL.name
                }
                Triple(0.0, false, revertedStatus)
            } else {
                Triple(targetVal, true, HabitRecordStatus.DONE.name)
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
        return updateRecord(habitId, targetDate, zoneId, enforceSchedule = false) { habit, existing, effectiveTarget ->
            val currentVal = existing?.actualValue ?: 0.0
            val step = evaluateMeasurement.defaultStep(habit.measurement)
            val newVal = if (increment) {
                currentVal + step
            } else {
                (currentVal - step).coerceAtLeast(0.0)
            }
            val isCompleted = evaluateMeasurement.isCompleted(habit.measurement, newVal)
            val status = when {
                habit.measurement is MeasurementType.BooleanChoice -> {
                    if (isCompleted) HabitRecordStatus.DONE.name else HabitRecordStatus.UNRECORDED.name
                }
                isCompleted -> HabitRecordStatus.DONE.name
                existing?.status == HabitRecordStatus.SKIPPED.name -> HabitRecordStatus.SKIPPED.name
                newVal > 0.0 -> HabitRecordStatus.PARTIAL.name
                else -> HabitRecordStatus.PARTIAL.name
            }
            Triple(newVal, isCompleted, status)
        }
    }

    private suspend fun updateRecord(
        habitId: String,
        targetDate: LocalDate,
        zoneId: ZoneId,
        enforceSchedule: Boolean,
        customNotes: String? = null,
        customTargetValue: Double? = null,
        calculateNewValues: (habit: com.habit1.app.domain.model.Habit, existing: HabitRecordEntity?, targetVal: Double) -> Triple<Double, Boolean, String>
    ): Result<HabitRecordEntity> {
        val today = DateTimeUtils.today(zoneId)
        if (targetDate.isAfter(today)) {
            return Result.failure(IllegalStateException("Cannot record progress for future date: $targetDate"))
        }

        val habitEntity = habitRepository.getHabitById(habitId)
            ?: return Result.failure(IllegalArgumentException("Habit not found: $habitId"))

        if (habitEntity.isArchived) {
            return Result.failure(IllegalStateException("Cannot record progress on archived habit: $habitId"))
        }

        if (habitEntity.isPaused || habitEntity.isCurrentlyPaused) {
            return Result.failure(IllegalStateException("Cannot record progress on paused habit: $habitId"))
        }

        val habit = habitEntity.toDomain(zoneId)

        if (habitEntity.startDate.isNotBlank() && targetDate.isBefore(habit.startDate)) {
            return Result.failure(IllegalStateException("Cannot record progress before habit start date (${habit.startDate}): $targetDate"))
        }

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

        // Snapshot preservation: If record already existed, preserve its historical targetValue, measurementType, and unit
        // If backfilling a new record, use customTargetValue if provided, otherwise default to current targetVal
        val targetSnapshot = existingRecord?.targetValue ?: customTargetValue ?: targetVal
        val measurementTypeSnapshot = existingRecord?.measurementType ?: habit.measurement.typeName
        val unitSnapshot = existingRecord?.unit ?: habitEntity.unit
        val notesToSave = customNotes ?: existingRecord?.notes

        val (newActualValue, newIsCompleted, newStatus) = calculateNewValues(habit, existingRecord, targetSnapshot)

        val recordToSave = HabitRecordEntity(
            id = existingRecord?.id ?: UUID.randomUUID().toString(),
            habitId = habitId,
            date = dateString,
            actualValue = newActualValue,
            targetValue = targetSnapshot,
            measurementType = measurementTypeSnapshot,
            unit = unitSnapshot,
            isCompleted = newIsCompleted,
            notes = notesToSave,
            recordedAt = System.currentTimeMillis(),
            status = newStatus
        )

        habitRecordRepository.recordProgress(recordToSave)

        if (targetDate == today && (recordToSave.isCompleted || recordToSave.status == HabitRecordStatus.DONE.name)) {
            reminderCoordinator?.onHabitCompletedToday(habitId, zoneId)
        }

        return Result.success(recordToSave)
    }
}
