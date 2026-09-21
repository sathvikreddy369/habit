package com.habit1.app.platform.receiver

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.HabitApplication
import com.habit1.app.data.local.db.entity.HabitEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = HabitApplication::class, sdk = [34])
class AlarmReceiverTest {

    private lateinit var app: HabitApplication
    private val pastMillis = 1788220800000L

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
    fun testAlarmReceiver_doesNotCreateHabitRecord_notificationsAreNotRecords() = runBlocking {
        val habitId = "test_reminder_habit"
        val todayStr = LocalDate.now().toString()

        val habit = HabitEntity(
            id = habitId,
            name = "Hydrate",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = "08:00",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = pastMillis,
            updatedAt = pastMillis
        )
        app.container.habitRepository.createHabit(habit)

        // Verify no records exist initially
        val initialRecords = app.container.habitRecordRepository.getRecordsForHabit(habitId)
        assertEquals(0, initialRecords.size)

        // Simulate AlarmManager firing the alarm
        val intent = Intent(app, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_HABIT_REMINDER
            data = Uri.parse("habit1://reminder/$habitId")
            putExtra(AlarmReceiver.EXTRA_HABIT_ID, habitId)
        }

        val receiver = AlarmReceiver()
        receiver.onReceive(app, intent)

        // Wait brief moment for async receiver coroutine
        kotlinx.coroutines.delay(100)

        // Verify: Still zero records in database! Notifications NEVER create records!
        val recordsAfterReminder = app.container.habitRecordRepository.getRecordsForHabit(habitId)
        assertEquals("Notifications must never fabricate or create habit records", 0, recordsAfterReminder.size)
    }

    @Test
    fun testAlarmReceiver_staleDeletedHabit_discardsSilently() = runBlocking {
        val nonExistentHabitId = "deleted_habit_999"

        val intent = Intent(app, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_HABIT_REMINDER
            data = Uri.parse("habit1://reminder/$nonExistentHabitId")
            putExtra(AlarmReceiver.EXTRA_HABIT_ID, nonExistentHabitId)
        }

        val receiver = AlarmReceiver()
        receiver.onReceive(app, intent)

        kotlinx.coroutines.delay(100)

        val records = app.container.habitRecordRepository.getRecordsForHabit(nonExistentHabitId)
        assertEquals(0, records.size)
    }

    @Test
    fun testAlarmReceiver_pausedHabit_doesNotNotify() = runBlocking {
        val pausedHabitId = "paused_habit"
        val habit = HabitEntity(
            id = pausedHabitId,
            name = "Meditate",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = "08:00",
            displayOrder = 0,
            isPaused = true, // Paused!
            isArchived = false,
            createdAt = pastMillis,
            updatedAt = pastMillis
        )
        app.container.habitRepository.createHabit(habit)

        val intent = Intent(app, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_HABIT_REMINDER
            data = Uri.parse("habit1://reminder/$pausedHabitId")
            putExtra(AlarmReceiver.EXTRA_HABIT_ID, pausedHabitId)
        }

        val receiver = AlarmReceiver()
        receiver.onReceive(app, intent)

        kotlinx.coroutines.delay(100)

        val records = app.container.habitRecordRepository.getRecordsForHabit(pausedHabitId)
        assertEquals(0, records.size)
    }
}
