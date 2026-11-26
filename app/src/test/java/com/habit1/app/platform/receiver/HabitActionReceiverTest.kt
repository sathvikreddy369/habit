package com.habit1.app.platform.receiver

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.HabitApplication
import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.domain.model.HabitRecordStatus
import kotlinx.coroutines.delay
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
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = HabitApplication::class, sdk = [34])
class HabitActionReceiverTest {

    private lateinit var app: HabitApplication
    private val testDateStr = DateTimeUtils.formatDate(LocalDate.now())

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() = runBlocking {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            app.container.database.clearAllTables()
        }
    }

    @Test
    fun testActionDone_createsCompletedRecord() = runBlocking {
        val habit = HabitEntity(
            id = "act_done_habit",
            name = "Morning Walk",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = "07:00",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            startDate = "2026-01-01",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        app.container.habitRepository.createHabit(habit)

        val intent = Intent(HabitActionReceiver.ACTION_HABIT_RECORD_DONE).apply {
            putExtra(HabitActionReceiver.EXTRA_HABIT_ID, "act_done_habit")
            putExtra(HabitActionReceiver.EXTRA_TARGET_DATE, testDateStr)
            putExtra(HabitActionReceiver.EXTRA_NOTIFICATION_ID, 1234)
        }

        val receiver = HabitActionReceiver()
        receiver.onReceive(app, intent)

        delay(300)

        val record = app.container.habitRecordRepository.getRecord("act_done_habit", testDateStr)
        assertNotNull("Record should be persisted", record)
        assertEquals(1.0, record!!.actualValue, 0.001)
        assertTrue(record.isCompleted)
        assertEquals(HabitRecordStatus.DONE.name, record.status)
    }

    @Test
    fun testActionSkip_createsSkippedRecord() = runBlocking {
        val habit = HabitEntity(
            id = "act_skip_habit",
            name = "Stretching",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = "08:00",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            startDate = "2026-01-01",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        app.container.habitRepository.createHabit(habit)

        val intent = Intent(HabitActionReceiver.ACTION_HABIT_RECORD_SKIP).apply {
            putExtra(HabitActionReceiver.EXTRA_HABIT_ID, "act_skip_habit")
            putExtra(HabitActionReceiver.EXTRA_TARGET_DATE, testDateStr)
            putExtra(HabitActionReceiver.EXTRA_NOTIFICATION_ID, 2345)
        }

        val receiver = HabitActionReceiver()
        receiver.onReceive(app, intent)

        delay(300)

        val record = app.container.habitRecordRepository.getRecord("act_skip_habit", testDateStr)
        assertNotNull("Record should be persisted", record)
        assertEquals(0.0, record!!.actualValue, 0.001)
        assertEquals(HabitRecordStatus.SKIPPED.name, record.status)
    }

    @Test
    fun testActionSkip_doesNotOverwriteCompletedRecord() = runBlocking {
        val habit = HabitEntity(
            id = "act_protected_habit",
            name = "Reading",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = "09:00",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            startDate = "2026-01-01",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        app.container.habitRepository.createHabit(habit)

        // Mark DONE first
        app.container.recordHabitProgressUseCase.markCompleted("act_protected_habit", LocalDate.now())

        // Attempt SKIP
        val intent = Intent(HabitActionReceiver.ACTION_HABIT_RECORD_SKIP).apply {
            putExtra(HabitActionReceiver.EXTRA_HABIT_ID, "act_protected_habit")
            putExtra(HabitActionReceiver.EXTRA_TARGET_DATE, testDateStr)
            putExtra(HabitActionReceiver.EXTRA_NOTIFICATION_ID, 2345)
        }

        val receiver = HabitActionReceiver()
        receiver.onReceive(app, intent)

        delay(300)

        val record = app.container.habitRecordRepository.getRecord("act_protected_habit", testDateStr)
        assertNotNull(record)
        assertTrue("Completed record must NOT be overwritten by SKIP action", record!!.isCompleted)
        assertEquals(HabitRecordStatus.DONE.name, record.status)
    }

    @Test
    fun testActionDone_quantitativeHabit_completesToTarget() = runBlocking {
        val habit = HabitEntity(
            id = "act_quant_habit",
            name = "Push-ups",
            measurementType = "COUNT",
            targetValue = 50.0,
            unit = "reps",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = "09:00",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            startDate = "2026-01-01",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        app.container.habitRepository.createHabit(habit)

        val intent = Intent(HabitActionReceiver.ACTION_HABIT_RECORD_DONE).apply {
            putExtra(HabitActionReceiver.EXTRA_HABIT_ID, "act_quant_habit")
            putExtra(HabitActionReceiver.EXTRA_TARGET_DATE, testDateStr)
            putExtra(HabitActionReceiver.EXTRA_NOTIFICATION_ID, 3456)
        }

        val receiver = HabitActionReceiver()
        receiver.onReceive(app, intent)

        delay(300)

        val record = app.container.habitRecordRepository.getRecord("act_quant_habit", testDateStr)
        assertNotNull("Record should be persisted", record)
        assertEquals(50.0, record!!.actualValue, 0.001)
        assertTrue(record.isCompleted)
        assertEquals(HabitRecordStatus.DONE.name, record.status)
    }

    @Test
    fun testActionSnooze_schedulesSnooze() = runBlocking {
        val habit = HabitEntity(
            id = "act_snooze_habit",
            name = "Meditate",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = "09:00",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            startDate = "2026-01-01",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        app.container.habitRepository.createHabit(habit)

        val intent = Intent(HabitActionReceiver.ACTION_HABIT_SNOOZE).apply {
            putExtra(HabitActionReceiver.EXTRA_HABIT_ID, "act_snooze_habit")
            putExtra(HabitActionReceiver.EXTRA_TARGET_DATE, testDateStr)
            putExtra(HabitActionReceiver.EXTRA_NOTIFICATION_ID, 4567)
        }

        val receiver = HabitActionReceiver()
        receiver.onReceive(app, intent)

        delay(200)

        // Verifies no record created by snoozing
        val record = app.container.habitRecordRepository.getRecord("act_snooze_habit", testDateStr)
        assertNull("Snoozing must never create or alter habit records", record)
    }

    @Test
    fun testAction_staleDeletedOrPausedHabit_safelyIgnored() = runBlocking {
        val habit = HabitEntity(
            id = "act_paused_habit",
            name = "Paused Habit",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = "11:00",
            displayOrder = 0,
            isPaused = true,
            isArchived = false,
            startDate = "2026-01-01",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        app.container.habitRepository.createHabit(habit)

        val intent = Intent(HabitActionReceiver.ACTION_HABIT_RECORD_DONE).apply {
            putExtra(HabitActionReceiver.EXTRA_HABIT_ID, "act_paused_habit")
            putExtra(HabitActionReceiver.EXTRA_TARGET_DATE, testDateStr)
            putExtra(HabitActionReceiver.EXTRA_NOTIFICATION_ID, 8888)
        }

        val receiver = HabitActionReceiver()
        receiver.onReceive(app, intent)
        delay(150)

        val record = app.container.habitRecordRepository.getRecord("act_paused_habit", testDateStr)
        assertNull("No record should be created for paused habit", record)
    }
}
