package com.habit1.app.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.ui.components.CompletionTrendGraph
import com.habit1.app.ui.components.HabitFrequencyCard
import com.habit1.app.ui.components.HabitHeatmap
import com.habit1.app.ui.components.HabitHistoryBarChart
import com.habit1.app.ui.components.HabitOverviewCard
import com.habit1.app.ui.components.HabitStreaksCard
import com.habit1.app.ui.components.HeatmapDayDetailDialog
import com.habit1.app.ui.components.QuantitativePerformanceCard
import com.habit1.app.ui.theme.HabitColors

/**
 * Habit Details & Analytics History Screen matching Loop Habit Tracker visual depth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitHistoryScreen(
    viewModel: HabitHistoryViewModel,
    onNavigateBack: () -> Unit,
    onEditHabit: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val habit = uiState.habit
    val summary = uiState.summary
    val analyticsSummary = uiState.analyticsSummary

    val habitColor = HabitColors.parseColor(habit?.colorHex)
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = habit?.name ?: "Habit Details",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (habit != null) {
                        IconButton(
                            onClick = { onEditHabit(habit.id) },
                            modifier = Modifier.semantics {
                                contentDescription = "Edit habit ${habit.name}"
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit"
                            )
                        }

                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options"
                                )
                            }

                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export") },
                                    onClick = {
                                        showOverflowMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Delete",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (habit == null || (summary == null && analyticsSummary == null)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Habit not found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Habit Metadata Header (Question, Frequency, Reminder)
                item(key = "habit_metadata_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        if (!habit.description.isNullOrBlank()) {
                            Text(
                                text = habit.description,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val scheduleText = when (val s = habit.schedule) {
                                is HabitSchedule.Daily -> "Every day"
                                is HabitSchedule.SpecificDays -> "${s.days.size} days a week"
                                is HabitSchedule.Interval -> "Every ${s.everyNDays} days"
                            }
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = scheduleText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (habit.reminderTime != null) {
                                Spacer(modifier = Modifier.width(16.dp))
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = DateTimeUtils.formatTime(habit.reminderTime),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 1. Overview Section (Circular Donut Ring & High-Density Stats)
                item(key = "overview_section") {
                    val scorePercent = analyticsSummary?.completionRate?.toInt()
                        ?: summary?.streakResult?.completionRate?.toInt() ?: 0
                    val dailyDays = analyticsSummary?.dailyBreakdown ?: summary?.historyDays ?: emptyList()
                    val last30 = dailyDays.takeLast(30)
                    val monthCompleted = last30.count { it.status is com.habit1.app.domain.model.CalendarDayStatus.Completed }
                    val monthDelta = if (last30.isNotEmpty()) (monthCompleted * 100) / last30.size else null
                    val totalCompletions = analyticsSummary?.completedDays ?: summary?.completedDaysCount ?: 0

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Overview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = habitColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HabitOverviewCard(
                            scorePercent = scorePercent,
                            monthDelta = monthDelta,
                            yearPercent = scorePercent,
                            totalCompletions = totalCompletions,
                            accentColor = habitColor
                        )
                    }
                }

                // 2. Score Trend Section
                if (analyticsSummary != null) {
                    item(key = "score_trend_section") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Score",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = habitColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            CompletionTrendGraph(
                                summary = analyticsSummary,
                                selectedPreset = uiState.selectedPreset,
                                onDayClick = { viewModel.onEvent(HabitHistoryUiEvent.SelectDay(it)) },
                                accentColor = habitColor
                            )
                        }
                    }
                }

                // 3. History Bar Chart Section
                if (analyticsSummary != null) {
                    item(key = "history_barchart_section") {
                        HabitHistoryBarChart(
                            days = analyticsSummary.dailyBreakdown,
                            accentColor = habitColor
                        )
                    }
                }

                // 4. Calendar Heatmap Matrix Section
                if (analyticsSummary != null) {
                    item(key = "calendar_section") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Calendar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = habitColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            HabitHeatmap(
                                summary = analyticsSummary,
                                selectedPreset = uiState.selectedPreset,
                                formattedRange = uiState.formattedRange,
                                canNavigateNext = uiState.canNavigateNext,
                                isCurrentRange = uiState.isCurrentRange,
                                onSelectPreset = { viewModel.onEvent(HabitHistoryUiEvent.SelectPreset(it)) },
                                onPreviousRange = { viewModel.onEvent(HabitHistoryUiEvent.PreviousRange) },
                                onNextRange = { viewModel.onEvent(HabitHistoryUiEvent.NextRange) },
                                onResetToToday = { viewModel.onEvent(HabitHistoryUiEvent.ResetToToday) },
                                onDayClick = { viewModel.onEvent(HabitHistoryUiEvent.SelectDay(it)) },
                                accentColor = habitColor
                            )
                        }
                    }
                }

                // 5. Best Streaks Section (Horizontal Timeline Bars)
                if (analyticsSummary != null) {
                    item(key = "best_streaks_section") {
                        HabitStreaksCard(
                            currentStreak = analyticsSummary.currentStreak,
                            longestStreak = analyticsSummary.longestStreak,
                            historyDays = analyticsSummary.dailyBreakdown,
                            accentColor = habitColor
                        )
                    }
                }

                // 6. Weekday Frequency Section (Dot Density)
                if (analyticsSummary != null) {
                    item(key = "frequency_section") {
                        HabitFrequencyCard(
                            historyDays = analyticsSummary.dailyBreakdown,
                            accentColor = habitColor
                        )
                    }
                }

                // 7. Quantitative Performance Section (if applicable)
                if (habit.measurement !is MeasurementType.BooleanChoice && analyticsSummary != null) {
                    item(key = "quantitative_performance") {
                        QuantitativePerformanceCard(
                            habit = habit,
                            summary = analyticsSummary,
                            selectedPreset = uiState.selectedPreset
                        )
                    }
                }

                // 8. Consistency Breakdown Details
                item(key = "consistency_summary") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Consistency Breakdown (${uiState.selectedPreset.label})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (analyticsSummary != null) {
                                StatRow(label = "Completion Rate", value = "${analyticsSummary.completionRate.toInt()}%")
                                StatRow(label = "Completed Days", value = "${analyticsSummary.completedDays}")
                                StatRow(label = "Scheduled Days", value = "${analyticsSummary.scheduledDays}")
                                StatRow(label = "Recorded Incomplete", value = "${analyticsSummary.recordedIncompleteDays}")
                                StatRow(label = "Projected Missed", value = "${analyticsSummary.missedDays}")
                                StatRow(label = "Projected Rest", value = "${analyticsSummary.restDays}")
                            } else if (summary != null) {
                                StatRow(label = "Completion Rate", value = "${summary.streakResult.completionRate.toInt()}%")
                                StatRow(label = "Completed Days", value = "${summary.completedDaysCount}")
                                StatRow(label = "Scheduled Days", value = "${summary.streakResult.totalScheduledDays}")
                                StatRow(label = "Recorded Incomplete", value = "${summary.recordedIncompleteDaysCount}")
                                StatRow(label = "Projected Missed", value = "${summary.projectedMissedDaysCount}")
                                StatRow(label = "Projected Rest", value = "${summary.projectedRestDaysCount}")
                            }
                        }
                    }
                }

                // 9. Chronological Recorded History Header
                item(key = "records_header") {
                    Text(
                        text = "Historical Activity Log (${uiState.records.size} in range)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // 10. Historical Record Items
                if (uiState.records.isEmpty()) {
                    item(key = "empty_records") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Text(
                                text = "No records logged in this date range.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(uiState.records, key = { "record_${it.id}" }) { record ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = record.date,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Recorded: ${formatStatValue(record.actualValue)} / Target: ${formatStatValue(record.targetValue)} ${record.unit ?: ""}".trim(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!record.notes.isNullOrBlank()) {
                                        Text(
                                            text = record.notes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (record.isCompleted) habitColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = if (record.isCompleted) "Completed" else "Incomplete",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (record.isCompleted) habitColor else MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 11. Explanatory Note
                item(key = "schedule_limitation_note") {
                    Text(
                        text = "Note: Schedule expectations for unrecorded past dates are projected based on the habit's current schedule. Recorded historical completions and snapshots are immutable truth.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }

    // Day Detail Dialog
    uiState.selectedDayDetail?.let { day ->
        HeatmapDayDetailDialog(
            day = day,
            habitName = habit?.name ?: "Habit",
            onDismiss = { viewModel.onEvent(HabitHistoryUiEvent.DismissDayDetail) }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog && habit != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Delete Habit?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Delete '${habit.name}'?\n\nThis permanently removes the habit and all of its recorded history. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.onEvent(HabitHistoryUiEvent.DeleteHabit)
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatStatValue(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        "%.1f".format(value)
    }
}
