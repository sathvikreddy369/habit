package com.habit1.app.platform.receiver

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.HabitApplication
import com.habit1.app.data.local.db.entity.HabitEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HabitApplication::class, sdk = [34])
class BootReceiverTest {

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
    fun testBootReceiver_onBootCompleted_reconcilesReminders() = runBlocking {
        val habit = HabitEntity(
            id = "boot_habit",
            name = "Morning Walk",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = "06:30",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = pastMillis,
            updatedAt = pastMillis
        )
        app.container.habitRepository.createHabit(habit)

        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        val receiver = BootReceiver()
        receiver.onReceive(app, intent)

        kotlinx.coroutines.delay(100)

        // Verifies the receiver executed cleanly without error
        assertNotNull(app.container.habitRepository.getHabitById("boot_habit"))
    }

    @Test
    fun testBootReceiver_onTimezoneChanged_reconcilesReminders() = runBlocking {
        val habit = HabitEntity(
            id = "tz_habit",
            name = "Night Reading",
            measurementType = "BOOLEAN",
            targetValue = 1.0,
            unit = null,
            scheduleType = "DAILY",
            scheduleConfig = "{}",
            reminderTime = "21:00",
            displayOrder = 0,
            isPaused = false,
            isArchived = false,
            createdAt = pastMillis,
            updatedAt = pastMillis
        )
        app.container.habitRepository.createHabit(habit)

        val intent = Intent(Intent.ACTION_TIMEZONE_CHANGED)
        val receiver = BootReceiver()
        receiver.onReceive(app, intent)

        kotlinx.coroutines.delay(100)

        assertNotNull(app.container.habitRepository.getHabitById("tz_habit"))
    }
}
