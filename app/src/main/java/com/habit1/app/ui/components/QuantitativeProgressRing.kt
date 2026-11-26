package com.habit1.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Custom animated progress ring system for Quantitative habits (COUNT, DURATION, QUANTITY).
 *
 * Rules:
 * - Cap visual arc at 100% (1.0f), while preserving actual values internally.
 * - 0% to 33%: Red.
 * - >33% to <75%: Amber / Yellow.
 * - 75% to <100%: Light green (visibly incomplete).
 * - >=100%: Fully completed deep green ring.
 * - Background track: Neutral subtle surface outline.
 * - Smooth, restrained animations for progress arc and color transitions.
 */
@Composable
fun QuantitativeProgressRing(
    progressRatio: Float,
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.5.dp,
    showCenterText: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    // Visual arc clamped between 0f and 1f
    val clampedRatio = progressRatio.coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = clampedRatio,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "quantitative_ring_arc"
    )

    // Color thresholds
    val targetColor = when {
        isCompleted || progressRatio >= 1.0f -> Color(0xFF2E7D32) // Deep Completed Green
        progressRatio >= 0.75f -> Color(0xFF43A047)             // Light Green (visibly incomplete)
        progressRatio > 0.33f -> Color(0xFFFB8C00)              // Amber / Yellow
        else -> Color(0xFFE53935)                               // Red (0% - 33%)
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 350),
        label = "quantitative_ring_color"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

    val percentageInt = (progressRatio * 100).toInt()
    val semanticsDesc = if (isCompleted) {
        "$percentageInt percent complete, goal reached"
    } else {
        "$percentageInt percent complete"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .semantics { contentDescription = semanticsDesc }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val arcSize = size.toPx() - strokePx
            val topLeftOffset = strokePx / 2f

            // Neutral background track
            drawCircle(
                color = trackColor,
                radius = arcSize / 2f,
                style = Stroke(width = strokePx)
            )

            // Dynamic progress arc (starts at 12 o'clock / -90 deg)
            if (animatedProgress > 0f) {
                drawArc(
                    color = animatedColor,
                    startAngle = -90f,
                    sweepAngle = animatedProgress * 360f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(topLeftOffset, topLeftOffset),
                    size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        if (showCenterText) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = animatedColor,
                    modifier = Modifier.size(size * 0.44f)
                )
            } else {
                Text(
                    text = "$percentageInt%",
                    fontSize = if (size >= 52.dp) 11.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
