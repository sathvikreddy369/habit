package com.habit1.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.HabitAnalyticsSummary
import com.habit1.app.domain.model.HabitHistoryDay
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.ui.history.HeatmapRangePreset
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.ceil

/**
 * Habit1 Calendar Heatmap component (Decision 5.1).
 *
 * Implements accessible 5-state status visual grammar:
 * - DONE: Solid accent fill
 * - PARTIAL: Partial fill or progress ring using habit accent color
 * - SKIPPED: Neutral slash or distinct subdued symbol
 * - MISSED: Distinct hollow ring with muted border
 * - Past scheduled UNRECORDED: Dashed / hollow outline
 * - REST / UNSCHEDULED: Plain neutral surface (subtle '+' marker if bonus activity)
 * - PAUSED: Paused muted surface
 */
@Composable
fun HabitHeatmap(
    summary: HabitAnalyticsSummary,
    selectedPreset: HeatmapRangePreset,
    formattedRange: String,
    canNavigateNext: Boolean,
    isCurrentRange: Boolean,
    onSelectPreset: (HeatmapRangePreset) -> Unit,
    onPreviousRange: () -> Unit,
    onNextRange: () -> Unit,
    onResetToToday: () -> Unit,
    onDayClick: (HabitHistoryDay) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val isQuantitative = summary.habit.measurement !is MeasurementType.BooleanChoice

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title
            Text(
                text = "Activity Heatmap",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Standardized Rolling Range Preset Chips (Decision 5.1.2)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeatmapRangePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = selectedPreset == preset,
                        onClick = { onSelectPreset(preset) },
                        label = {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Navigation Bar: Prev / Range Text / Next / Today (bounded at today)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPreviousRange,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous Range",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = formattedRange,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = onNextRange,
                        enabled = canNavigateNext,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next Range",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (!isCurrentRange) {
                    TextButton(
                        onClick = onResetToToday,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Heatmap View Dispatcher based on range duration
            when (selectedPreset) {
                HeatmapRangePreset.LAST_7_DAYS -> {
                    WeekHeatmapView(
                        summary = summary,
                        isQuantitative = isQuantitative,
                        onDayClick = onDayClick,
                        accentColor = accentColor
                    )
                }
                HeatmapRangePreset.LAST_30_DAYS -> {
                    MonthCalendarHeatmapView(
                        summary = summary,
                        isQuantitative = isQuantitative,
                        onDayClick = onDayClick,
                        accentColor = accentColor
                    )
                }
                HeatmapRangePreset.LAST_90_DAYS,
                HeatmapRangePreset.LAST_365_DAYS,
                HeatmapRangePreset.ALL_TIME -> {
                    YearActivityMatrixView(
                        summary = summary,
                        isQuantitative = isQuantitative,
                        onDayClick = onDayClick,
                        accentColor = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Accessible Legend
            HeatmapLegend(
                isQuantitative = isQuantitative,
                accentColor = accentColor
            )
        }
    }
}

/**
 * 1. Weekly Heatmap: 7 equal-width columns (Monday through Sunday).
 */
@Composable
private fun WeekHeatmapView(
    summary: HabitAnalyticsSummary,
    isQuantitative: Boolean,
    onDayClick: (HabitHistoryDay) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val today = remember { LocalDate.now() }
    val daysMap = remember(summary.dailyBreakdown) { summary.dailyBreakdown.associateBy { it.date } }
    val weekdays = listOf("M", "T", "W", "T", "F", "S", "S")

    Column(modifier = modifier.fillMaxWidth()) {
        // Weekday letter header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            weekdays.forEach { letter ->
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 7 days in the week range
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val monday = summary.range.startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            for (i in 0 until 7) {
                val date = monday.plusDays(i.toLong())
                val historyDay = daysMap[date]
                val isToday = date == today

                if (historyDay != null) {
                    WeekHeatmapCell(
                        day = historyDay,
                        isToday = isToday,
                        isQuantitative = isQuantitative,
                        onClick = { onDayClick(historyDay) },
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WeekHeatmapCell(
    day: HabitHistoryDay,
    isToday: Boolean,
    isQuantitative: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val status = day.status
    val accessibilityLabel = buildCellAccessibilityText(day, isQuantitative)
    val (bgColor, textColor, border, cellSymbol) = resolveCellStyle(status, isQuantitative, isToday, accentColor)

    Box(
        modifier = modifier
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .then(if (border != null) Modifier.border(border, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = accessibilityLabel },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                fontWeight = if (status is CalendarDayStatus.Completed || isToday) FontWeight.Bold else FontWeight.SemiBold,
                color = textColor,
                textAlign = TextAlign.Center
            )

            if (cellSymbol != null) {
                Text(
                    text = cellSymbol,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

/**
 * 2. 7-column Calendar Heatmap (Monday-first alignment).
 */
@Composable
private fun MonthCalendarHeatmapView(
    summary: HabitAnalyticsSummary,
    isQuantitative: Boolean,
    onDayClick: (HabitHistoryDay) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val today = remember { LocalDate.now() }
    val daysMap = remember(summary.dailyBreakdown) { summary.dailyBreakdown.associateBy { it.date } }
    val weekdays = listOf("M", "T", "W", "T", "F", "S", "S")

    val startDate = summary.range.startDate
    val daysCount = summary.range.dayCount
    // Monday = 1, Sunday = 7 -> leading offset (Mon=0..Sun=6)
    val leadingOffset = (startDate.dayOfWeek.value - 1)
    val totalSlots = leadingOffset + daysCount
    val rowCount = ceil(totalSlots / 7.0).toInt()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Weekday header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            weekdays.forEach { letter ->
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Calendar rows
        for (row in 0 until rowCount) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (col in 0 until 7) {
                    val slotIndex = row * 7 + col
                    val dayOffset = slotIndex - leadingOffset

                    if (dayOffset in 0 until daysCount) {
                        val date = startDate.plusDays(dayOffset.toLong())
                        val historyDay = daysMap[date]
                        val isToday = date == today

                        if (historyDay != null) {
                            MonthCalendarCell(
                                day = historyDay,
                                isToday = isToday,
                                isQuantitative = isQuantitative,
                                onClick = { onDayClick(historyDay) },
                                accentColor = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCalendarCell(
    day: HabitHistoryDay,
    isToday: Boolean,
    isQuantitative: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val status = day.status
    val accessibilityLabel = buildCellAccessibilityText(day, isQuantitative)
    val (bgColor, textColor, border, cellSymbol) = resolveCellStyle(status, isQuantitative, isToday, accentColor)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .then(if (border != null) Modifier.border(border, RoundedCornerShape(6.dp)) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = accessibilityLabel },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = if (status is CalendarDayStatus.Completed || isToday) FontWeight.Bold else FontWeight.SemiBold,
                color = textColor,
                textAlign = TextAlign.Center
            )

            if (cellSymbol != null) {
                Text(
                    text = cellSymbol,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

/**
 * 3. Horizontal Activity Matrix (7 rows Mon-Sun by N weeks, scrollable).
 */
@Composable
private fun YearActivityMatrixView(
    summary: HabitAnalyticsSummary,
    isQuantitative: Boolean,
    onDayClick: (HabitHistoryDay) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val today = remember { LocalDate.now() }
    val daysMap = remember(summary.dailyBreakdown) { summary.dailyBreakdown.associateBy { it.date } }

    val startDate = summary.range.startDate
    val endDate = summary.range.endDate
    val firstMonday = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val totalDays = ChronoUnit.DAYS.between(firstMonday, endDate) + 1
    val totalWeeks = ceil(totalDays / 7.0).toInt().coerceAtLeast(1)

    val scrollState = rememberScrollState()

    // Scroll to the latest weeks when opening
    LaunchedEffect(summary.range) {
        val targetScroll = (totalWeeks * 20).coerceAtLeast(0)
        scrollState.scrollTo(targetScroll)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Day of week labels on the left (M, W, F)
            Column(
                modifier = Modifier.width(18.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Spacer(modifier = Modifier.height(18.dp)) // Offset for month labels
                listOf("M", "", "W", "", "F", "", "S").forEach { label ->
                    Box(
                        modifier = Modifier.size(13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Horizontally scrollable matrix
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                Column {
                    // Month labels row
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (week in 0 until totalWeeks) {
                            val weekStartDate = firstMonday.plusDays((week * 7).toLong())
                            val isFirstWeekOfMonth = weekStartDate.dayOfMonth <= 7
                            Box(
                                modifier = Modifier.width(13.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (isFirstWeekOfMonth && !weekStartDate.isAfter(endDate)) {
                                    Text(
                                        text = weekStartDate.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())).take(3),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 7 rows of days (Monday through Sunday)
                    for (dayOfWeek in 0 until 7) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (week in 0 until totalWeeks) {
                                val date = firstMonday.plusDays((week * 7 + dayOfWeek).toLong())
                                val isWithinRange = date in summary.range
                                val historyDay = daysMap[date]
                                val isToday = date == today

                                if (isWithinRange && historyDay != null) {
                                    YearMatrixCell(
                                        day = historyDay,
                                        isToday = isToday,
                                        isQuantitative = isQuantitative,
                                        onClick = { onDayClick(historyDay) },
                                        accentColor = accentColor
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(13.dp)
                                            .background(Color.Transparent)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Factual metrics banner
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${summary.completedDays} / ${summary.eligibleScheduledDays} days completed (${summary.completionRate.toInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Best: ${summary.longestStreak}d • Current: ${summary.currentStreak}d",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun YearMatrixCell(
    day: HabitHistoryDay,
    isToday: Boolean,
    isQuantitative: Boolean,
    onClick: () -> Unit,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val status = day.status
    val accessibilityLabel = buildCellAccessibilityText(day, isQuantitative)
    val (bgColor, _, border, cellSymbol) = resolveCellStyle(status, isQuantitative, isToday, accentColor)

    Box(
        modifier = Modifier
            .size(13.dp)
            .clip(RoundedCornerShape(2.5.dp))
            .background(bgColor)
            .then(if (border != null) Modifier.border(border, RoundedCornerShape(2.5.dp)) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = accessibilityLabel },
        contentAlignment = Alignment.Center
    ) {
        if (cellSymbol != null && cellSymbol == "+") {
            Box(
                modifier = Modifier
                    .size(2.5.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
    }
}

/**
 * Shared color, border, and symbol resolution across heatmap views (Decision 5.1.1).
 *
 * Returns: Quadruple(bgColor, textColor, borderStroke, cellSymbol)
 */
@Composable
private fun resolveCellStyle(
    status: CalendarDayStatus,
    isQuantitative: Boolean,
    isToday: Boolean,
    accentColor: Color = MaterialTheme.colorScheme.primary
): CellStyleResult {
    val todayBorder = if (isToday) BorderStroke(1.5.dp, accentColor) else null

    return when (status) {
        is CalendarDayStatus.Completed -> {
            val cellBorder = if (isToday) BorderStroke(2.dp, Color.White) else null
            CellStyleResult(
                backgroundColor = accentColor,
                textColor = Color.White,
                border = cellBorder,
                symbol = null
            )
        }

        // PARTIAL: Accent color with partial fill / ring
        is CalendarDayStatus.RecordedIncomplete -> {
            CellStyleResult(
                backgroundColor = accentColor.copy(alpha = 0.35f),
                textColor = MaterialTheme.colorScheme.onSurface,
                border = todayBorder ?: BorderStroke(1.5.dp, accentColor),
                symbol = null
            )
        }

        // MISSED: Distinct hollow ring with muted border
        is CalendarDayStatus.ExplicitMissed -> {
            CellStyleResult(
                backgroundColor = MaterialTheme.colorScheme.surface,
                textColor = MaterialTheme.colorScheme.error,
                border = todayBorder ?: BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
                symbol = "✕"
            )
        }

        // SKIPPED: Neutral surface with subdued slash/dash
        is CalendarDayStatus.Skipped -> {
            CellStyleResult(
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                textColor = MaterialTheme.colorScheme.outline,
                border = todayBorder ?: BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                symbol = "—"
            )
        }

        // Past scheduled UNRECORDED: Dashed/hollow outline
        CalendarDayStatus.ProjectedMissed -> {
            CellStyleResult(
                backgroundColor = MaterialTheme.colorScheme.surface,
                textColor = MaterialTheme.colorScheme.outline,
                border = todayBorder ?: BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                symbol = null
            )
        }

        // REST / UNSCHEDULED: Plain neutral surface (subtle '+' marker if bonus activity logged)
        CalendarDayStatus.ProjectedRest -> {
            CellStyleResult(
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                border = todayBorder ?: BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                symbol = null
            )
        }

        // PAUSED: Paused surface
        CalendarDayStatus.Paused -> {
            CellStyleResult(
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                border = todayBorder ?: BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                symbol = null
            )
        }

        CalendarDayStatus.PreCreation -> {
            CellStyleResult(
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                textColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                symbol = null
            )
        }

        CalendarDayStatus.Future -> {
            CellStyleResult(
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
                textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                border = todayBorder ?: BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                symbol = null
            )
        }
    }
}

private data class CellStyleResult(
    val backgroundColor: Color,
    val textColor: Color,
    val border: BorderStroke?,
    val symbol: String?
)

/**
 * Status legend covering all 5 primary states (Decision 5.1.1).
 */
@Composable
private fun HeatmapLegend(
    isQuantitative: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(label = "Done", color = accentColor)
        LegendItem(label = "Partial", color = accentColor.copy(alpha = 0.35f), hasBorder = true, borderColor = accentColor)
        LegendItem(label = "Skipped", color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), symbol = "—")
        LegendItem(label = "Missed", color = MaterialTheme.colorScheme.surface, hasBorder = true, borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), symbol = "✕")
        LegendItem(label = "Unrecorded", color = MaterialTheme.colorScheme.surface, hasBorder = true, borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        LegendItem(label = "Rest", color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
    }
}

@Composable
private fun LegendItem(
    label: String,
    color: Color,
    hasBorder: Boolean = false,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
    symbol: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
                .then(
                    if (hasBorder) Modifier.border(
                        1.dp,
                        borderColor,
                        RoundedCornerShape(3.dp)
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (symbol != null) {
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    fontWeight = FontWeight.Bold,
                    color = borderColor
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Inspection dialog for tapping a heatmap cell.
 */
@Composable
fun HeatmapDayDetailDialog(
    day: HabitHistoryDay,
    habitName: String,
    onDismiss: () -> Unit,
    onEditRecord: (() -> Unit)? = null
) {
    val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
    val formattedDate = day.date.format(formatter)

    val canEdit = onEditRecord != null &&
        !day.date.isAfter(LocalDate.now()) &&
        day.status !is CalendarDayStatus.PreCreation &&
        day.status !is CalendarDayStatus.Paused

    val (statusLabel, statusColor, onStatusColor) = when (day.status) {
        is CalendarDayStatus.Completed -> Triple("Completed", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        is CalendarDayStatus.RecordedIncomplete -> Triple("Incomplete", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        is CalendarDayStatus.ExplicitMissed -> Triple("Missed", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        is CalendarDayStatus.Skipped -> Triple("Skipped", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.outline)
        CalendarDayStatus.ProjectedMissed -> Triple("Projected Missed", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        CalendarDayStatus.ProjectedRest -> Triple("Rest Day", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outline)
        CalendarDayStatus.Paused -> Triple("Paused", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.outline)
        CalendarDayStatus.PreCreation -> Triple("Pre-Creation", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outline)
        CalendarDayStatus.Future -> Triple("Upcoming", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outline)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = habitName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = onStatusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                when (val status = day.status) {
                    is CalendarDayStatus.Completed -> {
                        val unitStr = status.unit?.let { " $it" } ?: ""
                        if (status.measurementType == MeasurementType.BooleanChoice.TYPE_NAME) {
                            Text(
                                text = "Habit was completed and recorded for this day.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            val ratio = if (status.targetValue > 0.0) (status.actualValue / status.targetValue * 100).toInt() else 100
                            Text(
                                text = "Recorded: ${formatValue(status.actualValue)} / ${formatValue(status.targetValue)}$unitStr ($ratio% of target)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Target was achieved.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    is CalendarDayStatus.RecordedIncomplete -> {
                        val unitStr = status.unit?.let { " $it" } ?: ""
                        val ratio = if (status.targetValue > 0.0) (status.actualValue / status.targetValue * 100).toInt() else 0
                        Text(
                            text = "Recorded: ${formatValue(status.actualValue)} / ${formatValue(status.targetValue)}$unitStr ($ratio% of target)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Factual attempt recorded, but target was not reached.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    is CalendarDayStatus.ExplicitMissed -> {
                        Text(
                            text = "Explicitly recorded as missed for this day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        if (status.notes != null) {
                            Text(
                                text = "Note: ${status.notes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is CalendarDayStatus.Skipped -> {
                        Text(
                            text = "Skipped with permission. Active streak is preserved.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    CalendarDayStatus.ProjectedMissed -> {
                        Text(
                            text = "Projected missed based on the habit's current schedule. No factual record was logged for this date.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    CalendarDayStatus.ProjectedRest -> {
                        Text(
                            text = "Rest day. This habit was not scheduled on this day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    CalendarDayStatus.Paused -> {
                        Text(
                            text = "The habit was paused on this date. Paused days are not counted as missed and do not break streaks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    CalendarDayStatus.PreCreation -> {
                        Text(
                            text = "This date was before the habit was created.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    CalendarDayStatus.Future -> {
                        Text(
                            text = "Upcoming scheduled day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (canEdit) {
                    TextButton(onClick = {
                        onDismiss()
                        onEditRecord?.invoke()
                    }) {
                        Text("Edit Record")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

/**
 * Formats values cleanly (e.g. 50.0 -> "50", 2.5 -> "2.5").
 */
private fun formatValue(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        "%.1f".format(value)
    }
}

/**
 * Builds meaningful accessibility descriptions for TalkBack.
 */
fun buildCellAccessibilityText(day: HabitHistoryDay, isQuantitative: Boolean): String {
    val dateStr = day.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
    return when (val status = day.status) {
        is CalendarDayStatus.Completed -> {
            if (isQuantitative) {
                val unitStr = status.unit?.let { " $it" } ?: ""
                "$dateStr: Completed. ${formatValue(status.actualValue)} of ${formatValue(status.targetValue)}$unitStr."
            } else {
                "$dateStr: Completed."
            }
        }

        is CalendarDayStatus.RecordedIncomplete -> {
            val unitStr = status.unit?.let { " $it" } ?: ""
            "$dateStr: Incomplete. ${formatValue(status.actualValue)} of ${formatValue(status.targetValue)}$unitStr."
        }

        is CalendarDayStatus.ExplicitMissed -> "$dateStr: Explicitly marked as missed."
        is CalendarDayStatus.Skipped -> "$dateStr: Skipped."
        CalendarDayStatus.ProjectedMissed -> "$dateStr: Projected missed. No record logged for scheduled day."
        CalendarDayStatus.ProjectedRest -> "$dateStr: Rest day. Not scheduled."
        CalendarDayStatus.Paused -> "$dateStr: Paused."
        CalendarDayStatus.PreCreation -> "$dateStr: Before habit creation."
        CalendarDayStatus.Future -> "$dateStr: Upcoming."
    }
}
