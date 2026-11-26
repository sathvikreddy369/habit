package com.habit1.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habit1.app.domain.model.DailyCompletionPoint
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Modern, clean completion trend line graph showing adherence over time.
 * - Denominator is based strictly on scheduled habit instances.
 * - Shows interactive touch inspection point with date, scheduled count, completed count and %.
 * - Adapts cleanly to Light, Dark, and AMOLED Black modes.
 */
@Composable
fun DailyTrendLineChart(
    points: List<DailyCompletionPoint>,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    var selectedPoint by remember { mutableStateOf<DailyCompletionPoint?>(null) }
    val eligiblePoints = remember(points) { points.filter { it.isEligible } }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Daily Completion Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Scheduled habit instances completed over time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                selectedPoint?.let { pt ->
                    val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${pt.date.format(formatter)}: ${pt.completedHabitsCount}/${pt.scheduledHabitsCount} (${pt.completionRate.toInt()}%)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            val textColor = MaterialTheme.colorScheme.onSurfaceVariant

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .pointerInput(points) {
                            detectTapGestures { tapOffset ->
                                val spacing = size.width / (points.size.coerceAtLeast(2) - 1).toFloat()
                                val clickedIdx = ((tapOffset.x + spacing / 2) / spacing).toInt().coerceIn(0, points.size - 1)
                                val candidate = points[clickedIdx]
                                selectedPoint = if (candidate.isEligible) candidate else null
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val bottomPadding = 20.dp.toPx()
                    val chartHeight = h - bottomPadding

                    // Draw baseline grid lines (0%, 50%, 100%)
                    val y100 = 0f
                    val y50 = chartHeight / 2f
                    val y0 = chartHeight

                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    drawLine(gridColor, Offset(0f, y100), Offset(w, y100), strokeWidth = 1f, pathEffect = dashEffect)
                    drawLine(gridColor, Offset(0f, y50), Offset(w, y50), strokeWidth = 1f, pathEffect = dashEffect)
                    drawLine(gridColor, Offset(0f, y0), Offset(w, y0), strokeWidth = 1.5f)

                    if (points.size < 2) return@Canvas

                    val stepX = w / (points.size - 1).toFloat()
                    val path = Path()
                    var started = false

                    // Build line path connecting eligible days
                    points.forEachIndexed { i, pt ->
                        if (pt.isEligible) {
                            val x = i * stepX
                            val y = chartHeight - (pt.completionRate / 100f * chartHeight)
                            if (!started) {
                                path.moveTo(x, y)
                                started = true
                            } else {
                                path.lineTo(x, y)
                            }
                        }
                    }

                    // Stroke trend line
                    drawPath(
                        path = path,
                        color = accentColor,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Draw interactive dots
                    points.forEachIndexed { i, pt ->
                        if (pt.isEligible) {
                            val x = i * stepX
                            val y = chartHeight - (pt.completionRate / 100f * chartHeight)
                            val isSelected = selectedPoint?.date == pt.date

                            if (isSelected) {
                                drawCircle(
                                    color = accentColor.copy(alpha = 0.25f),
                                    radius = 10.dp.toPx(),
                                    center = Offset(x, y)
                                )
                            }
                            drawCircle(
                                color = if (isSelected) Color.White else accentColor,
                                radius = if (isSelected) 5.dp.toPx() else 3.dp.toPx(),
                                center = Offset(x, y)
                            )
                            if (isSelected) {
                                drawCircle(
                                    color = accentColor,
                                    radius = 5.dp.toPx(),
                                    center = Offset(x, y),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }

            // X-Axis range labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val startFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
                Text(
                    text = points.first().date.format(startFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tap any point to inspect",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = points.last().date.format(startFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
