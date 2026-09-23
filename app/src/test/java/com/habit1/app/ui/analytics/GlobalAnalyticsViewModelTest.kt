package com.habit1.app.ui.analytics

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
import com.habit1.app.domain.usecase.ComputeGlobalAnalyticsUseCase
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlobalAnalyticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var habitRepository: HabitRepository
    private lateinit var habitRecordRepository: HabitRecordRepository
    private lateinit var computeGlobalAnalyticsUseCase: ComputeGlobalAnalyticsUseCase
    private lateinit var viewModel: GlobalAnalyticsViewModel

    private val fixedToday = LocalDate.of(2026, 9, 21)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemoryDatabase(context)
        habitRepository = HabitRepositoryImpl(database.habitDao(), testDispatcher)
        habitRecordRepository = HabitRecordRepositoryImpl(database.habitRecordDao(), testDispatcher)
        computeGlobalAnalyticsUseCase = ComputeGlobalAnalyticsUseCase()

        viewModel = GlobalAnalyticsViewModel(
            habitRepository = habitRepository,
            habitRecordRepository = habitRecordRepository,
            computeGlobalAnalyticsUseCase = computeGlobalAnalyticsUseCase,
            todayDateProvider = { fixedToday },
            defaultDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun emptyDatabase_emitsZeroSummary() = runTest(testDispatcher) {
        viewModel.uiState.test {
            val state = awaitItem()
            advanceUntilIdle()
            val loadedState = if (state.isLoading) awaitItem() else state

            assertNotNull(loadedState.summary)
            assertEquals(0, loadedState.summary?.activeHabitsCount)
            assertEquals(0.0f, loadedState.summary?.globalCompletionRate ?: -1f, 0.001f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun withHabitsAndRecords_computesCorrectSummary() = runTest(testDispatcher) {
        val habit = HabitEntity(
            id = "h1",
            name = "Morning Run",
            measurementType = "BOOLEAN",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = 1756684800000L,
            updatedAt = 1756684800000L
        )
        database.habitDao().insert(habit)

        val record = HabitRecordEntity(
            id = "r1",
            habitId = "h1",
            date = "2026-09-20",
            actualValue = 1.0,
            targetValue = 1.0,
            measurementType = "BOOLEAN",
            isCompleted = true,
            recordedAt = 1758355200000L
        )
        database.habitRecordDao().upsert(record)

        viewModel.onEvent(GlobalAnalyticsUiEvent.Refresh)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            val loadedState = if (state.isLoading) awaitItem() else state

            assertEquals(1, loadedState.summary?.activeHabitsCount)
            assertEquals(1, loadedState.summary?.totalCompletedDays)
            assertEquals("Morning Run", loadedState.summary?.habitPerformances?.firstOrNull()?.habitName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun changingPreset_updatesRangeAndPreset() = runTest(testDispatcher) {
        viewModel.onEvent(GlobalAnalyticsUiEvent.SelectPreset(GlobalAnalyticsPreset.ThisWeek))
        advanceUntilIdle()

        assertEquals(GlobalAnalyticsPreset.ThisWeek, viewModel.uiState.value.selectedPreset)
        assertEquals(7, viewModel.uiState.value.summary?.range?.dayCount)

        viewModel.onEvent(GlobalAnalyticsUiEvent.SelectPreset(GlobalAnalyticsPreset.ThisYear))
        advanceUntilIdle()

        assertEquals(GlobalAnalyticsPreset.ThisYear, viewModel.uiState.value.selectedPreset)
        assertEquals(365, viewModel.uiState.value.summary?.range?.dayCount)
    }
}
