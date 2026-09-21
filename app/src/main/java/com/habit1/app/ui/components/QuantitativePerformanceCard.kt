package com.habit1.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habit1.app.domain.model.CalendarDayStatus
import com.habit1.app.domain.model.Habit
import com.habit1.app.domain.model.HabitAnalyticsSummary
import com.habit1.app.domain.model.HabitHistoryDay
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.ui.history.HeatmapRangePreset

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
 *
 * Rules:
 * - Each recorded day is evaluated against its immutable historical snapshot target.
 * - Recorded 0 is treated as a factual recorded attempt (Below target).
 * - Projected missed, rest, paused, pre-creation, and future days have NO record and are excluded.
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

            // Unrecorded days are not included in recorded-day distributions
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
 * Clean, accessible quantitative analytics component native to Habit1.
 *
 * Visualizes:
 * 1. 2x2 Metric Summary Tiles (Total, Daily Target, Average on Recorded, Target Achievement)
 * 2. Actual vs Target Visual Comparison
 * 3. Performance Distribution across recorded attempts (Below, At, Above target)
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

    val targetValue = remember(habit.measurement) {
        when (val m = habit.measurement) {
            is MeasurementType.Count -> m.target.toDouble()
            is MeasurementType.Duration -> m.targetMinutes.toDouble()
            is MeasurementType.Quantity -> m.target
            is MeasurementType.BooleanChoice -> 1.0
        }
    }

    val cardA11yDescription = remember(summary, qStats, unitStr, targetValue) {
        val totalFormatted = formatQuantityValue(qStats.totalActualValue)
        val avgFormatted = qStats.averageOnRecordedDays?.let { formatQuantityValue(it) } ?: "none"
        val targetFormatted = formatQuantityValue(targetValue)
        val rateFormatted = "${qStats.targetAchievementRate.toInt()}%"
        "Quantitative performance for ${habit.name}. Total volume: $totalFormatted $unitStr. Average on recorded days: $avgFormatted $unitStr. Daily target: $targetFormatted $unitStr. Target achievement rate: $rateFormatted."
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardA11yDescription },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
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

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Core Summary Metrics 2x2 Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    label = "Average (Recorded)",
                    value = qStats.averageOnRecordedDays?.let { "${formatQuantityValue(it)} $unitStr" } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Target Achievement",
                    value = "${qStats.targetAchievementRate.toInt()}%",
                    modifier = Modifier.weight(1f)
                )
            }

            // Secondary completed-days average if available
            qStats.averageOnCompletedDays?.let { avgCompleted ->
                if (qStats.averageOnRecordedDays != null && avgCompleted != qStats.averageOnRecordedDays) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Average on completed days: ${formatQuantityValue(avgCompleted)} $unitStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Actual vs Target Visual Comparison
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

                    // Comparison Status Pill
                    Text(
                        text = comparisonLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (diff >= 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Performance Distribution / Variability
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
