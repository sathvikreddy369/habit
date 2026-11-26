package com.habit1.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.backup.BackupExportSummary
import com.habit1.app.data.backup.BackupPreviewInfo
import com.habit1.app.data.backup.BackupRestoreSummary
import com.habit1.app.data.backup.RestoreMode
import com.habit1.app.data.local.preferences.UserPreferences
import com.habit1.app.data.local.preferences.UserPreferencesDataStore
import com.habit1.app.data.repository.BackupRepository
import com.habit1.app.domain.model.AppLockTimeout
import com.habit1.app.platform.security.BiometricAuthManager
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var userPreferences: FakeUserPreferencesDataStore
    private lateinit var fakeBackupRepo: FakeBackupRepository
    private lateinit var fakeBiometricAuthManager: FakeBiometricAuthManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        userPreferences = FakeUserPreferencesDataStore()
        fakeBackupRepo = FakeBackupRepository()
        fakeBiometricAuthManager = FakeBiometricAuthManager(isSecure = true)
        viewModel = SettingsViewModel(
            backupRepository = fakeBackupRepo,
            userPreferences = userPreferences,
            biometricAuthManager = fakeBiometricAuthManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialThemeMode() = runTest {
        advanceUntilIdle()
        assertEquals("SYSTEM", viewModel.uiState.value.themeMode)
    }

    @Test
    fun testSelectThemeModeDarkAndLight() = runTest {
        advanceUntilIdle()

        viewModel.selectThemeMode("DARK")
        advanceUntilIdle()
        assertEquals("DARK", viewModel.uiState.value.themeMode)

        viewModel.selectThemeMode("LIGHT")
        advanceUntilIdle()
        assertEquals("LIGHT", viewModel.uiState.value.themeMode)

        viewModel.selectThemeMode("SYSTEM")
        advanceUntilIdle()
        assertEquals("SYSTEM", viewModel.uiState.value.themeMode)
    }

    @Test
    fun testAppLockInitialState() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.appLockEnabled)
        assertEquals(AppLockTimeout.IMMEDIATELY, viewModel.uiState.value.appLockTimeout)
        assertTrue(viewModel.uiState.value.isDeviceSecure)
    }

    @Test
    fun testToggleAppLockWhenDeviceSecure() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.appLockEnabled)

        viewModel.toggleAppLock(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.appLockEnabled)

        viewModel.toggleAppLock(false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.appLockEnabled)
    }

    @Test
    fun testToggleAppLockWhenDeviceNotSecureShowsError() = runTest {
        fakeBiometricAuthManager.isSecure = false
        advanceUntilIdle()

        viewModel.toggleAppLock(true)
        advanceUntilIdle()

        assertFalse("App lock should not enable when device is not secure", viewModel.uiState.value.appLockEnabled)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun testUpdateAppLockTimeout() = runTest {
        advanceUntilIdle()
        assertEquals(AppLockTimeout.IMMEDIATELY, viewModel.uiState.value.appLockTimeout)

        viewModel.updateAppLockTimeout(AppLockTimeout.FIVE_MINUTES)
        advanceUntilIdle()
        assertEquals(AppLockTimeout.FIVE_MINUTES, viewModel.uiState.value.appLockTimeout)

        viewModel.updateAppLockTimeout(AppLockTimeout.SCREEN_OFF)
        advanceUntilIdle()
        assertEquals(AppLockTimeout.SCREEN_OFF, viewModel.uiState.value.appLockTimeout)
    }

    private class FakeBiometricAuthManager(
        var canAuth: Boolean = true,
        var isSecure: Boolean = true
    ) : BiometricAuthManager {
        override fun canAuthenticate(): Int =
            if (canAuth) androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
            else androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

        override fun isDeviceSecure(): Boolean = isSecure

        override fun authenticate(
            activity: androidx.fragment.app.FragmentActivity,
            title: String,
            subtitle: String,
            onSuccess: () -> Unit,
            onError: (Int, CharSequence) -> Unit,
            onFailed: () -> Unit
        ) {
            if (canAuth) onSuccess() else onError(1, "Auth error")
        }
    }

    private class FakeBackupRepository : BackupRepository {
        override suspend fun exportBackup(uri: Uri): Result<BackupExportSummary> =
            Result.success(
                BackupExportSummary(
                    formatVersion = 1,
                    exportedAt = "2026-09-20T12:00:00Z",
                    habitsCount = 1,
                    recordsCount = 1,
                    goalsCount = 1,
                    subtasksCount = 0,
                    reviewsCount = 0,
                    bytesWritten = 100L
                )
            )

        override suspend fun inspectBackup(uri: Uri): Result<BackupPreviewInfo> =
            Result.success(
                BackupPreviewInfo(
                    formatVersion = 1,
                    appVersion = "1.0",
                    exportedAt = "2026-09-20",
                    habitsCount = 1,
                    reminderHabitsCount = 0,
                    recordsCount = 1,
                    goalsCount = 1,
                    subtasksCount = 0,
                    reviewsCount = 0
                )
            )

        override suspend fun restoreBackup(uri: Uri, mode: RestoreMode): Result<BackupRestoreSummary> =
            Result.success(
                BackupRestoreSummary(
                    mode = mode,
                    habitsRestored = 1,
                    recordsRestored = 1,
                    goalsRestored = 1,
                    subtasksRestored = 0,
                    reviewsRestored = 0
                )
            )

        override suspend fun restoreBackup(envelope: com.habit1.app.data.backup.model.BackupEnvelopeDto, mode: RestoreMode): Result<BackupRestoreSummary> =
            restoreBackup(Uri.EMPTY, mode)
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
