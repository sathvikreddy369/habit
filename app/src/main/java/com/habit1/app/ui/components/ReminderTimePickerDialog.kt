package com.habit1.app.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalTime

/**
 * Native Material 3 TimePicker dialog for habit reminders.
 * Provides clock dial and direct keyboard entry, respects device 12/24-hour setting,
 * and provides clear, confirm, and cancel actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimePickerDialog(
    initialTime: LocalTime?,
    minTime: LocalTime? = null,
    onConfirm: (LocalTime) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val (defaultHour, defaultMinute) = remember(initialTime, minTime) {
        if (initialTime != null) {
            initialTime.hour to initialTime.minute
        } else if (minTime != null) {
            val candidate = minTime.plusMinutes(15)
            candidate.hour to ((candidate.minute / 5) * 5).coerceIn(0, 55)
        } else {
            8 to 30
        }
    }

    val timePickerState = rememberTimePickerState(
        initialHour = defaultHour,
        initialMinute = defaultMinute,
        is24Hour = false
    )
    var isKeyboardInputMode by remember { mutableStateOf(false) }

    val selectedTime = try {
        LocalTime.of(timePickerState.hour, timePickerState.minute)
    } catch (e: Exception) {
        null
    }

    val isPastTime = minTime != null && selectedTime != null && selectedTime.isBefore(minTime)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reminder Time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                TextButton(
                    onClick = { isKeyboardInputMode = !isKeyboardInputMode }
                ) {
                    Text(
                        text = if (isKeyboardInputMode) "Use Clock" else "Type Time",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isKeyboardInputMode) {
                    TimeInput(state = timePickerState)
                } else {
                    TimePicker(state = timePickerState)
                }

                if (isPastTime && minTime != null) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Reminder cannot be set in the past for today (current time is ${com.habit1.app.core.util.DateTimeUtils.formatTime(minTime)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedTime != null && !isPastTime) {
                        onConfirm(selectedTime)
                    }
                },
                enabled = !isPastTime && selectedTime != null
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (initialTime != null) {
                    TextButton(
                        onClick = onClear,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Clear")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
