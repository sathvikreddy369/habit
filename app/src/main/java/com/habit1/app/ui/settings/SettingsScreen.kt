package com.habit1.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit1.app.data.backup.BackupPreviewInfo
import com.habit1.app.data.backup.RestoreMode
import com.habit1.app.data.export.ExportFormat
import com.habit1.app.domain.model.AppLockTimeout
import com.habit1.app.ui.components.DataExportDialog
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.exportSuccessMessage) {
        uiState.exportSuccessMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(uiState.restoreSuccessMessage) {
        uiState.restoreSuccessMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    val context = LocalContext.current
    val isNotificationPermissionGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    val alarmManager = remember {
        context.getSystemService(android.content.Context.ALARM_SERVICE) as? android.app.AlarmManager
    }
    val isExactAlarmPermitted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        alarmManager?.canScheduleExactAlarms() ?: true
    } else {
        true
    }

    var showDigestTimePicker by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled */ }

    val appVersion = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportBackup(it) }
    }

    val habitExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(uiState.exportFormat.mimeType)
    ) { uri: Uri? ->
        uri?.let { viewModel.executeHabitExport(it, context) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Safely ignored if provider does not support persistable permissions
            }
            viewModel.onBackupFileSelected(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. APPEARANCE SECTION
                SettingsSectionHeader(title = "APPEARANCE")
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Theme",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Choose light, dark, system-following, or true AMOLED Black for maximum power efficiency.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "SYSTEM" to "System",
                                "LIGHT" to "Light",
                                "DARK" to "Dark",
                                "AMOLED" to "AMOLED Black"
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = (uiState.themeMode == mode),
                                    onClick = { viewModel.selectThemeMode(mode) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }

                // 2. NOTIFICATIONS SECTION
                SettingsSectionHeader(title = "NOTIFICATIONS")
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Notification Delivery",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isNotificationPermissionGranted)
                                        "Notifications allowed. Reminders trigger on scheduled days."
                                    else
                                        "Notifications disabled. Reminders and digests will not appear.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(
                                imageVector = if (isNotificationPermissionGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isNotificationPermissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        if (!isNotificationPermissionGranted) {
                            OutlinedButton(
                                onClick = {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Enable Notifications in Settings")
                            }
                        }

                        // Exact Alarm Status (Android 12+)
                        if (!isExactAlarmPermitted && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Exact Alarms (Optional)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Exact alarm scheduling is disabled. Habit reminders use battery-friendly fallback, but may be slightly delayed.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Allow Exact Alarms")
                            }
                        }

                        HorizontalDivider()

                        // Evening Digest Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Evening Digest",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Daily calm summary of all scheduled habits each evening.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = uiState.eveningDigestEnabled,
                                onCheckedChange = { viewModel.toggleEveningDigest(it) }
                            )
                        }

                        if (uiState.eveningDigestEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Digest Time: ${uiState.eveningDigestTime}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                OutlinedButton(
                                    onClick = { showDigestTimePicker = true }
                                ) {
                                    Text("Change Time")
                                }
                            }
                        }
                    }
                }

                if (showDigestTimePicker) {
                    val initialTime = try {
                        java.time.LocalTime.parse(uiState.eveningDigestTime)
                    } catch (_: Exception) {
                        java.time.LocalTime.of(21, 0)
                    }
                    com.habit1.app.ui.components.ReminderTimePickerDialog(
                        initialTime = initialTime,
                        onConfirm = { newTime ->
                            viewModel.updateEveningDigestTime(newTime)
                            showDigestTimePicker = false
                        },
                        onClear = {
                            showDigestTimePicker = false
                        },
                        onDismiss = {
                            showDigestTimePicker = false
                        }
                    )
                }

                // 3. SECURITY & PRIVACY SECTION
                SettingsSectionHeader(title = "SECURITY & PRIVACY")
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "App Lock",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Require biometric (fingerprint/face) or device credential (PIN/pattern/password) to open.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = uiState.appLockEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.toggleAppLock(enabled)
                                }
                            )
                        }

                        if (uiState.appLockEnabled) {
                            HorizontalDivider()

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Lock Behavior",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Select when Habit1 should lock after leaving the app.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val timeouts = listOf(
                                        AppLockTimeout.IMMEDIATELY to "Immediately",
                                        AppLockTimeout.ONE_MINUTE to "1 min",
                                        AppLockTimeout.FIVE_MINUTES to "5 min",
                                        AppLockTimeout.SCREEN_OFF to "Screen off"
                                    )
                                    timeouts.forEach { (timeout, label) ->
                                        FilterChip(
                                            selected = (uiState.appLockTimeout == timeout),
                                            onClick = { viewModel.updateAppLockTimeout(timeout) },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider()

                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Screenshots are blocked and app previews are hidden in Recent Apps. Notification quick actions (Done, Snooze, Skip) work directly without requiring unlock.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 4. DATA PORTABILITY & BACKUP SECTION
                SettingsSectionHeader(title = "DATA PORTABILITY & BACKUP")

                // Local-first Ownership Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Local-First Privacy & Ownership",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• All habits and history are stored exclusively on this device.\n" +
                                   "• Zero trackers, zero cloud servers, zero analytics SDKs.\n" +
                                   "• Fully operational offline with portable CSV and JSON exports.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Export & Backup Actions Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Export Habit Data (CSV / JSON)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Export Habit Data",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Export habits, completion records, and notes to standard CSV or structured JSON.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = { viewModel.openExportDialog() },
                                enabled = !uiState.isExporting && !uiState.isRestoring && !uiState.isInspecting
                            ) {
                                Text("Export")
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Export Full Backup Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Full Backup Archive",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Creates an unencrypted portable JSON backup with SHA-256 integrity checksum.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            OutlinedButton(
                                onClick = {
                                    val defaultFilename = "habit1_backup_${LocalDate.now()}.json"
                                    exportLauncher.launch(defaultFilename)
                                },
                                enabled = !uiState.isExporting && !uiState.isRestoring && !uiState.isInspecting
                            ) {
                                Text("Backup")
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        // Restore Backup Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Restore Backup",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Validates and imports habits, goals, and history from a backup file.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            OutlinedButton(
                                onClick = {
                                    importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                                },
                                enabled = !uiState.isExporting && !uiState.isRestoring && !uiState.isInspecting
                            ) {
                                Text("Restore")
                            }
                        }
                    }
                }

                // 5. STORAGE ACCURACY SECTION
                SettingsSectionHeader(title = "LOCAL STORAGE USAGE")
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Storage Breakdown",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Actual physical storage allocated on your device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.refreshStorageUsage() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Storage Usage",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        HorizontalDivider()

                        // Database Row
                        StorageMetricRow(
                            label = "Database (Room SQLite)",
                            subtext = "Includes habit_app.db, WAL journal & SHM files",
                            sizeString = uiState.storageUsage?.databaseFormatted ?: "Calculating..."
                        )

                        // Preferences Row
                        StorageMetricRow(
                            label = "User Preferences",
                            subtext = "DataStore and app configuration files",
                            sizeString = uiState.storageUsage?.preferencesFormatted ?: "Calculating..."
                        )

                        // Cache Row
                        StorageMetricRow(
                            label = "App Cache",
                            subtext = "Temporary app execution files",
                            sizeString = uiState.storageUsage?.cacheFormatted ?: "Calculating..."
                        )

                        HorizontalDivider()

                        // Total Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total App Data",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = uiState.storageUsage?.totalFormatted ?: "Calculating...",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 6. HISTORICAL TRUTH & RULES SECTION
                SettingsSectionHeader(title = "HISTORICAL TRUTH & RULES")
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Historical Truth Rules",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Recorded completions and progress are permanent, immutable facts.\n" +
                                   "• Editing a habit applies from today onward; past records never change.\n" +
                                   "• Archive pauses a habit and hides it while preserving all historical records (reversible).\n" +
                                   "• Delete permanently removes a habit and all of its historical records (irreversible).\n" +
                                   "• Completed Daily Goals remain permanent personal history.\n" +
                                   "• Incomplete Daily Goals older than 7 calendar days are automatically cleaned up.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 7. ABOUT SECTION
                SettingsSectionHeader(title = "ABOUT")
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Habit1",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Version $appVersion",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "A calm, local-first habit tracking application designed for personal ownership, historical truth, and zero tracking.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Progress Overlay
            if (uiState.isExporting || uiState.isInspecting || uiState.isRestoring) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            val statusText = when {
                                uiState.isExporting -> "Exporting data..."
                                uiState.isInspecting -> "Validating backup integrity..."
                                else -> "Restoring database..."
                            }
                            Text(text = statusText, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }

    // Data Export Dialog
    if (uiState.isExportDialogOpen) {
        DataExportDialog(
            allHabits = uiState.allHabits,
            selectedHabitIds = uiState.selectedHabitIds,
            exportFormat = uiState.exportFormat,
            onToggleHabit = { viewModel.toggleHabitSelection(it) },
            onSelectAllHabits = { viewModel.selectAllHabits(it) },
            onFormatChange = { viewModel.updateExportFormat(it) },
            onDateRangeChange = { start, end -> viewModel.updateExportDateRange(start, end) },
            onConfirmExport = {
                val defaultFilename = "habit1_export_${LocalDate.now()}.${uiState.exportFormat.extension}"
                habitExportLauncher.launch(defaultFilename)
            },
            onDismiss = { viewModel.dismissExportDialog() }
        )
    }

    // Restore Preview Dialog
    uiState.previewInfo?.let { preview ->
        RestorePreviewDialog(
            preview = preview,
            onDismiss = { viewModel.dismissPreviewDialog() },
            onConfirm = { mode -> viewModel.confirmRestore(mode) }
        )
    }
}

@Composable
private fun StorageMetricRow(
    label: String,
    subtext: String,
    sizeString: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = sizeString,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun RestorePreviewDialog(
    preview: BackupPreviewInfo,
    onDismiss: () -> Unit,
    onConfirm: (RestoreMode) -> Unit
) {
    var selectedMode by remember { mutableStateOf(RestoreMode.Merge) }
    var showDestructiveConfirmation by remember { mutableStateOf(false) }

    if (showDestructiveConfirmation) {
        AlertDialog(
            onDismissRequest = { showDestructiveConfirmation = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Confirm Replace All") },
            text = {
                Text(
                    "Existing data will be replaced by the selected backup.\n\nThis action will permanently delete all existing habits, records, goals, and reviews currently on this device and replace them with the backup.\n\nAre you sure you wish to proceed?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDestructiveConfirmation = false
                        onConfirm(RestoreMode.ReplaceAll)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Yes, Replace All Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDestructiveConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Backup Verified", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Integrity check passed (SHA-256 verified).\n" +
                            "Exported: ${preview.exportedAt}\n" +
                            "App Version: ${preview.appVersion} (Format v${preview.formatVersion})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("• Habits: ${preview.habitsCount} (${preview.reminderHabitsCount} with reminders)", style = MaterialTheme.typography.bodyMedium)
                        Text("• Records: ${preview.recordsCount}", style = MaterialTheme.typography.bodyMedium)
                        Text("• Daily Goals: ${preview.goalsCount} (${preview.subtasksCount} subtasks)", style = MaterialTheme.typography.bodyMedium)
                        Text("• Daily Reviews: ${preview.reviewsCount}", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Text(
                    text = "Select Restore Mode:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // Option: Merge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (selectedMode == RestoreMode.Merge),
                            onClick = { selectedMode = RestoreMode.Merge },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedMode == RestoreMode.Merge),
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Merge with Existing Data",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Adds missing data. Updates habits/goals where the backup is newer. Conflicting historical records are never overwritten.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Option: Replace All
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (selectedMode == RestoreMode.ReplaceAll),
                            onClick = { selectedMode = RestoreMode.ReplaceAll },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedMode == RestoreMode.ReplaceAll),
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Replace All Data (Destructive)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Wipes existing database and restores exactly what is in the backup file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedMode == RestoreMode.ReplaceAll) {
                        showDestructiveConfirmation = true
                    } else {
                        onConfirm(RestoreMode.Merge)
                    }
                }
            ) {
                Text("Confirm Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}
