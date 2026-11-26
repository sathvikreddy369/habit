package com.habit1.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitAnalyticsSummary
import com.habit1.app.domain.model.HabitHistoryDay
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.ui.history.HeatmapRangePreset

/**
 * Chart mode for quantitative analytics (Decision 5.2.1).
 */
enum class QuantitativeChartMode(val label: String) {
    DAILY_BARS("Daily Bars"),
    CUMULATIVE("Cumulative Running Total")
}

/**
 * Performance distribution breakdown of recorded days relative to their snapshot target.
 */
data class QuantitativeDistribution(
    val totalRecordedDays: Int,
    val belowTargetCount: Int,
    val atTargetCount: Int,
    val aboveTargetCount: Int
) {
    val belowTargetPercent: Float
        get() = if (totalRecordedDays > 0) (belowTargetCount.toFloat() / totalRecordedDays) * 100f else 0f

    val atTargetPercent: Float
        get() = if (totalRecordedDays > 0) (atTargetCount.toFloat() / totalRecordedDays) * 100f else 0f

    val aboveTargetPercent: Float
        get() = if (totalRecordedDays > 0) (aboveTargetCount.toFloat() / totalRecordedDays) * 100f else 0f
}

/**
 * Calculates distribution of recorded days from historical snapshots.
 */
fun calculateQuantitativeDistribution(
    dailyBreakdown: List<HabitHistoryDay>
): QuantitativeDistribution {
    var below = 0
    var at = 0
    var above = 0

    dailyBreakdown.forEach { day ->
        when (val status = day.status) {
            is CalendarDayStatus.Completed -> {
                when {
                    status.actualValue > status.targetValue -> above++
                    status.actualValue < status.targetValue -> below++
                    else -> at++
                }
            }

            is CalendarDayStatus.RecordedIncomplete -> {
                below++
            }

            is CalendarDayStatus.ExplicitMissed -> {
                below++
            }

            is CalendarDayStatus.Skipped,
            is CalendarDayStatus.ProjectedMissed,
            is CalendarDayStatus.ProjectedRest,
            is CalendarDayStatus.Paused,
            is CalendarDayStatus.PreCreation,
            is CalendarDayStatus.Future -> Unit
        }
    }

    val total = below + at + above
    return QuantitativeDistribution(
        totalRecordedDays = total,
        belowTargetCount = below,
        atTargetCount = at,
        aboveTargetCount = above
    )
}

/**
 * Formats a measurement unit string respecting the habit's configuration.
 */
fun formatMeasurementUnit(measurement: MeasurementType): String {
    return when (measurement) {
        is MeasurementType.Count -> measurement.unit ?: "reps"
        is MeasurementType.Duration -> "min"
        is MeasurementType.Quantity -> measurement.unit
        is MeasurementType.BooleanChoice -> ""
    }
}

/**
 * Formats a quantity value, omitting decimals when representing whole numbers.
 */
fun formatQuantityValue(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        "%.1f".format(value)
    }
}

/**
 * Quantitative analytics component native to Habit1 (Decision 5.2).
 *
 * Implements:
 * 1. Compact toggle between Daily Bars and Cumulative Running Total (Decision 5.2.1).
 * 2. Three Clearly Defined Quantitative Metrics (Decision 5.2.2):
 *    - Total Volume (disclosing bonus activity per Decision 5.2.3)
 *    - Active Day Average (Total Volume / Days with actual_value > 0)
 *    - Scheduled Daily Average (Volume on Scheduled Days / (Eligible Scheduled Days - Count(SKIPPED)))
 * 3. Daily target & performance distribution across recorded attempts.
 */
@Composable
fun QuantitativePerformanceCard(
    habit: Habit,
    summary: HabitAnalyticsSummary,
    selectedPreset: HeatmapRangePreset,
    modifier: Modifier = Modifier
) {
    val qStats = summary.quantitativeStats ?: return
    val unitStr = remember(habit.measurement) { formatMeasurementUnit(habit.measurement) }
    val distribution = remember(summary.dailyBreakdown) {
        calculateQuantitativeDistribution(summary.dailyBreakdown)
    }

    var chartMode by remember { mutableStateOf(QuantitativeChartMode.DAILY_BARS) }

    val targetValue = remember(habit.measurement) {
        when (val m = habit.measurement) {
            is MeasurementType.Count -> m.target.toDouble()
            is MeasurementType.Duration -> m.targetMinutes.toDouble()
            is MeasurementType.Quantity -> m.target
            is MeasurementType.BooleanChoice -> 1.0
        }
    }

    // Cumulative progression calculation
    val cumulativeSteps = remember(summary.dailyBreakdown) {
        var runningTotal = 0.0
        summary.dailyBreakdown.map { day ->
            val dayValue = when (val s = day.status) {
                is CalendarDayStatus.Completed -> s.actualValue
                is CalendarDayStatus.RecordedIncomplete -> s.actualValue
                else -> 0.0
            }
            runningTotal += dayValue
            Pair(day.date, runningTotal)
        }
    }

    val cardA11yDescription = remember(summary, qStats, unitStr, targetValue) {
        val totalFormatted = formatQuantityValue(qStats.totalActualValue)
        val activeAvgFormatted = qStats.activeDayAverage?.let { formatQuantityValue(it) } ?: "none"
        val schedAvgFormatted = qStats.scheduledDailyAverage?.let { formatQuantityValue(it) } ?: "none"
        val targetFormatted = formatQuantityValue(targetValue)
        "Quantitative performance for ${habit.name}. Total volume: $totalFormatted $unitStr. Active day average: $activeAvgFormatted $unitStr. Scheduled daily average: $schedAvgFormatted $unitStr. Daily target: $targetFormatted $unitStr."
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardA11yDescription },
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
            // Header with Unit Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quantitative Performance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (unitStr.isNotEmpty()) unitStr else selectedPreset.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mode Switcher: Daily Bars vs Cumulative Running Total (Decision 5.2.1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = chartMode == QuantitativeChartMode.DAILY_BARS,
                    onClick = { chartMode = QuantitativeChartMode.DAILY_BARS },
                    label = { Text("Daily Values", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(32.dp)
                )
                FilterChip(
                    selected = chartMode == QuantitativeChartMode.CUMULATIVE,
                    onClick = { chartMode = QuantitativeChartMode.CUMULATIVE },
                    label = { Text("Cumulative Total", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Core Quantitative Metrics (Decision 5.2.2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    label = "Total Volume",
                    value = "${formatQuantityValue(qStats.totalActualValue)} $unitStr",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Daily Target",
                    value = "${formatQuantityValue(targetValue)} $unitStr",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    label = "Active Day Avg",
                    value = qStats.activeDayAverage?.let { "${formatQuantityValue(it)} $unitStr" } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Scheduled Avg",
                    value = qStats.scheduledDailyAverage?.let { "${formatQuantityValue(it)} $unitStr" } ?: "—",
                    modifier = Modifier.weight(1f)
                )
            }

            // Bonus Activity Disclosure (Decision 5.2.3)
            if (qStats.bonusDaysCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val bonusUnit = if (unitStr.isNotEmpty()) " $unitStr" else ""
                val daysPlural = if (qStats.bonusDaysCount > 1) "s" else ""
                Text(
                    text = "${formatQuantityValue(qStats.totalActualValue)}$bonusUnit total · ${formatQuantityValue(qStats.bonusVolume)}$bonusUnit on ${qStats.bonusDaysCount} bonus day$daysPlural",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Chart Section: Daily Bars vs Cumulative Running Total
            when (chartMode) {
                QuantitativeChartMode.DAILY_BARS -> {
                    // Actual vs Target Comparison View
                    Text(
                        text = "Actual vs Target Comparison",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (qStats.averageOnRecordedDays == null) {
                        Text(
                            text = "No recorded days in this period to compare.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        val avgActual = qStats.averageOnRecordedDays
                        val maxRef = maxOf(targetValue, avgActual) * 1.15
                        val actualRatio = (avgActual / maxRef).toFloat().coerceIn(0f, 1f)
                        val targetRatio = (targetValue / maxRef).toFloat().coerceIn(0f, 1f)

                        val diff = avgActual - targetValue
                        val comparisonLabel = when {
                            diff > 0.0 -> "Above target (+${formatQuantityValue(diff)} $unitStr)"
                            diff < 0.0 -> "Below target (${formatQuantityValue(diff)} $unitStr)"
                            else -> "Exact target reached"
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription =
                                        "Average actual: ${formatQuantityValue(avgActual)} $unitStr. Daily target: ${formatQuantityValue(targetValue)} $unitStr. $comparisonLabel."
                                },
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Average Actual Bar
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Average actual",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${formatQuantityValue(avgActual)} $unitStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (avgActual >= targetValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(actualRatio)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(
                                                if (avgActual >= targetValue) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.tertiary
                                            )
                                    )
                                }
                            }

                            // Daily Target Bar
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Daily target",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${formatQuantityValue(targetValue)} $unitStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(targetRatio)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(MaterialTheme.colorScheme.outline)
                                    )
                                }
                            }

                            // Comparison Label
                            Text(
                                text = comparisonLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (diff >= 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                QuantitativeChartMode.CUMULATIVE -> {
                    // Cumulative Running Total View (Decision 5.2.1)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Cumulative Running Total",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Progressive volume accumulation across ${selectedPreset.label} (${formatQuantityValue(qStats.totalActualValue)} $unitStr total)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val finalTotal = cumulativeSteps.lastOrNull()?.second ?: 0.0
                        val maxCumulative = maxOf(finalTotal, 1.0)

                        // Stepped horizontal cumulative progress summary
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (finalTotal > 0.0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth((finalTotal / maxCumulative).toFloat().coerceIn(0f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Compact milestone breakdown
                        val eligibleDaysCount = summary.eligibleScheduledDays
                        val scheduledExpectedTotal = eligibleDaysCount * targetValue
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recorded: ${formatQuantityValue(finalTotal)} $unitStr",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (scheduledExpectedTotal > 0.0) {
                                Text(
                                    text = "Target pacing: ${formatQuantityValue(scheduledExpectedTotal)} $unitStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Performance Distribution across recorded attempts
            Text(
                text = "Performance Distribution",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${distribution.totalRecordedDays} recorded days in this period",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (distribution.totalRecordedDays == 0) {
                Text(
                    text = "No recorded attempts in this period to evaluate distribution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                val distA11y =
                    "Distribution across ${distribution.totalRecordedDays} recorded days: ${distribution.belowTargetCount} below target, ${distribution.atTargetCount} at target, ${distribution.aboveTargetCount} above target."

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = distA11y }
                ) {
                    // Segmented horizontal distribution bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (distribution.belowTargetCount > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(distribution.belowTargetPercent.coerceAtLeast(1f))
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.tertiary)
                            )
                        }
                        if (distribution.atTargetCount > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(distribution.atTargetPercent.coerceAtLeast(1f))
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        if (distribution.aboveTargetCount > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(distribution.aboveTargetPercent.coerceAtLeast(1f))
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.secondary)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Legend & Counts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DistributionLegendItem(
                            color = MaterialTheme.colorScheme.tertiary,
                            label = "Below",
                            count = distribution.belowTargetCount,
                            percent = distribution.belowTargetPercent.toInt()
                        )
                        DistributionLegendItem(
                            color = MaterialTheme.colorScheme.primary,
                            label = "Target",
                            count = distribution.atTargetCount,
                            percent = distribution.atTargetPercent.toInt()
                        )
                        DistributionLegendItem(
                            color = MaterialTheme.colorScheme.secondary,
                            label = "Above",
                            count = distribution.aboveTargetCount,
                            percent = distribution.aboveTargetPercent.toInt()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DistributionLegendItem(
    color: Color,
    label: String,
    count: Int,
    percent: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
            color = color
        ) {}
        Text(
            text = "$label: $count ($percent%)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
