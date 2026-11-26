package com.habit1.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habit1.app.domain.model.HabitRecordStatus
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.ui.theme.HabitColors
import com.habit1.app.ui.theme.HabitIcons
import com.habit1.app.ui.today.TodayHabitItem

/**
 * Modern, visually distinctive habit card for the Today / Home screen.
 *
 * Implements:
 * - Icon container with consistent visual badge.
 * - Boolean habits: direct visible Check/Done and adjacent X/Skip actions (>=48dp touch targets).
 * - Quantitative habits: integrated two-row layout with 4-tier QuantitativeProgressRing,
 *   numeric actual/target with units, overshoot indicators, and compact tactile steppers.
 * - High-contrast surface elevation, readable non-truncated typography, and zero clutter.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitCard(
    habitItem: TodayHabitItem,
    onToggle: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSetValue: ((Double) -> Unit)? = null,
    onMarkMissed: (() -> Unit)? = null,
    onMarkSkipped: (() -> Unit)? = null,
    onCompleteToTarget: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onOpenActionSheet: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showValueDialog by remember { mutableStateOf(false) }
    val habitColor = HabitColors.parseColor(habitItem.colorHex)

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

    val formattedTargetValue = remember(habitItem.targetValue, habitItem.measurementType) {
        when (habitItem.measurementType) {
            is MeasurementType.Count,
            is MeasurementType.Duration -> habitItem.targetValue.toInt().toString()
            is MeasurementType.Quantity -> {
                if (habitItem.targetValue % 1.0 == 0.0) {
                    habitItem.targetValue.toInt().toString()
                } else {
                    habitItem.targetValue.toString()
                }
            }
            is MeasurementType.BooleanChoice -> ""
        }
    }

    val unitStr = habitItem.unit?.let { " $it" } ?: ""

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = { onOpenActionSheet?.invoke() }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (habitItem.isCompleted) {
                habitColor.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // ROW 1: Habit Icon, Title, Metadata (Category, Streak), and More Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Habit Icon in consistent visual container
                HabitIconBadge(
                    habitItem = habitItem,
                    habitColor = habitColor
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Title & Subtitle Info (Name without truncation wherever reasonably possible)
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = habitItem.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Secondary row: Category chip, Streak badge, Status indicator, Notes preview
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (!habitItem.categoryName.isNullOrBlank()) {
                            val catColor = HabitColors.parseColor(habitItem.categoryColorHex)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = catColor.copy(alpha = 0.12f),
                                border = BorderStroke(0.5.dp, catColor.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = habitItem.categoryName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = catColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                )
                            }
                        }

                        if (habitItem.streakResult.currentStreak > 0) {
                            StreakBadge(
                                streakCount = habitItem.streakResult.currentStreak,
                                isFrozen = habitItem.streakResult.isFrozen,
                                accentColor = habitColor
                            )
                        }

                        // Show clearly highlighted status pill/text below title
                        val (statusLabel, statusColor, statusBg) = when (habitItem.status) {
                            HabitRecordStatus.DONE -> Triple(
                                "Done",
                                habitColor,
                                habitColor.copy(alpha = 0.12f)
                            )
                            HabitRecordStatus.SKIPPED -> Triple(
                                "Skipped",
                                Color(0xFFFB8C00),
                                Color(0xFFFB8C00).copy(alpha = 0.12f)
                            )
                            HabitRecordStatus.MISSED -> Triple(
                                "Missed",
                                MaterialTheme.colorScheme.error,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                            )
                            HabitRecordStatus.UNRECORDED, HabitRecordStatus.PARTIAL -> Triple(
                                "Not done",
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                Color.Transparent
                            )
                        }

                        if (habitItem.status == HabitRecordStatus.DONE ||
                            habitItem.status == HabitRecordStatus.SKIPPED ||
                            habitItem.status == HabitRecordStatus.MISSED
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = statusBg,
                                border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (habitItem.measurementType is MeasurementType.BooleanChoice) {
                            Text(
                                text = "• $statusLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (!habitItem.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "“${habitItem.notes}”",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // If Boolean habit: Place direct Check and Missed/X actions right here!
                if (habitItem.measurementType is MeasurementType.BooleanChoice) {
                    Spacer(modifier = Modifier.width(6.dp))
                    BooleanActionGroup(
                        habitItem = habitItem,
                        accentColor = habitColor,
                        onToggle = onToggle,
                        onMarkMissed = onMarkMissed ?: onMarkSkipped
                    )
                }

                // Options / Overflow menu button (>=48dp touch target)
                if (onOpenActionSheet != null) {
                    IconButton(
                        onClick = onOpenActionSheet,
                        modifier = Modifier
                            .size(44.dp)
                            .semantics { contentDescription = "Options for ${habitItem.name}" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ROW 2: Quantitative Habits Integrated Tracking Controls
            if (habitItem.measurementType !is MeasurementType.BooleanChoice) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Quantitative Progress Ring & Numeric progress
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        QuantitativeProgressRing(
                            progressRatio = habitItem.progressRatio,
                            isCompleted = habitItem.isCompleted,
                            size = 46.dp,
                            strokeWidth = 4.dp,
                            showCenterText = true,
                            onClick = {
                                if (onCompleteToTarget != null) {
                                    onCompleteToTarget()
                                } else {
                                    onToggle()
                                }
                            }
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            // Numeric actual / target with unit
                            Text(
                                text = "$formattedActualValue / $formattedTargetValue$unitStr",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Status / overshoot indicator
                            val overshoot = habitItem.actualValue - habitItem.targetValue
                            if (overshoot > 0) {
                                val overshootFormatted = if (overshoot % 1.0 == 0.0) overshoot.toInt().toString() else overshoot.toString()
                                Text(
                                    text = "+$overshootFormatted$unitStr overshoot",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            } else {
                                val percent = (habitItem.progressRatio * 100).toInt()
                                val statusText = when (habitItem.status) {
                                    HabitRecordStatus.SKIPPED -> "Skipped"
                                    HabitRecordStatus.MISSED -> "Missed"
                                    else -> "$percent% reached"
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Right: Compact tactile steppers & direct entry pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Decrement Button (>=48dp touch target)
                        OutlinedIconButton(
                            onClick = onDecrement,
                            modifier = Modifier
                                .size(38.dp)
                                .semantics { contentDescription = "Decrease ${habitItem.name}" },
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "−",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Tappable Value Pill for direct numeric entry
                        Surface(
                            onClick = {
                                if (onSetValue != null) {
                                    showValueDialog = true
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .height(38.dp)
                                .widthIn(min = 40.dp)
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
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Increment Button (>=48dp touch target)
                        OutlinedIconButton(
                            onClick = onIncrement,
                            modifier = Modifier
                                .size(38.dp)
                                .semantics { contentDescription = "Increase ${habitItem.name}" },
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
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

/**
 * Habit icon badge with a consistent, stylish visual container.
 */
@Composable
private fun HabitIconBadge(
    habitItem: TodayHabitItem,
    habitColor: Color,
    modifier: Modifier = Modifier
) {
    val categoryVector = HabitIcons.getVectorIcon(habitItem.categoryIcon)
    val isEmoji = HabitIcons.isEmoji(habitItem.categoryIcon)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(habitColor.copy(alpha = 0.12f))
            .border(1.dp, habitColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
    ) {
        when {
            categoryVector != null -> {
                Icon(
                    imageVector = categoryVector,
                    contentDescription = null,
                    tint = habitColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            isEmoji -> {
                Text(
                    text = habitItem.categoryIcon ?: "",
                    fontSize = 18.sp
                )
            }
            habitItem.measurementType is MeasurementType.BooleanChoice -> {
                Icon(
                    imageVector = Icons.Default.TaskAlt,
                    contentDescription = null,
                    tint = habitColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            habitItem.measurementType is MeasurementType.Count -> {
                Icon(
                    imageVector = Icons.Default.Numbers,
                    contentDescription = null,
                    tint = habitColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            habitItem.measurementType is MeasurementType.Duration -> {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = habitColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            habitItem.measurementType is MeasurementType.Quantity -> {
                Icon(
                    imageVector = Icons.Default.Straighten,
                    contentDescription = null,
                    tint = habitColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Dual action buttons for Boolean habits:
 * - Visible Check/Done action.
 * - Visible adjacent X action for Missed (not skipped; skipped is in options).
 * Either action is active, never both.
 * Both buttons enforce minimum 48dp touch targets and clear TalkBack semantics.
 */
@Composable
private fun BooleanActionGroup(
    habitItem: TodayHabitItem,
    accentColor: Color,
    onToggle: () -> Unit,
    onMarkMissed: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val isDone = habitItem.isCompleted
    val isMissed = habitItem.status == HabitRecordStatus.MISSED

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        // Missed / Wrong Mark Action Button (X)
        if (onMarkMissed != null) {
            val missedBgColor = if (isMissed) {
                Color(0xFFE53935)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
            val missedBorderColor = if (isMissed) {
                Color(0xFFE53935)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
            val missedIconColor = if (isMissed) {
                Color.White
            } else {
                MaterialTheme.colorScheme.outline
            }

            Surface(
                onClick = onMarkMissed,
                shape = CircleShape,
                color = missedBgColor,
                border = BorderStroke(if (isMissed) 0.dp else 1.dp, missedBorderColor),
                modifier = Modifier
                    .size(42.dp)
                    .semantics {
                        contentDescription = if (isMissed) {
                            "Unmark missed ${habitItem.name}"
                        } else {
                            "Mark ${habitItem.name} missed"
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = missedIconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Done Action Button (Check)
        val doneBgColor = if (isDone) {
            accentColor
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        }
        val doneBorderColor = if (isDone) {
            accentColor
        } else {
            accentColor.copy(alpha = 0.7f)
        }
        val doneIconColor = if (isDone) {
            Color.White
        } else {
            accentColor
        }

        Surface(
            onClick = onToggle,
            shape = CircleShape,
            color = doneBgColor,
            border = BorderStroke(if (isDone) 0.dp else 1.5.dp, doneBorderColor),
            modifier = Modifier
                .size(42.dp)
                .semantics {
                    contentDescription = if (isDone) {
                        "Mark ${habitItem.name} not completed"
                    } else {
                        "Mark ${habitItem.name} completed"
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = doneIconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
