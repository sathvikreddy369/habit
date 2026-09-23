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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.habit1.app.ui.components.ColorPickerDialog
import com.habit1.app.ui.theme.HabitColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
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
                .padding(innerPadding)
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
                    Text(
                        text = "Simple checkbox to mark completed each scheduled day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MeasurementKind.COUNT -> {
                    val targetError = uiState.errors.contains(HabitValidationError.TargetMustBePositive)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = uiState.targetInput,
                            onValueChange = { viewModel.onEvent(HabitFormUiEvent.UpdateTarget(it)) },
                            label = { Text("Daily target count") },
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
                            label = { Text("Unit (e.g. reps, pages)") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                MeasurementKind.DURATION -> {
                    val targetError = uiState.errors.contains(HabitValidationError.TargetMustBePositive)
                    OutlinedTextField(
                        value = uiState.targetInput,
                        onValueChange = { viewModel.onEvent(HabitFormUiEvent.UpdateTarget(it)) },
                        label = { Text("Target duration (minutes)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        isError = targetError,
                        supportingText = if (targetError) { { Text("Must be greater than 0 minutes") } } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                MeasurementKind.QUANTITY -> {
                    val targetError = uiState.errors.contains(HabitValidationError.TargetMustBePositive)
                    val unitError = uiState.errors.contains(HabitValidationError.UnitRequired)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = uiState.targetInput,
                            onValueChange = { viewModel.onEvent(HabitFormUiEvent.UpdateTarget(it)) },
                            label = { Text("Target quantity") },
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
                            label = { Text("Unit (e.g. L, km)") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            singleLine = true,
                            isError = unitError,
                            supportingText = if (unitError) { { Text("Unit required") } } else null,
                            modifier = Modifier.weight(1f)
                        )
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

            // Optional Reminder Time
            Text(
                text = "Reminder",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                Text(
                                    text = DateTimeUtils.format12HourTime(currentLocalTime),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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

            Spacer(modifier = Modifier.height(36.dp))
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
}
