package com.habit1.app.audit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.DailyGoalRepository
import com.habit1.app.data.repository.DailyGoalRepositoryImpl
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRecordRepositoryImpl
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.data.repository.HabitRepositoryImpl
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.HabitTemplateCategory
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.template.HabitTemplatesProvider
import com.habit1.app.domain.usecase.CalculateStreaksUseCase
import com.habit1.app.domain.usecase.ComputeHabitAnalyticsUseCase
import com.habit1.app.domain.usecase.EvaluateHabitHistoryUseCase
import com.habit1.app.domain.usecase.RecordHabitProgressUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class Stage8HardeningRegressionTest {

    private lateinit var database: AppDatabase
    private lateinit var habitRepository: HabitRepository
    private lateinit var habitRecordRepository: HabitRecordRepository
    private lateinit var dailyGoalRepository: DailyGoalRepository

    private val testDispatcher = StandardTestDispatcher()
    private val zoneId = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 9, 23)

    private val evaluateHabitHistory = EvaluateHabitHistoryUseCase()
    private val computeAnalytics = ComputeHabitAnalyticsUseCase()
    private val calculateStreaks = CalculateStreaksUseCase()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        habitRepository = HabitRepositoryImpl(database.habitDao(), testDispatcher)
        habitRecordRepository = HabitRecordRepositoryImpl(database.habitRecordDao(), testDispatcher)
        dailyGoalRepository = DailyGoalRepositoryImpl(database.dailyGoalDao(), testDispatcher)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testHistoricalRecordsUnchangedWhenTargetEdited() = runTest(testDispatcher) {
        // 1. Create habit with target = 10
        val habitId = UUID.randomUUID().toString()
        val habit = HabitEntity(
            id = habitId,
            name = "Pushups",
            measurementType = "COUNT",
            targetValue = 10.0,
            unit = "reps",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            createdAt = today.minusDays(5).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            updatedAt = today.minusDays(5).atStartOfDay(zoneId).toInstant().toEpochMilli()
        )
        habitRepository.createHabit(habit)

        // 2. Record completion with target snapshot = 10
        val recordDate = today.minusDays(2)
        val record = HabitRecordEntity(
            id = UUID.randomUUID().toString(),
            habitId = habitId,
            date = recordDate.toString(),
            actualValue = 10.0,
            targetValue = 10.0,
            measurementType = "COUNT",
            unit = "reps",
            isCompleted = true,
            recordedAt = System.currentTimeMillis()
        )
        habitRecordRepository.recordProgress(record)

        // 3. Edit habit target to 20
        val editedHabit = habit.copy(
            targetValue = 20.0,
            updatedAt = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        )
        habitRepository.updateHabit(editedHabit)

        // 4. Verify in DB and via EvaluateHabitHistoryUseCase: record target is still 10, isCompleted is still true
        val savedRecord = habitRecordRepository.getRecord(habitId, recordDate.toString())
        assertNotNull(savedRecord)
        assertEquals(10.0, savedRecord!!.targetValue, 0.001)
        assertEquals(10.0, savedRecord.actualValue, 0.001)
        assertTrue(savedRecord.isCompleted)

        val history = evaluateHabitHistory.execute(
            habit = editedHabit.toDomain(),
            records = listOf(savedRecord.toDomain()),
            startDate = today.minusDays(5),
            endDate = today,
            todayDate = today,
            zoneId = zoneId
        )
        val dayStatus = history.historyDays.find { it.date == recordDate }?.status
        assertTrue("Past recorded completion must remain Completed", dayStatus is CalendarDayStatus.Completed)
        val completedStatus = dayStatus as CalendarDayStatus.Completed
        assertEquals(10.0, completedStatus.targetValue, 0.001)
    }

    @Test
    fun testHistoricalRecordsUnchangedWhenHabitPaused() = runTest(testDispatcher) {
        // 1. Create habit and record completed days
        val habitId = UUID.randomUUID().toString()
        val habit = HabitEntity(
            id = habitId,
            name = "Reading",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            createdAt = today.minusDays(10).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            updatedAt = today.minusDays(10).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            isPaused = false
        )
        habitRepository.createHabit(habit)

        val records = mutableListOf<HabitRecordEntity>()
        for (i in 1..5) {
            val date = today.minusDays(i.toLong())
            val rec = HabitRecordEntity(
                id = UUID.randomUUID().toString(),
                habitId = habitId,
                date = date.toString(),
                actualValue = 1.0,
                targetValue = 1.0,
                measurementType = "BOOLEAN",
                unit = null,
                isCompleted = true,
                recordedAt = System.currentTimeMillis()
            )
            habitRecordRepository.recordProgress(rec)
            records.add(rec)
        }

        // 2. Pause habit today
        val pausedHabit = habit.copy(isPaused = true)
        habitRepository.updateHabit(pausedHabit)

        // 3. Evaluate history: all 5 past days must remain Completed (NOT Paused)
        val history = evaluateHabitHistory.execute(
            habit = pausedHabit.toDomain(),
            records = records.map { it.toDomain() },
            startDate = today.minusDays(10),
            endDate = today,
            todayDate = today,
            zoneId = zoneId
        )

        for (i in 1..5) {
            val date = today.minusDays(i.toLong())
            val dayStatus = history.historyDays.find { it.date == date }?.status
            assertTrue("Day $date must remain Completed even when habit is paused", dayStatus is CalendarDayStatus.Completed)
        }

        // 4. Calculate streaks: paused habit has 0 scheduled days; totalCompletions is preserved
        val streak = calculateStreaks.execute(
            habit = pausedHabit.toDomain(),
            records = records.map { it.toDomain() },
            todayDate = today,
            zoneId = zoneId
        )
        assertEquals(5, streak.totalCompletions)
        assertEquals(0, streak.totalScheduledDays)

        // When resumed/active, longest streak of 5 is calculated
        val activeStreak = calculateStreaks.execute(
            habit = habit.toDomain(),
            records = records.map { it.toDomain() },
            todayDate = today,
            zoneId = zoneId
        )
        assertEquals(5, activeStreak.longestStreak)
        assertEquals(5, activeStreak.totalCompletions)
    }

    @Test
    fun testDailyGoalCleanup7DayBoundary() = runTest(testDispatcher) {
        // Today is 2026-09-23
        // 6 days old = 2026-09-17 (should be retained)
        // 7 days old = 2026-09-16 (should be cleaned up)
        // 8 days old = 2026-09-15 (should be cleaned up)
        // 13 days old completed = 2026-09-10 (should be retained permanently)

        val g6 = DailyGoalEntity(
            id = "g_6d_incomp",
            title = "Incomplete 6 days old",
            targetDate = "2026-09-17",
            isCompleted = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val g7 = DailyGoalEntity(
            id = "g_7d_incomp",
            title = "Incomplete 7 days old",
            targetDate = "2026-09-16",
            isCompleted = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val g8 = DailyGoalEntity(
            id = "g_8d_incomp",
            title = "Incomplete 8 days old",
            targetDate = "2026-09-15",
            isCompleted = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val gOldCompleted = DailyGoalEntity(
            id = "g_old_comp",
            title = "Completed old goal",
            targetDate = "2026-09-10",
            isCompleted = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        dailyGoalRepository.createGoal(g6)
        dailyGoalRepository.createGoal(g7)
        dailyGoalRepository.createGoal(g8)
        dailyGoalRepository.createGoal(gOldCompleted)

        // Add subtask to g7 to verify cascade deletion
        val subtask = GoalSubtaskEntity(
            id = "sub_g7",
            goalId = "g_7d_incomp",
            title = "Subtask of 7d incomplete goal",
            isCompleted = false,
            displayOrder = 0,
            createdAt = 1700000000000L
        )
        dailyGoalRepository.addSubtask(subtask)

        // Cutoff is today.minusDays(7) = "2026-09-16"
        val cutoffDate = today.minusDays(7).toString()
        assertEquals("2026-09-16", cutoffDate)

        val deletedCount = dailyGoalRepository.cleanupIncompleteGoalsOlderThan(cutoffDate)
        assertEquals(2, deletedCount) // g7 and g8 deleted

        // Assert g6 (6 days old) is retained
        assertNotNull(dailyGoalRepository.getGoalById("g_6d_incomp"))

        // Assert g7 and g8 are deleted
        assertNull(dailyGoalRepository.getGoalById("g_7d_incomp"))
        assertNull(dailyGoalRepository.getGoalById("g_8d_incomp"))

        // Assert subtask is cascade deleted
        val remainingSubtasks = database.dailyGoalDao().getAllSubtasksList()
        assertFalse(remainingSubtasks.any { it.id == "sub_g7" })

        // Assert completed old goal is retained permanently!
        assertNotNull(dailyGoalRepository.getGoalById("g_old_comp"))
    }

    @Test
    fun testHabitDeletePermanentlyPurgesHabitAndRecords() = runTest(testDispatcher) {
        val habitId = "habit_to_delete"
        val habit = HabitEntity(
            id = habitId,
            name = "Temp Habit",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        habitRepository.createHabit(habit)

        val rec1 = HabitRecordEntity(
            id = "rec_1",
            habitId = habitId,
            date = "2026-09-20",
            actualValue = 1.0,
            targetValue = 1.0,
            measurementType = "BOOLEAN",
            unit = null,
            isCompleted = true,
            recordedAt = 1000L
        )
        habitRecordRepository.recordProgress(rec1)

        // Verify records exist
        assertEquals(1, habitRecordRepository.getRecordsForHabit(habitId).size)

        // Delete habit
        habitRepository.deleteHabit(habitId)

        // Habit is gone
        assertNull(habitRepository.getHabitById(habitId))

        // Records are cascade purged
        assertEquals(0, habitRecordRepository.getRecordsForHabit(habitId).size)
    }

    @Test
    fun testStaleNotificationRejectsDeletedOrArchivedHabit() = runTest(testDispatcher) {
        val progressUseCase = RecordHabitProgressUseCase(habitRepository, habitRecordRepository)

        // Stale non-existent habit
        val resultDeleted = progressUseCase.markCompleted("non_existent_habit", today, zoneId)
        assertTrue(resultDeleted.isFailure)

        // Archived habit
        val archivedHabit = HabitEntity(
            id = "archived_habit",
            name = "Archived",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            createdAt = 1000L,
            updatedAt = 1000L,
            isArchived = true
        )
        habitRepository.createHabit(archivedHabit)

        val resultArchived = progressUseCase.markCompleted("archived_habit", today, zoneId)
        assertTrue(resultArchived.isFailure)
        assertEquals(0, habitRecordRepository.getRecordsForHabit("archived_habit").size)
    }

    @Test
    fun testTemplatesCategoryConsolidation() {
        val categories = HabitTemplateCategory.entries.map { it.name }
        assertTrue("STUDENTS must exist", categories.contains("STUDENTS"))
        assertFalse("ENGINEERING_STUDENTS must be consolidated", categories.contains("ENGINEERING_STUDENTS"))
        assertFalse("MEDICAL_STUDENTS must be consolidated", categories.contains("MEDICAL_STUDENTS"))

        val studentTemplates = HabitTemplatesProvider.filterTemplates(HabitTemplateCategory.STUDENTS, "")
        assertTrue(studentTemplates.size >= 10)
        assertTrue(studentTemplates.any { it.title.contains("DSA", ignoreCase = true) })
        assertTrue(studentTemplates.any { it.title.contains("Clinical", ignoreCase = true) })
    }
}
