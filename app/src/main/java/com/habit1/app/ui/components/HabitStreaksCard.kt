package com.habit1.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.HabitHistoryDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class StreakInterval(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val length: Int
)

/**
 * Loop-style best streaks presentation with horizontal timeline bars.
 */
@Composable
fun HabitStreaksCard(
    currentStreak: Int,
    longestStreak: Int,
    historyDays: List<HabitHistoryDay>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val streaks = remember(historyDays, currentStreak, longestStreak) {
        computeTopStreaks(historyDays, currentStreak, longestStreak)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Best streaks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (streaks.isEmpty()) {
                Text(
                    text = "No streaks recorded yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
                val maxLength = (streaks.maxOfOrNull { it.length } ?: 1).coerceAtLeast(1)

                streaks.forEachIndexed { index, streak ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = streak.startDate.format(formatter),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = streak.endDate.format(formatter),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Horizontal streak bar
                        val widthFraction = (streak.length.toFloat() / maxLength).coerceIn(0.15f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(widthFraction)
                                .height(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor)
                                .align(Alignment.CenterHorizontally),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${streak.length}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (index < streaks.size - 1) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun computeTopStreaks(
    historyDays: List<HabitHistoryDay>,
    currentStreak: Int,
    longestStreak: Int
): List<StreakInterval> {
    val sorted = historyDays.sortedBy { it.date }
    val intervals = mutableListOf<StreakInterval>()

    var start: LocalDate? = null
    var end: LocalDate? = null
    var count = 0

    for (day in sorted) {
        if (day.status is CalendarDayStatus.Completed) {
            if (start == null) {
                start = day.date
            }
            end = day.date
            count++
        } else {
            if (start != null && end != null && count > 0) {
                intervals.add(StreakInterval(start, end, count))
            }
            start = null
            end = null
            count = 0
        }
    }
    if (start != null && end != null && count > 0) {
        intervals.add(StreakInterval(start, end, count))
    }

    if (intervals.isEmpty() && longestStreak > 0) {
        val today = LocalDate.now()
        intervals.add(
            StreakInterval(
                startDate = today.minusDays(longestStreak.toLong() - 1),
                endDate = today,
                length = longestStreak
            )
        )
    }

    return intervals.sortedByDescending { it.length }.take(3)
}
