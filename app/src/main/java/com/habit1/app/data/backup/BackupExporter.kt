package com.habit1.app.data.backup

import androidx.room.withTransaction
import com.habit1.app.data.backup.crypto.BackupChecksumCalculator
import com.habit1.app.data.backup.model.BackupEnvelopeDto
import com.habit1.app.data.backup.model.BackupPayloadDto
import com.habit1.app.data.backup.model.toBackupDto
import com.habit1.app.data.local.db.AppDatabase
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Summary of an export operation.
 */
data class BackupExportSummary(
    val formatVersion: Int,
    val exportedAt: String,
    val habitsCount: Int,
    val recordsCount: Int,
    val goalsCount: Int,
    val subtasksCount: Int,
    val reviewsCount: Int,
    val bytesWritten: Long,
    val aggregatesCount: Int = 0
)

/**
 * Captures a consistent database read snapshot and streams a versioned, checksummed
 * JSON backup to the provided OutputStream.
 */
class BackupExporter(
    private val database: AppDatabase,
    private val appVersion: String = "1.0.0"
) {
    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Snapshots the database inside a single Room transaction, canonicalizes, checksums,
     * and streams to [outputStream].
     *
     * IMPORTANT: The Room transaction completes immediately after in-memory snapshotting;
     * no slow SAF I/O is held inside the database transaction.
     */
    suspend fun exportToStream(outputStream: OutputStream): BackupExportSummary {
        // 1. Consistent snapshot read
        val payload = database.withTransaction {
            val habits = database.habitDao().getAllHabitsList().map { it.toBackupDto() }
            val records = database.habitRecordDao().getAllRecordsList().map { it.toBackupDto() }
            val goals = database.dailyGoalDao().getAllGoalsList().map { it.toBackupDto() }
            val subtasks = database.dailyGoalDao().getAllSubtasksList().map { it.toBackupDto() }
            val reviews = database.dailyReviewDao().getAllReviews().map { it.toBackupDto() }
            val aggregates = database.dailyGoalDao().getAllAggregates().map { it.toBackupDto() }

            BackupPayloadDto(
                habits = habits,
                records = records,
                goals = goals,
                subtasks = subtasks,
                reviews = reviews,
                aggregates = aggregates
            )
        }

        // 2. Compute SHA-256 Checksum on canonicalized payload
        val checksum = BackupChecksumCalculator.computeChecksum(payload)
        val exportedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

        // 3. Construct Envelope
        val envelope = BackupEnvelopeDto(
            formatVersion = BackupEnvelopeDto.CURRENT_FORMAT_VERSION,
            appVersion = appVersion,
            exportedAt = exportedAt,
            checksum = checksum,
            payload = payload
        )

        // 4. Serialize to output stream (pretty-printed for user inspection)
        val jsonString = prettyJson.encodeToString(BackupEnvelopeDto.serializer(), envelope)
        val bytes = jsonString.toByteArray(Charsets.UTF_8)

        outputStream.buffered().use { stream ->
            stream.write(bytes)
            stream.flush()
        }

        return BackupExportSummary(
            formatVersion = envelope.formatVersion,
            exportedAt = exportedAt,
            habitsCount = payload.habits.size,
            recordsCount = payload.records.size,
            goalsCount = payload.goals.size,
            subtasksCount = payload.subtasks.size,
            reviewsCount = payload.reviews.size,
            bytesWritten = bytes.size.toLong(),
            aggregatesCount = payload.aggregates.size
        )
    }
}
