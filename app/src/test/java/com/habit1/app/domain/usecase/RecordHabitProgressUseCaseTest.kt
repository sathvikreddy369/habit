package com.habit1.app.domain.usecase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRecordRepositoryImpl
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.data.repository.HabitRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordHabitProgressUseCaseTest {

    private lateinit var database: AppDatabase
    private lateinit var habitRepo: HabitRepository
    private lateinit var recordRepo: HabitRecordRepository
    private lateinit var useCase: RecordHabitProgressUseCase

    private val testDate = LocalDate.of(2026, 9, 21)
    private val testDateStr = "2026-09-21"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemoryDatabase(context)
        val dispatcher = Dispatchers.Unconfined
        habitRepo = HabitRepositoryImpl(database.habitDao(), dispatcher)
        recordRepo = HabitRecordRepositoryImpl(database.habitRecordDao(), dispatcher)
        useCase = RecordHabitProgressUseCase(habitRepo, recordRepo)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testBooleanHabitMarkCompletedAndIncomplete() = runBlocking {
        val habit = HabitEntity(
            id = "h_bool",
            name = "No Outside Food",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        habitRepo.createHabit(habit)

        // Mark completed (YES)
        val yesResult = useCase.markCompleted("h_bool", testDate)
        assertTrue(yesResult.isSuccess)
        val yesRecord = yesResult.getOrThrow()
        assertEquals(1.0, yesRecord.actualValue, 0.001)
        assertTrue(yesRecord.isCompleted)
        assertEquals("h_bool", yesRecord.habitId)
        assertEquals(testDateStr, yesRecord.date)

        // Verify persisted in DB
        val persistedYes = recordRepo.getRecord("h_bool", testDateStr)
        assertNotNull(persistedYes)
        assertEquals(1.0, persistedYes!!.actualValue, 0.001)
        assertTrue(persistedYes.isCompleted)

        // Mark incomplete (NO)
        val noResult = useCase.markIncomplete("h_bool", testDate)
        assertTrue(noResult.isSuccess)
        val noRecord = noResult.getOrThrow()
        assertEquals(0.0, noRecord.actualValue, 0.001)
        assertFalse(noRecord.isCompleted)
        // Verify same record ID is preserved (no duplicate rows)
        assertEquals(yesRecord.id, noRecord.id)

        val persistedNo = recordRepo.getRecord("h_bool", testDateStr)
        assertNotNull(persistedNo)
        assertEquals(0.0, persistedNo!!.actualValue, 0.001)
        assertFalse(persistedNo.isCompleted)
    }

    @Test
    fun testBooleanIdempotencyAndTransitions() = runBlocking {
        val habit = HabitEntity(
            id = "h_trans",
            name = "Meditation",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        habitRepo.createHabit(habit)

        // Transition: Initial -> NO
        val r1 = useCase.markIncomplete("h_trans", testDate).getOrThrow()
        assertFalse(r1.isCompleted)

        // Transition: NO -> NO
        val r2 = useCase.markIncomplete("h_trans", testDate).getOrThrow()
        assertFalse(r2.isCompleted)
        assertEquals(r1.id, r2.id)

        // Transition: NO -> YES
        val r3 = useCase.markCompleted("h_trans", testDate).getOrThrow()
        assertTrue(r3.isCompleted)
        assertEquals(r1.id, r3.id)

        // Transition: YES -> YES
        val r4 = useCase.markCompleted("h_trans", testDate).getOrThrow()
        assertTrue(r4.isCompleted)
        assertEquals(r1.id, r4.id)

        // Transition: YES -> NO
        val r5 = useCase.markIncomplete("h_trans", testDate).getOrThrow()
        assertFalse(r5.isCompleted)
        assertEquals(r1.id, r5.id)

        // Exactly 1 record in repository
        val records = recordRepo.getRecordsForHabits(listOf("h_trans"))
        assertEquals(1, records.size)
    }

    @Test
    fun testQuantitativeDoneSemanticsOnEmptyRecord() = runBlocking {
        val habit = HabitEntity(
            id = "h_study",
            name = "Study",
            measurementType = "DURATION",
            targetValue = 60.0,
            unit = "mins",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        habitRepo.createHabit(habit)

        // Empty record -> DONE sets actualValue = target (60.0)
        val result = useCase.markCompleted("h_study", testDate)
        assertTrue(result.isSuccess)
        val record = result.getOrThrow()
        assertEquals(60.0, record.actualValue, 0.001)
        assertEquals(60.0, record.targetValue, 0.001)
        assertTrue(record.isCompleted)
    }

    @Test
    fun testQuantitativeDonePromotesPartialToTarget() = runBlocking {
        val habit = HabitEntity(
            id = "h_water",
            name = "Water",
            measurementType = "QUANTITY",
            targetValue = 3.0,
            unit = "L",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        habitRepo.createHabit(habit)

        // Initial partial entry: 1.5L
        useCase.setActualValue("h_water", testDate, 1.5)
        val initial = recordRepo.getRecord("h_water", testDateStr)
        assertNotNull(initial)
        assertEquals(1.5, initial!!.actualValue, 0.001)
        assertFalse(initial.isCompleted)

        // DONE promotes to target (3.0L)
        val doneResult = useCase.markCompleted("h_water", testDate)
        assertTrue(doneResult.isSuccess)
        val record = doneResult.getOrThrow()
        assertEquals(3.0, record.actualValue, 0.001)
        assertTrue(record.isCompleted)
        assertEquals(initial.id, record.id)
    }

    @Test
    fun testQuantitativeDonePreservesExceededActualValue() = runBlocking {
        val habit = HabitEntity(
            id = "h_pushups",
            name = "Push-ups",
            measurementType = "COUNT",
            targetValue = 50.0,
            unit = "reps",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        habitRepo.createHabit(habit)

        // Existing completed entry exceeded target: 75 reps
        useCase.setActualValue("h_pushups", testDate, 75.0)
        val initial = recordRepo.getRecord("h_pushups", testDateStr)
        assertNotNull(initial)
        assertEquals(75.0, initial!!.actualValue, 0.001)
        assertTrue(initial.isCompleted)

        // Tapping DONE must NOT downgrade 75 to 50
        val doneResult = useCase.markCompleted("h_pushups", testDate)
        assertTrue(doneResult.isSuccess)
        val record = doneResult.getOrThrow()
        assertEquals(75.0, record.actualValue, 0.001)
        assertTrue(record.isCompleted)
    }

    @Test
    fun testExplicitTargetDateRespectedAcrossMidnight() = runBlocking {
        val habit = HabitEntity(
            id = "h_midnight",
            name = "Reading",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            displayOrder = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        habitRepo.createHabit(habit)

        val targetDate = LocalDate.of(2026, 9, 21)
        // User answers notification after midnight on 2026-09-22, passing explicit targetDate 2026-09-21
        val result = useCase.markCompleted("h_midnight", targetDate)
        assertTrue(result.isSuccess)
        val record = result.getOrThrow()
        assertEquals("2026-09-21", record.date)

        // Record exists under 2026-09-21
        assertNotNull(recordRepo.getRecord("h_midnight", "2026-09-21"))
        // No record exists on 2026-09-22
        val nextDayRecord = recordRepo.getRecord("h_midnight", "2026-09-22")
        org.junit.Assert.assertNull(nextDayRecord)
    }

    @Test
    fun testSafetyChecksRejectPausedArchivedAndMissingHabits() = runBlocking {
        val paused = HabitEntity(
            id = "h_paused",
            name = "Paused Habit",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            isPaused = true,
            displayOrder = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val archived = HabitEntity(
            id = "h_archived",
            name = "Archived Habit",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            isArchived = true,
            displayOrder = 1,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        habitRepo.createHabit(paused)
        habitRepo.createHabit(archived)

        // Fails on paused
        val pausedRes = useCase.markCompleted("h_paused", testDate)
        assertTrue(pausedRes.isFailure)

        // Fails on archived
        val archivedRes = useCase.markCompleted("h_archived", testDate)
        assertTrue(archivedRes.isFailure)

        // Fails on missing habit
        val missingRes = useCase.markCompleted("h_nonexistent", testDate)
        assertTrue(missingRes.isFailure)
    }
}
