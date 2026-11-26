package com.habit1.app.platform.security

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.local.preferences.UserPreferences
import com.habit1.app.data.local.preferences.UserPreferencesDataStore
import com.habit1.app.domain.model.AppLockTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AppLockManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var userPreferences: FakeUserPreferencesDataStore
    private var simulatedTimeMs = 1_000_000L

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        userPreferences = FakeUserPreferencesDataStore()
        simulatedTimeMs = 1_000_000L
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createManager(): AppLockManagerImpl {
        return AppLockManagerImpl(
            userPreferences = userPreferences,
            clock = { simulatedTimeMs },
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun testInitialStateDisabled() = runTest {
        val manager = createManager()

        assertFalse("App lock should be disabled by default", manager.isAppLockEnabled.value)
        assertFalse("App should not be locked when disabled", manager.isLocked.value)
        assertEquals(AppLockTimeout.IMMEDIATELY, manager.lockTimeout.value)
    }

    @Test
    fun testColdStartWithAppLockEnabledStartsLocked() = runTest {
        userPreferences.updateAppLockEnabled(true)

        val manager = createManager()

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager.onActivityStarted(activity)

        assertTrue(manager.isAppLockEnabled.value)
        assertTrue("Cold start with app lock enabled must start in locked state", manager.isLocked.value)
    }

    @Test
    fun testUnlockAndExplicitLock() = runTest {
        userPreferences.updateAppLockEnabled(true)

        val manager = createManager()

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager.onActivityStarted(activity)
        assertTrue(manager.isLocked.value)

        manager.unlock()
        assertFalse(manager.isLocked.value)

        manager.lock()
        assertTrue(manager.isLocked.value)
    }

    @Test
    fun testImmediatelyTimeoutLocksOnBackground() = runTest {
        userPreferences.updateAppLockEnabled(true)
        userPreferences.updateAppLockTimeout(AppLockTimeout.IMMEDIATELY.name)

        val manager = createManager()

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager.onActivityStarted(activity)
        manager.unlock()
        assertFalse(manager.isLocked.value)

        // Activity stops and leaves foreground
        manager.onActivityStopped(activity)
        assertTrue("Immediately timeout should lock when activity stops", manager.isLocked.value)
    }

    @Test
    fun testOneMinuteTimeout() = runTest {
        userPreferences.updateAppLockEnabled(true)
        userPreferences.updateAppLockTimeout(AppLockTimeout.ONE_MINUTE.name)

        val manager = createManager()

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager.onActivityStarted(activity)
        manager.unlock()
        assertFalse(manager.isLocked.value)

        // Backgrounded
        manager.onActivityStopped(activity)
        assertFalse("Should not lock immediately when 1-minute timeout is set", manager.isLocked.value)

        // Return after 30 seconds -> remain unlocked
        simulatedTimeMs += 30_000L
        manager.onActivityStarted(activity)
        assertFalse("Returning within 30s should remain unlocked", manager.isLocked.value)

        // Background again
        manager.onActivityStopped(activity)

        // Return after 61 seconds -> should lock
        simulatedTimeMs += 61_000L
        manager.onActivityStarted(activity)
        assertTrue("Returning after 61s with 1-min timeout should lock", manager.isLocked.value)
    }

    @Test
    fun testFiveMinutesTimeout() = runTest {
        userPreferences.updateAppLockEnabled(true)
        userPreferences.updateAppLockTimeout(AppLockTimeout.FIVE_MINUTES.name)

        val manager = createManager()

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager.onActivityStarted(activity)
        manager.unlock()
        assertFalse(manager.isLocked.value)

        // Backgrounded
        manager.onActivityStopped(activity)

        // Return after 4 minutes -> remain unlocked
        simulatedTimeMs += 240_000L
        manager.onActivityStarted(activity)
        assertFalse("Returning within 4 minutes should remain unlocked", manager.isLocked.value)

        // Background again
        manager.onActivityStopped(activity)

        // Return after 5 minutes 1 second -> should lock
        simulatedTimeMs += 301_000L
        manager.onActivityStarted(activity)
        assertTrue("Returning after 5m 1s with 5-min timeout should lock", manager.isLocked.value)
    }

    @Test
    fun testScreenOffTimeout() = runTest {
        userPreferences.updateAppLockEnabled(true)
        userPreferences.updateAppLockTimeout(AppLockTimeout.SCREEN_OFF.name)

        val manager = createManager()

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager.onActivityStarted(activity)
        manager.unlock()
        assertFalse(manager.isLocked.value)

        // Screen turns off
        manager.onScreenOff()
        assertTrue("Turning screen off should lock when SCREEN_OFF timeout is active", manager.isLocked.value)
    }

    @Test
    fun testConfigurationChangeDoesNotTriggerLock() = runTest {
        userPreferences.updateAppLockEnabled(true)
        userPreferences.updateAppLockTimeout(AppLockTimeout.IMMEDIATELY.name)

        val manager = createManager()

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager.onActivityStarted(activity)
        manager.unlock()
        assertFalse(manager.isLocked.value)

        // Simulate activity recreating due to rotation / configuration change
        // Activity.isChangingConfigurations is true during rotation recreation
        val rotatingActivity = object : Activity() {
            override fun isChangingConfigurations(): Boolean = true
        }

        manager.onActivityStopped(rotatingActivity)
        assertFalse("Configuration change must not lock the app", manager.isLocked.value)
    }

    @Test
    fun testInAppNavigationDoesNotTriggerLock() = runTest {
        userPreferences.updateAppLockEnabled(true)
        userPreferences.updateAppLockTimeout(AppLockTimeout.IMMEDIATELY.name)

        val manager = createManager()

        val activity1 = Robolectric.buildActivity(Activity::class.java).setup().get()
        val activity2 = Robolectric.buildActivity(Activity::class.java).setup().get()

        manager.onActivityStarted(activity1)
        manager.unlock()
        assertFalse(manager.isLocked.value)

        // In-app navigation: Activity 2 starts BEFORE Activity 1 stops
        manager.onActivityStarted(activity2)
        manager.onActivityStopped(activity1)

        assertFalse("In-app navigation must never lock the app", manager.isLocked.value)
    }

    @Test
    fun testDisablingAppLockUnlocksImmediately() = runTest {
        userPreferences.updateAppLockEnabled(true)

        val manager = createManager()

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        manager.onActivityStarted(activity)
        assertTrue(manager.isLocked.value)

        userPreferences.updateAppLockEnabled(false)

        assertFalse("Disabling app lock in preferences must immediately unlock", manager.isLocked.value)
        assertFalse(manager.isAppLockEnabled.value)
    }

    private class FakeUserPreferencesDataStore(
        initialPreferences: UserPreferences = UserPreferences()
    ) : UserPreferencesDataStore(ApplicationProvider.getApplicationContext<Context>()) {
        private val _prefs = MutableStateFlow(initialPreferences)
        override val userPreferencesFlow: Flow<UserPreferences> = _prefs

        override suspend fun updateThemeMode(themeMode: String) {
            _prefs.update { it.copy(themeMode = themeMode) }
        }

        override suspend fun updateAppLockEnabled(enabled: Boolean) {
            _prefs.update { it.copy(appLockEnabled = enabled) }
        }

        override suspend fun updateAppLockTimeout(timeout: String) {
            _prefs.update { it.copy(appLockTimeout = timeout) }
        }

        override suspend fun updateDailyReviewSettings(enabled: Boolean, time: String) {
            _prefs.update { it.copy(dailyReviewEnabled = enabled, dailyReviewTime = time) }
        }

        override suspend fun updateCategoryGroupingEnabled(enabled: Boolean) {
            _prefs.update { it.copy(categoryGroupingEnabled = enabled) }
        }

        override suspend fun clear() {
            _prefs.value = UserPreferences()
        }
    }
}
