package com.habit1.app.platform.receiver

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.HabitApplication
import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.domain.model.HabitRecordStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = HabitApplication::class, sdk = [34])
class AlarmReceiverTest {

    private lateinit var app: HabitApplication
    private lateinit var notificationManager: NotificationManager
    private val pastMillis = 1788220800000L
    private val today = LocalDate.now()
    private val todayStr = DateTimeUtils.formatDate(today)

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }

    @After
    fun tearDown() = runBlocking {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            app.container.database.clearAllTables()
        }
        notificationManager.cancelAll()
    }

    @Test
    fun testAlarmReceiver_doesNotCreateHabitRecord_notificationsAreNotRecords() = runBlocking {
        val habitId = "test_reminder_habit"

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
            startDate = "2026-01-01",
            createdAt = pastMillis,
            updatedAt = pastMillis
        )
        app.container.habitRepository.createHabit(habit)

        val intent = Intent(app, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_HABIT_REMINDER
            data = Uri.parse("habit1://reminder/$habitId")
            putExtra(AlarmReceiver.EXTRA_HABIT_ID, habitId)
        }

        val receiver = AlarmReceiver()
        receiver.onReceive(app, intent)

        var attempts = 0
        while (shadowOf(notificationManager).allNotifications.isEmpty() && attempts < 20) {
            delay(50)
            attempts++
        }

        // Verify: Still zero records in database! Notifications NEVER create records!
        val recordsAfterReminder = app.container.habitRecordRepository.getRecordsForHabit(habitId)
        assertEquals("Notifications must never fabricate or create habit records", 0, recordsAfterReminder.size)
        // Notification posted
        assertEquals(1, shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun testAlarmReceiver_completedHabitToday_suppressesNotificationCompletely() = runBlocking {
        val habitId = "done_habit"
        val habit = HabitEntity(
            id = habitId,
            name = "Workout",
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
            createdAt = pastMillis,
            updatedAt = pastMillis
        )
        app.container.habitRepository.createHabit(habit)

        // Mark DONE for today
        val doneRecord = HabitRecordEntity(
            id = "r_done",
            habitId = habitId,
            date = todayStr,
            actualValue = 1.0,
            targetValue = 1.0,
            measurementType = "BOOLEAN",
            isCompleted = true,
            status = HabitRecordStatus.DONE.name,
            recordedAt = System.currentTimeMillis()
        )
        app.container.habitRecordRepository.recordProgress(doneRecord)

        val intent = Intent(app, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_HABIT_REMINDER
            data = Uri.parse("habit1://reminder/$habitId")
            putExtra(AlarmReceiver.EXTRA_HABIT_ID, habitId)
        }

        val receiver = AlarmReceiver()
        receiver.onReceive(app, intent)

        var attempts = 0
        while (shadowOf(notificationManager).allNotifications.isEmpty() && attempts < 20) {
            delay(50)
            attempts++
        }

        // Zero notifications posted! Completely suppressed!
        assertEquals("Reminders for DONE habits must be completely suppressed", 0, shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun testAlarmReceiver_partialProgressToday_doesNotSuppressReminder() = runBlocking {
        val habitId = "partial_habit"
        val habit = HabitEntity(
            id = habitId,
            name = "Water",
            measurementType = "COUNT",
            targetValue = 8.0,
            unit = "glasses",
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = "14:00",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            startDate = "2026-01-01",
            createdAt = pastMillis,
            updatedAt = pastMillis
        )
        app.container.habitRepository.createHabit(habit)

        // PARTIAL progress (4 of 8 glasses)
        val partialRecord = HabitRecordEntity(
            id = "r_partial",
            habitId = habitId,
            date = todayStr,
            actualValue = 4.0,
            targetValue = 8.0,
            unit = "glasses",
            measurementType = "COUNT",
            isCompleted = false,
            status = HabitRecordStatus.PARTIAL.name,
            recordedAt = System.currentTimeMillis()
        )
        app.container.habitRecordRepository.recordProgress(partialRecord)

        val intent = Intent(app, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_HABIT_REMINDER
            data = Uri.parse("habit1://reminder/$habitId")
            putExtra(AlarmReceiver.EXTRA_HABIT_ID, habitId)
        }

        val receiver = AlarmReceiver()
        receiver.onReceive(app, intent)

        var attempts = 0
        while (shadowOf(notificationManager).allNotifications.isEmpty() && attempts < 20) {
            delay(50)
            attempts++
        }

        // Notification must NOT be suppressed for PARTIAL progress
        assertEquals("PARTIAL quantitative progress must not suppress reminders", 1, shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun testAlarmReceiver_skippedHabitToday_suppressesNotification() = runBlocking {
        val habitId = "skipped_habit"
        val habit = HabitEntity(
            id = habitId,
            name = "Stretching",
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
            createdAt = pastMillis,
            updatedAt = pastMillis
        )
        app.container.habitRepository.createHabit(habit)

        val skippedRecord = HabitRecordEntity(
            id = "r_skip",
            habitId = habitId,
            date = todayStr,
            actualValue = 0.0,
            targetValue = 1.0,
            measurementType = "BOOLEAN",
            isCompleted = false,
            status = HabitRecordStatus.SKIPPED.name,
            recordedAt = System.currentTimeMillis()
        )
        app.container.habitRecordRepository.recordProgress(skippedRecord)

        val intent = Intent(app, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_HABIT_REMINDER
            data = Uri.parse("habit1://reminder/$habitId")
            putExtra(AlarmReceiver.EXTRA_HABIT_ID, habitId)
        }

        val receiver = AlarmReceiver()
        receiver.onReceive(app, intent)

        var attempts = 0
        while (shadowOf(notificationManager).allNotifications.isEmpty() && attempts < 20) {
            delay(50)
            attempts++
        }

        assertEquals("Reminders for SKIPPED habits must be suppressed", 0, shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun testAlarmReceiver_preCreationHabit_doesNotNotify() = runBlocking {
        val habitId = "future_habit"
        val habit = HabitEntity(
            id = habitId,
            name = "Future Resolution",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = "08:00",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            startDate = today.plusDays(5).toString(), // Starts 5 days in the future!
            createdAt = pastMillis,
            updatedAt = pastMillis
        )
        app.container.habitRepository.createHabit(habit)

        val intent = Intent(app, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_HABIT_REMINDER
            data = Uri.parse("habit1://reminder/$habitId")
            putExtra(AlarmReceiver.EXTRA_HABIT_ID, habitId)
        }

        val receiver = AlarmReceiver()
        receiver.onReceive(app, intent)

        var attempts = 0
        while (shadowOf(notificationManager).allNotifications.isEmpty() && attempts < 20) {
            delay(50)
            attempts++
        }

        assertEquals("Habit before startDate must not notify", 0, shadowOf(notificationManager).allNotifications.size)
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

        delay(100)

        val records = app.container.habitRecordRepository.getRecordsForHabit(nonExistentHabitId)
        assertEquals(0, records.size)
        assertEquals(0, shadowOf(notificationManager).allNotifications.size)
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
            startDate = "2026-01-01",
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

        delay(100)

        val records = app.container.habitRecordRepository.getRecordsForHabit(pausedHabitId)
        assertEquals(0, records.size)
        assertEquals(0, shadowOf(notificationManager).allNotifications.size)
    }
}
