package com.habit1.app.platform.security

import android.app.Activity
import android.os.SystemClock
import com.habit1.app.data.local.preferences.UserPreferencesDataStore
import com.habit1.app.domain.model.AppLockTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Controller managing lifecycle-aware app lock state, timeout calculation,
 * and lock barrier enforcement (Decision 7.1).
 */
interface AppLockManager {
    /**
     * Observable state indicating whether the application content is currently locked behind the unlock barrier.
     */
    val isLocked: StateFlow<Boolean>

    /**
     * Observable state indicating whether app lock is enabled in user settings.
     */
    val isAppLockEnabled: StateFlow<Boolean>

    /**
     * Observable state indicating the currently configured timeout duration.
     */
    val lockTimeout: StateFlow<AppLockTimeout>

    /**
     * Unlocks the app after successful biometric or device credential authentication.
     */
    fun unlock()

    /**
     * Explicitly forces the app into locked state.
     */
    fun lock()

    /**
     * Called when an activity starts (enters foreground).
     */
    fun onActivityStarted(activity: Activity)

    /**
     * Called when an activity stops (leaves foreground).
     */
    fun onActivityStopped(activity: Activity)

    /**
     * Called when the device screen turns off.
     */
    fun onScreenOff()
}

/**
 * Production implementation of [AppLockManager].
 */
class AppLockManagerImpl(
    private val userPreferences: UserPreferencesDataStore,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : AppLockManager {

    private val _isLocked = MutableStateFlow(false)
    override val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isAppLockEnabled = MutableStateFlow(false)
    override val isAppLockEnabled: StateFlow<Boolean> = _isAppLockEnabled.asStateFlow()

    private val _lockTimeout = MutableStateFlow(AppLockTimeout.IMMEDIATELY)
    override val lockTimeout: StateFlow<AppLockTimeout> = _lockTimeout.asStateFlow()

    private var startedActivityCount = 0
    private var lastBackgroundTimeMs: Long = 0L
    private var screenOffOccurred = false
    private var screenOffTimeMs: Long = 0L
    private var isFirstLaunch = true

    init {
        coroutineScope.launch {
            userPreferences.userPreferencesFlow.collect { prefs ->
                val wasEnabled = _isAppLockEnabled.value
                val nowEnabled = prefs.appLockEnabled
                val timeout = AppLockTimeout.fromName(prefs.appLockTimeout)

                _isAppLockEnabled.value = nowEnabled
                _lockTimeout.value = timeout

                if (!nowEnabled) {
                    _isLocked.value = false
                } else if (isFirstLaunch) {
                    // Cold start with app lock enabled -> lock barrier
                    _isLocked.value = true
                } else if (!wasEnabled && nowEnabled) {
                    // Just enabled - remain unlocked until next background / lock
                }
            }
        }
    }

    override fun unlock() {
        _isLocked.value = false
        lastBackgroundTimeMs = 0L
        screenOffOccurred = false
        screenOffTimeMs = 0L
        isFirstLaunch = false
    }

    override fun lock() {
        if (_isAppLockEnabled.value) {
            _isLocked.value = true
        }
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++

        if (isFirstLaunch) {
            isFirstLaunch = false
            if (_isAppLockEnabled.value) {
                _isLocked.value = true
                return
            }
        }

        if (!_isAppLockEnabled.value) {
            _isLocked.value = false
            return
        }

        if (_isLocked.value) {
            // Already locked, remain locked
            return
        }

        // Evaluate lock based on screen off or background duration
        val now = clock()

        if (screenOffOccurred) {
            when (_lockTimeout.value) {
                AppLockTimeout.SCREEN_OFF,
                AppLockTimeout.IMMEDIATELY -> {
                    _isLocked.value = true
                    screenOffOccurred = false
                    return
                }
                AppLockTimeout.ONE_MINUTE -> {
                    if (now - screenOffTimeMs >= AppLockTimeout.ONE_MINUTE.durationMs) {
                        _isLocked.value = true
                        screenOffOccurred = false
                        return
                    }
                }
                AppLockTimeout.FIVE_MINUTES -> {
                    if (now - screenOffTimeMs >= AppLockTimeout.FIVE_MINUTES.durationMs) {
                        _isLocked.value = true
                        screenOffOccurred = false
                        return
                    }
                }
            }
            screenOffOccurred = false
        }

        if (lastBackgroundTimeMs > 0L) {
            val elapsed = now - lastBackgroundTimeMs
            when (_lockTimeout.value) {
                AppLockTimeout.IMMEDIATELY -> {
                    _isLocked.value = true
                }
                AppLockTimeout.ONE_MINUTE -> {
                    if (elapsed >= AppLockTimeout.ONE_MINUTE.durationMs) {
                        _isLocked.value = true
                    }
                }
                AppLockTimeout.FIVE_MINUTES -> {
                    if (elapsed >= AppLockTimeout.FIVE_MINUTES.durationMs) {
                        _isLocked.value = true
                    }
                }
                AppLockTimeout.SCREEN_OFF -> {
                    // Only locks when screen turned off
                }
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity.isChangingConfigurations) {
            // Configuration change (e.g. rotation): do not count as backgrounding
            startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
            return
        }

        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)

        if (startedActivityCount == 0) {
            // App has gone into the background
            lastBackgroundTimeMs = clock()
            if (_isAppLockEnabled.value && _lockTimeout.value == AppLockTimeout.IMMEDIATELY) {
                _isLocked.value = true
            }
        }
    }

    override fun onScreenOff() {
        if (_isAppLockEnabled.value) {
            screenOffOccurred = true
            screenOffTimeMs = clock()
            if (_lockTimeout.value == AppLockTimeout.SCREEN_OFF || _lockTimeout.value == AppLockTimeout.IMMEDIATELY) {
                _isLocked.value = true
            }
        }
    }
}
