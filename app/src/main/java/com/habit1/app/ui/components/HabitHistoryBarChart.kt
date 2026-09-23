package com.habit1.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.HabitHistoryDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class BarChartGrouping(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
    QUARTER("Quarter"),
    YEAR("Year")
}

data class BarItem(
    val label: String,
    val count: Int,
    val maxPossible: Int
)

/**
 * Loop-style vertical bar chart component showing completion count per period.
 */
@Composable
fun HabitHistoryBarChart(
    days: List<HabitHistoryDay>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    var selectedGrouping by remember { mutableStateOf(BarChartGrouping.WEEK) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val bars = remember(days, selectedGrouping) {
        computeBars(days, selectedGrouping)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with dropdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )

                Box {
                    Row(
                        modifier = Modifier
                            .clickable { dropdownExpanded = true }
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedGrouping.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select grouping",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        BarChartGrouping.entries.forEach { grouping ->
                            DropdownMenuItem(
                                text = { Text(grouping.label) },
                                onClick = {
                                    selectedGrouping = grouping
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (bars.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No history recorded yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val textMeasurer = rememberTextMeasurer()
                val maxCount = (bars.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    val availableWidth = size.width
                    val availableHeight = size.height - 30.dp.toPx() // Reserve 30dp for bottom labels
                    val baselineY = availableHeight
                    val barCount = bars.size
                    val slotWidth = availableWidth / barCount
                    val barWidth = (slotWidth * 0.45f).coerceIn(8.dp.toPx(), 24.dp.toPx())

                    // Baseline
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.25f),
                        start = Offset(0f, baselineY),
                        end = Offset(availableWidth, baselineY),
                        strokeWidth = 1.dp.toPx()
                    )

                    bars.forEachIndexed { index, bar ->
                        val centerX = slotWidth * index + slotWidth / 2f
                        val barHeight = if (maxCount > 0 && bar.count > 0) {
                            (bar.count.toFloat() / maxCount) * (availableHeight - 24.dp.toPx())
                        } else {
                            0f
                        }

                        val barTop = baselineY - barHeight
                        val barLeft = centerX - barWidth / 2f

                        // Draw bar
                        if (barHeight > 0) {
                            drawRoundRect(
                                color = accentColor,
                                topLeft = Offset(barLeft, barTop),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )

                            // Draw count on top of bar
                            val countText = bar.count.toString()
                            val countLayout = textMeasurer.measure(
                                text = countText,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentColor
                                )
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = countText,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentColor
                                ),
                                topLeft = Offset(
                                    centerX - countLayout.size.width / 2f,
                                    barTop - countLayout.size.height - 2.dp.toPx()
                                )
                            )
                        }

                        // Draw X-axis label below baseline
                        val labelLayout = textMeasurer.measure(
                            text = bar.label,
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = bar.label,
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = Color.Gray
                            ),
                            topLeft = Offset(
                                centerX - labelLayout.size.width / 2f,
                                baselineY + 6.dp.toPx()
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun computeBars(days: List<HabitHistoryDay>, grouping: BarChartGrouping): List<BarItem> {
    if (days.isEmpty()) return emptyList()

    val sortedDays = days.sortedBy { it.date }
    return when (grouping) {
        BarChartGrouping.WEEK -> {
            // Group by calendar week (last 6-8 weeks)
            val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
            sortedDays.groupBy {
                val dayOfWeekVal = it.date.dayOfWeek.value // 1..7 (Mon..Sun)
                it.date.minusDays((dayOfWeekVal - 1).toLong()) // start of week
            }.entries.toList().takeLast(7).map { (weekStart, daysInWeek) ->
                val completed = daysInWeek.count { it.status is CalendarDayStatus.Completed }
                BarItem(
                    label = weekStart.format(formatter),
                    count = completed,
                    maxPossible = daysInWeek.size
                )
            }
        }
        BarChartGrouping.MONTH -> {
            val formatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
            sortedDays.groupBy {
                it.date.withDayOfMonth(1)
            }.entries.toList().takeLast(6).map { (monthStart, daysInMonth) ->
                val completed = daysInMonth.count { it.status is CalendarDayStatus.Completed }
                BarItem(
                    label = monthStart.format(formatter),
                    count = completed,
                    maxPossible = daysInMonth.size
                )
            }
        }
        BarChartGrouping.QUARTER -> {
            sortedDays.groupBy {
                val quarter = (it.date.monthValue - 1) / 3 + 1
                "Q$quarter ${it.date.year}"
            }.entries.toList().takeLast(4).map { (qLabel, daysInQ) ->
                val completed = daysInQ.count { it.status is CalendarDayStatus.Completed }
                BarItem(
                    label = qLabel,
                    count = completed,
                    maxPossible = daysInQ.size
                )
            }
        }
        BarChartGrouping.YEAR -> {
            sortedDays.groupBy { it.date.year }.entries.toList().takeLast(5).map { (year, daysInYear) ->
                val completed = daysInYear.count { it.status is CalendarDayStatus.Completed }
                BarItem(
                    label = year.toString(),
                    count = completed,
                    maxPossible = daysInYear.size
                )
            }
        }
    }
}
