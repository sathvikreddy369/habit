package com.habit1.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.backup.BackupExportSummary
import com.habit1.app.data.backup.BackupPreviewInfo
import com.habit1.app.data.backup.BackupRestoreSummary
import com.habit1.app.data.backup.RestoreMode
import com.habit1.app.data.local.preferences.UserPreferencesDataStore
import com.habit1.app.data.repository.BackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
    private lateinit var userPreferences: UserPreferencesDataStore
    private lateinit var fakeBackupRepo: FakeBackupRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        userPreferences = UserPreferencesDataStore(context)
        fakeBackupRepo = FakeBackupRepository()
        viewModel = SettingsViewModel(fakeBackupRepo, userPreferences)
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
    }
}
