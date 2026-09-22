package com.habit1.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.habit1.app.domain.model.MeasurementType

sealed interface NumericValidationResult {
    data class Valid(val value: Double) : NumericValidationResult
    data class Invalid(val errorMessage: String) : NumericValidationResult
}

object NumericInputValidator {
    fun validate(rawInput: String, measurementType: MeasurementType): NumericValidationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return NumericValidationResult.Valid(0.0)
        }

        return when (measurementType) {
            is MeasurementType.Count -> {
                val parsed = trimmed.toIntOrNull()
                if (parsed == null || parsed < 0) {
                    NumericValidationResult.Invalid("Enter a non-negative whole number")
                } else {
                    NumericValidationResult.Valid(parsed.toDouble())
                }
            }
            is MeasurementType.Duration -> {
                val parsed = trimmed.toIntOrNull()
                if (parsed == null || parsed < 0) {
                    NumericValidationResult.Invalid("Enter whole minutes (e.g. 30)")
                } else {
                    NumericValidationResult.Valid(parsed.toDouble())
                }
            }
            is MeasurementType.Quantity -> {
                val parsed = com.habit1.app.domain.validation.HabitValidator.parseDecimal(trimmed)
                if (parsed == null || parsed < 0.0) {
                    NumericValidationResult.Invalid("Enter a valid amount (e.g. 3.5)")
                } else {
                    NumericValidationResult.Valid(parsed)
                }
            }
            is MeasurementType.BooleanChoice -> {
                NumericValidationResult.Valid(1.0)
            }
        }
    }
}

/**
 * Dialog enabling direct numeric progress entry for quantitative habits (Count, Duration, Quantity).
 * Enforces measurement-specific constraints, presents appropriate virtual keyboards, and validates input.
 */
@Composable
fun NumericEntryDialog(
    habitName: String,
    measurementType: MeasurementType,
    currentValue: Double,
    targetValue: Double,
    unit: String?,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val initialString = remember(currentValue, measurementType) {
        when (measurementType) {
            is MeasurementType.Count,
            is MeasurementType.Duration -> currentValue.toInt().toString()
            is MeasurementType.Quantity -> {
                if (currentValue % 1.0 == 0.0) {
                    currentValue.toInt().toString()
                } else {
                    currentValue.toString()
                }
            }
            is MeasurementType.BooleanChoice -> "1"
        }
    }

    // Initialize with full text selection so typing immediately replaces the current value
    var textValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialString,
                selection = TextRange(0, initialString.length)
            )
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val targetDescription = remember(measurementType, targetValue, unit) {
        when (measurementType) {
            is MeasurementType.Count -> {
                val u = unit?.ifBlank { null } ?: "reps"
                "Target: ${targetValue.toInt()} $u"
            }
            is MeasurementType.Duration -> {
                "Target: ${targetValue.toInt()} mins"
            }
            is MeasurementType.Quantity -> {
                val formattedTarget = if (targetValue % 1.0 == 0.0) targetValue.toInt().toString() else targetValue.toString()
                val u = unit?.ifBlank { null } ?: ""
                "Target: $formattedTarget $u".trim()
            }
            is MeasurementType.BooleanChoice -> ""
        }
    }

    val keyboardType = when (measurementType) {
        is MeasurementType.Count,
        is MeasurementType.Duration -> KeyboardType.Number
        is MeasurementType.Quantity -> KeyboardType.Decimal
        is MeasurementType.BooleanChoice -> KeyboardType.Number
    }

    fun validateAndSubmit() {
        when (val result = NumericInputValidator.validate(textValue.text, measurementType)) {
            is NumericValidationResult.Valid -> {
                onConfirm(result.value)
                onDismiss()
            }
            is NumericValidationResult.Invalid -> {
                errorMessage = result.errorMessage
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = habitName,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (targetDescription.isNotEmpty()) {
                    Text(
                        text = targetDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        errorMessage = null
                    },
                    label = {
                        val labelText = when (measurementType) {
                            is MeasurementType.Count -> "Completed count (${unit ?: "reps"})"
                            is MeasurementType.Duration -> "Completed duration (mins)"
                            is MeasurementType.Quantity -> "Completed quantity (${unit ?: ""})"
                            is MeasurementType.BooleanChoice -> "Value"
                        }
                        Text(labelText)
                    },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { validateAndSubmit() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { validateAndSubmit() }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
