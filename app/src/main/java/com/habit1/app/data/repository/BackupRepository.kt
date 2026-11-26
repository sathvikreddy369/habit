package com.habit1.app.data.repository

import android.net.Uri
import com.habit1.app.data.backup.BackupExportSummary
import com.habit1.app.data.backup.BackupPreviewInfo
import com.habit1.app.data.backup.BackupRestoreSummary
import com.habit1.app.data.backup.RestoreMode

interface BackupRepository {
    /**
     * Exports a full database snapshot into the user-selected SAF URI.
     */
    suspend fun exportBackup(uri: Uri): Result<BackupExportSummary>

    /**
     * Reads, parses, and comprehensively validates a backup file from the SAF URI without mutating
     * the database. Returns preview information for user confirmation.
     */
    suspend fun inspectBackup(uri: Uri): Result<BackupPreviewInfo>

    /**
     * Restores a previously validated backup file from the SAF URI using the selected mode.
     */
    suspend fun restoreBackup(uri: Uri, mode: RestoreMode): Result<BackupRestoreSummary>

    /**
     * Restores a pre-validated in-memory backup envelope using the selected mode without re-opening SAF streams.
     */
    suspend fun restoreBackup(envelope: com.habit1.app.data.backup.model.BackupEnvelopeDto, mode: RestoreMode): Result<BackupRestoreSummary>
}
