package com.habit1.app.data.repository

import com.habit1.app.data.local.db.entity.HabitRecordEntity
import kotlinx.coroutines.flow.Flow

interface HabitRecordRepository {
    fun observeRecordsForDate(date: String): Flow<List<HabitRecordEntity>>
    suspend fun getRecordsForDate(date: String): List<HabitRecordEntity>
    fun observeRecord(habitId: String, date: String): Flow<HabitRecordEntity?>
    suspend fun getRecord(habitId: String, date: String): HabitRecordEntity?
    fun observeRecordsForDateRange(startDate: String, endDate: String): Flow<List<HabitRecordEntity>>
    suspend fun getRecordsForDateRange(startDate: String, endDate: String): List<HabitRecordEntity>
    suspend fun getRecordsForHabit(habitId: String): List<HabitRecordEntity>
    suspend fun getRecordsForHabitInRange(habitId: String, startDate: String, endDate: String): List<HabitRecordEntity>
    suspend fun recordProgress(record: HabitRecordEntity)
    suspend fun deleteRecord(habitId: String, date: String)
    suspend fun countCompletedForHabit(habitId: String): Int
}
