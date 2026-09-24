package com.habit1.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.ui.today.TodayHabitItem

/**
 * Reusable habit card for the Today screen.
 * Supports Boolean check toggling, quantitative steppers, and direct numeric value entry.
 */
@Composable
fun HabitCard(
    habitItem: TodayHabitItem,
    onToggle: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSetValue: ((Double) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showValueDialog by remember { mutableStateOf(false) }
    val habitColor = com.habit1.app.ui.theme.HabitColors.parseColor(habitItem.colorHex)

    val checkColor by animateColorAsState(
        targetValue = if (habitItem.isCompleted) {
            habitColor
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        label = "check_color"
    )

    val iconTint by animateColorAsState(
        targetValue = if (habitItem.isCompleted) {
            androidx.compose.ui.graphics.Color.White
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        },
        label = "icon_tint"
    )

    val formattedActualValue = remember(habitItem.actualValue, habitItem.measurementType) {
        when (habitItem.measurementType) {
            is MeasurementType.Count,
            is MeasurementType.Duration -> habitItem.actualValue.toInt().toString()
            is MeasurementType.Quantity -> {
                if (habitItem.actualValue % 1.0 == 0.0) {
                    habitItem.actualValue.toInt().toString()
                } else {
                    habitItem.actualValue.toString()
                }
            }
            is MeasurementType.BooleanChoice -> ""
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left color accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(habitColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Left content: Name, details, streak
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = habitItem.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (habitItem.streakResult.currentStreak > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        StreakBadge(
                            streakCount = habitItem.streakResult.currentStreak,
                            accentColor = habitColor
                        )
                    }
                }

                if (!habitItem.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = habitItem.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = habitItem.formattedProgress,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (habitItem.isCompleted) {
                        habitColor
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    modifier = if (onSetValue != null && habitItem.measurementType !is MeasurementType.BooleanChoice) {
                        Modifier.clickable { showValueDialog = true }
                    } else {
                        Modifier
                    }
                )
            }

            // Right content: Interaction controls
            when (habitItem.measurementType) {
                is MeasurementType.BooleanChoice -> {
                    Surface(
                        onClick = onToggle,
                        modifier = Modifier
                            .size(46.dp)
                            .semantics {
                                contentDescription = if (habitItem.isCompleted) "Mark ${habitItem.name} not completed" else "Mark ${habitItem.name} completed"
                            },
                        shape = CircleShape,
                        color = checkColor
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                is MeasurementType.Count,
                is MeasurementType.Duration,
                is MeasurementType.Quantity -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedIconButton(
                            onClick = onDecrement,
                            modifier = Modifier
                                .size(36.dp)
                                .semantics {
                                    contentDescription = "Decrease ${habitItem.name}"
                                },
                            shape = CircleShape
                        ) {
                            Text(
                                text = "−",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Tappable actual value for direct numeric entry
                        Surface(
                            onClick = {
                                if (onSetValue != null) {
                                    showValueDialog = true
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .height(36.dp)
                                .widthIn(min = 36.dp)
                                .semantics {
                                    contentDescription = "Set ${habitItem.name} value directly, current value $formattedActualValue"
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = formattedActualValue,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        OutlinedIconButton(
                            onClick = onIncrement,
                            modifier = Modifier
                                .size(36.dp)
                                .semantics {
                                    contentDescription = "Increase ${habitItem.name}"
                                },
                            shape = CircleShape
                        ) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            onClick = onToggle,
                            modifier = Modifier
                                .size(44.dp)
                                .semantics {
                                    contentDescription = if (habitItem.isCompleted) "Mark ${habitItem.name} incomplete" else "Mark ${habitItem.name} complete"
                                },
                            shape = CircleShape,
                            color = checkColor
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showValueDialog && onSetValue != null) {
        NumericEntryDialog(
            habitName = habitItem.name,
            measurementType = habitItem.measurementType,
            currentValue = habitItem.actualValue,
            targetValue = habitItem.targetValue,
            unit = habitItem.unit,
            onDismiss = { showValueDialog = false },
            onConfirm = { value ->
                onSetValue(value)
                showValueDialog = false
            }
        )
    }
}
