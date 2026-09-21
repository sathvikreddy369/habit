package com.habit1.app.ui.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRecordRepositoryImpl
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.data.repository.HabitRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HabitHistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var habitRepository: HabitRepository
    private lateinit var habitRecordRepository: HabitRecordRepository

    private val fixedZone = ZoneId.of("UTC")
    private val pastMillis = 1788220800000L // Sep 2026

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemoryDatabase(context)

        habitRepository = HabitRepositoryImpl(database.habitDao(), testDispatcher)
        habitRecordRepository = HabitRecordRepositoryImpl(database.habitRecordDao(), testDispatcher)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun testLoadHabitHistory_withRecordsAndQuantitativeAverages() = runTest(testDispatcher) {
        val habit = HabitEntity(
            id = "habit_123",
            name = "Read Books",
            measurementType = "NUMERIC",
            targetValue = 20.0,
            unit = "pages",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isArchived = false,
            createdAt = pastMillis,
            updatedAt = pastMillis
        )
        database.habitDao().insert(habit)

        // Insert records
        database.habitRecordDao().upsert(
            HabitRecordEntity(
                id = "r1",
                habitId = "habit_123",
                date = "2026-09-01",
                actualValue = 25.0,
                targetValue = 20.0,
                unit = "pages",
                measurementType = "NUMERIC",
                isCompleted = true,
                recordedAt = pastMillis
            )
        )
        database.habitRecordDao().upsert(
            HabitRecordEntity(
                id = "r2",
                habitId = "habit_123",
                date = "2026-09-02",
                actualValue = 10.0,
                targetValue = 20.0,
                unit = "pages",
                measurementType = "NUMERIC",
                isCompleted = false,
                recordedAt = pastMillis
            )
        )

        val viewModel = HabitHistoryViewModel(
            habitId = "habit_123",
            habitRepository = habitRepository,
            habitRecordRepository = habitRecordRepository,
            zoneId = fixedZone,
            coroutineScope = CoroutineScope(testDispatcher)
        )

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.isLoading) {
                state = awaitItem()
            }

            assertEquals("Read Books", state.habit?.name)
            assertEquals(false, state.habit?.isArchived)
            assertEquals(false, state.habit?.isPaused)

            val summary = state.summary
            assertNotNull(summary)
            assertEquals(1, summary?.completedDaysCount)
            assertEquals(2, summary?.totalRecordedDays)
            assertEquals(1, summary?.recordedIncompleteDaysCount)
            assertEquals(25.0, summary?.averageOnCompletedDays!!, 0.001)
            assertEquals(17.5, summary?.averageOnRecordedDays!!, 0.001)

            assertEquals(2, state.records.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testArchivedHabit_isInspectedSuccessfully() = runTest(testDispatcher) {
        val habit = HabitEntity(
            id = "archived_habit",
            name = "Old Guitar Practice",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isArchived = true,
            createdAt = pastMillis,
            updatedAt = pastMillis
        )
        database.habitDao().insert(habit)

        val viewModel = HabitHistoryViewModel(
            habitId = "archived_habit",
            habitRepository = habitRepository,
            habitRecordRepository = habitRecordRepository,
            zoneId = fixedZone,
            coroutineScope = CoroutineScope(testDispatcher)
        )

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.isLoading) {
                state = awaitItem()
            }

            assertEquals("Old Guitar Practice", state.habit?.name)
            assertTrue("Archived status must be recognized in history UI", state.habit?.isArchived == true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testHeatmapRangeNavigation_andDaySelection() = runTest(testDispatcher) {
        val habit = HabitEntity(
            id = "habit_heatmap",
            name = "Heatmap Test",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isArchived = false,
            createdAt = pastMillis,
            updatedAt = pastMillis
        )
        database.habitDao().insert(habit)

        val viewModel = HabitHistoryViewModel(
            habitId = "habit_heatmap",
            habitRepository = habitRepository,
            habitRecordRepository = habitRecordRepository,
            zoneId = fixedZone,
            coroutineScope = CoroutineScope(testDispatcher)
        )

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.isLoading) {
                state = awaitItem()
            }

            assertEquals(HeatmapRangePreset.THIRTY_DAYS, state.selectedPreset)
            assertEquals(true, state.isCurrentRange)
            assertEquals(false, state.canNavigateNext)
            assertNotNull(state.analyticsSummary)
            assertTrue(state.analyticsSummary!!.dailyBreakdown.isNotEmpty())

            // Switch to 7D preset
            viewModel.onEvent(HabitHistoryUiEvent.SelectPreset(HeatmapRangePreset.SEVEN_DAYS))
            state = awaitItem()
            assertEquals(HeatmapRangePreset.SEVEN_DAYS, state.selectedPreset)
            assertEquals(7, state.currentRange.dayCount)

            // Navigate previous range
            viewModel.onEvent(HabitHistoryUiEvent.PreviousRange)
            state = awaitItem()
            assertEquals(true, state.canNavigateNext)
            assertEquals(false, state.isCurrentRange)

            // Navigate next range
            viewModel.onEvent(HabitHistoryUiEvent.NextRange)
            state = awaitItem()
            assertEquals(false, state.canNavigateNext)
            assertEquals(true, state.isCurrentRange)

            // Select a day for inspection
            val sampleDay = state.analyticsSummary!!.dailyBreakdown.first()
            viewModel.onEvent(HabitHistoryUiEvent.SelectDay(sampleDay))
            state = awaitItem()
            assertEquals(sampleDay.date, state.selectedDayDetail?.date)

            // Dismiss day detail
            viewModel.onEvent(HabitHistoryUiEvent.DismissDayDetail)
            state = awaitItem()
            assertEquals(null, state.selectedDayDetail)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
