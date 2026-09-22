package com.habit1.app.platform.receiver

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.HabitApplication
import com.habit1.app.data.local.db.entity.HabitEntity
import kotlinx.coroutines.delay
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

@RunWith(RobolectricTestRunner::class)
@Config(application = HabitApplication::class, sdk = [34])
class HabitActionReceiverTest {

    private lateinit var app: HabitApplication
    private val testDateStr = com.habit1.app.core.util.DateTimeUtils.formatDate(java.time.LocalDate.now())

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
    fun testActionYes_createsCompletedRecord() = runBlocking {
        val habit = HabitEntity(
            id = "act_yes_habit",
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
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        app.container.habitRepository.createHabit(habit)

        val intent = Intent(HabitActionReceiver.ACTION_HABIT_RECORD_YES).apply {
            putExtra(HabitActionReceiver.EXTRA_HABIT_ID, "act_yes_habit")
            putExtra(HabitActionReceiver.EXTRA_TARGET_DATE, testDateStr)
            putExtra(HabitActionReceiver.EXTRA_NOTIFICATION_ID, 1234)
        }

        val receiver = HabitActionReceiver()
        receiver.onReceive(app, intent)

        delay(300)

        val record = app.container.habitRecordRepository.getRecord("act_yes_habit", testDateStr)
        assertNotNull("Record should be persisted", record)
        assertEquals(1.0, record!!.actualValue, 0.001)
        assertTrue(record.isCompleted)
    }

    @Test
    fun testActionNo_createsIncompleteRecord() = runBlocking {
        val habit = HabitEntity(
            id = "act_no_habit",
            name = "Meditation",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = "08:00",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        app.container.habitRepository.createHabit(habit)

        val intent = Intent(HabitActionReceiver.ACTION_HABIT_RECORD_NO).apply {
            putExtra(HabitActionReceiver.EXTRA_HABIT_ID, "act_no_habit")
            putExtra(HabitActionReceiver.EXTRA_TARGET_DATE, testDateStr)
            putExtra(HabitActionReceiver.EXTRA_NOTIFICATION_ID, 2345)
        }

        val receiver = HabitActionReceiver()
        receiver.onReceive(app, intent)

        delay(300)

        val record = app.container.habitRecordRepository.getRecord("act_no_habit", testDateStr)
        assertNotNull("Record should be persisted", record)
        assertEquals(0.0, record!!.actualValue, 0.001)
        assertFalse(record.isCompleted)
    }

    @Test
    fun testActionDone_createsCompletedQuantitativeRecord() = runBlocking {
        val habit = HabitEntity(
            id = "act_done_habit",
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
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        app.container.habitRepository.createHabit(habit)

        val intent = Intent(HabitActionReceiver.ACTION_HABIT_RECORD_DONE).apply {
            putExtra(HabitActionReceiver.EXTRA_HABIT_ID, "act_done_habit")
            putExtra(HabitActionReceiver.EXTRA_TARGET_DATE, testDateStr)
            putExtra(HabitActionReceiver.EXTRA_NOTIFICATION_ID, 3456)
        }

        val receiver = HabitActionReceiver()
        receiver.onReceive(app, intent)

        delay(300)

        val record = app.container.habitRecordRepository.getRecord("act_done_habit", testDateStr)
        assertNotNull("Record should be persisted", record)
        assertEquals(50.0, record!!.actualValue, 0.001)
        assertTrue(record.isCompleted)
    }

    @Test
    fun testActionReceiver_missingExtras_safelyIgnored() = runBlocking {
        val intent = Intent(HabitActionReceiver.ACTION_HABIT_RECORD_YES)
        val receiver = HabitActionReceiver()
        // Should not throw or crash
        receiver.onReceive(app, intent)
    }
}
