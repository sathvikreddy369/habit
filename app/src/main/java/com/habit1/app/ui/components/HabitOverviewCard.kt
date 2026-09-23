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
    weekCompleted: Int,
    weekTotal: Int = 7,
    monthCompleted: Int,
    monthTotal: Int,
    yearCompleted: Int,
    yearTotal: Int,
    totalCompletions: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Donut score ring with center score percent
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(56.dp)
            ) {
                val sweepAngle = (scorePercent / 100f).coerceIn(0f, 1f) * 360f
                Canvas(modifier = Modifier.size(52.dp)) {
                    val strokeWidth = 6.dp.toPx()
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

            // Stat 1: Week (exact 7 days)
            StatMetricItem(
                value = "$weekCompleted/$weekTotal",
                label = "Week",
                accentColor = accentColor
            )

            // Stat 2: Month (exact days in month)
            StatMetricItem(
                value = "$monthCompleted/$monthTotal",
                label = "Month",
                accentColor = accentColor
            )

            // Stat 3: Year (exact 365 or 366 days)
            StatMetricItem(
                value = "$yearCompleted/$yearTotal",
                label = "Year",
                accentColor = accentColor
            )

            // Stat 4: Total completed
            StatMetricItem(
                value = "$totalCompletions",
                label = "Total",
                accentColor = accentColor
            )
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
