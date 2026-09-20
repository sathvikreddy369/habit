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
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddGoalDialog by remember { mutableStateOf(false) }

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
                onClick = { showAddGoalDialog = true },
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
                    progress = uiState.overallProgress
                )
            }

            // Habits Section
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
            if (uiState.goals.isNotEmpty()) {
                item(key = "goals_header") {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader(
                        title = "Daily Goals",
                        countText = "${uiState.completedGoalsCount}/${uiState.totalGoalsCount}"
                    )
                }

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
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // Empty State
            if (uiState.habits.isEmpty() && uiState.goals.isEmpty() && !uiState.isLoading) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 64.dp),
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

    if (showAddGoalDialog) {
        AddGoalDialog(
            onDismiss = { showAddGoalDialog = false },
            onConfirm = { title ->
                viewModel.onEvent(TodayUiEvent.AddGoal(title))
                showAddGoalDialog = false
            }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    countText: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = countText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AddGoalDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String) -> Unit
) {
    var goalTitle by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Daily Goal",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = "What is an intentional task for today?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = goalTitle,
                    onValueChange = { goalTitle = it },
                    label = { Text("Goal title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (goalTitle.isNotBlank()) {
                        onConfirm(goalTitle)
                    }
                },
                enabled = goalTitle.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
