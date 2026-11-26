package com.habit1.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Calm, non-judgmental streak badge showing current consistency.
 * Supports frozen status when an active streak is bridged across skip/pause periods.
 */
@Composable
fun StreakBadge(
    streakCount: Int,
    isFrozen: Boolean = false,
    modifier: Modifier = Modifier,
    accentColor: androidx.compose.ui.graphics.Color? = null
) {
    if (streakCount <= 0) return

    val label = if (streakCount == 1) "1 day" else "$streakCount days"
    val containerColor = if (isFrozen) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        accentColor?.copy(alpha = 0.15f) ?: MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isFrozen) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        accentColor ?: MaterialTheme.colorScheme.onSecondaryContainer
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFrozen) {
            Text(
                text = "❄️",
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = if (isFrozen) "$label (Frozen)" else label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}
