package com.habit1.app.data.export

import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitPause
import com.habit1.app.domain.model.HabitRecord
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.HabitScheduleVersion
import com.habit1.app.domain.model.MeasurementType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.time.LocalDate

/**
 * Filter configuration for habit data exports.
 */
data class ExportFilter(
    val habitIds: Set<String>? = null, // null means all habits
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val format: ExportFormat = ExportFormat.CSV
)

enum class ExportFormat(val extension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    JSON("json", "application/json")
}

@Serializable
data class HabitExportRecordDto(
    val date: String,
    val status: String,
    val actualValue: Double,
    val targetValue: Double,
    val unit: String? = null,
    val measurementType: String,
    val isCompleted: Boolean,
    val notes: String? = null,
    val recordedAt: String
)

@Serializable
data class HabitExportItemDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val categoryName: String? = null,
    val categoryColorHex: String? = null,
    val colorHex: String? = null,
    val measurementType: String,
    val targetValue: Double,
    val unit: String? = null,
    val scheduleType: String,
    val scheduleDetails: String,
    val startDate: String,
    val isPaused: Boolean,
    val isArchived: Boolean,
    val archivedAt: String? = null,
    val records: List<HabitExportRecordDto>
)

@Serializable
data class HabitExportEnvelopeDto(
    val app: String = "Habit1",
    val exportVersion: Int = 1,
    val exportedAt: String,
    val filterStartDate: String? = null,
    val filterEndDate: String? = null,
    val habitsCount: Int,
    val totalRecordsCount: Int,
    val habits: List<HabitExportItemDto>
)

/**
 * Comprehensive habit data exporter supporting RFC 4180 compliant CSV and structured JSON.
 * Preserves historical records, target snapshots, units, and notes without loss or distortion.
 */
object HabitDataExporter {

    private val jsonSerializer = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Serializes selected habits and records to CSV and writes to [outputStream].
     */
    fun exportToCsv(
        habits: List<Habit>,
        recordsByHabit: Map<String, List<HabitRecord>>,
        categoryNames: Map<String, String> = emptyMap(),
        filter: ExportFilter,
        outputStream: OutputStream
    ): Int {
        val filteredHabits = if (filter.habitIds != null) {
            habits.filter { filter.habitIds.contains(it.id) }
        } else {
            habits
        }

        var exportedRows = 0
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            // CSV Header
            writer.write(
                "Habit ID,Habit Name,Category,Measurement Type,Target,Unit,Schedule,Date,Status,Actual Value,Target Snapshot,Is Completed,Notes,Recorded At"
            )
            writer.newLine()

            filteredHabits.forEach { habit ->
                val allRecords = recordsByHabit[habit.id] ?: emptyList()
                val records = allRecords.filter { r ->
                    (filter.startDate == null || !r.date.isBefore(filter.startDate)) &&
                    (filter.endDate == null || !r.date.isAfter(filter.endDate))
                }.sortedBy { it.date }

                val category = habit.categoryId?.let { categoryNames[it] } ?: ""
                val measurementTypeStr = when (habit.measurement) {
                    is MeasurementType.BooleanChoice -> "BOOLEAN"
                    is MeasurementType.Count -> "COUNT"
                    is MeasurementType.Duration -> "DURATION"
                    is MeasurementType.Quantity -> "QUANTITY"
                }
                val targetStr = when (val m = habit.measurement) {
                    is MeasurementType.BooleanChoice -> "1.0"
                    is MeasurementType.Count -> m.target.toString()
                    is MeasurementType.Duration -> m.targetMinutes.toString()
                    is MeasurementType.Quantity -> m.target.toString()
                }
                val unitStr = when (val m = habit.measurement) {
                    is MeasurementType.BooleanChoice -> ""
                    is MeasurementType.Count -> m.unit ?: ""
                    is MeasurementType.Duration -> "mins"
                    is MeasurementType.Quantity -> m.unit
                }
                val scheduleStr = when (val s = habit.schedule) {
                    is HabitSchedule.Daily -> "Daily"
                    is HabitSchedule.SpecificDays -> "Days: ${s.days.joinToString(";")}"
                    is HabitSchedule.Interval -> "Every ${s.everyNDays} days"
                }

                if (records.isEmpty()) {
                    // Emit metadata line even if no daily records exist in range
                    writer.write(
                        listOf(
                            escapeCsv(habit.id),
                            escapeCsv(habit.name),
                            escapeCsv(category),
                            escapeCsv(measurementTypeStr),
                            escapeCsv(targetStr),
                            escapeCsv(unitStr),
                            escapeCsv(scheduleStr),
                            "", // Date
                            "NO_RECORDS", // Status
                            "", // Actual Value
                            "", // Target Snapshot
                            "", // Is Completed
                            "", // Notes
                            ""  // Recorded At
                        ).joinToString(",")
                    )
                    writer.newLine()
                    exportedRows++
                } else {
                    records.forEach { record ->
                        writer.write(
                            listOf(
                                escapeCsv(habit.id),
                                escapeCsv(habit.name),
                                escapeCsv(category),
                                escapeCsv(measurementTypeStr),
                                escapeCsv(targetStr),
                                escapeCsv(unitStr),
                                escapeCsv(scheduleStr),
                                escapeCsv(record.date.toString()),
                                escapeCsv(record.status.name),
                                escapeCsv(record.actualValue.toString()),
                                escapeCsv(record.targetValue.toString()),
                                escapeCsv(record.isCompleted.toString()),
                                escapeCsv(record.notes ?: ""),
                                escapeCsv(record.recordedAt.toString())
                            ).joinToString(",")
                        )
                        writer.newLine()
                        exportedRows++
                    }
                }
            }
            writer.flush()
        }
        return exportedRows
    }

    /**
     * Serializes selected habits and records to structured JSON and writes to [outputStream].
     */
    fun exportToJson(
        habits: List<Habit>,
        recordsByHabit: Map<String, List<HabitRecord>>,
        categoryNames: Map<String, String> = emptyMap(),
        filter: ExportFilter,
        outputStream: OutputStream
    ): Int {
        val filteredHabits = if (filter.habitIds != null) {
            habits.filter { filter.habitIds.contains(it.id) }
        } else {
            habits
        }

        var totalRecords = 0
        val habitDtos = filteredHabits.map { habit ->
            val allRecords = recordsByHabit[habit.id] ?: emptyList()
            val records = allRecords.filter { r ->
                (filter.startDate == null || !r.date.isBefore(filter.startDate)) &&
                (filter.endDate == null || !r.date.isAfter(filter.endDate))
            }.sortedBy { it.date }

            totalRecords += records.size

            val targetValue = when (val m = habit.measurement) {
                is MeasurementType.BooleanChoice -> 1.0
                is MeasurementType.Count -> m.target.toDouble()
                is MeasurementType.Duration -> m.targetMinutes.toDouble()
                is MeasurementType.Quantity -> m.target
            }
            val unitStr = when (val m = habit.measurement) {
                is MeasurementType.BooleanChoice -> null
                is MeasurementType.Count -> m.unit
                is MeasurementType.Duration -> "mins"
                is MeasurementType.Quantity -> m.unit
            }

            HabitExportItemDto(
                id = habit.id,
                name = habit.name,
                description = habit.description,
                categoryName = habit.categoryId?.let { categoryNames[it] },
                categoryColorHex = null,
                colorHex = habit.colorHex,
                measurementType = habit.measurement.typeName,
                targetValue = targetValue,
                unit = unitStr,
                scheduleType = habit.schedule.scheduleType,
                scheduleDetails = habit.schedule.toString(),
                startDate = habit.startDate.toString(),
                isPaused = habit.isPaused,
                isArchived = habit.isArchived,
                archivedAt = habit.archivedAt?.toString(),
                records = records.map { r ->
                    HabitExportRecordDto(
                        date = r.date.toString(),
                        status = r.status.name,
                        actualValue = r.actualValue,
                        targetValue = r.targetValue,
                        unit = r.unit,
                        measurementType = r.measurementType,
                        isCompleted = r.isCompleted,
                        notes = r.notes,
                        recordedAt = r.recordedAt.toString()
                    )
                }
            )
        }

        val envelope = HabitExportEnvelopeDto(
            exportedAt = java.time.Instant.now().toString(),
            filterStartDate = filter.startDate?.toString(),
            filterEndDate = filter.endDate?.toString(),
            habitsCount = habitDtos.size,
            totalRecordsCount = totalRecords,
            habits = habitDtos
        )

        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            val jsonString = jsonSerializer.encodeToString(HabitExportEnvelopeDto.serializer(), envelope)
            writer.write(jsonString)
            writer.flush()
        }

        return totalRecords
    }

    /**
     * RFC 4180 CSV escaping:
     * If value contains comma, quotes, or newline, enclose in quotes and double internal quotes.
     */
    fun escapeCsv(value: String): String {
        if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }
}
