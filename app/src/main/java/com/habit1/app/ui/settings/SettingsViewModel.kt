package com.habit1.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.habit1.app.data.backup.BackupPreviewInfo
import com.habit1.app.data.backup.RestoreMode
import com.habit1.app.data.repository.BackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isExporting: Boolean = false,
    val isInspecting: Boolean = false,
    val isRestoring: Boolean = false,
    val previewInfo: BackupPreviewInfo? = null,
    val pendingRestoreUri: Uri? = null,
    val exportSuccessMessage: String? = null,
    val restoreSuccessMessage: String? = null,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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
                    errorMessage = null,
                    restoreSuccessMessage = null,
                    previewInfo = null,
                    pendingRestoreUri = null
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
                        errorMessage = error.message ?: "Failed to validate backup file."
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
        private val backupRepository: BackupRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(backupRepository) as T
        }
    }
}
