package com.habit1.app.ui.habits.form

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRecordRepositoryImpl
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.data.repository.HabitRepositoryImpl
import com.habit1.app.domain.validation.HabitValidationError
import com.habit1.app.domain.validation.MeasurementKind
import com.habit1.app.domain.validation.ScheduleKind
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
import java.time.DayOfWeek

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HabitFormViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var habitRepository: HabitRepository
    private lateinit var habitRecordRepository: HabitRecordRepository

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
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun createHabit_allMeasurementTypesAndSchedules() = runTest(testDispatcher) {
        // 1. Create Count Habit with SpecificDays
        val viewModel = HabitFormViewModel(habitRepository, coroutineScope = this)
        viewModel.onEvent(HabitFormUiEvent.UpdateName("Pushups"))
        viewModel.onEvent(HabitFormUiEvent.SelectMeasurementKind(MeasurementKind.COUNT))
        viewModel.onEvent(HabitFormUiEvent.UpdateTarget("50"))
        viewModel.onEvent(HabitFormUiEvent.UpdateUnit("reps"))
        viewModel.onEvent(HabitFormUiEvent.SelectScheduleKind(ScheduleKind.SPECIFIC_DAYS))
        viewModel.onEvent(HabitFormUiEvent.ToggleDay(DayOfWeek.MONDAY))

        viewModel.onEvent(HabitFormUiEvent.SaveHabit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)

        val habits = habitRepository.getActiveHabitsList()
        assertEquals(1, habits.size)
        val created = habits[0]
        assertEquals("Pushups", created.name)
        assertEquals("COUNT", created.measurementType)
        assertEquals(50.0, created.targetValue, 0.001)
        assertEquals("reps", created.unit)
        assertEquals("SPECIFIC_DAYS", created.scheduleType)
    }

    @Test
    fun createHabit_intervalSchedule() = runTest(testDispatcher) {
        val viewModel = HabitFormViewModel(habitRepository, coroutineScope = this)
        viewModel.onEvent(HabitFormUiEvent.UpdateName("Water Plants"))
        viewModel.onEvent(HabitFormUiEvent.SelectMeasurementKind(MeasurementKind.BOOLEAN))
        viewModel.onEvent(HabitFormUiEvent.SelectScheduleKind(ScheduleKind.INTERVAL))
        viewModel.onEvent(HabitFormUiEvent.UpdateIntervalDays("3"))

        viewModel.onEvent(HabitFormUiEvent.SaveHabit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        val habit = habitRepository.getActiveHabitsList().first()
        assertEquals("INTERVAL", habit.scheduleType)
        assertTrue(habit.scheduleConfig.contains("\"everyNDays\":3"))
    }

    @Test
    fun invalidInputs_preventSavingAndExposeValidationErrors() = runTest(testDispatcher) {
        val viewModel = HabitFormViewModel(habitRepository, coroutineScope = this)

        // Blank name and invalid target
        viewModel.onEvent(HabitFormUiEvent.UpdateName("   "))
        viewModel.onEvent(HabitFormUiEvent.SelectMeasurementKind(MeasurementKind.COUNT))
        viewModel.onEvent(HabitFormUiEvent.UpdateTarget("-10"))

        viewModel.onEvent(HabitFormUiEvent.SaveHabit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertTrue(viewModel.uiState.value.errors.contains(HabitValidationError.NameBlank))
        assertTrue(viewModel.uiState.value.errors.contains(HabitValidationError.TargetMustBePositive))
        assertEquals(0, habitRepository.getActiveHabitsList().size)
    }

    @Test
    fun editHabit_preservesCreationDateAndHistoricalRecords() = runTest(testDispatcher) {
        val originalCreatedAt = 1700000000000L
        val habit = HabitEntity(
            id = "habit_edit_1",
            name = "Read",
            measurementType = "COUNT",
            targetValue = 10.0,
            unit = "pages",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = originalCreatedAt,
            updatedAt = originalCreatedAt
        )
        habitRepository.createHabit(habit)

        // Historical record with snapshot
        val pastRecord = HabitRecordEntity(
            id = "rec_past",
            habitId = "habit_edit_1",
            date = "2026-09-01",
            actualValue = 10.0,
            targetValue = 10.0,
            measurementType = "COUNT",
            unit = "pages",
            isCompleted = true,
            recordedAt = originalCreatedAt + 1000
        )
        habitRecordRepository.recordProgress(pastRecord)
        advanceUntilIdle()

        // Edit habit
        val editViewModel = HabitFormViewModel(
            habitRepository = habitRepository,
            initialHabitId = "habit_edit_1",
            coroutineScope = this
        )
        advanceUntilIdle()

        assertEquals("Read", editViewModel.uiState.value.name)
        assertEquals("10", editViewModel.uiState.value.targetInput)

        // Change target to 25 and name to "Read Daily"
        editViewModel.onEvent(HabitFormUiEvent.UpdateName("Read Daily"))
        editViewModel.onEvent(HabitFormUiEvent.UpdateTarget("25"))
        editViewModel.onEvent(HabitFormUiEvent.SaveHabit)
        advanceUntilIdle()

        assertTrue(editViewModel.uiState.value.isSaved)

        // Verify edited habit
        val updatedHabit = habitRepository.getHabitById("habit_edit_1")
        assertNotNull(updatedHabit)
        assertEquals("Read Daily", updatedHabit!!.name)
        assertEquals(25.0, updatedHabit.targetValue, 0.001)
        assertEquals(originalCreatedAt, updatedHabit.createdAt) // Creation date unchanged!

        // CRITICAL HISTORICAL INTEGRITY CHECK: Historical record is strictly unchanged!
        val preservedRecord = habitRecordRepository.getRecord("habit_edit_1", "2026-09-01")
        assertNotNull(preservedRecord)
        assertEquals(10.0, preservedRecord!!.actualValue, 0.001)
        assertEquals(10.0, preservedRecord.targetValue, 0.001) // Historical snapshot retained!
        assertEquals("COUNT", preservedRecord.measurementType)
        assertEquals("pages", preservedRecord.unit)
        assertTrue(preservedRecord.isCompleted)
    }

    @Test
    fun switchMeasurementKind_updatesTargetAndCleansUnitWithoutBleed() = runTest(testDispatcher) {
        val viewModel = HabitFormViewModel(habitRepository, coroutineScope = this)

        // Initial state is BOOLEAN
        assertEquals(MeasurementKind.BOOLEAN, viewModel.uiState.value.measurementKind)

        // Select COUNT: sets target to 10 and user sets unit "reps"
        viewModel.onEvent(HabitFormUiEvent.SelectMeasurementKind(MeasurementKind.COUNT))
        viewModel.onEvent(HabitFormUiEvent.UpdateUnit("reps"))
        assertEquals(MeasurementKind.COUNT, viewModel.uiState.value.measurementKind)
        assertEquals("10", viewModel.uiState.value.targetInput)
        assertEquals("reps", viewModel.uiState.value.unitInput)

        // Switch to QUANTITY: target defaults to positive (or existing valid number), unit "reps" is cleared
        viewModel.onEvent(HabitFormUiEvent.SelectMeasurementKind(MeasurementKind.QUANTITY))
        assertEquals(MeasurementKind.QUANTITY, viewModel.uiState.value.measurementKind)
        assertEquals("10", viewModel.uiState.value.targetInput) // 10 is valid for quantity
        assertEquals("", viewModel.uiState.value.unitInput) // "reps" cleared so user can enter "L"

        // Type "L" for unit and "3.25" for target
        viewModel.onEvent(HabitFormUiEvent.UpdateTarget("3.25"))
        viewModel.onEvent(HabitFormUiEvent.UpdateUnit("L"))

        // Switch to DURATION: target converted to integer minutes default, unit set to mins
        viewModel.onEvent(HabitFormUiEvent.SelectMeasurementKind(MeasurementKind.DURATION))
        assertEquals(MeasurementKind.DURATION, viewModel.uiState.value.measurementKind)
        assertEquals("30", viewModel.uiState.value.targetInput)
        assertEquals("mins", viewModel.uiState.value.unitInput)
    }
}
