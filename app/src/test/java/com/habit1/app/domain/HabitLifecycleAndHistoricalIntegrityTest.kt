package com.habit1.app.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.DailyGoalRepository
import com.habit1.app.data.repository.DailyGoalRepositoryImpl
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRecordRepositoryImpl
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.data.repository.HabitRepositoryImpl
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.usecase.CalculateStreaksUseCase
import com.habit1.app.domain.usecase.EvaluateScheduleUseCase
import com.habit1.app.ui.today.TodayUiEvent
import com.habit1.app.ui.today.TodayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HabitLifecycleAndHistoricalIntegrityTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var habitRepository: HabitRepository
    private lateinit var habitRecordRepository: HabitRecordRepository
    private lateinit var dailyGoalRepository: DailyGoalRepository
    private val zoneId = ZoneId.of("UTC")
    private val evaluateSchedule = EvaluateScheduleUseCase()
    private val calculateStreaks = CalculateStreaksUseCase(evaluateSchedule)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemoryDatabase(context)
        habitRepository = HabitRepositoryImpl(database.habitDao(), testDispatcher)
        habitRecordRepository = HabitRecordRepositoryImpl(database.habitRecordDao(), testDispatcher)
        dailyGoalRepository = DailyGoalRepositoryImpl(database.dailyGoalDao(), testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun habitCreationDate_notScheduledBeforeCreation_scheduledOnAndAfter() {
        // Created on 2026-09-15
        val creationInstant = LocalDate.of(2026, 9, 15).atStartOfDay(zoneId).toInstant()
        val habit = HabitEntity(
            id = "h_date",
            name = "Morning Run",
            measurementType = "BOOLEAN",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = creationInstant.toEpochMilli(),
            updatedAt = creationInstant.toEpochMilli()
        ).toDomain()

        // Dates before creation must NOT be scheduled
        assertFalse(evaluateSchedule.isScheduledOn(habit, LocalDate.of(2026, 9, 14), zoneId))
        assertFalse(evaluateSchedule.isScheduledOn(habit, LocalDate.of(2026, 9, 1), zoneId))

        // Creation date itself IS scheduled
        assertTrue(evaluateSchedule.isScheduledOn(habit, LocalDate.of(2026, 9, 15), zoneId))

        // Subsequent dates ARE scheduled
        assertTrue(evaluateSchedule.isScheduledOn(habit, LocalDate.of(2026, 9, 16), zoneId))
    }

    @Test
    fun historicalStatistics_doNotFabricatePreCreationMisses() {
        // Created on 2026-09-15
        val creationInstant = LocalDate.of(2026, 9, 15).atStartOfDay(zoneId).toInstant()
        val habit = HabitEntity(
            id = "h_streak",
            name = "Meditate",
            measurementType = "BOOLEAN",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = creationInstant.toEpochMilli(),
            updatedAt = creationInstant.toEpochMilli()
        ).toDomain()

        // Evaluated on 2026-09-16 with 1 completion on 2026-09-15
        val record15 = HabitRecordEntity(
            id = "r1",
            habitId = "h_streak",
            date = "2026-09-15",
            actualValue = 1.0,
            targetValue = 1.0,
            measurementType = "BOOLEAN",
            isCompleted = true,
            recordedAt = creationInstant.toEpochMilli()
        ).toDomain()

        val result = calculateStreaks.execute(
            habit = habit,
            records = listOf(record15),
            todayDate = LocalDate.of(2026, 9, 16),
            zoneId = zoneId
        )

        // Streak is 1, and total scheduled days is 2 (September 15 and September 16), NOT fabricated back to Jan 1!
        assertEquals(1, result.currentStreak)
        assertEquals(2, result.totalScheduledDays)
        assertEquals(1, result.totalCompletions)
        assertEquals(100.0f, result.completionRate, 0.001f)
    }

    @Test
    fun historicalIntegrity_editingHabitLeavesPastRecordsUntouched() = runTest(testDispatcher) {
        val createdAt = 1700000000000L
        val habit = HabitEntity(
            id = "h_integrity",
            name = "Pushups",
            measurementType = "COUNT",
            targetValue = 20.0,
            unit = "reps",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = createdAt,
            updatedAt = createdAt
        )
        habitRepository.createHabit(habit)

        // Historical record with past target of 20 reps
        val pastRecord = HabitRecordEntity(
            id = "rec_past_1",
            habitId = "h_integrity",
            date = "2026-08-01",
            actualValue = 20.0,
            targetValue = 20.0,
            measurementType = "COUNT",
            unit = "reps",
            isCompleted = true,
            recordedAt = createdAt + 1000
        )
        habitRecordRepository.recordProgress(pastRecord)
        advanceUntilIdle()

        // Edit habit: change measurement type to DURATION, target to 30 mins, unit to "mins"
        val editedHabit = habit.copy(
            measurementType = "DURATION",
            targetValue = 30.0,
            unit = "mins",
            updatedAt = System.currentTimeMillis()
        )
        habitRepository.updateHabit(editedHabit)
        advanceUntilIdle()

        // Verify updated habit
        val reloadedHabit = habitRepository.getHabitById("h_integrity")
        assertNotNull(reloadedHabit)
        assertEquals("DURATION", reloadedHabit!!.measurementType)
        assertEquals(30.0, reloadedHabit.targetValue, 0.001)
        assertEquals(createdAt, reloadedHabit.createdAt) // Creation date untouched!

        // Verify historical record is 100% UNCHANGED
        val preservedRecord = habitRecordRepository.getRecord("h_integrity", "2026-08-01")
        assertNotNull(preservedRecord)
        assertEquals("COUNT", preservedRecord!!.measurementType)
        assertEquals(20.0, preservedRecord.targetValue, 0.001)
        assertEquals("reps", preservedRecord.unit)
        assertEquals(20.0, preservedRecord.actualValue, 0.001)
        assertTrue(preservedRecord.isCompleted)
    }

    @Test
    fun todayIntegration_pausedAndArchivedHabitsDisappearFromToday() = runTest(testDispatcher) {
        val todayStr = DateTimeUtils.formatDate(DateTimeUtils.today(zoneId))
        val past = System.currentTimeMillis() - 86400000
        val habit = HabitEntity(
            id = "h_today",
            name = "Meditate Daily",
            measurementType = "BOOLEAN",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = past,
            updatedAt = past
        )
        habitRepository.createHabit(habit)
        advanceUntilIdle()

        val viewModel = TodayViewModel(
            habitRepository = habitRepository,
            habitRecordRepository = habitRecordRepository,
            dailyGoalRepository = dailyGoalRepository,
            zoneId = zoneId
        )

        viewModel.uiState.test {
            awaitItem() // loading
            val activeState = awaitItem()
            assertEquals(1, activeState.habits.size)
            assertEquals("Meditate Daily", activeState.habits[0].name)

            // Pause habit
            habitRepository.pauseHabit("h_today", isPaused = true)
            val pausedState = awaitItem()
            // Paused habit disappears from Today scheduling!
            assertEquals(0, pausedState.habits.size)

            // Resume habit
            habitRepository.pauseHabit("h_today", isPaused = false)
            val resumedState = awaitItem()
            assertEquals(1, resumedState.habits.size)

            // Archive habit
            habitRepository.archiveHabit("h_today", isArchived = true)
            val archivedState = awaitItem()
            // Archived habit disappears from Today scheduling!
            assertEquals(0, archivedState.habits.size)

            // Unarchive habit
            habitRepository.archiveHabit("h_today", isArchived = false)
            val unarchivedState = awaitItem()
            assertEquals(1, unarchivedState.habits.size)
        }
    }

    @Test
    fun quantitativeValues_cannotBecomeNegativeOnDecrement() = runTest(testDispatcher) {
        val past = System.currentTimeMillis() - 86400000
        val habit = HabitEntity(
            id = "h_water",
            name = "Drink Water",
            measurementType = "QUANTITY",
            targetValue = 2.0,
            unit = "L",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = past,
            updatedAt = past
        )
        habitRepository.createHabit(habit)
        advanceUntilIdle()

        val viewModel = TodayViewModel(
            habitRepository = habitRepository,
            habitRecordRepository = habitRecordRepository,
            dailyGoalRepository = dailyGoalRepository,
            zoneId = zoneId
        )

        viewModel.uiState.test {
            awaitItem() // loading
            val initial = awaitItem() // loaded with actualValue = 0.0
            assertEquals(0.0, initial.habits[0].actualValue, 0.001)

            // Increment to 0.25 (defaultStep for Quantity)
            viewModel.onEvent(TodayUiEvent.IncrementHabit("h_water"))
            val incState = awaitItem()
            assertEquals(0.25, incState.habits[0].actualValue, 0.001)

            // Decrement back to 0.0
            viewModel.onEvent(TodayUiEvent.DecrementHabit("h_water"))
            val decState = awaitItem()
            assertEquals(0.0, decState.habits[0].actualValue, 0.001)
            assertTrue(decState.habits[0].actualValue >= 0.0)

            // Increment again to 0.25
            viewModel.onEvent(TodayUiEvent.IncrementHabit("h_water"))
            val incAgain = awaitItem()
            assertEquals(0.25, incAgain.habits[0].actualValue, 0.001)

            // Explicitly set negative value via event: coerced to 0.0, never negative
            viewModel.onEvent(TodayUiEvent.SetHabitValue("h_water", -5.0))
            val negSetState = awaitItem()
            assertEquals(0.0, negSetState.habits[0].actualValue, 0.001)
            assertTrue(negSetState.habits[0].actualValue >= 0.0)
        }
    }
}
