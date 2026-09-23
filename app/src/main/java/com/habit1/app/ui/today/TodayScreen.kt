package com.habit1.app.ui.today

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.habit1.app.ui.components.DailyReviewCard
import com.habit1.app.ui.components.DailyReviewEditorDialog
import com.habit1.app.ui.components.GoalCard
import com.habit1.app.ui.components.GoalEditorDialog
import com.habit1.app.ui.components.HabitCard
import com.habit1.app.ui.components.TodayHeader

import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight

/**
 * Primary Today screen composable that renders habits, goals, and daily progress.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    modifier: Modifier = Modifier,
    onCreateHabit: () -> Unit = {},
    onInspectHabit: (String) -> Unit = {},
    onNavigateToHabits: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateSheet by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onEvent(TodayUiEvent.DismissMessage)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showCreateSheet = true },
                        modifier = Modifier.semantics {
                            contentDescription = "Create Habit or Daily Goal"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Habit or Daily Goal"
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.semantics {
                            contentDescription = "Settings and Data"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
        ) {
            // Header: Goal & habit progress overview
            item(key = "header") {
                TodayHeader(
                    formattedDate = uiState.formattedDate,
                    completedHabitsCount = uiState.completedHabitsCount,
                    totalScheduledHabitsCount = uiState.totalScheduledHabitsCount,
                    habitProgress = uiState.habitProgress,
                    completedGoalsCount = uiState.completedGoalsCount,
                    totalGoalsCount = uiState.totalGoalsCount,
                    goalProgress = uiState.goalProgress,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // 1. Daily Goals Section (Prioritized at top)
            item(key = "goals_header") {
                Spacer(modifier = Modifier.height(4.dp))
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
                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { viewModel.onEvent(TodayUiEvent.OpenAddGoalDialog) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "No goals planned for today",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Add Goal",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 2. Scheduled Habits Section
            item(key = "habits_header") {
                Spacer(modifier = Modifier.height(10.dp))
                SectionHeader(
                    title = "Habits",
                    countText = "${uiState.completedHabitsCount}/${uiState.totalScheduledHabitsCount}"
                )
            }

            if (uiState.habits.isNotEmpty()) {
                items(
                    items = uiState.habits,
                    key = { "habit_${it.id}" }
                ) { habitItem ->
                    HabitCard(
                        habitItem = habitItem,
                        onToggle = { viewModel.onEvent(TodayUiEvent.ToggleHabit(habitItem.id)) },
                        onIncrement = { viewModel.onEvent(TodayUiEvent.IncrementHabit(habitItem.id)) },
                        onDecrement = { viewModel.onEvent(TodayUiEvent.DecrementHabit(habitItem.id)) },
                        onSetValue = { value -> viewModel.onEvent(TodayUiEvent.SetHabitValue(habitItem.id, value)) },
                        onClick = { onInspectHabit(habitItem.id) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            } else if (!uiState.isLoading) {
                item(key = "habits_empty_state") {
                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No habits scheduled for today",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create your first habit or adjust schedules to start tracking today.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            androidx.compose.material3.OutlinedButton(
                                onClick = onNavigateToHabits
                            ) {
                                Text("Manage Habits & Reminders")
                            }
                        }
                    }
                }
            }

            // 3. Daily Reflection Section
            item(key = "daily_reflection") {
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader(
                    title = "Daily Reflection",
                    countText = if (uiState.dailyReview != null) "Recorded" else "Optional"
                )
                Spacer(modifier = Modifier.height(4.dp))
                DailyReviewCard(
                    review = uiState.dailyReview,
                    onAddClick = { viewModel.onEvent(TodayUiEvent.OpenReviewDialog) },
                    onEditClick = { viewModel.onEvent(TodayUiEvent.OpenReviewDialog) },
                    onDeleteClick = { viewModel.onEvent(TodayUiEvent.DeleteReview) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }

    // Create Bottom Sheet (Replaces dropdown for clean modern UX)
    if (showCreateSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showCreateSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = "Create",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showCreateSheet = false
                            onCreateHabit()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "New Habit",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Track daily, weekly, or specific days consistency",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showCreateSheet = false
                            viewModel.onEvent(TodayUiEvent.OpenAddGoalDialog)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Daily Goal",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "A one-day target strictly for today",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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

    // Daily Reflection Editor Dialog
    if (uiState.isReviewDialogOpen) {
        DailyReviewEditorDialog(
            initialNotes = uiState.dailyReview?.notes,
            initialMood = uiState.dailyReview?.mood,
            isEditMode = uiState.dailyReview != null,
            onDismiss = { viewModel.onEvent(TodayUiEvent.DismissReviewDialog) },
            onSave = { notes, mood ->
                viewModel.onEvent(TodayUiEvent.SaveReview(notes, mood))
            },
            onDelete = if (uiState.dailyReview != null) {
                { viewModel.onEvent(TodayUiEvent.DeleteReview) }
            } else null
        )
    }
}


@Composable
private fun SectionHeader(
    title: String,
    countText: String,
    modifier: Modifier = Modifier,
    onAddAction: (() -> Unit)? = null
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
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
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add $title",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
