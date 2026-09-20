package com.habit1.app.domain.mapper

import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.DailyGoalWithSubtasks
import com.habit1.app.data.local.db.entity.DailyReviewEntity
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.domain.model.DailyGoal
import com.habit1.app.domain.model.DailyReview
import com.habit1.app.domain.model.GoalSubtask
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitRecord
import com.habit1.app.domain.model.MeasurementType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

object EntityMappers {

    fun HabitEntity.toDomain(): Habit {
        val measurement = when (measurementType) {
            MeasurementType.Count.TYPE_NAME -> MeasurementType.Count(
                target = targetValue.toInt().coerceAtLeast(1),
                unit = unit
            )
            MeasurementType.Duration.TYPE_NAME -> MeasurementType.Duration(
                targetMinutes = targetValue.toInt().coerceAtLeast(1)
            )
            MeasurementType.Quantity.TYPE_NAME -> MeasurementType.Quantity(
                target = targetValue.coerceAtLeast(0.001),
                unit = unit ?: ""
            )
            else -> MeasurementType.BooleanChoice
        }

        val createdInstant = Instant.ofEpochMilli(createdAt)
        val schedule = ScheduleConfigSerializer.deserialize(
            scheduleType = scheduleType,
            scheduleConfig = scheduleConfig,
            fallbackAnchorDate = LocalDate.ofEpochDay(createdAt / (24 * 60 * 60 * 1000L))
        )

        val reminder = reminderTime?.let {
            try {
                LocalTime.parse(it)
            } catch (e: Exception) {
                null
            }
        }

        return Habit(
            id = id,
            name = name,
            description = description,
            measurement = measurement,
            schedule = schedule,
            reminderTime = reminder,
            displayOrder = displayOrder,
            isPaused = isPaused,
            isArchived = isArchived,
            createdAt = createdInstant,
            updatedAt = Instant.ofEpochMilli(updatedAt)
        )
    }

    fun Habit.toEntity(): HabitEntity {
        val (schedType, schedConfig) = ScheduleConfigSerializer.serialize(schedule)
        val (targetVal, unitVal) = when (val m = measurement) {
            is MeasurementType.BooleanChoice -> Pair(1.0, null)
            is MeasurementType.Count -> Pair(m.target.toDouble(), m.unit)
            is MeasurementType.Duration -> Pair(m.targetMinutes.toDouble(), "min")
            is MeasurementType.Quantity -> Pair(m.target, m.unit)
        }

        return HabitEntity(
            id = id,
            name = name,
            description = description,
            measurementType = measurement.typeName,
            targetValue = targetVal,
            unit = unitVal,
            scheduleType = schedType,
            scheduleConfig = schedConfig,
            reminderTime = reminderTime?.toString(),
            displayOrder = displayOrder,
            isPaused = isPaused,
            isArchived = isArchived,
            createdAt = createdAt.toEpochMilli(),
            updatedAt = updatedAt.toEpochMilli()
        )
    }

    fun HabitRecordEntity.toDomain(): HabitRecord {
        return HabitRecord(
            id = id,
            habitId = habitId,
            date = LocalDate.parse(date),
            actualValue = actualValue,
            targetValue = targetValue,
            measurementType = measurementType,
            unit = unit,
            isCompleted = isCompleted,
            notes = notes,
            recordedAt = Instant.ofEpochMilli(recordedAt)
        )
    }

    fun HabitRecord.toEntity(): HabitRecordEntity {
        return HabitRecordEntity(
            id = id,
            habitId = habitId,
            date = date.toString(),
            actualValue = actualValue,
            targetValue = targetValue,
            measurementType = measurementType,
            unit = unit,
            isCompleted = isCompleted,
            notes = notes,
            recordedAt = recordedAt.toEpochMilli()
        )
    }

    fun GoalSubtaskEntity.toDomain(): GoalSubtask {
        return GoalSubtask(
            id = id,
            goalId = goalId,
            title = title,
            isCompleted = isCompleted,
            displayOrder = displayOrder,
            createdAt = Instant.ofEpochMilli(createdAt)
        )
    }

    fun GoalSubtask.toEntity(): GoalSubtaskEntity {
        return GoalSubtaskEntity(
            id = id,
            goalId = goalId,
            title = title,
            isCompleted = isCompleted,
            displayOrder = displayOrder,
            createdAt = createdAt.toEpochMilli()
        )
    }

    fun DailyGoalWithSubtasks.toDomain(): DailyGoal {
        return DailyGoal(
            id = goal.id,
            title = goal.title,
            targetDate = LocalDate.parse(goal.targetDate),
            isCompleted = goal.isCompleted,
            displayOrder = goal.displayOrder,
            subtasks = subtasks.sortedWith(compareBy({ it.displayOrder }, { it.createdAt })).map { it.toDomain() },
            notes = goal.notes,
            createdAt = Instant.ofEpochMilli(goal.createdAt),
            updatedAt = Instant.ofEpochMilli(goal.updatedAt)
        )
    }

    fun DailyGoal.toEntity(): DailyGoalEntity {
        return DailyGoalEntity(
            id = id,
            title = title,
            targetDate = targetDate.toString(),
            isCompleted = isCompleted,
            displayOrder = displayOrder,
            notes = notes,
            createdAt = createdAt.toEpochMilli(),
            updatedAt = updatedAt.toEpochMilli()
        )
    }

    fun DailyReviewEntity.toDomain(): DailyReview {
        return DailyReview(
            date = LocalDate.parse(date),
            notes = notes,
            mood = mood,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt)
        )
    }

    fun DailyReview.toEntity(): DailyReviewEntity {
        return DailyReviewEntity(
            date = date.toString(),
            notes = notes,
            mood = mood,
            createdAt = createdAt.toEpochMilli(),
            updatedAt = updatedAt.toEpochMilli()
        )
    }
}
