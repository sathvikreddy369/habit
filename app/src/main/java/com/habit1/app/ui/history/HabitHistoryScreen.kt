package com.habit1.app.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.ui.components.CompletionTrendGraph
import com.habit1.app.ui.components.HabitHeatmap
import com.habit1.app.ui.components.HeatmapDayDetailDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitHistoryScreen(
    viewModel: HabitHistoryViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val habit = uiState.habit
    val summary = uiState.summary
    val analyticsSummary = uiState.analyticsSummary

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(habit?.name ?: "Habit History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
                // 1. Calendar Heatmap
                if (analyticsSummary != null) {
                    item(key = "heatmap") {
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
                            onDayClick = { viewModel.onEvent(HabitHistoryUiEvent.SelectDay(it)) }
                        )
                    }

                    // 2. Completion Trend Graph
                    item(key = "completion_trend") {
                        CompletionTrendGraph(
                            summary = analyticsSummary,
                            selectedPreset = uiState.selectedPreset,
                            onDayClick = { viewModel.onEvent(HabitHistoryUiEvent.SelectDay(it)) }
                        )
                    }
                }

                // 3. Streak Cards Row
                item(key = "streaks") {
                    val currentStreak = analyticsSummary?.currentStreak ?: summary?.streakResult?.currentStreak ?: 0
                    val longestStreak = analyticsSummary?.longestStreak ?: summary?.streakResult?.longestStreak ?: 0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Current Streak",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$currentStreak days",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Longest Streak",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$longestStreak days",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // 3. Consistency Summary Card
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

                // 4. Quantitative Progression Card (if applicable)
                if (habit.measurement !is MeasurementType.BooleanChoice) {
                    item(key = "quantitative_stats") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Quantitative Performance (${uiState.selectedPreset.label})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                if (analyticsSummary?.quantitativeStats != null) {
                                    val qStats = analyticsSummary.quantitativeStats
                                    StatRow(
                                        label = "Total Recorded Volume",
                                        value = formatStatValue(qStats.totalActualValue)
                                    )
                                    qStats.averageOnCompletedDays?.let { avgComp ->
                                        StatRow(
                                            label = "Average on completed days",
                                            value = "%.1f".format(avgComp)
                                        )
                                    }
                                    qStats.averageOnRecordedDays?.let { avgRec ->
                                        StatRow(
                                            label = "Average on recorded days",
                                            value = "%.1f".format(avgRec)
                                        )
                                    }
                                    qStats.successRateOnRecordedDays?.let { succRate ->
                                        StatRow(
                                            label = "Target success on recorded days",
                                            value = "${succRate.toInt()}%"
                                        )
                                    }
                                } else if (summary != null) {
                                    StatRow(
                                        label = "Total Recorded Volume",
                                        value = formatStatValue(summary.totalRecordedVolume)
                                    )
                                    summary.averageOnCompletedDays?.let { avgComp ->
                                        StatRow(
                                            label = "Average on completed days",
                                            value = "%.1f".format(avgComp)
                                        )
                                    }
                                    summary.averageOnRecordedDays?.let { avgRec ->
                                        StatRow(
                                            label = "Average on recorded days",
                                            value = "%.1f".format(avgRec)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Chronological Recorded History Header
                item(key = "records_header") {
                    Text(
                        text = "Historical Activity Log (${uiState.records.size} in range)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // 6. Historical Record Items
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
                                Column {
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
                                    color = if (record.isCompleted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = if (record.isCompleted) "Completed" else "Incomplete",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (record.isCompleted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 7. Explanatory Note
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
