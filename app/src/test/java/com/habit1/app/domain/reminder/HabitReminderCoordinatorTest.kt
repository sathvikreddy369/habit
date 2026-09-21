package com.habit1.app.domain.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.data.repository.HabitRepositoryImpl
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.platform.notification.NotificationHelper
import com.habit1.app.platform.reminder.HabitReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HabitReminderCoordinatorTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var habitRepository: HabitRepository

    private val scheduledHabits = mutableListOf<String>()
    private val canceledHabits = mutableListOf<String>()
    private val canceledNotifications = mutableListOf<String>()
    private var notificationsAllowed = true
    private var lastScheduledZoneId: ZoneId? = null

    private val fakeScheduler = object : HabitReminderScheduler {
        override fun scheduleNextReminder(habit: Habit, fromInstant: Instant, zoneId: ZoneId) {
            scheduledHabits.add(habit.id)
            lastScheduledZoneId = zoneId
        }

        override fun cancelReminder(habitId: String) {
            canceledHabits.add(habitId)
        }
    }

    private val fakeNotificationHelper by lazy {
        object : NotificationHelper(ApplicationProvider.getApplicationContext()) {
            override fun areNotificationsEnabled(): Boolean = notificationsAllowed
            override fun cancelNotification(habitId: String) {
                canceledNotifications.add(habitId)
            }
        }
    }

    private lateinit var coordinator: HabitReminderCoordinator
    private val pastMillis = 1788220800000L

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemoryDatabase(context)
        habitRepository = HabitRepositoryImpl(database.habitDao(), testDispatcher)

        scheduledHabits.clear()
        canceledHabits.clear()
        canceledNotifications.clear()
        notificationsAllowed = true

        coordinator = HabitReminderCoordinator(
            habitRepository = habitRepository,
            scheduler = fakeScheduler,
            notificationHelper = fakeNotificationHelper
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createHabit(
        id: String,
        reminderTime: LocalTime? = LocalTime.of(8, 0),
        isPaused: Boolean = false,
        isArchived: Boolean = false
    ): Habit {
        return Habit(
            id = id,
            name = "Test Habit $id",
            measurement = MeasurementType.BooleanChoice,
            schedule = HabitSchedule.Daily,
            reminderTime = reminderTime,
            isPaused = isPaused,
            isArchived = isArchived,
            createdAt = Instant.ofEpochMilli(pastMillis),
            updatedAt = Instant.ofEpochMilli(pastMillis)
        )
    }

    @Test
    fun testOnHabitCreated_withReminder_schedulesAlarm() {
        val habit = createHabit("h1", reminderTime = LocalTime.of(8, 0))
        coordinator.onHabitCreated(habit)

        assertTrue(scheduledHabits.contains("h1"))
        assertFalse(canceledHabits.contains("h1"))
    }

    @Test
    fun testOnHabitCreated_withoutReminder_doesNotScheduleAlarm() {
        val habit = createHabit("h2", reminderTime = null)
        coordinator.onHabitCreated(habit)

        assertFalse(scheduledHabits.contains("h2"))
    }

    @Test
    fun testOnHabitCreated_permissionDenied_doesNotScheduleAlarm() {
        notificationsAllowed = false
        val habit = createHabit("h3", reminderTime = LocalTime.of(9, 0))
        coordinator.onHabitCreated(habit)

        assertFalse(scheduledHabits.contains("h3"))
    }

    @Test
    fun testOnHabitUpdated_clearedReminder_cancelsAlarm() {
        val habit = createHabit("h1", reminderTime = null)
        coordinator.onHabitUpdated(habit)

        assertTrue(canceledHabits.contains("h1"))
    }

    @Test
    fun testOnHabitPausedAndResumed_lifecycle() = runTest(testDispatcher) {
        // Insert habit into database first (persistence succeeds first)
        val entity = HabitEntity(
            id = "h1",
            name = "Morning Stretch",
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
        database.habitDao().insert(entity)

        // Pause
        coordinator.onHabitPaused("h1")
        assertTrue(canceledHabits.contains("h1"))

        // Resume
        coordinator.onHabitResumed("h1")
        assertTrue(scheduledHabits.contains("h1"))
    }

    @Test
    fun testOnHabitArchivedAndUnarchived_lifecycle() = runTest(testDispatcher) {
        val entity = HabitEntity(
            id = "h1",
            name = "Morning Stretch",
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
        database.habitDao().insert(entity)

        // Archive
        coordinator.onHabitArchived("h1")
        assertTrue(canceledHabits.contains("h1"))

        // Unarchive
        coordinator.onHabitUnarchived("h1")
        assertTrue(scheduledHabits.contains("h1"))
    }

    @Test
    fun testOnHabitDeleted_cancelsAlarmAndNotification() {
        coordinator.onHabitDeleted("h1")
        assertTrue(canceledHabits.contains("h1"))
        assertTrue(canceledNotifications.contains("h1"))
    }

    @Test
    fun testReconcileAllReminders_permissionGranted_schedulesEligibleHabits() = runTest(testDispatcher) {
        // Insert two habits: one with reminder, one without
        database.habitDao().insert(
            HabitEntity(
                id = "h1",
                name = "With Reminder",
                measurementType = "BOOLEAN",
                targetValue = 1.0,
                unit = null,
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                reminderTime = "07:30",
                displayOrder = 0,
                isPaused = false,
                isArchived = false,
                createdAt = pastMillis,
                updatedAt = pastMillis
            )
        )
        database.habitDao().insert(
            HabitEntity(
                id = "h2",
                name = "No Reminder",
                measurementType = "BOOLEAN",
                targetValue = 1.0,
                unit = null,
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                reminderTime = null,
                displayOrder = 1,
                isPaused = false,
                isArchived = false,
                createdAt = pastMillis,
                updatedAt = pastMillis
            )
        )

        coordinator.reconcileAllReminders()

        assertTrue(scheduledHabits.contains("h1"))
        assertTrue(canceledHabits.contains("h2"))
    }

    @Test
    fun testReconcileAllReminders_permissionRevoked_cancelsAll() = runTest(testDispatcher) {
        database.habitDao().insert(
            HabitEntity(
                id = "h1",
                name = "With Reminder",
                measurementType = "BOOLEAN",
                targetValue = 1.0,
                unit = null,
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                reminderTime = "07:30",
                displayOrder = 0,
                isPaused = false,
                isArchived = false,
                createdAt = pastMillis,
                updatedAt = pastMillis
            )
        )

        notificationsAllowed = false
        coordinator.reconcileAllReminders()

        assertTrue(canceledHabits.contains("h1"))
        assertFalse(scheduledHabits.contains("h1"))
    }

    @Test
    fun testReconcileAllReminders_timezoneChange_recalculatesWithNewZoneId() = runTest(testDispatcher) {
        database.habitDao().insert(
            HabitEntity(
                id = "h_tz",
                name = "Timezone Habit",
                measurementType = "BOOLEAN",
                targetValue = 1.0,
                unit = null,
                scheduleType = "DAILY",
                scheduleConfig = "{}",
                reminderTime = "07:30",
                displayOrder = 0,
                isPaused = false,
                isArchived = false,
                createdAt = pastMillis,
                updatedAt = pastMillis
            )
        )

        val tokyoZone = ZoneId.of("Asia/Tokyo")
        coordinator.reconcileAllReminders(zoneId = tokyoZone)

        assertTrue(scheduledHabits.contains("h_tz"))
        assertEquals(tokyoZone, lastScheduledZoneId)

        val newYorkZone = ZoneId.of("America/New_York")
        coordinator.reconcileAllReminders(zoneId = newYorkZone)

        assertEquals(newYorkZone, lastScheduledZoneId)
    }
}
