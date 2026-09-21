package com.habit1.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.HabitAnalyticsSummary
import com.habit1.app.domain.model.HabitHistoryDay
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.ui.history.HeatmapRangePreset
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Clean, accessible calendar heatmap component native to Habit1.
 * Renders weekly rows (Monday through Sunday) with distinct visual states
 * for completed, partial, missed, rest, paused, pre-creation, and future days.
 */
@Composable
fun HabitHeatmap(
    summary: HabitAnalyticsSummary,
    selectedPreset: HeatmapRangePreset,
    formattedRange: String,
    canNavigateNext: Boolean,
    isCurrentRange: Boolean,
    onSelectPreset: (HeatmapRangePreset) -> Unit,
    onPreviousRange: () -> Unit,
    onNextRange: () -> Unit,
    onResetToToday: () -> Unit,
    onDayClick: (HabitHistoryDay) -> Unit,
    modifier: Modifier = Modifier
) {
    val isQuantitative = summary.habit.measurement !is MeasurementType.BooleanChoice

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title & Preset Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Activity Heatmap",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Range Preset Chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HeatmapRangePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = { onSelectPreset(preset) },
                            label = {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Navigation Bar: Prev / Range Text / Next / Today
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPreviousRange,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous Range",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = formattedRange,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = onNextRange,
                        enabled = canNavigateNext,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next Range",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (!isCurrentRange) {
                    TextButton(
                        onClick = onResetToToday,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Weekday Header Row (Mon to Sun)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { dayLetter ->
                    Text(
                        text = dayLetter,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Days Grid
            val daysMap = summary.dailyBreakdown.associateBy { it.date }
            val range = summary.range
            val firstMonday = range.startDate.minusDays(
                ((range.startDate.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7).toLong()
            )
            val lastSunday = range.endDate.plusDays(
                ((DayOfWeek.SUNDAY.value - range.endDate.dayOfWeek.value + 7) % 7).toLong()
            )

            val totalDays = java.time.temporal.ChronoUnit.DAYS.between(firstMonday, lastSunday) + 1
            val weekCount = (totalDays / 7).toInt()

            val gridModifier = if (weekCount > 6) {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            }

            Column(
                modifier = gridModifier,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                var cursor = firstMonday
                for (w in 0 until weekCount) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (d in 0 until 7) {
                            val currentDate = cursor
                            val historyDay = daysMap[currentDate]

                            if (historyDay != null && currentDate in range) {
                                HeatmapCell(
                                    day = historyDay,
                                    isQuantitative = isQuantitative,
                                    onClick = { onDayClick(historyDay) }
                                )
                            } else {
                                Spacer(modifier = Modifier.size(36.dp))
                            }
                            cursor = cursor.plusDays(1)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Legend
            HeatmapLegend(isQuantitative = isQuantitative)
        }
    }
}

/**
 * Individual heatmap cell with shape, color intensity, and full accessibility semantics.
 */
@Composable
fun HeatmapCell(
    day: HabitHistoryDay,
    isQuantitative: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = day.status
    val accessibilityLabel = buildCellAccessibilityText(day, isQuantitative)

    val (bgColor, textColor, border) = when (status) {
        is CalendarDayStatus.Completed -> {
            if (isQuantitative) {
                val ratio = if (status.targetValue > 0.0) (status.actualValue / status.targetValue).toFloat() else 1.0f
                val alpha = when {
                    ratio >= 1.0f -> 1.0f
                    ratio >= 0.5f -> 0.70f
                    else -> 0.40f
                }
                val hasExceeded = status.actualValue > status.targetValue
                Triple(
                    MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                    if (alpha >= 0.7f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    if (hasExceeded) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                )
            } else {
                Triple(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.onPrimary,
                    null
                )
            }
        }

        is CalendarDayStatus.RecordedIncomplete -> {
            Triple(
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                MaterialTheme.colorScheme.onErrorContainer,
                BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            )
        }

        CalendarDayStatus.ProjectedMissed -> {
            Triple(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            )
        }

        CalendarDayStatus.ProjectedRest -> {
            Triple(
                Color.Transparent,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                null
            )
        }

        CalendarDayStatus.Paused -> {
            Triple(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                null
            )
        }

        CalendarDayStatus.PreCreation -> {
            Triple(
                Color.Transparent,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                null
            )
        }

        CalendarDayStatus.Future -> {
            Triple(
                Color.Transparent,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            )
        }
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .then(
                if (border != null) Modifier.border(border, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = accessibilityLabel },
        contentAlignment = Alignment.Center
    ) {
        if (status is CalendarDayStatus.ProjectedRest) {
            // Neutral dot for rest days
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            )
        } else {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = if (status is CalendarDayStatus.Completed) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Compact legend tailored to Boolean or Quantitative habits.
 */
@Composable
private fun HeatmapLegend(
    isQuantitative: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isQuantitative) {
            LegendItem(label = "Rest", color = Color.Transparent, isRestDot = true)
            LegendItem(label = "Missed", color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), hasBorder = true)
            LegendItem(label = "Partial", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.40f))
            LegendItem(label = "Target", color = MaterialTheme.colorScheme.primary)
        } else {
            LegendItem(label = "Done", color = MaterialTheme.colorScheme.primary)
            LegendItem(label = "Incomplete", color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
            LegendItem(label = "Missed", color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), hasBorder = true)
            LegendItem(label = "Rest", color = Color.Transparent, isRestDot = true)
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    color: Color,
    isRestDot: Boolean = false,
    hasBorder: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
                .then(
                    if (hasBorder) Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        RoundedCornerShape(3.dp)
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isRestDot) {
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Inspection dialog for tapping a heatmap cell.
 */
@Composable
fun HeatmapDayDetailDialog(
    day: HabitHistoryDay,
    habitName: String,
    onDismiss: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
    val formattedDate = day.date.format(formatter)

    val (statusLabel, statusColor, onStatusColor) = when (day.status) {
        is CalendarDayStatus.Completed -> Triple("Completed", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        is CalendarDayStatus.RecordedIncomplete -> Triple("Incomplete", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        CalendarDayStatus.ProjectedMissed -> Triple("Projected Missed", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        CalendarDayStatus.ProjectedRest -> Triple("Rest Day", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outline)
        CalendarDayStatus.Paused -> Triple("Paused", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.outline)
        CalendarDayStatus.PreCreation -> Triple("Pre-Creation", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outline)
        CalendarDayStatus.Future -> Triple("Upcoming", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outline)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = habitName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = onStatusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                when (val status = day.status) {
                    is CalendarDayStatus.Completed -> {
                        val unitStr = status.unit?.let { " $it" } ?: ""
                        if (status.measurementType == MeasurementType.BooleanChoice.TYPE_NAME) {
                            Text(
                                text = "Habit was completed and recorded for this day.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            val ratio = if (status.targetValue > 0.0) (status.actualValue / status.targetValue * 100).toInt() else 100
                            Text(
                                text = "Recorded: ${formatValue(status.actualValue)} / ${formatValue(status.targetValue)}$unitStr ($ratio% of target)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Target was achieved.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    is CalendarDayStatus.RecordedIncomplete -> {
                        val unitStr = status.unit?.let { " $it" } ?: ""
                        val ratio = if (status.targetValue > 0.0) (status.actualValue / status.targetValue * 100).toInt() else 0
                        Text(
                            text = "Recorded: ${formatValue(status.actualValue)} / ${formatValue(status.targetValue)}$unitStr ($ratio% of target)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Factual attempt recorded, but target was not reached.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    CalendarDayStatus.ProjectedMissed -> {
                        Text(
                            text = "Projected missed based on the habit's current schedule. No factual record was logged for this date.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    CalendarDayStatus.ProjectedRest -> {
                        Text(
                            text = "Rest day. This habit was not scheduled on this day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    CalendarDayStatus.Paused -> {
                        Text(
                            text = "The habit was paused on this date. Paused days are not counted as missed and do not break streaks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    CalendarDayStatus.PreCreation -> {
                        Text(
                            text = "This date was before the habit was created.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    CalendarDayStatus.Future -> {
                        Text(
                            text = "Upcoming scheduled day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Formats values cleanly (e.g. 50.0 -> "50", 2.5 -> "2.5").
 */
private fun formatValue(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        "%.1f".format(value)
    }
}

/**
 * Builds meaningful accessibility descriptions for TalkBack.
 */
fun buildCellAccessibilityText(day: HabitHistoryDay, isQuantitative: Boolean): String {
    val dateStr = day.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
    return when (val status = day.status) {
        is CalendarDayStatus.Completed -> {
            if (isQuantitative) {
                val unitStr = status.unit?.let { " $it" } ?: ""
                "$dateStr: Completed. ${formatValue(status.actualValue)} of ${formatValue(status.targetValue)}$unitStr."
            } else {
                "$dateStr: Completed."
            }
        }

        is CalendarDayStatus.RecordedIncomplete -> {
            val unitStr = status.unit?.let { " $it" } ?: ""
            "$dateStr: Incomplete. ${formatValue(status.actualValue)} of ${formatValue(status.targetValue)}$unitStr."
        }

        CalendarDayStatus.ProjectedMissed -> "$dateStr: Projected missed. No record logged for scheduled day."
        CalendarDayStatus.ProjectedRest -> "$dateStr: Rest day. Not scheduled."
        CalendarDayStatus.Paused -> "$dateStr: Paused."
        CalendarDayStatus.PreCreation -> "$dateStr: Before habit creation."
        CalendarDayStatus.Future -> "$dateStr: Upcoming."
    }
}
