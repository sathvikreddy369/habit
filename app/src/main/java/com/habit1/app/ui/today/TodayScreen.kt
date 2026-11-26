package com.habit1.app.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit1.app.ui.components.DailyReviewCard
import com.habit1.app.ui.components.DailyReviewEditorDialog
import com.habit1.app.ui.components.GoalCard
import com.habit1.app.ui.components.GoalEditorDialog
import com.habit1.app.ui.theme.HabitColors
import com.habit1.app.ui.theme.HabitIcons
import com.habit1.app.ui.components.HabitActionSheet
import com.habit1.app.ui.components.HabitCard
import com.habit1.app.ui.components.HabitNotesDialog
import com.habit1.app.ui.components.NumericEntryDialog
import com.habit1.app.ui.components.TodayHeader

/**
 * Primary Today screen composable that renders habits, goals, daily progress,
 * 7-day bounded date stepper, past-date context banner, and safe 5-state interaction sheets.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
            val canUndoGoal = uiState.lastMovedGoalId != null
            val canUndoClear = uiState.lastClearedRecordHabitName != null
            val canUndo = canUndoGoal || canUndoClear
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (canUndo) "Undo" else null,
                duration = androidx.compose.material3.SnackbarDuration.Short
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                if (canUndoClear) {
                    viewModel.onEvent(TodayUiEvent.UndoClearRecord)
                } else if (canUndoGoal) {
                    viewModel.onEvent(TodayUiEvent.UndoLastMovedGoal)
                }
            }
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
                        onClick = { viewModel.onEvent(TodayUiEvent.ToggleCategoryGrouping) },
                        modifier = Modifier.semantics {
                            contentDescription = if (uiState.categoryGroupingEnabled) "Disable Category Grouping" else "Enable Category Grouping"
                        }
                    ) {
                        Icon(
                            imageVector = if (uiState.categoryGroupingEnabled) Icons.Default.Folder else Icons.Default.FolderOpen,
                            contentDescription = "Category Grouping",
                            tint = if (uiState.categoryGroupingEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
            // 1. Unified Daily Overview Dashboard (Integrated Stepper + Radial Donut + Remaining count)
            item(key = "header") {
                TodayHeader(
                    formattedDate = uiState.formattedDate,
                    isViewingPastDate = uiState.isViewingPastDate,
                    canStepBackward = uiState.canStepBackward,
                    canStepForward = uiState.canStepForward,
                    onStepBackward = { viewModel.onEvent(TodayUiEvent.StepDateBackward) },
                    onStepForward = { viewModel.onEvent(TodayUiEvent.StepDateForward) },
                    onReturnToToday = { viewModel.onEvent(TodayUiEvent.ReturnToToday) },
                    completedHabitsCount = uiState.completedHabitsCount,
                    totalScheduledHabitsCount = uiState.totalScheduledHabitsCount,
                    habitProgress = uiState.habitProgress,
                    completedGoalsCount = uiState.completedGoalsCount,
                    totalGoalsCount = uiState.totalGoalsCount,
                    goalProgress = uiState.goalProgress,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Top Category Focus Chips (shown when habit count >= 10 and categories exist)
            if (uiState.totalScheduledHabitsCount >= 10 && uiState.categories.isNotEmpty()) {
                item(key = "today_category_focus_chips") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = uiState.selectedCategoryFilter == null,
                            onClick = { viewModel.onEvent(TodayUiEvent.SelectCategoryFilter(null)) },
                            label = { Text("All", style = MaterialTheme.typography.labelSmall) }
                        )
                        uiState.categories.forEach { category ->
                            val isSelected = uiState.selectedCategoryFilter == category.id
                            val vectorIcon = HabitIcons.getVectorIcon(category.icon)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val targetId = if (isSelected) null else category.id
                                    viewModel.onEvent(TodayUiEvent.SelectCategoryFilter(targetId))
                                },
                                leadingIcon = {
                                    if (vectorIcon != null) {
                                        Icon(
                                            imageVector = vectorIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else HabitColors.parseColor(category.colorHex)
                                        )
                                    } else if (HabitIcons.isEmoji(category.icon)) {
                                        Text(category.icon ?: "", fontSize = 12.sp)
                                    }
                                },
                                label = { Text(category.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            // 1. Daily Goals Section (Prioritized at top)
            item(key = "goals_header") {
                Spacer(modifier = Modifier.height(6.dp))
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
                            viewModel.onEvent(TodayUiEvent.RequestMoveGoalTomorrow(goalItem))
                        },
                        onMoveGoalUp = { viewModel.onEvent(TodayUiEvent.MoveGoalUp(goalItem.id)) },
                        onMoveGoalDown = { viewModel.onEvent(TodayUiEvent.MoveGoalDown(goalItem.id)) },
                        onSetReminder = { reminderTime ->
                            viewModel.onEvent(TodayUiEvent.SetGoalReminder(goalItem.id, reminderTime))
                        },
                        onAddSubtask = { title -> viewModel.onEvent(TodayUiEvent.AddSubtask(goalItem.id, title)) },
                        onEditSubtask = { subtask -> viewModel.onEvent(TodayUiEvent.RequestEditSubtask(goalItem.id, subtask)) },
                        onDeleteSubtask = { subtaskId -> viewModel.onEvent(TodayUiEvent.DeleteSubtask(goalItem.id, subtaskId)) },
                        onMoveSubtaskUp = { subtaskId -> viewModel.onEvent(TodayUiEvent.MoveSubtaskUp(goalItem.id, subtaskId)) },
                        onMoveSubtaskDown = { subtaskId -> viewModel.onEvent(TodayUiEvent.MoveSubtaskDown(goalItem.id, subtaskId)) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else if (!uiState.isLoading) {
                item(key = "goals_empty_state") {
                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "No goals planned for this day",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { viewModel.onEvent(TodayUiEvent.OpenAddGoalDialog) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "Add Goal",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
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

            if (uiState.categoryGroupingEnabled && uiState.groupedHabits.isNotEmpty()) {
                uiState.groupedHabits.forEach { group ->
                    item(key = "cat_group_header_${group.categoryId ?: "uncat"}") {
                        TodayCategoryGroupHeader(
                            group = group,
                            onToggleCollapse = { viewModel.onEvent(TodayUiEvent.ToggleCategoryCollapsed(group.categoryId)) }
                        )
                    }

                    if (!group.isCollapsed) {
                        items(
                            items = group.habits,
                            key = { "group_habit_${it.id}" }
                        ) { habitItem ->
                            HabitCard(
                                habitItem = habitItem,
                                onToggle = { viewModel.onEvent(TodayUiEvent.ToggleHabit(habitItem.id)) },
                                onIncrement = { viewModel.onEvent(TodayUiEvent.IncrementHabit(habitItem.id)) },
                                onDecrement = { viewModel.onEvent(TodayUiEvent.DecrementHabit(habitItem.id)) },
                                onSetValue = { value -> viewModel.onEvent(TodayUiEvent.SetHabitValue(habitItem.id, value)) },
                                onMarkMissed = { viewModel.onEvent(TodayUiEvent.MarkMissed(habitItem.id)) },
                                onMarkSkipped = { viewModel.onEvent(TodayUiEvent.MarkSkipped(habitItem.id)) },
                                onCompleteToTarget = { viewModel.onEvent(TodayUiEvent.CompleteToTarget(habitItem.id)) },
                                onClick = { onInspectHabit(habitItem.id) },
                                onOpenActionSheet = { viewModel.onEvent(TodayUiEvent.OpenHabitActionSheet(habitItem)) },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            } else if (uiState.habits.isNotEmpty()) {
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
                        onMarkMissed = { viewModel.onEvent(TodayUiEvent.MarkMissed(habitItem.id)) },
                        onMarkSkipped = { viewModel.onEvent(TodayUiEvent.MarkSkipped(habitItem.id)) },
                        onCompleteToTarget = { viewModel.onEvent(TodayUiEvent.CompleteToTarget(habitItem.id)) },
                        onClick = { onInspectHabit(habitItem.id) },
                        onOpenActionSheet = { viewModel.onEvent(TodayUiEvent.OpenHabitActionSheet(habitItem)) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else if (!uiState.isLoading) {
                item(key = "habits_empty_state") {
                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No habits scheduled for this day",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create habits or adjust schedules to start tracking.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            androidx.compose.material3.OutlinedButton(
                                onClick = onNavigateToHabits
                            ) {
                                Text("Manage Habits & Reminders")
                            }
                        }
                    }
                }
            }

            // 3. Bonus & Rest Day Activity Section (Unscheduled habits on this date)
            if (uiState.unscheduledHabits.isNotEmpty()) {
                item(key = "unscheduled_header") {
                    Spacer(modifier = Modifier.height(14.dp))
                    SectionHeader(
                        title = "Bonus & Rest Day Activity",
                        countText = "${uiState.unscheduledHabits.count { it.isCompleted || it.actualValue > 0.0 }}/${uiState.unscheduledHabits.size}"
                    )
                }

                items(
                    items = uiState.unscheduledHabits,
                    key = { "unscheduled_${it.id}" }
                ) { habitItem ->
                    HabitCard(
                        habitItem = habitItem,
                        onToggle = { viewModel.onEvent(TodayUiEvent.ToggleHabit(habitItem.id)) },
                        onIncrement = { viewModel.onEvent(TodayUiEvent.IncrementHabit(habitItem.id)) },
                        onDecrement = { viewModel.onEvent(TodayUiEvent.DecrementHabit(habitItem.id)) },
                        onSetValue = { value -> viewModel.onEvent(TodayUiEvent.SetHabitValue(habitItem.id, value)) },
                        onMarkMissed = { viewModel.onEvent(TodayUiEvent.MarkMissed(habitItem.id)) },
                        onMarkSkipped = { viewModel.onEvent(TodayUiEvent.MarkSkipped(habitItem.id)) },
                        onCompleteToTarget = { viewModel.onEvent(TodayUiEvent.CompleteToTarget(habitItem.id)) },
                        onClick = { onInspectHabit(habitItem.id) },
                        onOpenActionSheet = { viewModel.onEvent(TodayUiEvent.OpenHabitActionSheet(habitItem)) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // 4. Daily Reflection Section
            item(key = "daily_reflection") {
                Spacer(modifier = Modifier.height(12.dp))
                SectionHeader(
                    title = "Daily Reflection",
                    countText = if (uiState.dailyReview != null) "Recorded" else "Optional"
                )
                Spacer(modifier = Modifier.height(2.dp))
                DailyReviewCard(
                    review = uiState.dailyReview,
                    onAddClick = { viewModel.onEvent(TodayUiEvent.OpenReviewDialog) },
                    onEditClick = { viewModel.onEvent(TodayUiEvent.OpenReviewDialog) },
                    onDeleteClick = { viewModel.onEvent(TodayUiEvent.DeleteReview) },
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }

    // Create Bottom Sheet
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

    // Habit Action Sheet (Decision 1.3, 2.2)
    if (uiState.isActionSheetOpen && uiState.activeActionSheetHabit != null) {
        val habit = uiState.activeActionSheetHabit!!
        HabitActionSheet(
            habitItem = habit,
            dateLabel = uiState.formattedDate,
            onDismiss = { viewModel.onEvent(TodayUiEvent.DismissHabitActionSheet) },
            onCompleteToTarget = { viewModel.onEvent(TodayUiEvent.CompleteToTarget(habit.id)) },
            onOpenNumericEntry = { viewModel.onEvent(TodayUiEvent.OpenNumericDialog(habit)) },
            onMarkSkipped = { viewModel.onEvent(TodayUiEvent.MarkSkipped(habit.id)) },
            onMarkMissed = { viewModel.onEvent(TodayUiEvent.MarkMissed(habit.id)) },
            onClearRecord = { viewModel.onEvent(TodayUiEvent.ClearRecord(habit.id)) },
            onEditNotes = { viewModel.onEvent(TodayUiEvent.OpenNotesDialog(habit)) }
        )
    }

    // Direct Numeric Entry Dialog
    uiState.numericDialogHabit?.let { habit ->
        NumericEntryDialog(
            habitName = habit.name,
            measurementType = habit.measurementType,
            currentValue = habit.actualValue,
            targetValue = habit.targetValue,
            unit = habit.unit,
            onDismiss = { viewModel.onEvent(TodayUiEvent.DismissNumericDialog) },
            onConfirm = { value ->
                viewModel.onEvent(TodayUiEvent.SetHabitValue(habit.id, value))
            }
        )
    }

    // Habit Notes Dialog
    uiState.notesDialogHabit?.let { habit ->
        HabitNotesDialog(
            habitName = habit.name,
            initialNotes = habit.notes,
            onDismiss = { viewModel.onEvent(TodayUiEvent.DismissNotesDialog) },
            onSave = { notes ->
                viewModel.onEvent(TodayUiEvent.SaveNotes(habit.id, notes))
            }
        )
    }

    // Add Goal Dialog
    if (uiState.isAddGoalDialogOpen) {
        GoalEditorDialog(
            initialTitle = "",
            initialNotes = null,
            initialDate = uiState.currentDate,
            initialReminderTime = null,
            isEditMode = false,
            onDismiss = { viewModel.onEvent(TodayUiEvent.DismissGoalDialog) },
            onSave = { title, notes, targetDate, reminderTime ->
                viewModel.onEvent(TodayUiEvent.SaveNewGoal(title, notes, targetDate, reminderTime))
            }
        )
    }

    // Edit Goal Dialog
    uiState.goalPendingEdit?.let { goal ->
        GoalEditorDialog(
            initialTitle = goal.title,
            initialNotes = goal.notes,
            initialDate = uiState.currentDate,
            initialReminderTime = goal.reminderTime,
            isEditMode = true,
            onDismiss = { viewModel.onEvent(TodayUiEvent.DismissGoalDialog) },
            onSave = { title, notes, _, reminderTime ->
                viewModel.onEvent(TodayUiEvent.SaveEditedGoal(goal.id, title, notes, reminderTime))
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

    // Move Goal to Tomorrow Confirmation Dialog
    uiState.goalPendingMoveTomorrow?.let { goal ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(TodayUiEvent.CancelMoveGoalTomorrow) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Move Goal to Tomorrow?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to move \"${goal.title}\" to tomorrow?\n\n" +
                        "• It will move off Today's list and be scheduled for tomorrow.\n" +
                        "• You can view and manage it in the History page under tomorrow's date.\n" +
                        "• If moved by mistake, you can easily move it back to today anytime.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onEvent(TodayUiEvent.ConfirmMoveGoalTomorrow) }
                ) {
                    Text("Move to Tomorrow")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.onEvent(TodayUiEvent.CancelMoveGoalTomorrow) }
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
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
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
                modifier = Modifier.size(36.dp)
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

@Composable
private fun TodayCategoryGroupHeader(
    group: TodayCategoryGroup,
    onToggleCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = HabitColors.parseColor(group.categoryColorHex)
    val vectorIcon = HabitIcons.getVectorIcon(group.categoryIcon)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onToggleCollapse),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(categoryColor.copy(alpha = 0.2f))
                ) {
                    if (vectorIcon != null) {
                        Icon(
                            imageVector = vectorIcon,
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (HabitIcons.isEmoji(group.categoryIcon)) {
                        Text(group.categoryIcon ?: "", fontSize = 14.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = group.categoryName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${group.completedCount}/${group.totalCount}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = if (group.isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (group.isCollapsed) "Expand" else "Collapse",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
