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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import com.habit1.app.domain.model.HabitHistoryDay
import com.habit1.app.domain.model.WeekdayAdherence
import java.time.DayOfWeek

/**
 * 7-day Monday through Sunday weekday adherence distribution card (Decision 5.1.3).
 *
 * Formula: Count(DONE on that weekday) / (Eligible Scheduled occurrences - Count(SKIPPED)) * 100.
 * If no eligible occurrences exist for a given weekday, displays "—" instead of 0%.
 */
@Composable
fun HabitFrequencyCard(
    historyDays: List<HabitHistoryDay> = emptyList(),
    weekdayAdherence: List<WeekdayAdherence> = emptyList(),
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val items = remember(weekdayAdherence, historyDays) {
        if (weekdayAdherence.isNotEmpty()) {
            weekdayAdherence
        } else {
            // Fallback from historyDays: Monday to Sunday
            val daysOfWeek = listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY
            )
            daysOfWeek.map { day ->
                val daysForDay = historyDays.filter { it.date.dayOfWeek == day }
                val completed = daysForDay.count { it.status is CalendarDayStatus.Completed }
                val eligible = daysForDay.count {
                    it.status is CalendarDayStatus.Completed ||
                        it.status is CalendarDayStatus.RecordedIncomplete ||
                        it.status is CalendarDayStatus.ExplicitMissed ||
                        it.status is CalendarDayStatus.ProjectedMissed
                }
                val rate = if (eligible > 0) ((completed.toDouble() / eligible.toDouble()) * 100.0).toFloat() else null
                WeekdayAdherence(
                    dayOfWeek = day,
                    completedCount = completed,
                    eligibleScheduledCount = eligible,
                    adherenceRate = rate
                )
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Day-of-Week Adherence",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items.forEach { adherence ->
                    val label = when (adherence.dayOfWeek) {
                        DayOfWeek.MONDAY -> "Mon"
                        DayOfWeek.TUESDAY -> "Tue"
                        DayOfWeek.WEDNESDAY -> "Wed"
                        DayOfWeek.THURSDAY -> "Thu"
                        DayOfWeek.FRIDAY -> "Fri"
                        DayOfWeek.SATURDAY -> "Sat"
                        DayOfWeek.SUNDAY -> "Sun"
                    }

                    val rowA11y = if (adherence.adherenceRate != null) {
                        "$label: ${adherence.adherenceRate.toInt()}% adherence, ${adherence.completedCount} of ${adherence.eligibleScheduledCount} scheduled days completed"
                    } else {
                        "$label: no scheduled occurrences"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = rowA11y },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(36.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Adherence Progress Bar
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (adherence.adherenceRate != null && adherence.adherenceRate > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(adherence.adherenceRate / 100f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(accentColor)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Adherence Value Label (or '—' when 0 eligible)
                        if (adherence.adherenceRate != null) {
                            Text(
                                text = "${adherence.adherenceRate.toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (adherence.adherenceRate >= 80f) accentColor else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(42.dp)
                            )
                        } else {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.width(42.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
