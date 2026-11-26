package com.habit1.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.habit1.app.data.export.ExportFormat
import com.habit1.app.domain.model.Habit
import java.time.LocalDate

/**
 * Clean, structured dialog allowing users to configure and trigger data export:
 * - Scope: All habits vs individual selected habits
 * - Format: CSV or JSON
 * - Range: All Time vs Last 30 Days, 90 Days, 365 Days
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataExportDialog(
    allHabits: List<Habit>,
    selectedHabitIds: Set<String>,
    exportFormat: ExportFormat,
    onToggleHabit: (String) -> Unit,
    onSelectAllHabits: (Boolean) -> Unit,
    onFormatChange: (ExportFormat) -> Unit,
    onDateRangeChange: (LocalDate?, LocalDate?) -> Unit,
    onConfirmExport: () -> Unit,
    onDismiss: () -> Unit,
    isSingleHabitMode: Boolean = false
) {
    var selectedRangePreset by remember { mutableStateOf("ALL") } // "ALL", "30D", "90D", "365D"
    val today = remember { LocalDate.now() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isSingleHabitMode) "Export Habit Data" else "Export Habit Data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                // 1. Format Selection
                Text(
                    text = "Export Format",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = exportFormat == ExportFormat.CSV,
                        onClick = { onFormatChange(ExportFormat.CSV) },
                        label = { Text("CSV (Spreadsheets)") },
                        leadingIcon = if (exportFormat == ExportFormat.CSV) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = exportFormat == ExportFormat.JSON,
                        onClick = { onFormatChange(ExportFormat.JSON) },
                        label = { Text("JSON (Complete)") },
                        leadingIcon = if (exportFormat == ExportFormat.JSON) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Date Range Filter
                Text(
                    text = "Date Range",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "ALL" to "All Time",
                        "30D" to "Last 30d",
                        "90D" to "Last 90d",
                        "365D" to "Last 1y"
                    ).forEach { (preset, label) ->
                        FilterChip(
                            selected = selectedRangePreset == preset,
                            onClick = {
                                selectedRangePreset = preset
                                when (preset) {
                                    "ALL" -> onDateRangeChange(null, null)
                                    "30D" -> onDateRangeChange(today.minusDays(29), today)
                                    "90D" -> onDateRangeChange(today.minusDays(89), today)
                                    "365D" -> onDateRangeChange(today.minusDays(364), today)
                                }
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                if (isSingleHabitMode) {
                    val habitName = allHabits.firstOrNull()?.name ?: "Habit"
                    Text(
                        text = "Habit: $habitName",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "All recorded history, notes, and targets for this habit will be exported.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // 3. Habit Selection Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Habits (${selectedHabitIds.size} of ${allHabits.size} selected)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val isAllSelected = selectedHabitIds.size == allHabits.size
                        TextButton(
                            onClick = { onSelectAllHabits(!isAllSelected) }
                        ) {
                            Text(if (isAllSelected) "Deselect All" else "Select All", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // 4. Habit List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(allHabits, key = { it.id }) { habit ->
                            val isChecked = selectedHabitIds.contains(habit.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleHabit(habit.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onToggleHabit(habit.id) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = habit.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmExport,
                enabled = selectedHabitIds.isNotEmpty()
            ) {
                Text(if (isSingleHabitMode) "Export" else "Export (${selectedHabitIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
