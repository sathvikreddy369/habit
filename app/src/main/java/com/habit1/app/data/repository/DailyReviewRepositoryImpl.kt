package com.habit1.app.data.repository

import com.habit1.app.data.local.db.dao.DailyReviewDao
import com.habit1.app.data.local.db.entity.DailyReviewEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class DailyReviewRepositoryImpl(
    private val dailyReviewDao: DailyReviewDao,
    private val ioDispatcher: CoroutineDispatcher
) : DailyReviewRepository {

    override fun observeReview(date: String): Flow<DailyReviewEntity?> =
        dailyReviewDao.observeReview(date).flowOn(ioDispatcher)

    override fun observeReviewsForDateRange(startDate: String, endDate: String): Flow<List<DailyReviewEntity>> =
        dailyReviewDao.observeReviewsForDateRange(startDate, endDate).flowOn(ioDispatcher)

    override suspend fun getReview(date: String): DailyReviewEntity? =
        withContext(ioDispatcher) {
            dailyReviewDao.getReview(date)
        }

    override suspend fun saveReview(review: DailyReviewEntity) =
        withContext(ioDispatcher) {
            dailyReviewDao.upsert(review)
        }

    override suspend fun deleteReview(date: String) =
        withContext(ioDispatcher) {
            dailyReviewDao.deleteReview(date)
        }
}
