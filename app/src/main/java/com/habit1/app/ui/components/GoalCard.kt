package com.habit1.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.habit1.app.ui.today.TodayGoalItem
import com.habit1.app.ui.today.TodaySubtaskItem

/**
 * Reusable card displaying a daily goal and its subtasks on the Today screen.
 * Follows the product principle: "Simple at first glance; powerful when interacted with."
 */
@Composable
fun GoalCard(
    goalItem: TodayGoalItem,
    onToggle: () -> Unit,
    onToggleSubtask: (subtaskId: String) -> Unit,
    onToggleExpanded: () -> Unit = {},
    onEditGoal: () -> Unit = {},
    onDeleteGoal: () -> Unit = {},
    onMoveGoalTomorrow: () -> Unit = {},
    onMoveGoalUp: () -> Unit = {},
    onMoveGoalDown: () -> Unit = {},
    onAddSubtask: (title: String) -> Unit = {},
    onEditSubtask: (subtask: TodaySubtaskItem) -> Unit = {},
    onDeleteSubtask: (subtaskId: String) -> Unit = {},
    onMoveSubtaskUp: (subtaskId: String) -> Unit = {},
    onMoveSubtaskDown: (subtaskId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var goalMenuExpanded by remember { mutableStateOf(false) }
    var isAddingSubtask by remember { mutableStateOf(false) }
    var newSubtaskTitle by remember { mutableStateOf("") }

    val completedSubtasks = goalItem.subtasks.count { it.isCompleted }
    val totalSubtasks = goalItem.subtasks.size

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Main Goal Header Row: [Checkbox] [Title + Progress] [Menu]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = goalItem.isCompleted,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (goalItem.subtasks.isNotEmpty()) onToggleExpanded()
                        }
                ) {
                    Text(
                        text = goalItem.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (goalItem.isCompleted) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (goalItem.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!goalItem.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = goalItem.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (totalSubtasks > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$completedSubtasks/$totalSubtasks subtasks",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = if (goalItem.isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (goalItem.isExpanded) "Collapse subtasks" else "Expand subtasks",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Goal Overflow Menu
                IconButton(
                    onClick = { goalMenuExpanded = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Goal options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = goalMenuExpanded,
                    onDismissRequest = { goalMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Goal") },
                        onClick = {
                            goalMenuExpanded = false
                            onEditGoal()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Move to Tomorrow") },
                        onClick = {
                            goalMenuExpanded = false
                            onMoveGoalTomorrow()
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) }
                    )
                    if (goalItem.canMoveUp) {
                        DropdownMenuItem(
                            text = { Text("Move Up") },
                            onClick = {
                                goalMenuExpanded = false
                                onMoveGoalUp()
                            },
                            leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null) }
                        )
                    }
                    if (goalItem.canMoveDown) {
                        DropdownMenuItem(
                            text = { Text("Move Down") },
                            onClick = {
                                goalMenuExpanded = false
                                onMoveGoalDown()
                            },
                            leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text("Delete Goal", color = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            goalMenuExpanded = false
                            onDeleteGoal()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }

            // Expanded Subtasks Section
            AnimatedVisibility(visible = goalItem.isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 36.dp, top = 8.dp)
                ) {
                    // Subtasks items
                    goalItem.subtasks.forEach { subtask ->
                        SubtaskRow(
                            subtask = subtask,
                            onToggle = { onToggleSubtask(subtask.id) },
                            onEdit = { onEditSubtask(subtask) },
                            onDelete = { onDeleteSubtask(subtask.id) },
                            onMoveUp = { onMoveSubtaskUp(subtask.id) },
                            onMoveDown = { onMoveSubtaskDown(subtask.id) }
                        )
                    }

                    // Add Subtask Row / Button
                    if (isAddingSubtask) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newSubtaskTitle,
                                onValueChange = { newSubtaskTitle = it },
                                placeholder = { Text("Subtask title...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    if (newSubtaskTitle.isNotBlank()) {
                                        onAddSubtask(newSubtaskTitle.trim())
                                        newSubtaskTitle = ""
                                        isAddingSubtask = false
                                    }
                                }
                            ) {
                                Text("Add")
                            }
                            TextButton(
                                onClick = {
                                    newSubtaskTitle = ""
                                    isAddingSubtask = false
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isAddingSubtask = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add subtask",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add subtask",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtaskRow(
    subtask: TodaySubtaskItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = subtask.isCompleted,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = subtask.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (subtask.isCompleted) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Subtask options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    menuExpanded = false
                    onEdit()
                },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            if (subtask.canMoveUp) {
                DropdownMenuItem(
                    text = { Text("Move Up") },
                    onClick = {
                        menuExpanded = false
                        onMoveUp()
                    },
                    leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
            if (subtask.canMoveDown) {
                DropdownMenuItem(
                    text = { Text("Move Down") },
                    onClick = {
                        menuExpanded = false
                        onMoveDown()
                    },
                    leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}
