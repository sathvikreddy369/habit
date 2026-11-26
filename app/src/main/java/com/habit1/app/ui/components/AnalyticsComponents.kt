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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habit1.app.domain.model.HabitInsight
import com.habit1.app.domain.model.PeriodComparison
import com.habit1.app.domain.model.RecordBreakdown

/**
 * Breakdown of record instances in a period:
 * Completed, Partial, Skipped, Missed, Rest.
 */
@Composable
fun RecordBreakdownCard(
    breakdown: RecordBreakdown,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Record Distribution",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Factual breakdown of occurrences across this period",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Multi-segment stacked distribution bar
            val totalInstances = (breakdown.completedCount + breakdown.partialCount + breakdown.skippedCount + breakdown.missedCount).coerceAtLeast(1)
            val completedRatio = breakdown.completedCount.toFloat() / totalInstances
            val partialRatio = breakdown.partialCount.toFloat() / totalInstances
            val skippedRatio = breakdown.skippedCount.toFloat() / totalInstances
            val missedRatio = breakdown.missedCount.toFloat() / totalInstances

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            ) {
                if (completedRatio > 0f) {
                    Box(modifier = Modifier.weight(completedRatio).height(10.dp).background(Color(0xFF2E7D32)))
                }
                if (partialRatio > 0f) {
                    Box(modifier = Modifier.weight(partialRatio).height(10.dp).background(Color(0xFFFB8C00)))
                }
                if (skippedRatio > 0f) {
                    Box(modifier = Modifier.weight(skippedRatio).height(10.dp).background(Color(0xFF8E24AA)))
                }
                if (missedRatio > 0f) {
                    Box(modifier = Modifier.weight(missedRatio).height(10.dp).background(MaterialTheme.colorScheme.error))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Legend Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BreakdownLegendItem(label = "Completed", count = breakdown.completedCount, color = Color(0xFF2E7D32))
                BreakdownLegendItem(label = "Partial", count = breakdown.partialCount, color = Color(0xFFFB8C00))
                BreakdownLegendItem(label = "Skipped", count = breakdown.skippedCount, color = Color(0xFF8E24AA))
                BreakdownLegendItem(label = "Missed", count = breakdown.missedCount, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun BreakdownLegendItem(
    label: String,
    count: Int,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Period comparison card comparing the selected range with preceding equivalent period.
 */
@Composable
fun PeriodComparisonCard(
    comparison: PeriodComparison,
    modifier: Modifier = Modifier
) {
    if (!comparison.isSufficientData) return

    val isPositive = comparison.rateDelta >= 0
    val deltaColor = if (isPositive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    val icon = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown
    val sign = if (isPositive) "+" else ""

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Period Comparison",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "vs preceding equivalent period (${comparison.previousCompletionRate.toInt()}% completion)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = deltaColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, deltaColor.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = deltaColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$sign${comparison.rateDelta.toInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = deltaColor
                    )
                }
            }
        }
    }
}

/**
 * Compact, deterministic insights section displaying factual analytical findings.
 */
@Composable
fun AnalyticsInsightsSection(
    insights: List<HabitInsight>,
    onInspectHabit: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (insights.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Observations & Patterns",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            insights.forEach { insight ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    onClick = {
                        if (insight.habitId != null && onInspectHabit != null) {
                            onInspectHabit(insight.habitId)
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = insight.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = insight.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        insight.metricValue?.let { metric ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = metric,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
