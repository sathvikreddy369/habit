package com.habit1.app.data.repository

import com.habit1.app.data.local.db.entity.DailyReviewEntity
import kotlinx.coroutines.flow.Flow

interface DailyReviewRepository {
    fun observeReview(date: String): Flow<DailyReviewEntity?>
    suspend fun getReview(date: String): DailyReviewEntity?
    suspend fun saveReview(review: DailyReviewEntity)
    suspend fun deleteReview(date: String)
}
