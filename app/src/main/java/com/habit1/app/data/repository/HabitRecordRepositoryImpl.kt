package com.habit1.app.data.repository

import com.habit1.app.data.local.db.dao.HabitRecordDao
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class HabitRecordRepositoryImpl(
    private val habitRecordDao: HabitRecordDao,
    private val ioDispatcher: CoroutineDispatcher
) : HabitRecordRepository {

    override fun observeRecordsForDate(date: String): Flow<List<HabitRecordEntity>> =
        habitRecordDao.observeRecordsForDate(date).flowOn(ioDispatcher)

    override suspend fun getRecordsForDate(date: String): List<HabitRecordEntity> =
        withContext(ioDispatcher) {
            habitRecordDao.getRecordsForDate(date)
        }

    override fun observeRecord(habitId: String, date: String): Flow<HabitRecordEntity?> =
        habitRecordDao.observeRecord(habitId, date).flowOn(ioDispatcher)

    override suspend fun getRecord(habitId: String, date: String): HabitRecordEntity? =
        withContext(ioDispatcher) {
            habitRecordDao.getRecord(habitId, date)
        }

    override fun observeRecordsForDateRange(startDate: String, endDate: String): Flow<List<HabitRecordEntity>> =
        habitRecordDao.observeRecordsForDateRange(startDate, endDate).flowOn(ioDispatcher)

    override suspend fun getRecordsForDateRange(startDate: String, endDate: String): List<HabitRecordEntity> =
        withContext(ioDispatcher) {
            habitRecordDao.getRecordsForDateRange(startDate, endDate)
        }

    override suspend fun getRecordsForHabit(habitId: String): List<HabitRecordEntity> =
        withContext(ioDispatcher) {
            habitRecordDao.getRecordsForHabit(habitId)
        }

    override suspend fun getRecordsForHabits(habitIds: List<String>): List<HabitRecordEntity> =
        withContext(ioDispatcher) {
            if (habitIds.isEmpty()) emptyList()
            else habitRecordDao.getRecordsForHabits(habitIds)
        }

    override fun observeRecordsForHabitInRange(
        habitId: String,
        startDate: String,
        endDate: String
    ): Flow<List<HabitRecordEntity>> =
        habitRecordDao.observeRecordsForHabitInRange(habitId, startDate, endDate).flowOn(ioDispatcher)

    override suspend fun getRecordsForHabitInRange(
        habitId: String,
        startDate: String,
        endDate: String
    ): List<HabitRecordEntity> =
        withContext(ioDispatcher) {
            habitRecordDao.getRecordsForHabitInRange(habitId, startDate, endDate)
        }

    override suspend fun recordProgress(record: HabitRecordEntity) =
        withContext(ioDispatcher) {
            habitRecordDao.upsert(record)
        }

    override suspend fun deleteRecord(habitId: String, date: String) =
        withContext(ioDispatcher) {
            habitRecordDao.deleteRecord(habitId, date)
        }

    override suspend fun countCompletedForHabit(habitId: String): Int =
        withContext(ioDispatcher) {
            habitRecordDao.countCompletedForHabit(habitId)
        }
}
