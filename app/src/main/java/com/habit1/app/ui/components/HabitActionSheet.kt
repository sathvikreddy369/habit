package com.habit1.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habit1.app.domain.model.HabitRecordStatus
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.ui.today.TodayHabitItem

/**
 * Modal bottom sheet for safe date-specific record inspection and editing.
 * Implements non-destructive actions for 5-state transitions, clear record, and notes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitActionSheet(
    habitItem: TodayHabitItem,
    dateLabel: String,
    onDismiss: () -> Unit,
    onCompleteToTarget: () -> Unit,
    onOpenNumericEntry: () -> Unit,
    onMarkSkipped: () -> Unit,
    onMarkMissed: () -> Unit,
    onClearRecord: () -> Unit,
    onEditNotes: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    val habitColor = com.habit1.app.ui.theme.HabitColors.parseColor(habitItem.colorHex)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // Header: Color chip, title, date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(habitColor)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habitItem.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Current Status Badge
                StatusPill(status = habitItem.status)
            }

            // Progress & snapshot details
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Recorded Progress",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = habitItem.formattedProgress,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (habitItem.measurementType !is MeasurementType.BooleanChoice) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Target Snapshot",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val unitStr = habitItem.unit?.let { " $it" } ?: ""
                            Text(
                                text = "${habitItem.targetValue}$unitStr",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // User notes preview
            if (!habitItem.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "“${habitItem.notes}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action: Complete to Target
            if (habitItem.status != HabitRecordStatus.DONE) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = if (habitItem.measurementType is MeasurementType.BooleanChoice) "Mark as Done" else "Complete to Target",
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = habitColor
                        )
                    },
                    modifier = Modifier.clickable { onCompleteToTarget() }
                )
            }

            // Action: Edit Numeric Value (Quantitative only)
            if (habitItem.measurementType !is MeasurementType.BooleanChoice) {
                ListItem(
                    headlineContent = { Text("Set Value Directly", fontWeight = FontWeight.Medium) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable { onOpenNumericEntry() }
                )
            }

            // Action: Mark as Skipped
            if (habitItem.status != HabitRecordStatus.SKIPPED) {
                ListItem(
                    headlineContent = { Text("Mark as Skipped (Excused)", fontWeight = FontWeight.Medium) },
                    supportingContent = { Text("Preserves active streak as neutral bridge") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    },
                    modifier = Modifier.clickable { onMarkSkipped() }
                )
            }

            // Action: Mark as Missed
            if (habitItem.status != HabitRecordStatus.MISSED) {
                ListItem(
                    headlineContent = { Text("Mark as Missed", fontWeight = FontWeight.Medium) },
                    supportingContent = { Text("Acknowledges non-completion and resets streak") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable { onMarkMissed() }
                )
            }

            // Action: Clear Record
            if (habitItem.status != HabitRecordStatus.UNRECORDED) {
                ListItem(
                    headlineContent = { Text("Clear Record", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Deletes record and reverts to unrecorded") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable { onClearRecord() }
                )
            }

            // Action: Add/Edit Notes
            ListItem(
                headlineContent = {
                    Text(
                        text = if (habitItem.notes.isNullOrBlank()) "Add Note" else "Edit Note",
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clickable { onEditNotes() }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun StatusPill(status: HabitRecordStatus, modifier: Modifier = Modifier) {
    val (label, bg, fg) = when (status) {
        HabitRecordStatus.DONE -> Triple(
            "DONE",
            Color(0xFF2E7D32).copy(alpha = 0.15f),
            Color(0xFF2E7D32)
        )
        HabitRecordStatus.PARTIAL -> Triple(
            "PARTIAL",
            Color(0xFFF57C00).copy(alpha = 0.15f),
            Color(0xFFF57C00)
        )
        HabitRecordStatus.SKIPPED -> Triple(
            "SKIPPED",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        HabitRecordStatus.MISSED -> Triple(
            "MISSED",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        HabitRecordStatus.UNRECORDED -> Triple(
            "UNRECORDED",
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.outline
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}
