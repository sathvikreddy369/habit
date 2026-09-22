package com.habit1.app.data.backup.model

import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.DailyReviewEntity
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import kotlinx.serialization.Serializable

/**
 * Top-level versioned backup envelope containing metadata, SHA-256 integrity checksum,
 * and the actual payload.
 */
@Serializable
data class BackupEnvelopeDto(
    val formatVersion: Int,
    val appVersion: String,
    val exportedAt: String, // ISO-8601 UTC timestamp
    val checksum: String,   // Hex-encoded SHA-256 of canonicalized payload JSON
    val payload: BackupPayloadDto
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

/**
 * The data payload containing all user data entities.
 */
@Serializable
data class BackupPayloadDto(
    val habits: List<HabitBackupDto> = emptyList(),
    val records: List<HabitRecordBackupDto> = emptyList(),
    val goals: List<DailyGoalBackupDto> = emptyList(),
    val subtasks: List<GoalSubtaskBackupDto> = emptyList(),
    val reviews: List<DailyReviewBackupDto> = emptyList(),
    val aggregates: List<DailyGoalHistoryAggregateBackupDto> = emptyList()
)

@Serializable
data class DailyGoalHistoryAggregateBackupDto(
    val date: String,
    val completedCount: Int,
    val totalCount: Int
)

@Serializable
data class HabitBackupDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val measurementType: String,
    val targetValue: Double,
    val unit: String? = null,
    val scheduleType: String,
    val scheduleConfig: String,
    val reminderTime: String? = null,
    val displayOrder: Int,
    val isPaused: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class HabitRecordBackupDto(
    val id: String,
    val habitId: String,
    val date: String, // Civil date: YYYY-MM-DD
    val isCompleted: Boolean,
    val actualValue: Double,
    val targetValue: Double,
    val unit: String? = null,
    val measurementType: String,
    val notes: String? = null,
    val recordedAt: Long
)

@Serializable
data class DailyGoalBackupDto(
    val id: String,
    val targetDate: String, // Civil date: YYYY-MM-DD
    val title: String,
    val notes: String? = null,
    val isCompleted: Boolean,
    val displayOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class GoalSubtaskBackupDto(
    val id: String,
    val goalId: String,
    val title: String,
    val isCompleted: Boolean,
    val displayOrder: Int,
    val createdAt: Long
)

@Serializable
data class DailyReviewBackupDto(
    val date: String, // Civil date: YYYY-MM-DD
    val notes: String? = null,
    val mood: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

// Extension functions for mapping between Room entities and Backup DTOs

fun HabitEntity.toBackupDto(): HabitBackupDto = HabitBackupDto(
    id = id,
    name = name,
    description = description,
    measurementType = measurementType,
    targetValue = targetValue,
    unit = unit,
    scheduleType = scheduleType,
    scheduleConfig = scheduleConfig,
    reminderTime = reminderTime,
    displayOrder = displayOrder,
    isPaused = isPaused,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun HabitBackupDto.toEntity(): HabitEntity = HabitEntity(
    id = id,
    name = name,
    description = description,
    measurementType = measurementType,
    targetValue = targetValue,
    unit = unit,
    scheduleType = scheduleType,
    scheduleConfig = scheduleConfig,
    reminderTime = reminderTime,
    displayOrder = displayOrder,
    isPaused = isPaused,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun HabitRecordEntity.toBackupDto(): HabitRecordBackupDto = HabitRecordBackupDto(
    id = id,
    habitId = habitId,
    date = date,
    isCompleted = isCompleted,
    actualValue = actualValue,
    targetValue = targetValue,
    unit = unit,
    measurementType = measurementType,
    notes = notes,
    recordedAt = recordedAt
)

fun HabitRecordBackupDto.toEntity(): HabitRecordEntity = HabitRecordEntity(
    id = id,
    habitId = habitId,
    date = date,
    isCompleted = isCompleted,
    actualValue = actualValue,
    targetValue = targetValue,
    unit = unit,
    measurementType = measurementType,
    notes = notes,
    recordedAt = recordedAt
)

fun DailyGoalEntity.toBackupDto(): DailyGoalBackupDto = DailyGoalBackupDto(
    id = id,
    targetDate = targetDate,
    title = title,
    notes = notes,
    isCompleted = isCompleted,
    displayOrder = displayOrder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun DailyGoalBackupDto.toEntity(): DailyGoalEntity = DailyGoalEntity(
    id = id,
    targetDate = targetDate,
    title = title,
    notes = notes,
    isCompleted = isCompleted,
    displayOrder = displayOrder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun GoalSubtaskEntity.toBackupDto(): GoalSubtaskBackupDto = GoalSubtaskBackupDto(
    id = id,
    goalId = goalId,
    title = title,
    isCompleted = isCompleted,
    displayOrder = displayOrder,
    createdAt = createdAt
)

fun GoalSubtaskBackupDto.toEntity(): GoalSubtaskEntity = GoalSubtaskEntity(
    id = id,
    goalId = goalId,
    title = title,
    isCompleted = isCompleted,
    displayOrder = displayOrder,
    createdAt = createdAt
)

fun DailyReviewEntity.toBackupDto(): DailyReviewBackupDto = DailyReviewBackupDto(
    date = date,
    notes = notes,
    mood = mood,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun DailyReviewBackupDto.toEntity(): DailyReviewEntity = DailyReviewEntity(
    date = date,
    notes = notes,
    mood = mood,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun com.habit1.app.data.local.db.entity.DailyGoalHistoryAggregateEntity.toBackupDto(): DailyGoalHistoryAggregateBackupDto = DailyGoalHistoryAggregateBackupDto(
    date = date,
    completedCount = completedCount,
    totalCount = totalCount
)

fun DailyGoalHistoryAggregateBackupDto.toEntity(): com.habit1.app.data.local.db.entity.DailyGoalHistoryAggregateEntity = com.habit1.app.data.local.db.entity.DailyGoalHistoryAggregateEntity(
    date = date,
    completedCount = completedCount,
    totalCount = totalCount
)

