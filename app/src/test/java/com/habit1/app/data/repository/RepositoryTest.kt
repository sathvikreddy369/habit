package com.habit1.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.DailyReviewEntity
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var habitRepo: HabitRepository
    private lateinit var recordRepo: HabitRecordRepository
    private lateinit var goalRepo: DailyGoalRepository
    private lateinit var reviewRepo: DailyReviewRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemoryDatabase(context)
        val dispatcher = Dispatchers.Unconfined
        habitRepo = HabitRepositoryImpl(database.habitDao(), dispatcher)
        recordRepo = HabitRecordRepositoryImpl(database.habitRecordDao(), dispatcher)
        goalRepo = DailyGoalRepositoryImpl(database.dailyGoalDao(), dispatcher)
        reviewRepo = DailyReviewRepositoryImpl(database.dailyReviewDao(), dispatcher)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testHabitAndRecordRepositoryFlow() = runBlocking {
        val now = System.currentTimeMillis()
        val habit = HabitEntity(
            id = "h_repo",
            name = "Morning Walk",
            measurementType = "DISTANCE",
            targetValue = 5.0,
            unit = "km",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            createdAt = now,
            updatedAt = now
        )
        habitRepo.createHabit(habit)

        val active = habitRepo.observeActiveHabits().first()
        assertEquals(1, active.size)
        assertEquals("Morning Walk", active[0].name)

        val record = HabitRecordEntity(
            id = "r_repo",
            habitId = "h_repo",
            date = "2026-09-20",
            actualValue = 5.2,
            targetValue = 5.0,
            measurementType = "DISTANCE",
            unit = "km",
            isCompleted = true,
            recordedAt = now
        )
        recordRepo.recordProgress(record)

        val records = recordRepo.observeRecordsForDate("2026-09-20").first()
        assertEquals(1, records.size)
        assertEquals(5.2, records[0].actualValue, 0.001)
        assertEquals("km", records[0].unit)
        assertTrue(records[0].isCompleted)

        val completedCount = recordRepo.countCompletedForHabit("h_repo")
        assertEquals(1, completedCount)
    }

    @Test
    fun testDailyGoalRepositoryFlow() = runBlocking {
        val now = System.currentTimeMillis()
        val goal = DailyGoalEntity(
            id = "g_repo",
            title = "Ship Phase 2",
            targetDate = "2026-09-20",
            isCompleted = false,
            displayOrder = 0,
            createdAt = now,
            updatedAt = now
        )
        goalRepo.createGoal(goal)

        val subtask = GoalSubtaskEntity(
            id = "s_repo",
            goalId = "g_repo",
            title = "Write Unit Tests",
            isCompleted = true,
            displayOrder = 0,
            createdAt = now
        )
        goalRepo.addSubtask(subtask)

        val goals = goalRepo.observeGoalsForDate("2026-09-20").first()
        assertEquals(1, goals.size)
        assertEquals("Ship Phase 2", goals[0].goal.title)
        assertEquals(1, goals[0].subtasks.size)
        assertTrue(goals[0].subtasks[0].isCompleted)

        // Mark goal complete
        goalRepo.setGoalCompleted("g_repo", true)
        val updatedGoal = goalRepo.getGoalById("g_repo")
        assertNotNull(updatedGoal)
        assertTrue(updatedGoal!!.goal.isCompleted)

        // Move goal to tomorrow
        goalRepo.moveGoalDate("g_repo", "2026-09-21", 0)
        assertEquals(0, goalRepo.getGoalsForDate("2026-09-20").size)
        assertEquals(1, goalRepo.getGoalsForDate("2026-09-21").size)
    }

    @Test
    fun testDailyReviewRepositoryFlow() = runBlocking {
        val now = System.currentTimeMillis()
        val review = DailyReviewEntity(
            date = "2026-09-20",
            notes = "Great momentum today.",
            mood = "EXCELLENT",
            createdAt = now,
            updatedAt = now
        )
        reviewRepo.saveReview(review)

        val fetched = reviewRepo.getReview("2026-09-20")
        assertNotNull(fetched)
        assertEquals("Great momentum today.", fetched!!.notes)

        reviewRepo.deleteReview("2026-09-20")
        assertNull(reviewRepo.getReview("2026-09-20"))
    }
}
