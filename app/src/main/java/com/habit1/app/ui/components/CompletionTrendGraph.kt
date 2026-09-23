package com.habit1.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.HabitAnalyticsSummary
import com.habit1.app.domain.model.HabitHistoryDay
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.ui.history.HeatmapRangePreset
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Data model for an individual plotted data point on the completion trend graph.
 */
data class TrendPoint(
    val dayIndex: Int,
    val totalRangeDays: Int,
    val date: LocalDate,
    val status: CalendarDayStatus,
    val percentage: Float,
    val isFactual: Boolean,
    val isTargetAchieved: Boolean,
    val accessibilityText: String,
    val day: HabitHistoryDay
)

/**
 * Extracts plottable trend points from [HabitAnalyticsSummary.dailyBreakdown].
 *
 * Rules:
 * - Completed: 100% (Boolean) or (actual / target * 100)% (Quantitative)
 * - RecordedIncomplete: 0% (Boolean) or (actual / target * 100)% (Quantitative)
 * - ProjectedMissed: 0%, marked as non-factual projection (hollow marker)
 * - Rest, Paused, PreCreation, Future: not plotted (no points, no line continuity invented)
 */
fun extractTrendPoints(summary: HabitAnalyticsSummary): List<TrendPoint> {
    val isQuantitative = summary.habit.measurement !is MeasurementType.BooleanChoice
    val totalDays = summary.range.dayCount.coerceAtLeast(1)
    val points = mutableListOf<TrendPoint>()

    val dateDescFormatter = DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault())

    summary.dailyBreakdown.forEachIndexed { index, day ->
        when (val status = day.status) {
            is CalendarDayStatus.Completed -> {
                val pct = if (isQuantitative && status.targetValue > 0) {
                    ((status.actualValue / status.targetValue) * 100.0).toFloat()
                } else {
                    100.0f
                }
                val formattedActual = formatTrendValue(status.actualValue)
                val formattedTarget = formatTrendValue(status.targetValue)
                val unitStr = status.unit?.let { " $it" } ?: ""
                val desc = if (isQuantitative) {
                    "${day.date.format(dateDescFormatter)} — completed — $formattedActual of $formattedTarget$unitStr (${pct.toInt()}%)"
                } else {
                    "${day.date.format(dateDescFormatter)} — completed — 100%"
                }
                points.add(
                    TrendPoint(
                        dayIndex = index,
                        totalRangeDays = totalDays,
                        date = day.date,
                        status = status,
                        percentage = pct,
                        isFactual = true,
                        isTargetAchieved = true,
                        accessibilityText = desc,
                        day = day
                    )
                )
            }

            is CalendarDayStatus.RecordedIncomplete -> {
                val pct = if (isQuantitative && status.targetValue > 0) {
                    ((status.actualValue / status.targetValue) * 100.0).toFloat()
                } else {
                    0.0f
                }
                val formattedActual = formatTrendValue(status.actualValue)
                val formattedTarget = formatTrendValue(status.targetValue)
                val unitStr = status.unit?.let { " $it" } ?: ""
                val desc = if (isQuantitative) {
                    "${day.date.format(dateDescFormatter)} — partial — $formattedActual of $formattedTarget$unitStr (${pct.toInt()}%)"
                } else {
                    "${day.date.format(dateDescFormatter)} — incomplete — 0%"
                }
                points.add(
                    TrendPoint(
                        dayIndex = index,
                        totalRangeDays = totalDays,
                        date = day.date,
                        status = status,
                        percentage = pct,
                        isFactual = true,
                        isTargetAchieved = false,
                        accessibilityText = desc,
                        day = day
                    )
                )
            }

            is CalendarDayStatus.ProjectedMissed -> {
                val desc = "${day.date.format(dateDescFormatter)} — projected missed"
                points.add(
                    TrendPoint(
                        dayIndex = index,
                        totalRangeDays = totalDays,
                        date = day.date,
                        status = status,
                        percentage = 0.0f,
                        isFactual = false,
                        isTargetAchieved = false,
                        accessibilityText = desc,
                        day = day
                    )
                )
            }

            // Non-scheduled, paused, pre-creation, and future days do NOT have plotted points
            is CalendarDayStatus.ProjectedRest,
            is CalendarDayStatus.Paused,
            is CalendarDayStatus.PreCreation,
            is CalendarDayStatus.Future -> Unit
        }
    }
    return points
}

private fun formatTrendValue(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        "%.1f".format(value)
    }
}

/**
 * Native Jetpack Compose completion trend graph.
 *
 * Visualizes consistency trajectory over time:
 * - Line segments connect consecutive scheduled days only; rest days produce gaps.
 * - Solid markers for factual records; hollow ring markers for projected misses.
 * - 100% Target reference line for quantitative habits.
 * - Tap interaction to inspect exact day details.
 * - Full TalkBack semantic descriptions.
 */
@Composable
fun CompletionTrendGraph(
    summary: HabitAnalyticsSummary,
    selectedPreset: HeatmapRangePreset,
    onDayClick: (HabitHistoryDay) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val isQuantitative = summary.habit.measurement !is MeasurementType.BooleanChoice
    val points = remember(summary) { extractTrendPoints(summary) }
    var selectedPointDate by remember(summary) { mutableStateOf<LocalDate?>(null) }
    val textMeasurer = rememberTextMeasurer()

    // Determine scale: Y axis 0% to 100% (or higher if values exceed target)
    val maxPercentage = remember(points) { points.maxOfOrNull { it.percentage } ?: 100f }
    val maxY = remember(maxPercentage, isQuantitative) {
        if (isQuantitative && maxPercentage > 100f) {
            (ceil(maxPercentage / 25f) * 25f).coerceAtLeast(100f)
        } else {
            100f
        }
    }

    val overallDescription = remember(summary, isQuantitative) {
        val habitName = summary.habit.name
        val rangeStr = "${summary.range.startDate} to ${summary.range.endDate}"
        val rateStr = "${summary.completionRate.toInt()}%"
        val schedStr = "${summary.completedDays} of ${summary.scheduledDays} scheduled days completed"
        "Completion trend graph for $habitName from $rangeStr. Completion rate: $rateStr. $schedStr."
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = overallDescription },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 1. Header & Metric Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Completion Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${summary.completionRate.toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle factual metrics
            val subtitleText = if (isQuantitative) {
                val (targetVal, unitStr) = when (val m = summary.habit.measurement) {
                    is MeasurementType.Count -> Pair(m.target.toDouble(), m.unit?.let { " $it" } ?: "")
                    is MeasurementType.Duration -> Pair(m.targetMinutes.toDouble(), " min")
                    is MeasurementType.Quantity -> Pair(m.target, " ${m.unit}")
                    is MeasurementType.BooleanChoice -> Pair(1.0, "")
                }
                val avgStr = summary.averageActualValue?.let { " • Avg: ${formatTrendValue(it)}" } ?: ""
                val targetStr = " • Target: ${formatTrendValue(targetVal)}"
                "${summary.completedDays} / ${summary.scheduledDays} target reached$targetStr$unitStr$avgStr"
            } else {
                "${summary.completedDays} / ${summary.scheduledDays} scheduled days completed"
            }

            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Chart Canvas or Empty State
            if (points.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No scheduled days in this range",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Consistency trend will appear as scheduled days occur.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                val primaryColor = accentColor
                val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                val outlineColor = MaterialTheme.colorScheme.outlineVariant
                val warningColor = MaterialTheme.colorScheme.tertiary
                val surfaceColor = MaterialTheme.colorScheme.surface
                val errorColor = MaterialTheme.colorScheme.error

                val labelStyle = TextStyle(
                    fontSize = 10.sp,
                    color = onSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )

                val density = LocalDensity.current

                val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val chartHeight = if (isLandscape) 280.dp else 200.dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(chartHeight)
                            .pointerInput(points, summary) {
                                detectTapGestures { offset ->
                                    val leftPad = with(density) { 36.dp.toPx() }
                                    val rightPad = with(density) { 16.dp.toPx() }
                                    val topPad = with(density) { 16.dp.toPx() }
                                    val bottomPad = with(density) { 24.dp.toPx() }
                                    val chartWidth = size.width - leftPad - rightPad
                                    val chartHeight = size.height - topPad - bottomPad
                                    val totalDays = summary.range.dayCount.coerceAtLeast(1)

                                    var closestPoint: TrendPoint? = null
                                    var minDistance = Float.MAX_VALUE
                                    val maxTapRadius = with(density) { 32.dp.toPx() }

                                    for (pt in points) {
                                        val x = if (totalDays > 1) {
                                            leftPad + (pt.dayIndex.toFloat() / (totalDays - 1).toFloat()) * chartWidth
                                        } else {
                                            leftPad + chartWidth / 2f
                                        }
                                        val y = topPad + (1f - (pt.percentage / maxY).coerceIn(0f, 1f)) * chartHeight
                                        val dist = hypot(x - offset.x, y - offset.y)
                                        if (dist < minDistance && dist <= maxTapRadius) {
                                            minDistance = dist
                                            closestPoint = pt
                                        }
                                    }

                                    closestPoint?.let {
                                        selectedPointDate = it.date
                                        onDayClick(it.day)
                                    }
                                }
                            }
                    ) {
                        val leftPad = 36.dp.toPx()
                        val rightPad = 16.dp.toPx()
                        val topPad = 16.dp.toPx()
                        val bottomPad = 24.dp.toPx()
                        val chartWidth = size.width - leftPad - rightPad
                        val chartHeight = size.height - topPad - bottomPad
                        val totalDays = summary.range.dayCount.coerceAtLeast(1)

                        // 1. Draw Horizontal Gridlines & Y-Axis Labels
                        val ySteps = if (isQuantitative && maxY > 100f) {
                            listOf(0f, 50f, 100f, maxY)
                        } else {
                            listOf(0f, 50f, 100f)
                        }

                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                        ySteps.forEach { yVal ->
                            val yPos = topPad + (1f - (yVal / maxY)) * chartHeight
                            val isTargetLine = isQuantitative && yVal == 100f

                            // Line
                            drawLine(
                                color = if (isTargetLine) primaryColor.copy(alpha = 0.5f) else outlineColor.copy(alpha = 0.3f),
                                start = Offset(leftPad, yPos),
                                end = Offset(leftPad + chartWidth, yPos),
                                strokeWidth = if (isTargetLine) 1.5.dp.toPx() else 1.dp.toPx(),
                                pathEffect = if (isTargetLine) dashEffect else null
                            )

                            // Y label
                            val labelText = "${yVal.toInt()}%"
                            val measuredText = textMeasurer.measure(labelText, labelStyle)
                            drawText(
                                textLayoutResult = measuredText,
                                topLeft = Offset(
                                    x = leftPad - measuredText.size.width - 6.dp.toPx(),
                                    y = yPos - measuredText.size.height / 2f
                                )
                            )
                        }

                        // 2. Draw Target Label if quantitative
                        if (isQuantitative) {
                            val targetY = topPad + (1f - (100f / maxY)) * chartHeight
                            val targetLabel = "Target"
                            val measuredTarget = textMeasurer.measure(
                                targetLabel,
                                labelStyle.copy(color = primaryColor, fontWeight = FontWeight.SemiBold)
                            )
                            drawText(
                                textLayoutResult = measuredTarget,
                                topLeft = Offset(
                                    x = leftPad + chartWidth - measuredTarget.size.width - 4.dp.toPx(),
                                    y = targetY - measuredTarget.size.height - 2.dp.toPx()
                                )
                            )
                        }

                        // 3. Draw X-Axis Labels (Date markers)
                        val xLabelCount = when (selectedPreset) {
                            HeatmapRangePreset.THIS_WEEK -> 4
                            HeatmapRangePreset.THIS_MONTH -> 4
                            HeatmapRangePreset.THIS_YEAR -> 6
                        }

                        val xFormatter = when (selectedPreset) {
                            HeatmapRangePreset.THIS_WEEK -> DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())
                            HeatmapRangePreset.THIS_MONTH -> DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
                            HeatmapRangePreset.THIS_YEAR -> DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
                        }

                        val step = (totalDays - 1).toFloat() / (xLabelCount - 1).coerceAtLeast(1)
                        for (i in 0 until xLabelCount) {
                            val dayIdx = (i * step).toInt().coerceIn(0, totalDays - 1)
                            val dateAtIdx = summary.range.startDate.plusDays(dayIdx.toLong())
                            val xPos = if (totalDays > 1) {
                                leftPad + (dayIdx.toFloat() / (totalDays - 1).toFloat()) * chartWidth
                            } else {
                                leftPad + chartWidth / 2f
                            }
                            val labelText = dateAtIdx.format(xFormatter)
                            val measuredDate = textMeasurer.measure(labelText, labelStyle)
                            drawText(
                                textLayoutResult = measuredDate,
                                topLeft = Offset(
                                    x = (xPos - measuredDate.size.width / 2f).coerceIn(
                                        leftPad,
                                        leftPad + chartWidth - measuredDate.size.width
                                    ),
                                    y = topPad + chartHeight + 6.dp.toPx()
                                )
                            )
                        }

                        // 4. Calculate Coordinates for Plotted Points
                        val pointCoords = points.map { pt ->
                            val x = if (totalDays > 1) {
                                leftPad + (pt.dayIndex.toFloat() / (totalDays - 1).toFloat()) * chartWidth
                            } else {
                                leftPad + chartWidth / 2f
                            }
                            val clampedYRatio = (pt.percentage / maxY).coerceIn(0f, 1f)
                            val y = topPad + (1f - clampedYRatio) * chartHeight
                            Pair(x, y)
                        }

                        // 5. Draw Line Segments (Consecutive Scheduled Days Only!)
                        for (i in 0 until points.size - 1) {
                            val p1 = points[i]
                            val p2 = points[i + 1]

                            // Only connect if consecutive civil days (p1.date + 1 day == p2.date)
                            // Rest days / paused days break the line to prevent inventing continuity!
                            if (p1.date.plusDays(1) == p2.date) {
                                val c1 = pointCoords[i]
                                val c2 = pointCoords[i + 1]

                                val bothFactual = p1.isFactual && p2.isFactual
                                val lineColor = if (bothFactual) {
                                    primaryColor
                                } else {
                                    onSurfaceVariant.copy(alpha = 0.4f)
                                }

                                drawLine(
                                    color = lineColor,
                                    start = Offset(c1.first, c1.second),
                                    end = Offset(c2.first, c2.second),
                                    strokeWidth = 2.dp.toPx(),
                                    pathEffect = if (!bothFactual) dashEffect else null
                                )
                            }
                        }

                        // 6. Draw Point Markers
                        points.forEachIndexed { i, pt ->
                            val (px, py) = pointCoords[i]
                            val isSelected = pt.date == selectedPointDate

                            // Outer selection halo
                            if (isSelected) {
                                drawCircle(
                                    color = primaryColor.copy(alpha = 0.25f),
                                    radius = 8.dp.toPx(),
                                    center = Offset(px, py)
                                )
                            }

                            when {
                                // Completed factual
                                pt.status is CalendarDayStatus.Completed -> {
                                    drawCircle(
                                        color = primaryColor,
                                        radius = 4.5.dp.toPx(),
                                        center = Offset(px, py)
                                    )
                                    drawCircle(
                                        color = surfaceColor,
                                        radius = 4.5.dp.toPx(),
                                        center = Offset(px, py),
                                        style = Stroke(width = 1.dp.toPx())
                                    )
                                }

                                // Incomplete factual
                                pt.status is CalendarDayStatus.RecordedIncomplete -> {
                                    drawCircle(
                                        color = warningColor,
                                        radius = 4.5.dp.toPx(),
                                        center = Offset(px, py)
                                    )
                                    drawCircle(
                                        color = surfaceColor,
                                        radius = 4.5.dp.toPx(),
                                        center = Offset(px, py),
                                        style = Stroke(width = 1.dp.toPx())
                                    )
                                }

                                // Projected missed (Hollow ring marker)
                                pt.status is CalendarDayStatus.ProjectedMissed -> {
                                    drawCircle(
                                        color = surfaceColor,
                                        radius = 4.dp.toPx(),
                                        center = Offset(px, py)
                                    )
                                    drawCircle(
                                        color = errorColor.copy(alpha = 0.8f),
                                        radius = 4.dp.toPx(),
                                        center = Offset(px, py),
                                        style = Stroke(width = 1.5.dp.toPx())
                                    )
                                }

                                else -> Unit
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Compact Legend
            TrendGraphLegend(isQuantitative = isQuantitative, accentColor = accentColor)
        }
    }
}

/**
 * Compact legend explaining point marker styles and reference lines.
 */
@Composable
private fun TrendGraphLegend(
    isQuantitative: Boolean,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Completed / Target Reached
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                modifier = Modifier.size(8.dp),
                shape = CircleShape,
                color = accentColor
            ) {}
            Text(
                text = if (isQuantitative) "Achieved" else "Completed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Incomplete / Partial
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                modifier = Modifier.size(8.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiary
            ) {}
            Text(
                text = if (isQuantitative) "Partial" else "Incomplete",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Projected Missed
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .border(
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.8f)),
                        CircleShape
                    )
            )
            Text(
                text = "Projected miss",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Target Line (if quantitative)
        if (isQuantitative) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "---",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Target",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
