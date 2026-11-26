package com.habit1.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact, unified daily overview dashboard for the Today / Home screen.
 *
 * Implements:
 * - Integrated 7-day bounded date stepper navigation with past-date context indicator.
 * - Prominent but compact radial completion donut (54dp).
 * - Exact habit completion counts and clear remaining habits indicator.
 * - Non-intrusive daily goals status.
 * - Elegant empty state when no habits are scheduled.
 */
@Composable
fun TodayHeader(
    formattedDate: String,
    isViewingPastDate: Boolean,
    canStepBackward: Boolean,
    canStepForward: Boolean,
    onStepBackward: () -> Unit,
    onStepForward: () -> Unit,
    onReturnToToday: () -> Unit,
    completedHabitsCount: Int,
    totalScheduledHabitsCount: Int,
    habitProgress: Float,
    completedGoalsCount: Int = 0,
    totalGoalsCount: Int = 0,
    goalProgress: Float = 0.0f,
    modifier: Modifier = Modifier
) {
    val animatedHabitProgress by animateFloatAsState(
        targetValue = habitProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "overview_radial_progress"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // 1. DATE NAVIGATION BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Backward Chevron Button
                IconButton(
                    onClick = onStepBackward,
                    enabled = canStepBackward,
                    modifier = Modifier
                        .size(36.dp)
                        .semantics { contentDescription = "Previous Day" }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = if (canStepBackward) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Date Title & Context
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isViewingPastDate) formattedDate else "Today • $formattedDate",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isViewingPastDate) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = onReturnToToday,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.height(26.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Today,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Today",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Forward Chevron Button
                IconButton(
                    onClick = onStepForward,
                    enabled = canStepForward,
                    modifier = Modifier
                        .size(36.dp)
                        .semantics { contentDescription = "Next Day" }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = if (canStepForward) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. RADIAL COMPLETION DONUT & REMAINING SUMMARY ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Radial Donut (54dp)
                val primaryColor = MaterialTheme.colorScheme.primary
                val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                val isAllCompleted = totalScheduledHabitsCount > 0 && completedHabitsCount == totalScheduledHabitsCount

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(54.dp)
                        .semantics {
                            val percent = (habitProgress * 100).toInt()
                            contentDescription = "$percent percent of scheduled habits completed"
                        }
                ) {
                    Canvas(modifier = Modifier.size(54.dp)) {
                        val strokePx = 5.dp.toPx()
                        val arcSize = 54.dp.toPx() - strokePx
                        val offset = strokePx / 2f

                        // Background track
                        drawCircle(
                            color = trackColor,
                            radius = arcSize / 2f,
                            style = Stroke(width = strokePx)
                        )

                        // Progress arc
                        if (animatedHabitProgress > 0f) {
                            drawArc(
                                color = if (isAllCompleted) Color(0xFF2E7D32) else primaryColor,
                                startAngle = -90f,
                                sweepAngle = animatedHabitProgress * 360f,
                                useCenter = false,
                                topLeft = androidx.compose.ui.geometry.Offset(offset, offset),
                                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                                style = Stroke(width = strokePx, cap = StrokeCap.Round)
                            )
                        }
                    }

                    if (isAllCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        val percentInt = (habitProgress * 100).toInt()
                        Text(
                            text = "$percentInt%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Textual Summary & Remaining Indicator
                Column(modifier = Modifier.weight(1f)) {
                    val habitHeadline = when {
                        totalScheduledHabitsCount == 0 -> "No habits scheduled"
                        isAllCompleted -> "All $totalScheduledHabitsCount habits completed"
                        else -> "$completedHabitsCount of $totalScheduledHabitsCount habits completed"
                    }

                    Text(
                        text = habitHeadline,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    val remainingHabits = (totalScheduledHabitsCount - completedHabitsCount).coerceAtLeast(0)
                    val remainingText = when {
                        totalScheduledHabitsCount == 0 -> "Rest day • Bonus activities can still be logged"
                        isAllCompleted -> "All caught up for today! 🎉"
                        remainingHabits == 1 -> "1 habit remaining"
                        else -> "$remainingHabits habits remaining"
                    }
                    val remainingColor = when {
                        isAllCompleted -> Color(0xFF2E7D32)
                        totalScheduledHabitsCount == 0 -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Text(
                        text = remainingText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = remainingColor
                    )

                    // Secondary daily goals progress if any
                    if (totalGoalsCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Daily Goals: $completedGoalsCount of $totalGoalsCount completed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
