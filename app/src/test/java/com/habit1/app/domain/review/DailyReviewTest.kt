package com.habit1.app.domain.review

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.DailyReviewEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.DailyReviewRepository
import com.habit1.app.data.repository.DailyReviewRepositoryImpl
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.mapper.EntityMappers.toEntity
import com.habit1.app.domain.model.DailyReview
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitRecord
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.usecase.CalculateStreaksUseCase
import com.habit1.app.domain.usecase.EvaluateHabitHistoryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DailyReviewTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: DailyReviewRepository
    private val dispatcher = Dispatchers.Unconfined
    private val zoneId = ZoneId.of("UTC")
    private val calculateStreaks = CalculateStreaksUseCase()
    private val evaluateHistory = EvaluateHabitHistoryUseCase()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemoryDatabase(context)
        repository = DailyReviewRepositoryImpl(database.dailyReviewDao(), dispatcher)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createAndObserveReview() = runBlocking {
        val now = System.currentTimeMillis()
        val review = DailyReviewEntity(
            date = "2026-09-21",
            notes = "Quiet focus day.",
            mood = "Calm",
            createdAt = now,
            updatedAt = now
        )

        repository.saveReview(review)

        val observed = repository.observeReview("2026-09-21").first()
        assertNotNull(observed)
        assertEquals("2026-09-21", observed!!.date)
        assertEquals("Quiet focus day.", observed.notes)
        assertEquals("Calm", observed.mood)
    }

    @Test
    fun updateReview() = runBlocking {
        val now = System.currentTimeMillis()
        val review = DailyReviewEntity(
            date = "2026-09-21",
            notes = "Initial thought.",
            mood = "Tired",
            createdAt = now,
            updatedAt = now
        )
        repository.saveReview(review)

        val updated = DailyReviewEntity(
            date = "2026-09-21",
            notes = "Reflected further, feeling energized now.",
            mood = "Energized",
            createdAt = now,
            updatedAt = now + 5000
        )
        repository.saveReview(updated)

        val fetched = repository.getReview("2026-09-21")
        assertNotNull(fetched)
        assertEquals("Reflected further, feeling energized now.", fetched!!.notes)
        assertEquals("Energized", fetched.mood)
        assertEquals(now, fetched.createdAt)
        assertEquals(now + 5000, fetched.updatedAt)
    }

    @Test
    fun deleteReview() = runBlocking {
        val now = System.currentTimeMillis()
        val review = DailyReviewEntity(
            date = "2026-09-21",
            notes = "Will be deleted.",
            mood = "Focused",
            createdAt = now,
            updatedAt = now
        )
        repository.saveReview(review)
        assertNotNull(repository.getReview("2026-09-21"))

        repository.deleteReview("2026-09-21")
        assertNull(repository.getReview("2026-09-21"))
    }

    @Test
    fun reviewForHistoricalDateAndEmptyState() = runBlocking {
        val pastDate = "2026-08-15"
        val currentDate = "2026-09-21"

        // Empty state
        assertNull(repository.getReview(pastDate))
        assertNull(repository.getReview(currentDate))

        val now = System.currentTimeMillis()
        val historicalReview = DailyReviewEntity(
            date = pastDate,
            notes = "Deep work on release architecture.",
            mood = "Grateful",
            createdAt = now,
            updatedAt = now
        )
        repository.saveReview(historicalReview)

        val retrievedPast = repository.getReview(pastDate)
        assertNotNull(retrievedPast)
        assertEquals("Grateful", retrievedPast!!.mood)

        // Current date remains empty
        assertNull(repository.getReview(currentDate))
    }

    @Test
    fun reviewDoesNotAffectHabitRecordsOrStreaksOrConsistencyStats(): Unit = runBlocking {
        val habitId = "h_meditation"
        val createdMillis = LocalDate.of(2026, 9, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val today = LocalDate.of(2026, 9, 21)

        val habitEntity = HabitEntity(
            id = habitId,
            name = "Meditation",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            createdAt = createdMillis,
            updatedAt = createdMillis
        )
        database.habitDao().insert(habitEntity)


        val record1 = HabitRecordEntity(
            id = "r_1",
            habitId = habitId,
            date = "2026-09-20",
            actualValue = 1.0,
            targetValue = 1.0,
            measurementType = "BOOLEAN",
            unit = null,
            isCompleted = true,
            recordedAt = now
        )
        val record2 = HabitRecordEntity(
            id = "r_2",
            habitId = habitId,
            date = "2026-09-21",
            actualValue = 1.0,
            targetValue = 1.0,
            measurementType = "BOOLEAN",
            unit = null,
            isCompleted = true,
            recordedAt = now
        )
        database.habitRecordDao().upsert(record1)
        database.habitRecordDao().upsert(record2)

        val domainHabit: Habit = habitEntity.toDomain()
        val domainRecords: List<HabitRecord> = listOf(record1.toDomain(), record2.toDomain())

        // Calculate baseline streaks & stats before any daily review
        val baselineStreaks = calculateStreaks.execute(domainHabit, domainRecords, today, zoneId)
        val baselineHistory = evaluateHistory.execute(
            habit = domainHabit,
            records = domainRecords,
            startDate = LocalDate.of(2026, 9, 1),
            endDate = today,
            todayDate = today,
            zoneId = zoneId
        )

        assertEquals(2, baselineStreaks.currentStreak)
        assertEquals(2, baselineStreaks.longestStreak)
        assertEquals(2, baselineHistory.completedDaysCount)

        // Now save, update, and delete daily reviews
        repository.saveReview(
            DailyReviewEntity(
                date = "2026-09-20",
                notes = "Felt distracted initially.",
                mood = "Tired",
                createdAt = now,
                updatedAt = now
            )
        )
        repository.saveReview(
            DailyReviewEntity(
                date = "2026-09-21",
                notes = "Clear and centered.",
                mood = "Calm",
                createdAt = now,
                updatedAt = now
            )
        )

        // Habit records in database must remain exactly 2
        val recordsInDb = database.habitRecordDao().getRecordsForHabit(habitId)
        assertEquals(2, recordsInDb.size)

        // Streak calculation MUST be completely identical
        val streaksAfterReviews = calculateStreaks.execute(domainHabit, domainRecords, today, zoneId)
        assertEquals(baselineStreaks.currentStreak, streaksAfterReviews.currentStreak)
        assertEquals(baselineStreaks.longestStreak, streaksAfterReviews.longestStreak)
        assertEquals(baselineStreaks.totalCompletions, streaksAfterReviews.totalCompletions)
        assertEquals(baselineStreaks.completionRate, streaksAfterReviews.completionRate, 0.001f)

        // Consistency evaluation MUST be completely identical
        val historyAfterReviews = evaluateHistory.execute(
            habit = domainHabit,
            records = domainRecords,
            startDate = LocalDate.of(2026, 9, 1),
            endDate = today,
            todayDate = today,
            zoneId = zoneId
        )
        assertEquals(baselineHistory.completedDaysCount, historyAfterReviews.completedDaysCount)
        assertEquals(baselineHistory.totalRecordedDays, historyAfterReviews.totalRecordedDays)
        assertEquals(baselineHistory.streakResult.completionRate, historyAfterReviews.streakResult.completionRate, 0.001f)
    }



    @Test
    fun domainModelMappersPreserveAllFields() {
        val now = Instant.now()
        val domain = DailyReview(
            date = LocalDate.of(2026, 9, 21),
            notes = "Deep work on final phase.",
            mood = "Energized",
            createdAt = now,
            updatedAt = now
        )

        val entity = domain.toEntity()
        assertEquals("2026-09-21", entity.date)
        assertEquals("Deep work on final phase.", entity.notes)
        assertEquals("Energized", entity.mood)
        assertEquals(now.toEpochMilli(), entity.createdAt)
        assertEquals(now.toEpochMilli(), entity.updatedAt)

        val roundtrip = entity.toDomain()
        assertEquals(domain.date, roundtrip.date)
        assertEquals(domain.notes, roundtrip.notes)
        assertEquals(domain.mood, roundtrip.mood)
        assertEquals(domain.createdAt.toEpochMilli(), roundtrip.createdAt.toEpochMilli())
        assertEquals(domain.updatedAt.toEpochMilli(), roundtrip.updatedAt.toEpochMilli())
    }
}
