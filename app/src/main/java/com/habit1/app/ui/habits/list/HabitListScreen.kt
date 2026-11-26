package com.habit1.app.ui.habits.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit1.app.domain.model.Category
import com.habit1.app.ui.components.CategoryPickerDialog
import com.habit1.app.ui.theme.HabitColors
import com.habit1.app.ui.theme.HabitIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitListScreen(
    viewModel: HabitListViewModel,
    onCreateHabit: () -> Unit,
    onEditHabit: (habitId: String) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    onInspectHabit: (habitId: String) -> Unit = {},
    onNavigateToTemplates: () -> Unit = {},
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isSortMenuExpanded by remember { mutableStateOf(false) }
    var isFilterMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Habits", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToTemplates) {
                        Text("Templates", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = onCreateHabit) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Habit")
                    }
                    if (onNavigateToSettings != null) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Thin, sleek Search Field + Status Filter Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onEvent(HabitListUiEvent.UpdateSearchQuery(it)) },
                    placeholder = { Text("Search habits", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onEvent(HabitListUiEvent.UpdateSearchQuery("")) }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )

                // Status Filter Button (All, Active, Paused, Archived in dropdown menu)
                Box {
                    val isFilterActive = uiState.statusFilter != HabitStatusFilter.ALL
                    Surface(
                        onClick = { isFilterMenuExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isFilterActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (isFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter status: ${uiState.statusFilter.label}",
                                tint = if (isFilterActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = isFilterMenuExpanded,
                        onDismissRequest = { isFilterMenuExpanded = false }
                    ) {
                        HabitStatusFilter.entries.forEach { filter ->
                            val isSelected = uiState.statusFilter == filter
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = filter.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    viewModel.onEvent(HabitListUiEvent.SelectStatusFilter(filter))
                                    isFilterMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Category Filter Chips & Category Management
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = uiState.selectedCategoryId == null,
                    onClick = { viewModel.onEvent(HabitListUiEvent.SelectCategoryFilter(null)) },
                    label = { Text("All Categories") }
                )

                uiState.categories.forEach { category ->
                    val isSelected = uiState.selectedCategoryId == category.id
                    val vectorIcon = HabitIcons.getVectorIcon(category.icon)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val targetId = if (isSelected) null else category.id
                            viewModel.onEvent(HabitListUiEvent.SelectCategoryFilter(targetId))
                        },
                        leadingIcon = {
                            if (vectorIcon != null) {
                                Icon(
                                    imageVector = vectorIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else HabitColors.parseColor(category.colorHex)
                                )
                            } else if (HabitIcons.isEmoji(category.icon)) {
                                Text(category.icon ?: "", fontSize = 14.sp)
                            }
                        },
                        label = { Text(category.name) }
                    )
                }

                SuggestionChip(
                    onClick = { viewModel.onEvent(HabitListUiEvent.ToggleManageCategoriesDialog(true)) },
                    label = { Text("Categories...") }
                )
            }

            // Sort Selector Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.filteredHabits.size} habits",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box {
                    OutlinedButton(
                        onClick = { isSortMenuExpanded = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = uiState.sortMode.label,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    DropdownMenu(
                        expanded = isSortMenuExpanded,
                        onDismissRequest = { isSortMenuExpanded = false }
                    ) {
                        HabitSortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                onClick = {
                                    viewModel.onEvent(HabitListUiEvent.SelectSortMode(mode))
                                    isSortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Habits List
            if (uiState.filteredHabits.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) "No Matching Habits" else "No Habits Found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) {
                                "Try modifying your search or filters."
                            } else {
                                "Create a new habit or adjust your active filters."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
                ) {
                    items(
                        items = uiState.filteredHabits,
                        key = { it.id }
                    ) { habit ->
                        HabitManagementCard(
                            habit = habit,
                            isManualSort = uiState.sortMode == HabitSortMode.MANUAL,
                            onInspect = { onInspectHabit(habit.id) },
                            onEdit = { onEditHabit(habit.id) },
                            onTogglePause = {
                                if (habit.isPaused) {
                                    viewModel.onEvent(HabitListUiEvent.ResumeHabit(habit.id))
                                } else {
                                    viewModel.onEvent(HabitListUiEvent.PauseHabit(habit.id))
                                }
                            },
                            onToggleArchive = {
                                if (habit.isArchived) {
                                    viewModel.onEvent(HabitListUiEvent.UnarchiveHabit(habit.id))
                                } else {
                                    viewModel.onEvent(HabitListUiEvent.ArchiveHabit(habit.id))
                                }
                            },
                            onMoveUp = { viewModel.onEvent(HabitListUiEvent.MoveUp(habit.id)) },
                            onMoveDown = { viewModel.onEvent(HabitListUiEvent.MoveDown(habit.id)) },
                            onDelete = { viewModel.onEvent(HabitListUiEvent.RequestDeleteHabit(habit)) },
                            modifier = Modifier.padding(vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }

    // Category Creation / Edit Dialog
    if (uiState.isCategoryEditorOpen) {
        val editing = uiState.editingCategory
        CategoryPickerDialog(
            initialName = editing?.name ?: "",
            initialColorHex = editing?.colorHex,
            initialIcon = editing?.icon,
            isEdit = editing != null,
            onConfirm = { name, colorHex, icon ->
                viewModel.onEvent(HabitListUiEvent.SaveCategory(editing?.id, name, colorHex, icon))
            },
            onDelete = editing?.let { cat ->
                { viewModel.onEvent(HabitListUiEvent.DeleteCategory(cat.id)) }
            },
            onDismiss = { viewModel.onEvent(HabitListUiEvent.DismissCategoryEditor) }
        )
    }

    // Category Management Dialog
    if (uiState.isManageCategoriesDialogOpen) {
        ManageCategoriesDialog(
            categories = uiState.categories,
            onAddCategory = {
                viewModel.onEvent(HabitListUiEvent.OpenCategoryEditor(null))
            },
            onEditCategory = { category ->
                viewModel.onEvent(HabitListUiEvent.OpenCategoryEditor(category))
            },
            onDeleteCategory = { categoryId ->
                viewModel.onEvent(HabitListUiEvent.DeleteCategory(categoryId))
            },
            onDismiss = {
                viewModel.onEvent(HabitListUiEvent.ToggleManageCategoriesDialog(false))
            }
        )
    }

    // Deletion Confirmation Dialog
    uiState.habitPendingDeletion?.let { pendingHabit ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(HabitListUiEvent.CancelDeleteHabit) },
            title = {
                Text(
                    text = "Delete Habit?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${pendingHabit.name}'? This is permanent and removes the habit and all associated historical records.\n\nTo preserve historical data, use 'Archive' instead.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onEvent(HabitListUiEvent.ConfirmDeleteHabit) }
                ) {
                    Text(
                        text = "Delete Permanently",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.onEvent(HabitListUiEvent.CancelDeleteHabit) }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ManageCategoriesDialog(
    categories: List<Category>,
    onAddCategory: () -> Unit,
    onEditCategory: (Category) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var categoryPendingDeletion by remember { mutableStateOf<Category?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Manage Categories", fontWeight = FontWeight.Bold)
                IconButton(onClick = onAddCategory) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Category")
                }
            }
        },
        text = {
            if (categories.isEmpty()) {
                Text(
                    text = "No categories created yet. Tap '+' to create your first category.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    categories.forEach { category ->
                        val vectorIcon = HabitIcons.getVectorIcon(category.icon)
                        val color = HabitColors.parseColor(category.colorHex)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(color.copy(alpha = 0.2f))
                                ) {
                                    if (vectorIcon != null) {
                                        Icon(
                                            imageVector = vectorIcon,
                                            contentDescription = null,
                                            tint = color,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else if (HabitIcons.isEmoji(category.icon)) {
                                        Text(category.icon ?: "", fontSize = 14.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = { onEditCategory(category) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { categoryPendingDeletion = category },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )

    categoryPendingDeletion?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryPendingDeletion = null },
            title = { Text("Delete Category?") },
            text = {
                Text("Deleting '${cat.name}' will set its habits to Uncategorized. No habits or records will be deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCategory(cat.id)
                        categoryPendingDeletion = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryPendingDeletion = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HabitManagementCard(
    habit: HabitListItem,
    isManualSort: Boolean,
    onInspect: () -> Unit,
    onEdit: () -> Unit,
    onTogglePause: () -> Unit,
    onToggleArchive: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val habitColor = HabitColors.parseColor(habit.colorHex)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Habit color bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(habitColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = habit.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!habit.description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = habit.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (habit.isPaused) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text("Paused", style = MaterialTheme.typography.labelSmall) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    labelColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        if (habit.isArchived) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text("Archived", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Metadata row: Category Badge, Measurement & Schedule, Streak & Adherence
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        if (habit.categoryName != null) {
                            val catColor = HabitColors.parseColor(habit.categoryColorHex)
                            val vectorIcon = HabitIcons.getVectorIcon(habit.categoryIcon)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = catColor.copy(alpha = 0.15f),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    if (vectorIcon != null) {
                                        Icon(
                                            imageVector = vectorIcon,
                                            contentDescription = null,
                                            tint = catColor,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                    } else if (HabitIcons.isEmoji(habit.categoryIcon)) {
                                        Text(habit.categoryIcon ?: "", fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(3.dp))
                                    }
                                    Text(
                                        text = habit.categoryName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = catColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${habit.measurementSummary} • ${habit.scheduleSummary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (habit.currentStreak > 0) {
                        Text(
                            text = "🔥 ${habit.currentStreak}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Action row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isManualSort && !habit.isArchived) {
                        // Reordering controls active only in MANUAL mode
                        Row {
                            IconButton(
                                onClick = onMoveUp,
                                enabled = habit.canMoveUp,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move Up",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onMoveDown,
                                enabled = habit.canMoveDown,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move Down",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = onInspect,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("History", style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        if (habit.isArchived) {
                            OutlinedButton(
                                onClick = onToggleArchive,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Restore", style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onTogglePause,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(if (habit.isPaused) "Resume" else "Pause", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        OutlinedButton(
                            onClick = onEdit,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Edit", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
