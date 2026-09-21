package com.habit1.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habit1.app.data.local.db.entity.DailyReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyReviewDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(review: DailyReviewEntity)

    @Query("SELECT * FROM daily_reviews WHERE date = :date LIMIT 1")
    suspend fun getReview(date: String): DailyReviewEntity?

    @Query("SELECT * FROM daily_reviews WHERE date = :date LIMIT 1")
    fun observeReview(date: String): Flow<DailyReviewEntity?>

    @Query("SELECT * FROM daily_reviews WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getReviewsForDateRange(startDate: String, endDate: String): List<DailyReviewEntity>

    @Query("DELETE FROM daily_reviews WHERE date = :date")
    suspend fun deleteReview(date: String)

    @Query("SELECT * FROM daily_reviews ORDER BY date ASC")
    suspend fun getAllReviews(): List<DailyReviewEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reviews: List<DailyReviewEntity>)

    @Query("DELETE FROM daily_reviews")
    suspend fun deleteAllReviews()
}
