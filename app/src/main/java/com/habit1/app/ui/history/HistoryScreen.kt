package com.habit1.app.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habit1.app.domain.model.CalendarDayStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onInspectHabit: (habitId: String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val isViewingCurrentMonth = uiState.selectedMonth == YearMonth.from(today)
    val isViewingToday = uiState.selectedDate == today

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("History & Calendar") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. View Mode Selector (Month vs Year)
            item(key = "view_mode_selector") {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = uiState.viewMode == HistoryViewMode.MONTH,
                        onClick = { viewModel.onEvent(HistoryUiEvent.ToggleViewMode(HistoryViewMode.MONTH)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Month")
                    }
                    SegmentedButton(
                        selected = uiState.viewMode == HistoryViewMode.YEAR,
                        onClick = { viewModel.onEvent(HistoryUiEvent.ToggleViewMode(HistoryViewMode.YEAR)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Year (${uiState.selectedYear})")
                    }
                }
            }

            if (uiState.viewMode == HistoryViewMode.YEAR) {
                // Year Navigation Header
                item(key = "year_nav") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.onEvent(HistoryUiEvent.PreviousYear) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Year")
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${uiState.selectedYear}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                if (uiState.selectedYear != today.year) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.clickable {
                                            viewModel.onEvent(HistoryUiEvent.JumpToCurrentYear)
                                        }
                                    ) {
                                        Text(
                                            text = "Current Year",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = { viewModel.onEvent(HistoryUiEvent.NextYear) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Year")
                            }
                        }
                    }
                }

                // 12-Month Yearly Overview Grid
                item(key = "yearly_overview_grid") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.yearlyOverview.chunked(2).forEach { rowMonths ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowMonths.forEach { monthItem ->
                                    YearMonthCard(
                                        item = monthItem,
                                        onClick = { viewModel.onEvent(HistoryUiEvent.SelectMonthFromYear(monthItem.yearMonth)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowMonths.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
                // 1. Month Navigation Header with Today shortcut
                item(key = "month_nav") {
                    Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.onEvent(HistoryUiEvent.PreviousMonth) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = uiState.formattedMonth,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            if (!isViewingCurrentMonth || !isViewingToday) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.clickable {
                                        viewModel.onEvent(HistoryUiEvent.JumpToToday)
                                    }
                                ) {
                                    Text(
                                        text = "Today",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        IconButton(onClick = { viewModel.onEvent(HistoryUiEvent.NextMonth) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month")
                        }
                    }
                }
            }

            // 2. Calendar Grid
            item(key = "calendar_grid") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Weekday row (Mon to Sun)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            listOf("M", "T", "W", "T", "F", "S", "S").forEach { dayLabel ->
                                Text(
                                    text = dayLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(44.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Days grid
                        val firstDayOfWeek = uiState.selectedMonth.atDay(1).dayOfWeek
                        val leadingEmptyDays = (firstDayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
                        val totalCells = leadingEmptyDays + uiState.calendarDays.size
                        val rows = (totalCells + 6) / 7

                        for (rowIndex in 0 until rows) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                for (colIndex in 0 until 7) {
                                    val cellIndex = rowIndex * 7 + colIndex
                                    val dayIndex = cellIndex - leadingEmptyDays
                                    if (dayIndex in uiState.calendarDays.indices) {
                                        val dayItem = uiState.calendarDays[dayIndex]
                                        CalendarDayCell(
                                            item = dayItem,
                                            onClick = { viewModel.onEvent(HistoryUiEvent.SelectDate(dayItem.date)) }
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(44.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Calendar Legend
                        CalendarLegend()
                    }
                }
            }

            // 3. Month Summary Card
            uiState.monthSummary?.let { summary ->
                item(key = "month_summary") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Monthly Consistency",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.semantics {
                                        contentDescription = "Habits consistency: ${summary.habitCompletionRate.toInt()} percent, ${summary.totalHabitCompletions} of ${summary.totalHabitScheduledDays} scheduled days completed."
                                    }
                                ) {
                                    Text("Habits Consistency", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${summary.habitCompletionRate.toInt()}% (${summary.totalHabitCompletions}/${summary.totalHabitScheduledDays})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(
                                    modifier = Modifier.semantics {
                                        contentDescription = "Daily goals consistency: ${summary.goalCompletionRate.toInt()} percent, ${summary.completedGoals} of ${summary.totalGoals} goals completed."
                                    }
                                ) {
                                    Text("Daily Goals", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${summary.goalCompletionRate.toInt()}% (${summary.completedGoals}/${summary.totalGoals})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Selected Date Breakdown
            uiState.selectedDateBreakdown?.let { breakdown ->
                val isSelectedToday = breakdown.date == today
                val hasNoActivity = breakdown.habits.none { it.isCompleted || it.isPartial } &&
                    breakdown.goals.isEmpty() &&
                    breakdown.historicalGoalAggregate == null &&
                    breakdown.dailyReview == null

                item(key = "date_breakdown_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = breakdown.formattedDate,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (isSelectedToday) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "Today",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Factual empty state for days with no recorded activity
                if (hasNoActivity) {
                    item(key = "date_breakdown_no_activity") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "No activity recorded for this day.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "No habit completions, daily goals, or reflections were logged on this date.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Habits breakdown for selected day
                if (breakdown.habits.isNotEmpty()) {
                    val completedCount = breakdown.habits.count { it.isCompleted }
                    item(key = "breakdown_habits_title") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Habits Activity",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "$completedCount of ${breakdown.habits.size} completed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(breakdown.habits, key = { "breakdown_habit_${it.habitId}" }) { item ->
                        val itemDescription = "${item.habitName}, ${item.formattedProgress}. Tap to view habit history."
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    onClickLabel = "View ${item.habitName} history",
                                    onClick = { onInspectHabit(item.habitId) }
                                )
                                .semantics { contentDescription = itemDescription },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.habitName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (item.formattedProgress.isNotBlank()) {
                                        Text(
                                            text = item.formattedProgress,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StatusBadge(status = item.status)
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Goals breakdown for selected day (Supports both Historical Aggregates and Detailed Goals)
                if (breakdown.historicalGoalAggregate != null) {
                    val agg = breakdown.historicalGoalAggregate
                    val pct = if (agg.totalCount > 0) ((agg.completedCount.toDouble() / agg.totalCount.toDouble()) * 100.0).toInt() else 0
                    item(key = "breakdown_goals_title") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daily Goals",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "${agg.completedCount} of ${agg.totalCount} completed · $pct%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (breakdown.goals.isEmpty()) {
                        item(key = "breakdown_aggregate_notice") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Detailed goal records are no longer retained.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        item(key = "breakdown_aggregate_notice") {
                            Text(
                                text = "Detailed goal records are no longer retained for completed items.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(breakdown.goals, key = { "breakdown_goal_${it.id}" }) { goal ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = goal.isCompleted,
                                            onCheckedChange = null,
                                            enabled = false,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = goal.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textDecoration = if (goal.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                    }
                                    if (goal.subtasks.isNotEmpty()) {
                                        val completedSubs = goal.subtasks.count { it.isCompleted }
                                        Text(
                                            text = "$completedSubs/${goal.subtasks.size} subtasks completed",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 28.dp, top = 4.dp)
                                        )
                                    }
                                    if (!goal.notes.isNullOrBlank()) {
                                        Text(
                                            text = goal.notes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 28.dp, top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (breakdown.goals.isNotEmpty()) {
                    val completedGoals = breakdown.goals.count { it.isCompleted }
                    item(key = "breakdown_goals_title") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daily Goals",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "$completedGoals of ${breakdown.goals.size} completed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(breakdown.goals, key = { "breakdown_goal_${it.id}" }) { goal ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = goal.isCompleted,
                                        onCheckedChange = null,
                                        enabled = false,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = goal.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textDecoration = if (goal.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                }
                                if (goal.subtasks.isNotEmpty()) {
                                    val completedSubs = goal.subtasks.count { it.isCompleted }
                                    Text(
                                        text = "$completedSubs/${goal.subtasks.size} subtasks completed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 28.dp, top = 4.dp)
                                    )
                                }
                                if (!goal.notes.isNullOrBlank()) {
                                    Text(
                                        text = goal.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 28.dp, top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Daily Reflection Section
                item(key = "breakdown_reflection_header") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Daily Reflection",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                if (breakdown.dailyReview != null) {
                    val review = breakdown.dailyReview
                    item(key = "breakdown_reflection_card") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (!review.mood.isNullOrBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Mood:",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = review.mood,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }

                                if (!review.notes.isNullOrBlank()) {
                                    Text(
                                        text = review.notes,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }

                                Text(
                                    text = "User reflection recorded on this date.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                } else {
                    item(key = "breakdown_reflection_empty") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "No reflection recorded for this date.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    item: HistoryCalendarDayItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = item.isSelected
    val isToday = item.isToday

    val cellDescription = buildString {
        append(item.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())))
        if (isToday) append(", Today")
        if (isSelected) append(", Selected")
        if (item.completedHabitsCount > 0) append(", ${item.completedHabitsCount} habits completed")
        if (item.partialHabitsCount > 0) append(", ${item.partialHabitsCount} habits partial")
        if (item.completedGoalsCount > 0) append(", ${item.completedGoalsCount} goals completed")
        if (item.hasReview) append(", Daily reflection recorded")
        if (!item.hasRecordedActivity) append(", No activity recorded")
    }

    Column(
        modifier = modifier
            .size(width = 44.dp, height = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    isToday -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isToday && !isSelected) 1.dp else 0.dp,
                color = if (isToday && !isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = cellDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = item.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                isToday -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Multi-indicator row
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Habit dot
            if (item.completedHabitsCount > 0) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            } else if (item.partialHabitsCount > 0) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }

            // Goal dot
            if (item.completedGoalsCount > 0) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                )
            }

            // Review dot
            if (item.hasReview) {
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }

            if (item.completedHabitsCount == 0 && item.partialHabitsCount == 0 && item.completedGoalsCount == 0 && !item.hasReview) {
                if (item.hasRecordedActivity) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun CalendarLegend(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(color = MaterialTheme.colorScheme.primary, label = "Completed")
        LegendItem(color = MaterialTheme.colorScheme.tertiary, label = "Partial")
        LegendItem(color = MaterialTheme.colorScheme.secondary, label = "Goal")
        LegendItem(color = MaterialTheme.colorScheme.onSurfaceVariant, label = "Reflection")
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusBadge(
    status: CalendarDayStatus,
    modifier: Modifier = Modifier
) {
    val (label, bgColor, textColor) = when (status) {
        is CalendarDayStatus.Completed -> Triple(
            "Completed",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        is CalendarDayStatus.RecordedIncomplete -> {
            if (status.actualValue > 0.0) {
                Triple(
                    "Partial",
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer
                )
            } else {
                Triple(
                    "Incomplete",
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        CalendarDayStatus.ProjectedMissed -> Triple(
            "Missed",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        CalendarDayStatus.ProjectedRest -> Triple(
            "Rest",
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.outline
        )
        CalendarDayStatus.Paused -> Triple(
            "Paused",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.outline
        )
        CalendarDayStatus.PreCreation -> Triple(
            "Pre-Creation",
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.outline
        )
        CalendarDayStatus.Future -> Triple(
            "Upcoming",
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.outline
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun YearMonthCard(
    item: YearMonthSummaryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isCurrentMonth) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (item.isCurrentMonth) 1.5.dp else 1.dp,
            color = if (item.isCurrentMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.monthName.take(3),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (item.isCurrentMonth) {
                    Text(
                        text = "Current",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (item.isFuture) {
                Text(
                    text = "Upcoming",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(14.dp))
            } else {
                Text(
                    text = "${item.totalHabitCompletions} / ${item.totalHabitScheduledDays} (${item.habitCompletionRate.toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (item.habitCompletionRate / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (item.totalGoals > 0) {
                    Text(
                        text = "${item.completedGoals}/${item.totalGoals} goals",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    Text(
                        text = "No goals",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

