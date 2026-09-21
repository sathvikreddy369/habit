package com.habit1.app.data.repository

import android.content.Context
import android.net.Uri
import com.habit1.app.data.backup.BackupExportSummary
import com.habit1.app.data.backup.BackupExporter
import com.habit1.app.data.backup.BackupImporter
import com.habit1.app.data.backup.BackupPreviewInfo
import com.habit1.app.data.backup.BackupRestoreSummary
import com.habit1.app.data.backup.BackupValidationResult
import com.habit1.app.data.backup.BackupValidator
import com.habit1.app.data.backup.RestoreMode
import com.habit1.app.data.backup.model.BackupEnvelopeDto
import com.habit1.app.data.local.db.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream

class BackupRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase,
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BackupRepository {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun exportBackup(uri: Uri): Result<BackupExportSummary> = withContext(ioDispatcher) {
        try {
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Unable to open output stream for URI: $uri"))

            val summary = exporter.exportToStream(outputStream)
            Result.success(summary)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun inspectBackup(uri: Uri): Result<BackupPreviewInfo> = withContext(ioDispatcher) {
        try {
            val envelope = readAndParseEnvelope(uri).getOrElse { return@withContext Result.failure(it) }

            val existingHabitIds = database.habitDao().getAllHabitsList().map { it.id }.toSet()
            val existingGoalIds = database.dailyGoalDao().getAllGoalsList().map { it.id }.toSet()
            val existingRecordKeys = database.habitRecordDao().getAllRecordsList().map {
                Pair(it.habitId, it.date)
            }.toSet()

            when (val result = BackupValidator.validate(envelope, existingHabitIds, existingGoalIds, existingRecordKeys)) {
                is BackupValidationResult.Valid -> Result.success(result.preview)
                is BackupValidationResult.Invalid -> Result.failure(IllegalStateException(result.error.message))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun restoreBackup(uri: Uri, mode: RestoreMode): Result<BackupRestoreSummary> = withContext(ioDispatcher) {
        try {
            val envelope = readAndParseEnvelope(uri).getOrElse { return@withContext Result.failure(it) }

            val existingHabitIds = database.habitDao().getAllHabitsList().map { it.id }.toSet()
            val existingGoalIds = database.dailyGoalDao().getAllGoalsList().map { it.id }.toSet()
            val existingRecordKeys = database.habitRecordDao().getAllRecordsList().map {
                Pair(it.habitId, it.date)
            }.toSet()

            when (val validation = BackupValidator.validate(envelope, existingHabitIds, existingGoalIds, existingRecordKeys)) {
                is BackupValidationResult.Valid -> {
                    val summary = importer.restore(envelope, mode)
                    Result.success(summary)
                }
                is BackupValidationResult.Invalid -> {
                    Result.failure(IllegalStateException(validation.error.message))
                }
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun readAndParseEnvelope(uri: Uri): Result<BackupEnvelopeDto> {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return Result.failure(IllegalStateException("Unable to open input stream for URI: $uri"))

        return try {
            val bytes = readStreamWithLimit(inputStream, BackupValidator.MAX_FILE_SIZE_BYTES)
            val jsonString = String(bytes, Charsets.UTF_8)
            val envelope = jsonParser.decodeFromString<BackupEnvelopeDto>(jsonString)
            Result.success(envelope)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            inputStream.close()
        }
    }

    private fun readStreamWithLimit(inputStream: InputStream, maxBytes: Long): ByteArray {
        val buffer = ByteArray(8192)
        val baos = ByteArrayOutputStream()
        var totalRead = 0L
        var bytesRead: Int

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            totalRead += bytesRead
            if (totalRead > maxBytes) {
                throw IllegalStateException("File size exceeds maximum limit of ${maxBytes / (1024 * 1024)} MB.")
            }
            baos.write(buffer, 0, bytesRead)
        }
        return baos.toByteArray()
    }
}
