package com.habit1.app.ui

import android.content.Intent
import android.net.Uri
import com.habit1.app.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityIntentRoutingTest {

    @Test
    fun parseDestination_standardOpenHabitUri_routesToHabitHistory() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("habit1://open_habit/habit-abc-123/2026-09-23")
        }

        val destination = MainActivity.parseDestination(intent)

        assertEquals(Screen.HabitHistory("habit-abc-123"), destination)
    }

    @Test
    fun parseDestination_openHabitUriWithoutDate_routesToHabitHistory() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("habit1://open_habit/habit-def-456")
        }

        val destination = MainActivity.parseDestination(intent)

        assertEquals(Screen.HabitHistory("habit-def-456"), destination)
    }

    @Test
    fun parseDestination_pathStyleOpenHabitUri_routesToHabitHistory() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("habit1:///open_habit/habit-ghi-789/2026-09-23")
        }

        val destination = MainActivity.parseDestination(intent)

        assertEquals(Screen.HabitHistory("habit-ghi-789"), destination)
    }

    @Test
    fun parseDestination_reminderBodyClick_returnsNullForTodayDefault() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("habit1://reminder_open/habit-abc-123/2026-09-23")
        }

        val destination = MainActivity.parseDestination(intent)

        assertNull("Reminder body click should default to Today screen", destination)
    }

    @Test
    fun parseDestination_confirmedClick_returnsNullForTodayDefault() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("habit1://confirmed/habit-abc-123/2026-09-23")
        }

        val destination = MainActivity.parseDestination(intent)

        assertNull(destination)
    }

    @Test
    fun parseDestination_nullIntentOrData_returnsNull() {
        assertNull(MainActivity.parseDestination(null))
        assertNull(MainActivity.parseDestination(Intent()))
    }

    @Test
    fun parseDestination_foreignScheme_returnsNull() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://example.com/open_habit/habit-abc-123")
        }

        assertNull(MainActivity.parseDestination(intent))
    }

    @Test
    fun parseDestination_emptyHabitId_returnsNull() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("habit1://open_habit/")
        }

        assertNull(MainActivity.parseDestination(intent))
    }
}
