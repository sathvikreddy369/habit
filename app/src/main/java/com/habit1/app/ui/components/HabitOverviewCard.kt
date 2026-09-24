package com.habit1.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Overview statistics card with circular progress score ring and exact calendar-based period completion indicators.
 */
@Composable
fun HabitOverviewCard(
    scorePercent: Int,
    currentStreak: Int = 0,
    longestStreak: Int = 0,
    weekCompleted: Int,
    weekTotal: Int = 7,
    monthCompleted: Int,
    monthTotal: Int,
    yearCompleted: Int,
    yearTotal: Int,
    totalCompletions: Int,
    scheduledDays: Int = 0,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Primary Metrics Row: Donut + Completion Rate & Streak Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Donut score ring
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(54.dp)
                    ) {
                        val sweepAngle = (scorePercent / 100f).coerceIn(0f, 1f) * 360f
                        Canvas(modifier = Modifier.size(50.dp)) {
                            val strokeWidth = 5.dp.toPx()
                            // Track
                            drawCircle(
                                color = accentColor.copy(alpha = 0.15f),
                                style = Stroke(width = strokeWidth)
                            )
                            // Progress Arc
                            if (sweepAngle > 0f) {
                                drawArc(
                                    color = accentColor,
                                    startAngle = -90f,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                        }
                        Text(
                            text = "$scorePercent%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Completion Rate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val totalSummaryText = if (scheduledDays > 0) {
                            "$totalCompletions of $scheduledDays scheduled"
                        } else {
                            "$totalCompletions total completed"
                        }
                        Text(
                            text = totalSummaryText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatMetricItem(
                        value = "$currentStreak",
                        label = "Streak",
                        accentColor = accentColor
                    )
                    StatMetricItem(
                        value = "$longestStreak",
                        label = "Best",
                        accentColor = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Period Indicators: Week, Month, Year
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatMetricItem(
                    value = "$weekCompleted/$weekTotal",
                    label = "Week",
                    accentColor = accentColor
                )
                StatMetricItem(
                    value = "$monthCompleted/$monthTotal",
                    label = "Month",
                    accentColor = accentColor
                )
                StatMetricItem(
                    value = "$yearCompleted/$yearTotal",
                    label = "Year",
                    accentColor = accentColor
                )
            }
        }
    }
}

@Composable
private fun StatMetricItem(
    value: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
