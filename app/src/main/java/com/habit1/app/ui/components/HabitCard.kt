package com.habit1.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.ui.today.TodayHabitItem

/**
 * Reusable habit card for the Today screen.
 * Supports Boolean check toggling and quantitative steppers right on the card.
 */
@Composable
fun HabitCard(
    habitItem: TodayHabitItem,
    onToggle: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val checkColor by animateColorAsState(
        targetValue = if (habitItem.isCompleted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "check_color"
    )

    val iconTint by animateColorAsState(
        targetValue = if (habitItem.isCompleted) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.outline
        },
        label = "icon_tint"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left content: Name, details, streak
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
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
                        StreakBadge(streakCount = habitItem.streakResult.currentStreak)
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
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }

            // Right content: Interaction controls
            when (habitItem.measurementType) {
                is MeasurementType.BooleanChoice -> {
                    FilledIconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = checkColor,
                            contentColor = iconTint
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = if (habitItem.isCompleted) "Completed" else "Mark Done",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                is MeasurementType.Count,
                is MeasurementType.Duration,
                is MeasurementType.Quantity -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedIconButton(
                            onClick = onDecrement,
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "−",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedIconButton(
                            onClick = onIncrement,
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        FilledIconButton(
                            onClick = onToggle,
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = checkColor,
                                contentColor = iconTint
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Mark Target Done",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
