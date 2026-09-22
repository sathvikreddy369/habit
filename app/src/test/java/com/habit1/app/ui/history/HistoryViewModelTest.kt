package com.habit1.app.ui.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.DailyGoalRepository
import com.habit1.app.data.repository.DailyGoalRepositoryImpl
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRecordRepositoryImpl
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.data.repository.HabitRepositoryImpl
import com.habit1.app.domain.model.CalendarDayStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var habitRepository: HabitRepository
    private lateinit var habitRecordRepository: HabitRecordRepository
    private lateinit var dailyGoalRepository: DailyGoalRepository
    private lateinit var dailyReviewRepository: com.habit1.app.data.repository.DailyReviewRepository
    private lateinit var viewModel: HistoryViewModel

    private val fixedZone = ZoneId.of("UTC")
    private val pastMillis = 1788220800000L // Sep 2026 epoch millis

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemoryDatabase(context)

        habitRepository = HabitRepositoryImpl(database.habitDao(), testDispatcher)
        habitRecordRepository = HabitRecordRepositoryImpl(database.habitRecordDao(), testDispatcher)
        dailyGoalRepository = DailyGoalRepositoryImpl(database.dailyGoalDao(), testDispatcher)
        dailyReviewRepository = com.habit1.app.data.repository.DailyReviewRepositoryImpl(database.dailyReviewDao(), testDispatcher)

        viewModel = HistoryViewModel(
            habitRepository = habitRepository,
            habitRecordRepository = habitRecordRepository,
            dailyGoalRepository = dailyGoalRepository,
            dailyReviewRepository = dailyReviewRepository,
            zoneId = fixedZone,
            coroutineScope = CoroutineScope(testDispatcher)
        )
    }


    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState_emptyDatabase() = runTest(testDispatcher) {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertTrue(initial.isLoading)

            val state = awaitItem()
            assertEquals(false, state.isLoading)
            assertNotNull(state.selectedMonth)
            assertNotNull(state.selectedDate)
            assertEquals(0, state.monthSummary?.totalHabitCompletions ?: 0)
            assertEquals(0, state.monthSummary?.completedGoals ?: 0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testMonthNavigation_previousAndNext() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // initial loading
            val initial = awaitItem()
            val initialMonth = initial.selectedMonth

            viewModel.onEvent(HistoryUiEvent.PreviousMonth)
            val prevMonthState = awaitItem()
            assertEquals(initialMonth.minusMonths(1), prevMonthState.selectedMonth)

            viewModel.onEvent(HistoryUiEvent.NextMonth)
            val nextMonthState = awaitItem()
            assertEquals(initialMonth, nextMonthState.selectedMonth)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testSelectDate_updatesBreakdown() = runTest(testDispatcher) {
        val targetDate = LocalDate.of(2026, 9, 15)

        // Insert a habit and record for Sep 15
        val habit = HabitEntity(
            id = "h1",
            name = "Exercise",
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

        val record = HabitRecordEntity(
            id = "r1",
            habitId = "h1",
            date = "2026-09-15",
            actualValue = 1.0,
            targetValue = 1.0,
            unit = null,
            measurementType = "BOOLEAN",
            isCompleted = true,
            recordedAt = pastMillis
        )
        database.habitRecordDao().upsert(record)

        // Insert a goal for Sep 15
        val goal = DailyGoalEntity(
            id = "g1",
            title = "Ship Feature",
            targetDate = "2026-09-15",
            isCompleted = true,
            displayOrder = 0,
            createdAt = pastMillis,
            updatedAt = pastMillis
        )
        database.dailyGoalDao().insertGoal(goal)

        viewModel.uiState.test {
            awaitItem() // loading
            awaitItem() // loaded initial

            viewModel.onEvent(HistoryUiEvent.SelectDate(targetDate))
            val updatedState = awaitItem()

            assertEquals(targetDate, updatedState.selectedDate)
            assertEquals(YearMonth.of(2026, 9), updatedState.selectedMonth)

            val breakdown = updatedState.selectedDateBreakdown
            assertNotNull(breakdown)
            assertEquals(targetDate, breakdown?.date)
            assertEquals(1, breakdown?.habits?.size)
            assertEquals("Exercise", breakdown?.habits?.first()?.habitName)
            assertTrue(breakdown?.habits?.first()?.status is CalendarDayStatus.Completed)

            assertEquals(1, breakdown?.goals?.size)
            assertEquals("Ship Feature", breakdown?.goals?.first()?.title)
            assertEquals(true, breakdown?.goals?.first()?.isCompleted)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testMonthlySummaryCalculations() = runTest(testDispatcher) {
        val habit = HabitEntity(
            id = "h1",
            name = "Drink Water",
            measurementType = "NUMERIC",
            targetValue = 8.0,
            unit = "glasses",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isArchived = false,
            createdAt = pastMillis,
            updatedAt = pastMillis
        )
        database.habitDao().insert(habit)

        // Record 1 completed, 1 incomplete
        database.habitRecordDao().upsert(
            HabitRecordEntity(
                id = "r1",
                habitId = "h1",
                date = "2026-09-01",
                actualValue = 8.0,
                targetValue = 8.0,
                unit = "glasses",
                measurementType = "NUMERIC",
                isCompleted = true,
                recordedAt = pastMillis
            )
        )
        database.habitRecordDao().upsert(
            HabitRecordEntity(
                id = "r2",
                habitId = "h1",
                date = "2026-09-02",
                actualValue = 4.0,
                targetValue = 8.0,
                unit = "glasses",
                measurementType = "NUMERIC",
                isCompleted = false,
                recordedAt = pastMillis
            )
        )

        viewModel.uiState.test {
            awaitItem() // loading
            var state = awaitItem()
            // If data update caused subsequent emission, consume it
            while (state.monthSummary?.totalHabitCompletions != 1) {
                state = awaitItem()
            }
            assertEquals(1, state.monthSummary?.totalHabitCompletions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testSelectedDateBreakdown_includesDailyReviewWhenPresent() = runTest(testDispatcher) {
        val dateStr = "2026-09-01"
        database.dailyReviewDao().upsert(
            com.habit1.app.data.local.db.entity.DailyReviewEntity(
                date = dateStr,
                notes = "Reflective evening walk.",
                mood = "Calm",
                createdAt = pastMillis,
                updatedAt = pastMillis
            )
        )

        viewModel.uiState.test {
            awaitItem() // loading
            var state = awaitItem()
            // Select the date with review
            viewModel.onEvent(HistoryUiEvent.SelectDate(java.time.LocalDate.of(2026, 9, 1)))
            state = awaitItem()

            while (state.selectedDateBreakdown?.dailyReview == null) {
                state = awaitItem()
            }

            val breakdown = state.selectedDateBreakdown
            assertNotNull(breakdown)
            assertNotNull(breakdown!!.dailyReview)
            assertEquals("Reflective evening walk.", breakdown.dailyReview!!.notes)
            assertEquals("Calm", breakdown.dailyReview!!.mood)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testJumpToToday_resetsMonthAndDate() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // loading
            awaitItem() // initial

            // Move to previous month
            viewModel.onEvent(HistoryUiEvent.PreviousMonth)
            val prev = awaitItem()
            val initialMonth = prev.selectedMonth.plusMonths(1)

            // Jump to today
            viewModel.onEvent(HistoryUiEvent.JumpToToday)
            val todayState = awaitItem()
            assertEquals(initialMonth, todayState.selectedMonth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testQuantitativeProgressFormatting_withUnitsAndPercentages() = runTest(testDispatcher) {
        val targetDate = LocalDate.of(2026, 9, 10)
        val habit = HabitEntity(
            id = "h_quant",
            name = "Reading",
            measurementType = "DURATION",
            targetValue = 60.0,
            unit = "min",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isArchived = false,
            createdAt = pastMillis,
            updatedAt = pastMillis
        )
        database.habitDao().insert(habit)

        val record = HabitRecordEntity(
            id = "r_quant",
            habitId = "h_quant",
            date = "2026-09-10",
            actualValue = 45.0,
            targetValue = 60.0,
            unit = "min",
            measurementType = "DURATION",
            isCompleted = false,
            recordedAt = pastMillis
        )
        database.habitRecordDao().upsert(record)

        viewModel.uiState.test {
            awaitItem() // loading
            var state = awaitItem()

            viewModel.onEvent(HistoryUiEvent.SelectDate(targetDate))
            state = awaitItem()
            while (state.selectedDateBreakdown?.habits?.any { it.habitId == "h_quant" } != true) {
                state = awaitItem()
            }

            val item = state.selectedDateBreakdown!!.habits.first { it.habitId == "h_quant" }
            assertEquals("45 / 60 min • 75%", item.formattedProgress)
            assertTrue(item.isPartial)
            assertEquals(false, item.isCompleted)
            assertTrue(item.status is CalendarDayStatus.RecordedIncomplete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testGoalAndSubtasksIndependence() = runTest(testDispatcher) {
        val targetDate = LocalDate.of(2026, 9, 12)
        val goal = DailyGoalEntity(
            id = "g_indep",
            title = "Write Report",
            targetDate = "2026-09-12",
            isCompleted = false,
            displayOrder = 0,
            createdAt = pastMillis,
            updatedAt = pastMillis
        )
        database.dailyGoalDao().insertGoal(goal)

        val subtask1 = com.habit1.app.data.local.db.entity.GoalSubtaskEntity(
            id = "s1",
            goalId = "g_indep",
            title = "Section 1",
            isCompleted = true,
            displayOrder = 0,
            createdAt = pastMillis
        )
        val subtask2 = com.habit1.app.data.local.db.entity.GoalSubtaskEntity(
            id = "s2",
            goalId = "g_indep",
            title = "Section 2",
            isCompleted = false,
            displayOrder = 1,
            createdAt = pastMillis
        )
        database.dailyGoalDao().insertAllSubtasks(listOf(subtask1, subtask2))

        viewModel.uiState.test {
            awaitItem() // loading
            var state = awaitItem()

            viewModel.onEvent(HistoryUiEvent.SelectDate(targetDate))
            state = awaitItem()
            while (state.selectedDateBreakdown?.goals?.any { it.id == "g_indep" } != true) {
                state = awaitItem()
            }

            val goalItem = state.selectedDateBreakdown!!.goals.first { it.id == "g_indep" }
            assertEquals(false, goalItem.isCompleted) // Goal incomplete
            assertEquals(2, goalItem.subtasks.size)
            assertEquals(1, goalItem.subtasks.count { it.isCompleted }) // 1 of 2 subtasks complete
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testCalendarIndicators_trackHabitsGoalsAndReview() = runTest(testDispatcher) {
        val targetDate = LocalDate.of(2026, 9, 20)
        database.dailyReviewDao().upsert(
            com.habit1.app.data.local.db.entity.DailyReviewEntity(
                date = "2026-09-20",
                notes = "Productive Sunday",
                mood = "Focused",
                createdAt = pastMillis,
                updatedAt = pastMillis
            )
        )

        viewModel.uiState.test {
            awaitItem() // loading
            var state = awaitItem()
            while (state.calendarDays.none { it.date == targetDate && it.hasReview }) {
                state = awaitItem()
            }

            val day = state.calendarDays.first { it.date == targetDate }
            assertTrue(day.hasReview)
            assertTrue(day.hasRecordedActivity)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testYearNavigation_previousAndNextYear() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // loading
            val initial = awaitItem()
            val initialYear = initial.selectedYear

            viewModel.onEvent(HistoryUiEvent.NextYear)
            val nextYearState = awaitItem()
            assertEquals(initialYear + 1, nextYearState.selectedYear)

            viewModel.onEvent(HistoryUiEvent.PreviousYear)
            val restoredYearState = awaitItem()
            assertEquals(initialYear, restoredYearState.selectedYear)

            viewModel.onEvent(HistoryUiEvent.SelectYear(2024)) // Leap year
            val leapYearState = awaitItem()
            assertEquals(2024, leapYearState.selectedYear)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testYearlyOverview_aggregates12MonthsCorrectly() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // loading
            val state = awaitItem()

            assertEquals(12, state.yearlyOverview.size)
            assertEquals("January", state.yearlyOverview[0].monthName)
            assertEquals("December", state.yearlyOverview[11].monthName)

            // Select month from yearly overview
            val targetYm = YearMonth.of(state.selectedYear, 6)
            viewModel.onEvent(HistoryUiEvent.SelectMonthFromYear(targetYm))
            val updatedState = awaitItem()
            assertEquals(targetYm, updatedState.selectedMonth)
            assertEquals(HistoryViewMode.MONTH, updatedState.viewMode)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testViewModeToggle_monthAndYear() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // loading
            val initial = awaitItem()
            assertEquals(HistoryViewMode.MONTH, initial.viewMode)

            viewModel.onEvent(HistoryUiEvent.ToggleViewMode(HistoryViewMode.YEAR))
            val yearModeState = awaitItem()
            assertEquals(HistoryViewMode.YEAR, yearModeState.viewMode)

            viewModel.onEvent(HistoryUiEvent.ToggleViewMode(HistoryViewMode.MONTH))
            val monthModeState = awaitItem()
            assertEquals(HistoryViewMode.MONTH, monthModeState.viewMode)

            cancelAndIgnoreRemainingEvents()
        }
    }
}

