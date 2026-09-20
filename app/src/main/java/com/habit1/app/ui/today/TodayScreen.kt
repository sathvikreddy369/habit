package com.habit1.app.ui.today

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit1.app.ui.components.GoalCard
import com.habit1.app.ui.components.GoalEditorDialog
import com.habit1.app.ui.components.HabitCard
import com.habit1.app.ui.components.TodayHeader

/**
 * Primary Today screen composable that renders habits, goals, and daily progress.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onNavigateToHabits: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onEvent(TodayUiEvent.DismissMessage)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {},
                actions = {
                    TextButton(onClick = onNavigateToHistory) {
                        Text(
                            text = "History",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    TextButton(onClick = onNavigateToHabits) {
                        Text(
                            text = "Habits",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onEvent(TodayUiEvent.OpenAddGoalDialog) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Daily Goal"
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // Header: Date & overall progress
            item(key = "header") {
                TodayHeader(
                    formattedDate = uiState.formattedDate,
                    completedCount = uiState.completedHabitsCount + uiState.completedGoalsCount,
                    totalCount = uiState.totalScheduledHabitsCount + uiState.totalGoalsCount,
                    progress = uiState.overallProgress,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            // Scheduled Habits Section
            if (uiState.habits.isNotEmpty()) {
                item(key = "habits_header") {
                    SectionHeader(
                        title = "Habits",
                        countText = "${uiState.completedHabitsCount}/${uiState.totalScheduledHabitsCount}"
                    )
                }

                items(
                    items = uiState.habits,
                    key = { "habit_${it.id}" }
                ) { habitItem ->
                    HabitCard(
                        habitItem = habitItem,
                        onToggle = { viewModel.onEvent(TodayUiEvent.ToggleHabit(habitItem.id)) },
                        onIncrement = { viewModel.onEvent(TodayUiEvent.IncrementHabit(habitItem.id)) },
                        onDecrement = { viewModel.onEvent(TodayUiEvent.DecrementHabit(habitItem.id)) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // Daily Goals Section
            item(key = "goals_header") {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(
                    title = "Daily Goals",
                    countText = "${uiState.completedGoalsCount}/${uiState.totalGoalsCount}",
                    onAddAction = { viewModel.onEvent(TodayUiEvent.OpenAddGoalDialog) }
                )
            }

            if (uiState.goals.isNotEmpty()) {
                items(
                    items = uiState.goals,
                    key = { "goal_${it.id}" }
                ) { goalItem ->
                    GoalCard(
                        goalItem = goalItem,
                        onToggle = { viewModel.onEvent(TodayUiEvent.ToggleGoal(goalItem.id)) },
                        onToggleSubtask = { subtaskId ->
                            viewModel.onEvent(TodayUiEvent.ToggleSubtask(goalItem.id, subtaskId))
                        },
                        onToggleExpanded = { viewModel.onEvent(TodayUiEvent.ToggleGoalExpanded(goalItem.id)) },
                        onEditGoal = { viewModel.onEvent(TodayUiEvent.RequestEditGoal(goalItem)) },
                        onDeleteGoal = { viewModel.onEvent(TodayUiEvent.RequestDeleteGoal(goalItem)) },
                        onMoveGoalTomorrow = {
                            viewModel.onEvent(TodayUiEvent.MoveGoalDate(goalItem.id, uiState.currentDate.plusDays(1)))
                        },
                        onMoveGoalUp = { viewModel.onEvent(TodayUiEvent.MoveGoalUp(goalItem.id)) },
                        onMoveGoalDown = { viewModel.onEvent(TodayUiEvent.MoveGoalDown(goalItem.id)) },
                        onAddSubtask = { title -> viewModel.onEvent(TodayUiEvent.AddSubtask(goalItem.id, title)) },
                        onEditSubtask = { subtask -> viewModel.onEvent(TodayUiEvent.RequestEditSubtask(goalItem.id, subtask)) },
                        onDeleteSubtask = { subtaskId -> viewModel.onEvent(TodayUiEvent.DeleteSubtask(goalItem.id, subtaskId)) },
                        onMoveSubtaskUp = { subtaskId -> viewModel.onEvent(TodayUiEvent.MoveSubtaskUp(goalItem.id, subtaskId)) },
                        onMoveSubtaskDown = { subtaskId -> viewModel.onEvent(TodayUiEvent.MoveSubtaskDown(goalItem.id, subtaskId)) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            } else if (!uiState.isLoading) {
                item(key = "goals_empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No goals planned for today. Tap '+' to set an intentional outcome.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Full Screen Empty State (both habits and goals empty)
            if (uiState.habits.isEmpty() && uiState.goals.isEmpty() && !uiState.isLoading) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Clear Horizon",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "No habits or goals scheduled for today.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.material3.OutlinedButton(
                                onClick = onNavigateToHabits
                            ) {
                                Text("Manage Habits")
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Goal Dialog
    if (uiState.isAddGoalDialogOpen) {
        GoalEditorDialog(
            initialTitle = "",
            initialNotes = null,
            initialDate = uiState.currentDate,
            isEditMode = false,
            onDismiss = { viewModel.onEvent(TodayUiEvent.DismissGoalDialog) },
            onSave = { title, notes, targetDate ->
                viewModel.onEvent(TodayUiEvent.SaveNewGoal(title, notes, targetDate))
            }
        )
    }

    // Edit Goal Dialog
    uiState.goalPendingEdit?.let { goal ->
        GoalEditorDialog(
            initialTitle = goal.title,
            initialNotes = goal.notes,
            initialDate = uiState.currentDate,
            isEditMode = true,
            onDismiss = { viewModel.onEvent(TodayUiEvent.DismissGoalDialog) },
            onSave = { title, notes, _ ->
                viewModel.onEvent(TodayUiEvent.SaveEditedGoal(goal.id, title, notes))
            }
        )
    }

    // Delete Goal Confirmation Dialog
    uiState.goalPendingDeletion?.let { goal ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(TodayUiEvent.CancelDeleteGoal) },
            title = { Text("Delete Goal?") },
            text = {
                val subtaskWarning = if (goal.subtasks.isNotEmpty()) {
                    " This goal contains ${goal.subtasks.size} subtasks which will also be permanently deleted."
                } else ""
                Text("Are you sure you want to delete \"${goal.title}\"?$subtaskWarning This action is irreversible.")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onEvent(TodayUiEvent.ConfirmDeleteGoal) }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.onEvent(TodayUiEvent.CancelDeleteGoal) }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Subtask Dialog
    uiState.subtaskPendingEdit?.let { (goalId, subtask) ->
        var editedTitle by remember { mutableStateOf(subtask.title) }
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(TodayUiEvent.DismissSubtaskEditDialog) },
            title = { Text("Edit Subtask") },
            text = {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    label = { Text("Subtask title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editedTitle.isNotBlank()) {
                            viewModel.onEvent(TodayUiEvent.SaveEditedSubtask(goalId, subtask.id, editedTitle.trim()))
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.onEvent(TodayUiEvent.DismissSubtaskEditDialog) }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    countText: String,
    onAddAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = countText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (onAddAction != null) {
            IconButton(
                onClick = onAddAction,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add $title",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
