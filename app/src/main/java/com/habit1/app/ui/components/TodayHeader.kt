package com.habit1.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Calm, focused header for the Today screen displaying civil date and daily progress.
 *
 * Strict UX Principle:
 * Keeps habits and daily goals strictly separate. Never combines them into a single percentage.
 */
@Composable
fun TodayHeader(
    formattedDate: String = "",
    completedHabitsCount: Int,
    totalScheduledHabitsCount: Int,
    habitProgress: Float,
    completedGoalsCount: Int = 0,
    totalGoalsCount: Int = 0,
    goalProgress: Float = 0.0f,
    modifier: Modifier = Modifier
) {
    val animatedHabitProgress by animateFloatAsState(
        targetValue = habitProgress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "daily_habit_progress"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {

        // Habit Progress Section
        val habitSummaryText = if (totalScheduledHabitsCount == 0) {
            "No habits scheduled"
        } else if (completedHabitsCount == totalScheduledHabitsCount) {
            "All $totalScheduledHabitsCount habits completed"
        } else {
            "$completedHabitsCount of $totalScheduledHabitsCount habits completed"
        }

        val habitPercent = (habitProgress * 100).toInt()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "$habitSummaryText, $habitPercent percent completed"
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = habitSummaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (totalScheduledHabitsCount > 0) {
                Text(
                    text = "$habitPercent%",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (totalScheduledHabitsCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animatedHabitProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        // Daily Goals Progress (Separate from habits)
        if (totalGoalsCount > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            val goalSummaryText = if (completedGoalsCount == totalGoalsCount) {
                "All $totalGoalsCount daily goals completed"
            } else {
                "$completedGoalsCount of $totalGoalsCount daily goals completed"
            }
            val goalPercent = (goalProgress * 100).toInt()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "$goalSummaryText, $goalPercent percent completed"
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = goalSummaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$goalPercent%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
