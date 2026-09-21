package com.habit1.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: HabitRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<HabitRecordEntity>)

    @Query("SELECT * FROM habit_records WHERE habit_id = :habitId AND date = :date LIMIT 1")
    suspend fun getRecord(habitId: String, date: String): HabitRecordEntity?

    @Query("SELECT * FROM habit_records WHERE habit_id = :habitId AND date = :date LIMIT 1")
    fun observeRecord(habitId: String, date: String): Flow<HabitRecordEntity?>

    @Query("SELECT * FROM habit_records WHERE date = :date")
    fun observeRecordsForDate(date: String): Flow<List<HabitRecordEntity>>

    @Query("SELECT * FROM habit_records WHERE date = :date")
    suspend fun getRecordsForDate(date: String): List<HabitRecordEntity>

    /**
     * Date-windowed query for range inspection (e.g. calendar heatmap, monthly view).
     */
    @Query("SELECT * FROM habit_records WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun observeRecordsForDateRange(startDate: String, endDate: String): Flow<List<HabitRecordEntity>>

    @Query("SELECT * FROM habit_records WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getRecordsForDateRange(startDate: String, endDate: String): List<HabitRecordEntity>

    @Query("SELECT * FROM habit_records WHERE habit_id = :habitId ORDER BY date ASC")
    suspend fun getRecordsForHabit(habitId: String): List<HabitRecordEntity>

    @Query("SELECT * FROM habit_records WHERE habit_id IN (:habitIds) ORDER BY date ASC")
    suspend fun getRecordsForHabits(habitIds: List<String>): List<HabitRecordEntity>

    @Query("SELECT * FROM habit_records WHERE habit_id = :habitId AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun observeRecordsForHabitInRange(habitId: String, startDate: String, endDate: String): Flow<List<HabitRecordEntity>>

    @Query("SELECT * FROM habit_records WHERE habit_id = :habitId AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getRecordsForHabitInRange(habitId: String, startDate: String, endDate: String): List<HabitRecordEntity>

    @Query("DELETE FROM habit_records WHERE habit_id = :habitId AND date = :date")
    suspend fun deleteRecord(habitId: String, date: String)

    @Query("SELECT COUNT(*) FROM habit_records WHERE habit_id = :habitId AND is_completed = 1")
    suspend fun countCompletedForHabit(habitId: String): Int

    @Query("SELECT COUNT(*) FROM habit_records")
    suspend fun totalRecordsCount(): Int

    @Query("SELECT * FROM habit_records ORDER BY date ASC")
    suspend fun getAllRecordsList(): List<HabitRecordEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(records: List<HabitRecordEntity>)

    @Query("DELETE FROM habit_records")
    suspend fun deleteAllRecords()
}
