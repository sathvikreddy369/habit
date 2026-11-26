package com.habit1.app.ui.habits.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.habit1.app.ui.components.CategoryPickerDialog
import com.habit1.app.ui.components.CATEGORY_SUGGESTIONS
import com.habit1.app.ui.components.ColorPickerDialog
import com.habit1.app.ui.theme.HabitColors
import com.habit1.app.ui.theme.HabitIcons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.domain.validation.HabitValidationError
import com.habit1.app.domain.validation.MeasurementKind
import com.habit1.app.domain.validation.ScheduleKind
import com.habit1.app.ui.components.ReminderTimePickerDialog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitFormScreen(
    viewModel: HabitFormViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val nameFocusRequester = remember { FocusRequester() }
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var prefillCategoryName by remember { mutableStateOf("") }
    var prefillCategoryColor by remember { mutableStateOf<String?>(null) }
    var prefillCategoryIcon by remember { mutableStateOf<String?>(null) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!uiState.isEditMode) {
            nameFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            viewModel.onEvent(HabitFormUiEvent.ResetSaveState)
            onNavigateBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isEditMode) "Edit habit" else "New habit",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
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
                    OutlinedButton(
                        onClick = { viewModel.onEvent(HabitFormUiEvent.SaveHabit) },
                        enabled = !uiState.isSaving && uiState.name.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (!uiState.isSaving && uiState.name.isNotBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "SAVE",
                            fontWeight = FontWeight.Bold,
                            color = if (!uiState.isSaving && uiState.name.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isEditMode) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Changes affect this habit from today onward. Past records will not change.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // First Row: Name and Color swatch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                val nameError = uiState.errors.firstOrNull { it is HabitValidationError.NameBlank || it is HabitValidationError.NameTooLong }
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = { viewModel.onEvent(HabitFormUiEvent.UpdateName(it)) },
                    label = { Text("Name") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = {
                        when (nameError) {
                            is HabitValidationError.NameBlank -> Text("Name cannot be empty")
                            is HabitValidationError.NameTooLong -> Text("Name must be ${nameError.maxLength} characters or less")
                            else -> null
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(nameFocusRequester)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Color Swatch Box
                OutlinedCard(
                    onClick = { showColorPickerDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .width(88.dp)
                        .height(64.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Color",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(HabitColors.parseColor(uiState.colorHex))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Question (description prompt)
            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.onEvent(HabitFormUiEvent.UpdateDescription(it)) },
                label = { Text("Question") },
                placeholder = { Text("e.g. Did you study today?") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Category Section
            Text(
                text = "Category (Optional)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "None" / Uncategorized chip
                FilterChip(
                    selected = uiState.categoryId == null,
                    onClick = { viewModel.onEvent(HabitFormUiEvent.SelectCategory(null)) },
                    label = { Text("None") }
                )

                // User-created categories
                uiState.categories.forEach { category ->
                    val isSelected = uiState.categoryId == category.id
                    val vectorIcon = HabitIcons.getVectorIcon(category.icon)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onEvent(HabitFormUiEvent.SelectCategory(category.id)) },
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

                // "+ New Category" button
                AssistChip(
                    onClick = {
                        prefillCategoryName = ""
                        prefillCategoryColor = null
                        prefillCategoryIcon = null
                        showCategoryDialog = true
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("New Category") }
                )
            }

            // Quick suggestion chips when few categories exist
            if (uiState.categories.size < 4) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Suggestions:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                    CATEGORY_SUGGESTIONS.take(5).forEach { suggestion ->
                        val alreadyExists = uiState.categories.any { it.name.equals(suggestion.name, ignoreCase = true) }
                        if (!alreadyExists) {
                            SuggestionChip(
                                onClick = {
                                    prefillCategoryName = suggestion.name
                                    prefillCategoryColor = suggestion.colorHex
                                    prefillCategoryIcon = suggestion.iconKey
                                    showCategoryDialog = true
                                },
                                label = { Text(suggestion.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Measurement Section
            Text(
                text = "Measurement",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MeasurementKind.entries.forEach { kind ->
                    val label = when (kind) {
                        MeasurementKind.BOOLEAN -> "Done / Not Done"
                        MeasurementKind.COUNT -> "Count"
                        MeasurementKind.DURATION -> "Duration"
                        MeasurementKind.QUANTITY -> "Quantity"
                    }
                    FilterChip(
                        selected = uiState.measurementKind == kind,
                        onClick = { viewModel.onEvent(HabitFormUiEvent.SelectMeasurementKind(kind)) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dynamic measurement fields
            when (uiState.measurementKind) {
                MeasurementKind.BOOLEAN -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Simple Yes / No check each scheduled day.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Examples: Floss teeth, Wake up before 7am, Take vitamins, Journal.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                MeasurementKind.COUNT -> {
                    val targetError = uiState.errors.contains(HabitValidationError.TargetMustBePositive)
                    Column {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = uiState.targetInput,
                                onValueChange = { viewModel.onEvent(HabitFormUiEvent.UpdateTarget(it)) },
                                label = { Text("Daily target count") },
                                placeholder = { Text("e.g. 50") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                singleLine = true,
                                isError = targetError,
                                supportingText = if (targetError) { { Text("Must be a positive number") } } else null,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            OutlinedTextField(
                                value = uiState.unitInput,
                                onValueChange = { viewModel.onEvent(HabitFormUiEvent.UpdateUnit(it)) },
                                label = { Text("Unit") },
                                placeholder = { Text("e.g. pushups") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        // Quick example chips for Count
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Examples:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            listOf(
                                Triple("10", "glasses", "10 glasses water"),
                                Triple("30", "pushups", "30 pushups"),
                                Triple("20", "pages", "20 pages read"),
                                Triple("5", "calls", "5 sales calls")
                            ).forEach { (presetTarget, presetUnit, label) ->
                                SuggestionChip(
                                    onClick = {
                                        viewModel.onEvent(HabitFormUiEvent.UpdateTarget(presetTarget))
                                        viewModel.onEvent(HabitFormUiEvent.UpdateUnit(presetUnit))
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
                MeasurementKind.DURATION -> {
                    val targetError = uiState.errors.contains(HabitValidationError.TargetMustBePositive)
                    Column {
                        OutlinedTextField(
                            value = uiState.targetInput,
                            onValueChange = { viewModel.onEvent(HabitFormUiEvent.UpdateTarget(it)) },
                            label = { Text("Target duration (minutes)") },
                            placeholder = { Text("e.g. 30") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            isError = targetError,
                            supportingText = if (targetError) { { Text("Must be greater than 0 minutes") } } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // Quick example chips for Duration
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Examples:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            listOf(
                                "15" to "15 min Meditation",
                                "30" to "30 min Reading",
                                "45" to "45 min Workout",
                                "60" to "1 hr Deep Work"
                            ).forEach { (presetMins, label) ->
                                SuggestionChip(
                                    onClick = {
                                        viewModel.onEvent(HabitFormUiEvent.UpdateTarget(presetMins))
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
                MeasurementKind.QUANTITY -> {
                    val targetError = uiState.errors.contains(HabitValidationError.TargetMustBePositive)
                    val unitError = uiState.errors.contains(HabitValidationError.UnitRequired)
                    Column {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = uiState.targetInput,
                                onValueChange = { viewModel.onEvent(HabitFormUiEvent.UpdateTarget(it)) },
                                label = { Text("Target quantity") },
                                placeholder = { Text("e.g. 2.5") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Next
                                ),
                                singleLine = true,
                                isError = targetError,
                                supportingText = if (targetError) { { Text("Must be greater than 0") } } else null,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            OutlinedTextField(
                                value = uiState.unitInput,
                                onValueChange = { viewModel.onEvent(HabitFormUiEvent.UpdateUnit(it)) },
                                label = { Text("Unit") },
                                placeholder = { Text("e.g. L, km") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                singleLine = true,
                                isError = unitError,
                                supportingText = if (unitError) { { Text("Unit required") } } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        // Quick example chips for Quantity
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Examples:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            listOf(
                                Triple("2.5", "L", "2.5 L Water"),
                                Triple("5", "km", "5 km Run"),
                                Triple("10000", "steps", "10,000 Steps"),
                                Triple("500", "kcal", "500 kcal Burn")
                            ).forEach { (presetTarget, presetUnit, label) ->
                                SuggestionChip(
                                    onClick = {
                                        viewModel.onEvent(HabitFormUiEvent.UpdateTarget(presetTarget))
                                        viewModel.onEvent(HabitFormUiEvent.UpdateUnit(presetUnit))
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Schedule Section
            Text(
                text = "Schedule",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScheduleKind.entries.forEach { kind ->
                    val label = when (kind) {
                        ScheduleKind.DAILY -> "Daily"
                        ScheduleKind.SPECIFIC_DAYS -> "Selected Days"
                        ScheduleKind.INTERVAL -> "Interval"
                    }
                    FilterChip(
                        selected = uiState.scheduleKind == kind,
                        onClick = { viewModel.onEvent(HabitFormUiEvent.SelectScheduleKind(kind)) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (uiState.scheduleKind) {
                ScheduleKind.DAILY -> {
                    Text(
                        text = "Scheduled every calendar day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ScheduleKind.SPECIFIC_DAYS -> {
                    val daysError = uiState.errors.contains(HabitValidationError.SpecificDaysEmpty)
                    Text(
                        text = "Select the days of the week when this habit is expected:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DayOfWeek.entries.forEach { day ->
                            val isSelected = uiState.selectedDays.contains(day)
                            val shortName = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.onEvent(HabitFormUiEvent.ToggleDay(day)) },
                                label = { Text(shortName) }
                            )
                        }
                    }
                    if (daysError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Please select at least one day of the week",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                ScheduleKind.INTERVAL -> {
                    val intervalError = uiState.errors.any { it is HabitValidationError.IntervalTooSmall }
                    OutlinedTextField(
                        value = uiState.intervalDaysInput,
                        onValueChange = { viewModel.onEvent(HabitFormUiEvent.UpdateIntervalDays(it)) },
                        label = { Text("Every N days") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        isError = intervalError,
                        supportingText = {
                            if (intervalError) {
                                Text("Interval frequency must be at least 2 days")
                            } else {
                                Text("e.g. Every 2 days, Every 3 days")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 1. Optional Reminder Time (Positioned above tracking start date)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reminder",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Get notified",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            val context = LocalContext.current
            val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
            val isNotificationPermissionGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            }

            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { /* Permission result handled */ }

            val currentLocalTime = remember(uiState.reminderTimeInput) {
                if (uiState.reminderTimeInput.isNotBlank()) {
                    try {
                        DateTimeUtils.parseTime(uiState.reminderTimeInput)
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }

            if (currentLocalTime == null) {
                OutlinedCard(
                    onClick = { showTimePickerDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Add reminder",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Get notified",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                Card(
                    onClick = { showTimePickerDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Get notified at",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = DateTimeUtils.format12HourTime(currentLocalTime),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "Daily reminder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showTimePickerDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit reminder time"
                                )
                            }
                            IconButton(onClick = { viewModel.onEvent(HabitFormUiEvent.UpdateReminderTime("")) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear reminder"
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.reminderTimeInput.isNotBlank() && !isNotificationPermissionGranted) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Notifications are currently disabled on this device. Reminders will be scheduled once permission is granted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Notification Permission")
                    }
                }
            }

            if (showTimePickerDialog) {
                ReminderTimePickerDialog(
                    initialTime = currentLocalTime,
                    onConfirm = { time ->
                        viewModel.onEvent(HabitFormUiEvent.UpdateReminderTime(DateTimeUtils.formatTime(time)))
                        showTimePickerDialog = false
                    },
                    onClear = {
                        viewModel.onEvent(HabitFormUiEvent.UpdateReminderTime(""))
                        showTimePickerDialog = false
                    },
                    onDismiss = { showTimePickerDialog = false }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Behavioral Start Date (Decision 2.1 - Positioned below Reminder)
            Text(
                text = "Tracking Start Date",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Dates before this are non-applicable (pre-creation) and excluded from streaks and analytics. Future dates cannot be selected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            val startDateContext = LocalContext.current
            val startDateFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()) }
            OutlinedCard(
                onClick = {
                    android.app.DatePickerDialog(
                        startDateContext,
                        { _, year, month, dayOfMonth ->
                            val picked = LocalDate.of(year, month + 1, dayOfMonth)
                            viewModel.onEvent(HabitFormUiEvent.UpdateStartDate(picked))
                        },
                        uiState.startDate.year,
                        uiState.startDate.monthValue - 1,
                        uiState.startDate.dayOfMonth
                    ).apply {
                        // Strict Future Date Ban (Decision 2.1)
                        datePicker.maxDate = System.currentTimeMillis()
                    }.show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = uiState.startDate.format(startDateFormatter),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val isToday = uiState.startDate == LocalDate.now()
                            Text(
                                text = if (isToday) "Starts today" else "Backfilled to ${uiState.startDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (uiState.isEditMode) {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Habit Management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Pause / Resume Button
                OutlinedButton(
                    onClick = { viewModel.onEvent(HabitFormUiEvent.TogglePauseHabit) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.originalIsPaused) "Resume Habit" else "Pause Habit")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Archive / Unarchive Button
                    OutlinedButton(
                        onClick = { showArchiveDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (uiState.originalIsArchived) "Restore to Active" else "Archive Habit")
                    }

                    // Delete Button
                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Habit")
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }

    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = {
                Text(
                    text = if (uiState.originalIsArchived) "Restore Habit?" else "Archive Habit?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = if (uiState.originalIsArchived) {
                        "Restore '${uiState.name}' to active habits? Reminders will be resumed according to its schedule."
                    } else {
                        "Archive this habit?\n\nIt will pause the habit and preserve all of its history. You can restore it later."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showArchiveDialog = false
                        if (uiState.originalIsArchived) {
                            viewModel.onEvent(HabitFormUiEvent.UnarchiveHabit)
                        } else {
                            viewModel.onEvent(HabitFormUiEvent.ArchiveHabit)
                        }
                    }
                ) {
                    Text(if (uiState.originalIsArchived) "Restore" else "Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteDialog) {
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
                    text = "Delete this habit?\n\nThis permanently deletes the habit and its history. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.onEvent(HabitFormUiEvent.DeleteHabit)
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

    if (showColorPickerDialog) {
        ColorPickerDialog(
            selectedColorHex = uiState.colorHex,
            onColorSelected = { viewModel.onEvent(HabitFormUiEvent.UpdateColor(it)) },
            onDismiss = { showColorPickerDialog = false }
        )
    }

    if (showCategoryDialog) {
        CategoryPickerDialog(
            initialName = prefillCategoryName,
            initialColorHex = prefillCategoryColor,
            initialIcon = prefillCategoryIcon,
            onConfirm = { name, colorHex, icon ->
                viewModel.onEvent(HabitFormUiEvent.CreateCategory(name, colorHex, icon))
            },
            onDismiss = { showCategoryDialog = false }
        )
    }
}
