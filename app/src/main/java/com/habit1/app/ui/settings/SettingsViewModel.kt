package com.habit1.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.habit1.app.data.backup.BackupPreviewInfo
import com.habit1.app.data.backup.RestoreMode
import com.habit1.app.data.export.ExportFilter
import com.habit1.app.data.export.ExportFormat
import com.habit1.app.data.export.HabitDataExporter
import com.habit1.app.data.repository.BackupRepository
import com.habit1.app.data.repository.CategoryRepository
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.data.storage.AppStorageUsage
import com.habit1.app.data.storage.StorageCalculator
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.Habit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.data.local.preferences.UserPreferencesDataStore
import com.habit1.app.domain.model.AppLockTimeout
import com.habit1.app.platform.reminder.EveningDigestScheduler
import com.habit1.app.platform.security.AppLockManager
import com.habit1.app.platform.security.BiometricAuthManager
import java.time.LocalDate
import java.time.LocalTime

data class SettingsUiState(
    val themeMode: String = "SYSTEM",
    val eveningDigestEnabled: Boolean = false,
    val eveningDigestTime: String = "21:00",
    val appLockEnabled: Boolean = false,
    val appLockTimeout: AppLockTimeout = AppLockTimeout.IMMEDIATELY,
    val canAuthenticate: Boolean = false,
    val isDeviceSecure: Boolean = false,
    val isExporting: Boolean = false,
    val isInspecting: Boolean = false,
    val isRestoring: Boolean = false,
    val previewInfo: BackupPreviewInfo? = null,
    val pendingRestoreUri: Uri? = null,
    val exportSuccessMessage: String? = null,
    val restoreSuccessMessage: String? = null,
    val errorMessage: String? = null,

    // Storage Usage
    val storageUsage: AppStorageUsage? = null,

    // Data Export Dialog State
    val isExportDialogOpen: Boolean = false,
    val allHabits: List<Habit> = emptyList(),
    val selectedHabitIds: Set<String> = emptySet(),
    val exportStartDate: LocalDate? = null,
    val exportEndDate: LocalDate? = null,
    val exportFormat: ExportFormat = ExportFormat.CSV
)

class SettingsViewModel(
    private val backupRepository: BackupRepository,
    private val userPreferences: UserPreferencesDataStore,
    private val habitRepository: HabitRepository? = null,
    private val habitRecordRepository: HabitRecordRepository? = null,
    private val categoryRepository: CategoryRepository? = null,
    private val eveningDigestScheduler: EveningDigestScheduler? = null,
    private val biometricAuthManager: BiometricAuthManager? = null,
    private val appLockManager: AppLockManager? = null,
    private val appContext: Context? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val canAuth = biometricAuthManager?.canAuthenticate() == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        val isSecure = biometricAuthManager?.isDeviceSecure() ?: false
        viewModelScope.launch {
            userPreferences.userPreferencesFlow.collect { prefs ->
                _uiState.update {
                    it.copy(
                        themeMode = prefs.themeMode,
                        eveningDigestEnabled = prefs.eveningDigestEnabled,
                        eveningDigestTime = prefs.eveningDigestTime,
                        appLockEnabled = prefs.appLockEnabled,
                        appLockTimeout = AppLockTimeout.fromName(prefs.appLockTimeout),
                        canAuthenticate = canAuth,
                        isDeviceSecure = isSecure
                    )
                }
            }
        }
        refreshStorageUsage()
        loadHabitsForExport()
    }

    fun refreshStorageUsage() {
        val context = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val usage = StorageCalculator.calculateStorageUsage(context)
            _uiState.update { it.copy(storageUsage = usage) }
        }
    }

    private fun loadHabitsForExport() {
        val repo = habitRepository ?: return
        viewModelScope.launch {
            val habitEntities = repo.observeAllHabits().first()
            val habits = habitEntities.map { it.toDomain() }
            _uiState.update {
                it.copy(
                    allHabits = habits,
                    selectedHabitIds = habits.map { h -> h.id }.toSet()
                )
            }
        }
    }

    fun openExportDialog(preselectedHabitId: String? = null) {
        val habits = _uiState.value.allHabits
        val initialSelected = if (preselectedHabitId != null) {
            setOf(preselectedHabitId)
        } else {
            habits.map { it.id }.toSet()
        }
        _uiState.update {
            it.copy(
                isExportDialogOpen = true,
                selectedHabitIds = initialSelected,
                exportStartDate = null,
                exportEndDate = null,
                exportFormat = ExportFormat.CSV
            )
        }
    }

    fun dismissExportDialog() {
        _uiState.update { it.copy(isExportDialogOpen = false) }
    }

    fun toggleHabitSelection(habitId: String) {
        val current = _uiState.value.selectedHabitIds
        val next = if (current.contains(habitId)) current - habitId else current + habitId
        _uiState.update { it.copy(selectedHabitIds = next) }
    }

    fun selectAllHabits(selectAll: Boolean) {
        val next = if (selectAll) _uiState.value.allHabits.map { it.id }.toSet() else emptySet()
        _uiState.update { it.copy(selectedHabitIds = next) }
    }

    fun updateExportFormat(format: ExportFormat) {
        _uiState.update { it.copy(exportFormat = format) }
    }

    fun updateExportDateRange(startDate: LocalDate?, endDate: LocalDate?) {
        _uiState.update { it.copy(exportStartDate = startDate, exportEndDate = endDate) }
    }

    fun executeHabitExport(uri: Uri, context: Context) {
        val state = _uiState.value
        val habitRepo = habitRepository ?: return
        val recordRepo = habitRecordRepository ?: return
        val catRepo = categoryRepository

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isExporting = true, isExportDialogOpen = false) }
            try {
                val habits = habitRepo.observeAllHabits().first().map { it.toDomain() }
                val selectedHabits = habits.filter { state.selectedHabitIds.contains(it.id) }
                val recordsEntities = recordRepo.getRecordsForHabits(selectedHabits.map { it.id })
                val recordsByHabit = recordsEntities.map { it.toDomain() }.groupBy { it.habitId }

                val categories = catRepo?.getAllCategories() ?: emptyList()
                val catMap = categories.associate { it.id to it.name }

                val filter = ExportFilter(
                    habitIds = state.selectedHabitIds,
                    startDate = state.exportStartDate,
                    endDate = state.exportEndDate,
                    format = state.exportFormat
                )

                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    when (state.exportFormat) {
                        ExportFormat.CSV -> HabitDataExporter.exportToCsv(
                            habits = selectedHabits,
                            recordsByHabit = recordsByHabit,
                            categoryNames = catMap,
                            filter = filter,
                            outputStream = stream
                        )
                        ExportFormat.JSON -> HabitDataExporter.exportToJson(
                            habits = selectedHabits,
                            recordsByHabit = recordsByHabit,
                            categoryNames = catMap,
                            filter = filter,
                            outputStream = stream
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportSuccessMessage = "Exported ${selectedHabits.size} habits in ${state.exportFormat.name} successfully."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = "Export failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun toggleAppLock(enabled: Boolean) {
        if (enabled) {
            val isSecure = biometricAuthManager?.isDeviceSecure() ?: true
            if (!isSecure) {
                _uiState.update {
                    it.copy(errorMessage = "A screen lock (PIN, pattern, or biometric) must be configured in device settings to enable App Lock.")
                }
                return
            }
        }
        _uiState.update { it.copy(appLockEnabled = enabled) }
        viewModelScope.launch {
            userPreferences.updateAppLockEnabled(enabled)
            if (!enabled) {
                appLockManager?.unlock()
            }
        }
    }

    fun updateAppLockTimeout(timeout: AppLockTimeout) {
        _uiState.update { it.copy(appLockTimeout = timeout) }
        viewModelScope.launch {
            userPreferences.updateAppLockTimeout(timeout.name)
        }
    }

    fun toggleEveningDigest(enabled: Boolean) {
        val currentTime = _uiState.value.eveningDigestTime
        _uiState.update { it.copy(eveningDigestEnabled = enabled) }
        viewModelScope.launch {
            userPreferences.updateEveningDigestSettings(enabled, currentTime)
            if (eveningDigestScheduler != null) {
                if (enabled) {
                    val time = try { LocalTime.parse(currentTime) } catch (_: Exception) { LocalTime.of(21, 0) }
                    eveningDigestScheduler.scheduleDigest(time)
                } else {
                    eveningDigestScheduler.cancelDigest()
                }
            }
        }
    }

    fun updateEveningDigestTime(time: LocalTime) {
        val timeStr = DateTimeUtils.formatTime(time)
        val enabled = _uiState.value.eveningDigestEnabled
        _uiState.update { it.copy(eveningDigestTime = timeStr) }
        viewModelScope.launch {
            userPreferences.updateEveningDigestSettings(enabled, timeStr)
            if (enabled && eveningDigestScheduler != null) {
                eveningDigestScheduler.scheduleDigest(time)
            }
        }
    }

    fun selectThemeMode(mode: String) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch {
            userPreferences.updateThemeMode(mode)
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, errorMessage = null, exportSuccessMessage = null) }
            val result = backupRepository.exportBackup(uri)
            result.onSuccess { summary ->
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportSuccessMessage = "Backup exported successfully (${summary.habitsCount} habits, ${summary.recordsCount} records, ${summary.goalsCount} goals)."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = error.message ?: "Failed to export backup."
                    )
                }
            }
        }
    }

    fun onBackupFileSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isInspecting = true,
                    errorMessage = null
                )
            }
            val result = backupRepository.inspectBackup(uri)
            result.onSuccess { preview ->
                _uiState.update {
                    it.copy(
                        isInspecting = false,
                        previewInfo = preview,
                        pendingRestoreUri = uri
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isInspecting = false,
                        errorMessage = error.message ?: "Invalid or unreadable backup file."
                    )
                }
            }
        }
    }

    fun confirmRestore(mode: RestoreMode) {
        val uri = _uiState.value.pendingRestoreUri ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRestoring = true,
                    previewInfo = null,
                    pendingRestoreUri = null,
                    errorMessage = null
                )
            }
            val result = backupRepository.restoreBackup(uri, mode)
            result.onSuccess { summary ->
                val modeDesc = if (mode == RestoreMode.ReplaceAll) "replaced" else "merged"
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        restoreSuccessMessage = "Data $modeDesc successfully (${summary.habitsRestored} habits, ${summary.recordsRestored} records, ${summary.goalsRestored} goals)."
                    )
                }
                refreshStorageUsage()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        errorMessage = error.message ?: "Restore failed. Your data has not been modified."
                    )
                }
            }
        }
    }

    fun dismissPreviewDialog() {
        _uiState.update {
            it.copy(
                previewInfo = null,
                pendingRestoreUri = null
            )
        }
    }

    fun dismissMessage() {
        _uiState.update {
            it.copy(
                exportSuccessMessage = null,
                restoreSuccessMessage = null,
                errorMessage = null
            )
        }
    }

    class Factory(
        private val backupRepository: BackupRepository,
        private val userPreferences: UserPreferencesDataStore,
        private val habitRepository: HabitRepository? = null,
        private val habitRecordRepository: HabitRecordRepository? = null,
        private val categoryRepository: CategoryRepository? = null,
        private val eveningDigestScheduler: EveningDigestScheduler? = null,
        private val biometricAuthManager: BiometricAuthManager? = null,
        private val appLockManager: AppLockManager? = null,
        private val appContext: Context? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                backupRepository = backupRepository,
                userPreferences = userPreferences,
                habitRepository = habitRepository,
                habitRecordRepository = habitRecordRepository,
                categoryRepository = categoryRepository,
                eveningDigestScheduler = eveningDigestScheduler,
                biometricAuthManager = biometricAuthManager,
                appLockManager = appLockManager,
                appContext = appContext
            ) as T
        }
    }
}
