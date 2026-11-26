package com.habit1.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.data.local.db.entity.DailyGoalEntity
import com.habit1.app.data.local.db.entity.DailyReviewEntity
import com.habit1.app.data.local.db.entity.GoalSubtaskEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.DailyGoalRepository
import com.habit1.app.data.repository.DailyReviewRepository
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.mapper.EntityMappers.toEntity
import com.habit1.app.domain.model.HabitRecordStatus
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.usecase.CalculateStreaksUseCase
import com.habit1.app.domain.usecase.EvaluateMeasurementUseCase
import com.habit1.app.domain.usecase.EvaluateScheduleUseCase
import com.habit1.app.domain.validation.GoalValidationError
import com.habit1.app.domain.validation.GoalValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * ViewModel for the Today screen.
 * Consumes pure domain use cases and exposes a single immutable StateFlow<TodayUiState>.
 * Handles 5-state habit status model, 7-day historical date stepper, safe quantitative actions,
 * action sheets, direct numeric dialogs, and undoable record clearing.
 */
class TodayViewModel(
    private val habitRepository: HabitRepository,
    private val habitRecordRepository: HabitRecordRepository,
    private val dailyGoalRepository: DailyGoalRepository,
    private val dailyReviewRepository: DailyReviewRepository? = null,
    private val goalReminderScheduler: com.habit1.app.platform.reminder.GoalReminderScheduler? = null,
    private val evaluateSchedule: EvaluateScheduleUseCase = EvaluateScheduleUseCase(),
    private val calculateStreaks: CalculateStreaksUseCase = CalculateStreaksUseCase(),
    private val evaluateMeasurement: EvaluateMeasurementUseCase = EvaluateMeasurementUseCase(),
    private val recordHabitProgress: com.habit1.app.domain.usecase.RecordHabitProgressUseCase =
        com.habit1.app.domain.usecase.RecordHabitProgressUseCase(habitRepository, habitRecordRepository, evaluateMeasurement, evaluateSchedule),
    private val categoryRepository: com.habit1.app.data.repository.CategoryRepository? = null,
    private val userPreferences: com.habit1.app.data.local.preferences.UserPreferencesDataStore? = null,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = coroutineScope ?: viewModelScope

    private val currentDateFlow = MutableStateFlow(DateTimeUtils.today(zoneId))
    private val userMessageFlow = MutableStateFlow<String?>(null)
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())

    // Category grouping and focus flows (Phase 5)
    private val selectedCategoryFilterFlow = MutableStateFlow<String?>(null)
    private val collapsedCategoryIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val manualCategoryGroupingFlow = MutableStateFlow<Boolean?>(null)
    private val categoriesFlow = categoryRepository?.observeAllCategories() ?: flowOf(emptyList())
    private val preferencesCategoryGroupingFlow = userPreferences?.userPreferencesFlow?.map { it.categoryGroupingEnabled } ?: flowOf(false)

    // Habit Action Sheet & Dialog flows (Phase 2)
    private val activeActionSheetHabitFlow = MutableStateFlow<TodayHabitItem?>(null)
    private val isActionSheetOpenFlow = MutableStateFlow(false)
    private val numericDialogHabitFlow = MutableStateFlow<TodayHabitItem?>(null)
    private val notesDialogHabitFlow = MutableStateFlow<TodayHabitItem?>(null)
    private val lastClearedRecordFlow = MutableStateFlow<Pair<String, HabitRecordEntity>?>(null)

    // UI state flows for goals, reviews, and expansion
    private val isAddGoalDialogOpenFlow = MutableStateFlow(false)
    private val isReviewDialogOpenFlow = MutableStateFlow(false)
    private val goalPendingEditFlow = MutableStateFlow<TodayGoalItem?>(null)
    private val goalPendingDeletionFlow = MutableStateFlow<TodayGoalItem?>(null)
    private val goalPendingMoveTomorrowFlow = MutableStateFlow<TodayGoalItem?>(null)
    private val lastMovedGoalFlow = MutableStateFlow<Pair<String, LocalDate>?>(null)
    private val subtaskPendingEditFlow = MutableStateFlow<Pair<String, TodaySubtaskItem>?>(null)
    private val collapsedGoalIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    init {
        scope.launch {
            val todayDate = DateTimeUtils.today(zoneId)
            val cutoffDate = DateTimeUtils.formatDate(todayDate.minusDays(7))
            dailyGoalRepository.cleanupIncompleteGoalsOlderThan(cutoffDate)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TodayUiState> = currentDateFlow.flatMapLatest { date ->
        val dateString = DateTimeUtils.formatDate(date)
        val reviewFlow = dailyReviewRepository?.observeReview(dateString) ?: flowOf(null)

        combine(
            habitRepository.observeActiveHabits(),
            habitRecordRepository.observeRecordsForDate(dateString),
            dailyGoalRepository.observeGoalsForDate(dateString),
            reviewFlow,
            userMessageFlow,
            isAddGoalDialogOpenFlow,
            isReviewDialogOpenFlow,
            goalPendingEditFlow,
            goalPendingDeletionFlow,
            goalPendingMoveTomorrowFlow,
            lastMovedGoalFlow,
            subtaskPendingEditFlow,
            collapsedGoalIdsFlow,
            activeActionSheetHabitFlow,
            isActionSheetOpenFlow,
            numericDialogHabitFlow,
            notesDialogHabitFlow,
            lastClearedRecordFlow,
            categoriesFlow,
            preferencesCategoryGroupingFlow,
            manualCategoryGroupingFlow,
            selectedCategoryFilterFlow,
            collapsedCategoryIdsFlow
        ) { args: Array<Any?> ->
            @Suppress("UNCHECKED_CAST")
            val activeEntities = args[0] as List<com.habit1.app.data.local.db.entity.HabitEntity>
            @Suppress("UNCHECKED_CAST")
            val recordEntities = args[1] as List<HabitRecordEntity>
            @Suppress("UNCHECKED_CAST")
            val goalWithSubtasksList = args[2] as List<com.habit1.app.data.local.db.entity.DailyGoalWithSubtasks>
            val reviewEntity = args[3] as DailyReviewEntity?
            val message = args[4] as String?
            val isAddGoalOpen = args[5] as Boolean
            val isReviewDialogOpen = args[6] as Boolean
            val goalPendingEdit = args[7] as TodayGoalItem?
            val goalPendingDeletion = args[8] as TodayGoalItem?
            val goalPendingMoveTomorrow = args[9] as TodayGoalItem?
            @Suppress("UNCHECKED_CAST")
            val lastMovedGoal = args[10] as Pair<String, LocalDate>?
            @Suppress("UNCHECKED_CAST")
            val subtaskPendingEdit = args[11] as Pair<String, TodaySubtaskItem>?
            @Suppress("UNCHECKED_CAST")
            val collapsedGoalIds = args[12] as Set<String>
            val activeActionSheetHabit = args[13] as TodayHabitItem?
            val isActionSheetOpen = args[14] as Boolean
            val numericDialogHabit = args[15] as TodayHabitItem?
            val notesDialogHabit = args[16] as TodayHabitItem?
            @Suppress("UNCHECKED_CAST")
            val lastClearedRecord = args[17] as Pair<String, HabitRecordEntity>?
            @Suppress("UNCHECKED_CAST")
            val categoryEntities = args[18] as List<com.habit1.app.data.local.db.entity.CategoryEntity>
            val prefCategoryGrouping = args[19] as Boolean
            val manualCategoryGrouping = args[20] as Boolean?
            val selectedCategoryFilter = args[21] as String?
            @Suppress("UNCHECKED_CAST")
            val collapsedCategoryIds = args[22] as Set<String>

            val categoryGroupingEnabled = manualCategoryGrouping ?: prefCategoryGrouping
            val domainCategories: List<com.habit1.app.domain.model.Category> = categoryEntities.map { entity: com.habit1.app.data.local.db.entity.CategoryEntity -> entity.toDomain() }
            val categoriesMap: Map<String, com.habit1.app.domain.model.Category> = domainCategories.associateBy { it.id }

            val habits = activeEntities.map { it.toDomain() }
            val recordsMap = recordEntities.associateBy { it.habitId }

            // Filter out pre-creation dates (date < habit.startDate)
            val applicableHabits = habits.filter { habit -> !date.isBefore(habit.startDate) }

            // 1. Partition habits into scheduled and unscheduled (rest/bonus) for this date
            val (scheduledHabits, unscheduledHabitsList) = applicableHabits.partition { habit ->
                evaluateSchedule.isScheduledOn(habit, date, zoneId)
            }

            // 2. Map to UI items (batch-fetching history to avoid N+1 SQLite queries)
            val allApplicableIds = applicableHabits.map { it.id }
            val batchRecordsByHabit = if (allApplicableIds.isNotEmpty()) {
                habitRecordRepository.getRecordsForHabits(allApplicableIds)
                    .groupBy { it.habitId }
            } else {
                emptyMap()
            }

            fun mapToUiItem(habit: com.habit1.app.domain.model.Habit, isScheduled: Boolean): TodayHabitItem {
                val recordEntity = recordsMap[habit.id]
                val record = recordEntity?.toDomain()

                val actualValue = record?.actualValue ?: 0.0
                val isCompleted = record?.isCompleted ?: false
                val defaultTarget = when (val m = habit.measurement) {
                    is MeasurementType.BooleanChoice -> 1.0
                    is MeasurementType.Count -> m.target.toDouble()
                    is MeasurementType.Duration -> m.targetMinutes.toDouble()
                    is MeasurementType.Quantity -> m.target
                }
                val defaultUnit = when (val m = habit.measurement) {
                    is MeasurementType.BooleanChoice -> null
                    is MeasurementType.Count -> m.unit
                    is MeasurementType.Duration -> "mins"
                    is MeasurementType.Quantity -> m.unit
                }

                val targetValue = record?.targetValue ?: defaultTarget
                val unit = record?.unit ?: defaultUnit

                val status = record?.status ?: HabitRecordStatus.UNRECORDED

                val progressRatio = evaluateMeasurement.progressRatio(habit.measurement, actualValue)
                val baseProgress = evaluateMeasurement.formatProgress(habit.measurement, actualValue)
                val formattedProgress = if (habit.measurement is MeasurementType.BooleanChoice) {
                    when (status) {
                        HabitRecordStatus.DONE -> "Done"
                        HabitRecordStatus.SKIPPED -> "Skipped"
                        HabitRecordStatus.MISSED -> "Missed"
                        HabitRecordStatus.UNRECORDED -> "Not done"
                        HabitRecordStatus.PARTIAL -> "Not done"
                    }
                } else {
                    val percent = if (targetValue > 0.0) ((actualValue / targetValue) * 100).toInt() else 0
                    when (status) {
                        HabitRecordStatus.SKIPPED -> "$baseProgress • Skipped"
                        HabitRecordStatus.MISSED -> "$baseProgress • Missed"
                        else -> "$baseProgress • $percent%"
                    }
                }

                val habitHistory = (batchRecordsByHabit[habit.id] ?: emptyList()).map { it.toDomain() }
                val streak = calculateStreaks.execute(habit, habitHistory, date, zoneId)

                val category = habit.categoryId?.let { categoriesMap[it] }

                return TodayHabitItem(
                    id = habit.id,
                    name = habit.name,
                    description = habit.description,
                    measurementType = habit.measurement,
                    targetValue = targetValue,
                    actualValue = actualValue,
                    unit = unit,
                    status = status,
                    isCompleted = isCompleted,
                    progressRatio = progressRatio,
                    formattedProgress = formattedProgress,
                    streakResult = streak,
                    colorHex = habit.colorHex,
                    notes = record?.notes,
                    isScheduled = isScheduled,
                    categoryId = category?.id,
                    categoryName = category?.name,
                    categoryColorHex = category?.colorHex,
                    categoryIcon = category?.icon
                )
            }

            val habitUiItems = scheduledHabits.map { mapToUiItem(it, isScheduled = true) }
            val unscheduledHabitUiItems = unscheduledHabitsList.map { mapToUiItem(it, isScheduled = false) }

            // Category Groups (Decision 6.1)
            val groupedHabitsList = if (categoryGroupingEnabled) {
                val groups = mutableListOf<TodayCategoryGroup>()

                // 1. Defined categories with scheduled habits
                domainCategories.forEach { cat ->
                    val catHabits = habitUiItems.filter { it.categoryId == cat.id }
                    if (catHabits.isNotEmpty()) {
                        val completed = catHabits.count { it.isCompleted }
                        val isCollapsed = collapsedCategoryIds.contains(cat.id)
                        groups.add(
                            TodayCategoryGroup(
                                categoryId = cat.id,
                                categoryName = cat.name,
                                categoryColorHex = cat.colorHex,
                                categoryIcon = cat.icon,
                                habits = catHabits,
                                completedCount = completed,
                                totalCount = catHabits.size,
                                isCollapsed = isCollapsed
                            )
                        )
                    }
                }

                // 2. Uncategorized scheduled habits placed at the bottom
                val uncategorized = habitUiItems.filter { it.categoryId == null }
                if (uncategorized.isNotEmpty()) {
                    val completed = uncategorized.count { it.isCompleted }
                    val isCollapsed = collapsedCategoryIds.contains("uncategorized")
                    groups.add(
                        TodayCategoryGroup(
                            categoryId = null,
                            categoryName = "Uncategorized",
                            categoryColorHex = null,
                            categoryIcon = null,
                            habits = uncategorized,
                            completedCount = completed,
                            totalCount = uncategorized.size,
                            isCollapsed = isCollapsed
                        )
                    )
                }
                groups
            } else {
                emptyList()
            }

            // Top Focus Filter (when active habit count >= 10, or when category chip selected)
            val finalHabits = if (selectedCategoryFilter != null) {
                habitUiItems.filter { it.categoryId == selectedCategoryFilter }
            } else {
                habitUiItems
            }

            val finalGroupedHabits = if (selectedCategoryFilter != null) {
                groupedHabitsList.filter { it.categoryId == selectedCategoryFilter }
            } else {
                groupedHabitsList
            }

            // 3. Map goals & subtasks
            val goalCount = goalWithSubtasksList.size
            val goalUiItems = goalWithSubtasksList.mapIndexed { index, item ->
                val domainGoal = item.toDomain()
                val subtaskCount = domainGoal.subtasks.size
                TodayGoalItem(
                    id = domainGoal.id,
                    title = domainGoal.title,
                    isCompleted = domainGoal.isCompleted,
                    subtasks = domainGoal.subtasks.mapIndexed { sIndex, subtask ->
                        TodaySubtaskItem(
                            id = subtask.id,
                            title = subtask.title,
                            isCompleted = subtask.isCompleted,
                            displayOrder = subtask.displayOrder,
                            canMoveUp = sIndex > 0,
                            canMoveDown = sIndex < subtaskCount - 1
                        )
                    },
                    notes = domainGoal.notes,
                    reminderTime = domainGoal.reminderTime,
                    displayOrder = domainGoal.displayOrder,
                    canMoveUp = index > 0,
                    canMoveDown = index < goalCount - 1,
                    isExpanded = !collapsedGoalIds.contains(domainGoal.id)
                )
            }

            // 4. Summaries
            val completedHabitsCount = habitUiItems.count { it.isCompleted }
            val totalHabitsCount = habitUiItems.size
            val completedGoalsCount = goalUiItems.count { it.isCompleted }
            val totalGoalsCount = goalUiItems.size

            val habitProgress = if (totalHabitsCount > 0) {
                (completedHabitsCount.toFloat() / totalHabitsCount.toFloat()).coerceIn(0.0f, 1.0f)
            } else {
                0.0f
            }
            val goalProgress = if (totalGoalsCount > 0) {
                (completedGoalsCount.toFloat() / totalGoalsCount.toFloat()).coerceIn(0.0f, 1.0f)
            } else {
                0.0f
            }

            val totalTasks = totalHabitsCount + totalGoalsCount
            val completedTasks = completedHabitsCount + completedGoalsCount
            val overallProgress = if (totalTasks > 0) {
                (completedTasks.toFloat() / totalTasks.toFloat()).coerceIn(0.0f, 1.0f)
            } else {
                0.0f
            }

            TodayUiState(
                currentDate = date,
                todayDate = DateTimeUtils.today(zoneId),
                formattedDate = date.format(dateFormatter),
                habits = finalHabits,
                unscheduledHabits = unscheduledHabitUiItems,
                goals = goalUiItems,
                completedHabitsCount = completedHabitsCount,
                totalScheduledHabitsCount = totalHabitsCount,
                completedGoalsCount = completedGoalsCount,
                totalGoalsCount = totalGoalsCount,
                habitProgress = habitProgress,
                goalProgress = goalProgress,
                overallProgress = overallProgress,
                dailyReview = reviewEntity?.toDomain(),
                isReviewDialogOpen = isReviewDialogOpen,
                isLoading = false,
                userMessage = message,
                isAddGoalDialogOpen = isAddGoalOpen,
                goalPendingEdit = goalPendingEdit,
                goalPendingDeletion = goalPendingDeletion,
                goalPendingMoveTomorrow = goalPendingMoveTomorrow,
                lastMovedGoalId = lastMovedGoal?.first,
                lastClearedRecordHabitName = lastClearedRecord?.first,
                subtaskPendingEdit = subtaskPendingEdit,
                activeActionSheetHabit = activeActionSheetHabit,
                isActionSheetOpen = isActionSheetOpen,
                numericDialogHabit = numericDialogHabit,
                notesDialogHabit = notesDialogHabit,
                categoryGroupingEnabled = categoryGroupingEnabled,
                selectedCategoryFilter = selectedCategoryFilter,
                categories = domainCategories,
                collapsedCategoryIds = collapsedCategoryIds,
                groupedHabits = finalGroupedHabits
            )
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodayUiState(
            currentDate = DateTimeUtils.today(zoneId),
            todayDate = DateTimeUtils.today(zoneId),
            formattedDate = DateTimeUtils.today(zoneId).format(dateFormatter),
            isLoading = true
        )
    )

    fun onEvent(event: TodayUiEvent) {
        scope.launch {
            when (event) {
                is TodayUiEvent.ToggleHabit -> handleToggleHabit(event.habitId)
                is TodayUiEvent.IncrementHabit -> handleAdjustHabit(event.habitId, increment = true)
                is TodayUiEvent.DecrementHabit -> handleAdjustHabit(event.habitId, increment = false)
                is TodayUiEvent.SetHabitValue -> handleSetHabitValue(event.habitId, event.value)
                is TodayUiEvent.ToggleGoal -> {
                    val willBeCompleted = !isGoalCompleted(event.goalId)
                    dailyGoalRepository.setGoalCompleted(event.goalId, willBeCompleted)
                    if (willBeCompleted) {
                        goalReminderScheduler?.cancelGoalReminder(event.goalId)
                    } else {
                        val goal = uiState.value.goals.find { it.id == event.goalId }
                        if (goal?.reminderTime != null) {
                            goalReminderScheduler?.scheduleGoalReminder(
                                goalId = goal.id,
                                title = goal.title,
                                notes = goal.notes,
                                targetDate = currentDateFlow.value,
                                reminderTime = goal.reminderTime
                            )
                        }
                    }
                }
                is TodayUiEvent.ToggleSubtask -> handleToggleSubtask(event.goalId, event.subtaskId)
                is TodayUiEvent.AddGoal -> handleAddGoal(event.title, notes = null, targetDate = null, reminderTime = null)
                is TodayUiEvent.AddHabitQuick -> handleAddHabitQuick(event.name, event.measurementType)
                is TodayUiEvent.RefreshDate -> currentDateFlow.value = DateTimeUtils.today(zoneId)
                is TodayUiEvent.DismissMessage -> userMessageFlow.value = null

                // Date Stepper Actions (Decision 2.1)
                is TodayUiEvent.StepDateBackward -> handleStepDateBackward()
                is TodayUiEvent.StepDateForward -> handleStepDateForward()
                is TodayUiEvent.ReturnToToday -> currentDateFlow.value = DateTimeUtils.today(zoneId)
                is TodayUiEvent.SelectDate -> handleSelectDate(event.date)

                // 5-State Actions & Sheets (Decisions 1.1, 1.3, 2.2)
                is TodayUiEvent.OpenHabitActionSheet -> {
                    activeActionSheetHabitFlow.value = event.habit
                    isActionSheetOpenFlow.value = true
                }
                is TodayUiEvent.DismissHabitActionSheet -> {
                    isActionSheetOpenFlow.value = false
                    activeActionSheetHabitFlow.value = null
                }
                is TodayUiEvent.CompleteToTarget -> handleCompleteToTarget(event.habitId)
                is TodayUiEvent.MarkSkipped -> handleMarkSkipped(event.habitId, event.notes)
                is TodayUiEvent.MarkMissed -> handleMarkMissed(event.habitId, event.notes)
                is TodayUiEvent.ClearRecord -> handleClearRecord(event.habitId)
                is TodayUiEvent.UndoClearRecord -> handleUndoClearRecord()
                is TodayUiEvent.OpenNumericDialog -> {
                    numericDialogHabitFlow.value = event.habit
                    isActionSheetOpenFlow.value = false
                }
                is TodayUiEvent.DismissNumericDialog -> numericDialogHabitFlow.value = null
                is TodayUiEvent.OpenNotesDialog -> {
                    notesDialogHabitFlow.value = event.habit
                    isActionSheetOpenFlow.value = false
                }
                is TodayUiEvent.DismissNotesDialog -> notesDialogHabitFlow.value = null
                is TodayUiEvent.SaveNotes -> handleSaveNotes(event.habitId, event.notes)

                // Goal dialogs and actions
                is TodayUiEvent.OpenAddGoalDialog -> isAddGoalDialogOpenFlow.value = true
                is TodayUiEvent.DismissGoalDialog -> {
                    isAddGoalDialogOpenFlow.value = false
                    goalPendingEditFlow.value = null
                }
                is TodayUiEvent.SaveNewGoal -> handleAddGoal(event.title, event.notes, event.targetDate, event.reminderTime)
                is TodayUiEvent.RequestEditGoal -> goalPendingEditFlow.value = event.goal
                is TodayUiEvent.SaveEditedGoal -> handleEditGoal(event.goalId, event.title, event.notes, event.reminderTime)
                is TodayUiEvent.SetGoalReminder -> handleSetGoalReminder(event.goalId, event.reminderTime)
                is TodayUiEvent.RequestDeleteGoal -> goalPendingDeletionFlow.value = event.goal
                is TodayUiEvent.ConfirmDeleteGoal -> {
                    goalPendingDeletionFlow.value?.let { goal ->
                        goalReminderScheduler?.cancelGoalReminder(goal.id)
                        dailyGoalRepository.deleteGoal(goal.id)
                        goalPendingDeletionFlow.value = null
                    }
                }
                is TodayUiEvent.CancelDeleteGoal -> goalPendingDeletionFlow.value = null
                is TodayUiEvent.RequestMoveGoalTomorrow -> goalPendingMoveTomorrowFlow.value = event.goal
                is TodayUiEvent.CancelMoveGoalTomorrow -> goalPendingMoveTomorrowFlow.value = null
                is TodayUiEvent.ConfirmMoveGoalTomorrow -> {
                    goalPendingMoveTomorrowFlow.value?.let { goal ->
                        val tomorrow = currentDateFlow.value.plusDays(1)
                        handleMoveGoalDate(goal.id, tomorrow)
                        lastMovedGoalFlow.value = goal.id to currentDateFlow.value
                        goalPendingMoveTomorrowFlow.value = null
                        userMessageFlow.value = "Moved \"${goal.title}\" to tomorrow. View in History."
                    }
                }
                is TodayUiEvent.UndoLastMovedGoal -> {
                    lastMovedGoalFlow.value?.let { (goalId, prevDate) ->
                        handleMoveGoalDate(goalId, prevDate)
                        lastMovedGoalFlow.value = null
                        userMessageFlow.value = "Moved goal back to Today"
                    }
                }
                is TodayUiEvent.MoveGoalDate -> handleMoveGoalDate(event.goalId, event.newDate)
                is TodayUiEvent.MoveGoalUp -> handleReorderGoal(event.goalId, moveUp = true)
                is TodayUiEvent.MoveGoalDown -> handleReorderGoal(event.goalId, moveUp = false)
                is TodayUiEvent.ToggleGoalExpanded -> handleToggleGoalExpanded(event.goalId)

                // Subtask actions
                is TodayUiEvent.AddSubtask -> handleAddSubtask(event.goalId, event.title)
                is TodayUiEvent.RequestEditSubtask -> subtaskPendingEditFlow.value = Pair(event.goalId, event.subtask)
                is TodayUiEvent.SaveEditedSubtask -> handleEditSubtask(event.subtaskId, event.title)
                is TodayUiEvent.DismissSubtaskEditDialog -> subtaskPendingEditFlow.value = null
                is TodayUiEvent.DeleteSubtask -> dailyGoalRepository.deleteSubtask(event.subtaskId)
                is TodayUiEvent.MoveSubtaskUp -> handleReorderSubtask(event.goalId, event.subtaskId, moveUp = true)
                is TodayUiEvent.MoveSubtaskDown -> handleReorderSubtask(event.goalId, event.subtaskId, moveUp = false)

                // Daily Reflection actions
                is TodayUiEvent.OpenReviewDialog -> isReviewDialogOpenFlow.value = true
                is TodayUiEvent.DismissReviewDialog -> isReviewDialogOpenFlow.value = false
                is TodayUiEvent.SaveReview -> handleSaveReview(event.notes, event.mood)
                is TodayUiEvent.DeleteReview -> handleDeleteReview()

                // Phase 5 Category Actions (Decision 6.1)
                is TodayUiEvent.ToggleCategoryGrouping -> {
                    val current = uiState.value.categoryGroupingEnabled
                    val next = !current
                    manualCategoryGroupingFlow.value = next
                    userPreferences?.let { prefs ->
                        scope.launch {
                            prefs.updateCategoryGroupingEnabled(next)
                        }
                    }
                }
                is TodayUiEvent.ToggleCategoryCollapsed -> {
                    val key = event.categoryId ?: "uncategorized"
                    val current = collapsedCategoryIdsFlow.value
                    collapsedCategoryIdsFlow.value = if (current.contains(key)) current - key else current + key
                }
                is TodayUiEvent.SelectCategoryFilter -> {
                    selectedCategoryFilterFlow.value = event.categoryId
                }
            }
        }
    }

    private fun handleStepDateBackward() {
        val current = currentDateFlow.value
        val minDate = DateTimeUtils.today(zoneId).minusDays(7)
        if (current.isAfter(minDate)) {
            currentDateFlow.value = current.minusDays(1)
        }
    }

    private fun handleStepDateForward() {
        val current = currentDateFlow.value
        val today = DateTimeUtils.today(zoneId)
        if (current.isBefore(today)) {
            currentDateFlow.value = current.plusDays(1)
        }
    }

    private fun handleSelectDate(date: LocalDate) {
        val today = DateTimeUtils.today(zoneId)
        val minDate = today.minusDays(7)
        if (!date.isAfter(today) && !date.isBefore(minDate)) {
            currentDateFlow.value = date
        }
    }

    private suspend fun handleToggleHabit(habitId: String) {
        recordHabitProgress.toggleHabit(habitId, currentDateFlow.value, zoneId)
    }

    private suspend fun handleAdjustHabit(habitId: String, increment: Boolean) {
        recordHabitProgress.adjustHabit(habitId, currentDateFlow.value, increment, zoneId)
    }

    private suspend fun handleSetHabitValue(habitId: String, value: Double) {
        recordHabitProgress.setActualValue(habitId, currentDateFlow.value, value, zoneId)
        numericDialogHabitFlow.value = null
    }

    private suspend fun handleCompleteToTarget(habitId: String) {
        recordHabitProgress.markCompleted(habitId, currentDateFlow.value, zoneId)
        isActionSheetOpenFlow.value = false
        activeActionSheetHabitFlow.value = null
    }

    private suspend fun handleMarkSkipped(habitId: String, notes: String?) {
        val date = currentDateFlow.value
        val dateString = DateTimeUtils.formatDate(date)
        val existing = habitRecordRepository.getRecord(habitId, dateString)
        if (existing?.status == HabitRecordStatus.SKIPPED.name) {
            recordHabitProgress.clearRecord(habitId, date, zoneId)
        } else {
            recordHabitProgress.markSkipped(habitId, date, notes, zoneId)
        }
        isActionSheetOpenFlow.value = false
        activeActionSheetHabitFlow.value = null
    }

    private suspend fun handleMarkMissed(habitId: String, notes: String?) {
        val date = currentDateFlow.value
        val dateString = DateTimeUtils.formatDate(date)
        val existing = habitRecordRepository.getRecord(habitId, dateString)
        if (existing?.status == HabitRecordStatus.MISSED.name) {
            recordHabitProgress.clearRecord(habitId, date, zoneId)
        } else {
            recordHabitProgress.markMissed(habitId, date, notes, zoneId)
        }
        isActionSheetOpenFlow.value = false
        activeActionSheetHabitFlow.value = null
    }

    private suspend fun handleClearRecord(habitId: String) {
        val date = currentDateFlow.value
        val dateString = DateTimeUtils.formatDate(date)
        val existing = habitRecordRepository.getRecord(habitId, dateString)
        val habit = habitRepository.getHabitById(habitId)
        if (existing != null) {
            lastClearedRecordFlow.value = Pair(habit?.name ?: "Habit", existing)
        }
        recordHabitProgress.clearRecord(habitId, date, zoneId)
        isActionSheetOpenFlow.value = false
        activeActionSheetHabitFlow.value = null
        userMessageFlow.value = "Cleared record for ${habit?.name ?: "habit"}"
    }

    private suspend fun handleUndoClearRecord() {
        lastClearedRecordFlow.value?.let { (_, record) ->
            habitRecordRepository.recordProgress(record)
            lastClearedRecordFlow.value = null
            userMessageFlow.value = "Restored record"
        }
    }

    private suspend fun handleSaveNotes(habitId: String, notes: String?) {
        val date = currentDateFlow.value
        val dateString = DateTimeUtils.formatDate(date)
        val existing = habitRecordRepository.getRecord(habitId, dateString)
        val trimmedNotes = notes?.trim()?.ifBlank { null }
        if (existing != null) {
            habitRecordRepository.recordProgress(existing.copy(notes = trimmedNotes))
        } else {
            // Explicit user note on unrecorded day records progress as MISSED with notes
            recordHabitProgress.markMissed(habitId, date, notes = trimmedNotes, zoneId = zoneId)
        }
        notesDialogHabitFlow.value = null
    }

    private fun isGoalCompleted(goalId: String): Boolean {
        return uiState.value.goals.find { it.id == goalId }?.isCompleted ?: false
    }

    private suspend fun handleToggleSubtask(goalId: String, subtaskId: String) {
        val goal = uiState.value.goals.find { it.id == goalId } ?: return
        val subtask = goal.subtasks.find { it.id == subtaskId } ?: return
        dailyGoalRepository.setSubtaskCompleted(subtaskId, !subtask.isCompleted)
    }

    private suspend fun handleAddGoal(
        title: String,
        notes: String?,
        targetDate: LocalDate?,
        reminderTime: java.time.LocalTime? = null
    ) {
        val dateToUse = targetDate ?: currentDateFlow.value
        val validationErrors = GoalValidator.validateGoal(title, notes, dateToUse)
        if (validationErrors.isNotEmpty()) {
            userMessageFlow.value = mapValidationError(validationErrors.first())
            return
        }

        val dateString = DateTimeUtils.formatDate(dateToUse)
        val now = System.currentTimeMillis()
        val currentGoalsCount = dailyGoalRepository.getGoalsForDate(dateString).size
        val goalId = UUID.randomUUID().toString()

        val newGoal = DailyGoalEntity(
            id = goalId,
            title = title.trim(),
            targetDate = dateString,
            isCompleted = false,
            displayOrder = currentGoalsCount,
            notes = notes?.trim()?.ifEmpty { null },
            reminderTime = reminderTime?.let { DateTimeUtils.formatTime(it) },
            createdAt = now,
            updatedAt = now
        )
        dailyGoalRepository.createGoal(newGoal)

        if (reminderTime != null) {
            goalReminderScheduler?.scheduleGoalReminder(
                goalId = goalId,
                title = title.trim(),
                notes = notes?.trim()?.ifEmpty { null },
                targetDate = dateToUse,
                reminderTime = reminderTime
            )
        }

        isAddGoalDialogOpenFlow.value = false
    }

    private suspend fun handleEditGoal(
        goalId: String,
        title: String,
        notes: String?,
        reminderTime: java.time.LocalTime? = null
    ) {
        val validationErrors = GoalValidator.validateGoal(title, notes, currentDateFlow.value)
        if (validationErrors.isNotEmpty()) {
            userMessageFlow.value = mapValidationError(validationErrors.first())
            return
        }

        val trimmedTitle = title.trim()
        val trimmedNotes = notes?.trim()?.ifEmpty { null }
        val reminderTimeStr = reminderTime?.let { DateTimeUtils.formatTime(it) }

        dailyGoalRepository.updateGoalContentWithReminder(
            id = goalId,
            title = trimmedTitle,
            notes = trimmedNotes,
            reminderTime = reminderTimeStr
        )

        if (reminderTime != null) {
            val isCompleted = isGoalCompleted(goalId)
            if (!isCompleted) {
                goalReminderScheduler?.scheduleGoalReminder(
                    goalId = goalId,
                    title = trimmedTitle,
                    notes = trimmedNotes,
                    targetDate = currentDateFlow.value,
                    reminderTime = reminderTime
                )
            }
        } else {
            goalReminderScheduler?.cancelGoalReminder(goalId)
        }

        goalPendingEditFlow.value = null
    }

    private suspend fun handleSetGoalReminder(goalId: String, reminderTime: java.time.LocalTime?) {
        val reminderTimeStr = reminderTime?.let { DateTimeUtils.formatTime(it) }
        dailyGoalRepository.updateGoalReminder(goalId, reminderTimeStr)

        if (reminderTime != null) {
            val goal = uiState.value.goals.find { it.id == goalId }
            if (goal != null && !goal.isCompleted) {
                goalReminderScheduler?.scheduleGoalReminder(
                    goalId = goalId,
                    title = goal.title,
                    notes = goal.notes,
                    targetDate = currentDateFlow.value,
                    reminderTime = reminderTime
                )
            }
        } else {
            goalReminderScheduler?.cancelGoalReminder(goalId)
        }
    }

    private suspend fun handleMoveGoalDate(goalId: String, newDate: LocalDate) {
        val newDateString = DateTimeUtils.formatDate(newDate)
        val nextDisplayOrder = dailyGoalRepository.getGoalsForDate(newDateString).size
        dailyGoalRepository.moveGoalDate(goalId, newDateString, nextDisplayOrder)
    }

    private suspend fun handleReorderGoal(goalId: String, moveUp: Boolean) {
        val currentDateStr = DateTimeUtils.formatDate(currentDateFlow.value)
        val currentGoals = uiState.value.goals.map { it.id }.toMutableList()
        val index = currentGoals.indexOf(goalId)
        if (index == -1) return

        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex in currentGoals.indices) {
            val item = currentGoals.removeAt(index)
            currentGoals.add(targetIndex, item)
            dailyGoalRepository.reorderGoals(currentDateStr, currentGoals)
        }
    }

    private fun handleToggleGoalExpanded(goalId: String) {
        val current = collapsedGoalIdsFlow.value.toMutableSet()
        if (current.contains(goalId)) {
            current.remove(goalId)
        } else {
            current.add(goalId)
        }
        collapsedGoalIdsFlow.value = current
    }

    private suspend fun handleAddSubtask(goalId: String, title: String) {
        val validationErrors = GoalValidator.validateSubtask(title)
        if (validationErrors.isNotEmpty()) {
            userMessageFlow.value = mapValidationError(validationErrors.first())
            return
        }

        val goal = uiState.value.goals.find { it.id == goalId }
        val displayOrder = goal?.subtasks?.size ?: 0
        val now = System.currentTimeMillis()

        val subtask = GoalSubtaskEntity(
            id = UUID.randomUUID().toString(),
            goalId = goalId,
            title = title.trim(),
            isCompleted = false,
            displayOrder = displayOrder,
            createdAt = now
        )
        dailyGoalRepository.addSubtask(subtask)
    }

    private suspend fun handleEditSubtask(subtaskId: String, title: String) {
        val validationErrors = GoalValidator.validateSubtask(title)
        if (validationErrors.isNotEmpty()) {
            userMessageFlow.value = mapValidationError(validationErrors.first())
            return
        }

        dailyGoalRepository.updateSubtaskTitle(subtaskId, title.trim())
        subtaskPendingEditFlow.value = null
    }

    private suspend fun handleReorderSubtask(goalId: String, subtaskId: String, moveUp: Boolean) {
        val goal = uiState.value.goals.find { it.id == goalId } ?: return
        val currentSubtasks = goal.subtasks.map { it.id }.toMutableList()
        val index = currentSubtasks.indexOf(subtaskId)
        if (index == -1) return

        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex in currentSubtasks.indices) {
            val item = currentSubtasks.removeAt(index)
            currentSubtasks.add(targetIndex, item)
            dailyGoalRepository.reorderSubtasks(goalId, currentSubtasks)
        }
    }

    private fun mapValidationError(error: GoalValidationError): String {
        return when (error) {
            GoalValidationError.TitleBlank -> "Goal title cannot be blank"
            is GoalValidationError.TitleTooLong -> "Goal title cannot exceed ${error.maxLength} characters"
            is GoalValidationError.NotesTooLong -> "Notes cannot exceed ${error.maxLength} characters"
            GoalValidationError.TargetDateNull -> "Target date must be specified"
            GoalValidationError.SubtaskTitleBlank -> "Subtask title cannot be blank"
            is GoalValidationError.SubtaskTitleTooLong -> "Subtask title cannot exceed ${error.maxLength} characters"
        }
    }

    private suspend fun handleAddHabitQuick(name: String, measurementType: MeasurementType) {
        if (name.isBlank()) return
        val now = System.currentTimeMillis()
        val currentHabitsCount = habitRepository.getActiveHabitsList().size

        val habit = com.habit1.app.domain.model.Habit(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            measurement = measurementType,
            schedule = HabitSchedule.Daily,
            displayOrder = currentHabitsCount,
            createdAt = java.time.Instant.ofEpochMilli(now),
            updatedAt = java.time.Instant.ofEpochMilli(now)
        )
        habitRepository.createHabit(habit.toEntity())
    }

    private suspend fun handleSaveReview(notes: String?, mood: String?) {
        val repo = dailyReviewRepository ?: return
        val date = currentDateFlow.value
        val dateString = DateTimeUtils.formatDate(date)
        val now = System.currentTimeMillis()
        val existing = repo.getReview(dateString)
        val created = existing?.createdAt ?: now
        val entity = DailyReviewEntity(
            date = dateString,
            notes = notes?.trim()?.ifBlank { null },
            mood = mood?.trim()?.ifBlank { null },
            createdAt = created,
            updatedAt = now
        )
        repo.saveReview(entity)
        isReviewDialogOpenFlow.value = false
    }

    private suspend fun handleDeleteReview() {
        val repo = dailyReviewRepository ?: return
        val date = currentDateFlow.value
        val dateString = DateTimeUtils.formatDate(date)
        repo.deleteReview(dateString)
        isReviewDialogOpenFlow.value = false
    }

    class Factory(
        private val habitRepository: HabitRepository,
        private val habitRecordRepository: HabitRecordRepository,
        private val dailyGoalRepository: DailyGoalRepository,
        private val dailyReviewRepository: DailyReviewRepository? = null,
        private val goalReminderScheduler: com.habit1.app.platform.reminder.GoalReminderScheduler? = null,
        private val categoryRepository: com.habit1.app.data.repository.CategoryRepository? = null,
        private val userPreferences: com.habit1.app.data.local.preferences.UserPreferencesDataStore? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TodayViewModel::class.java)) {
                return TodayViewModel(
                    habitRepository = habitRepository,
                    habitRecordRepository = habitRecordRepository,
                    dailyGoalRepository = dailyGoalRepository,
                    dailyReviewRepository = dailyReviewRepository,
                    goalReminderScheduler = goalReminderScheduler,
                    categoryRepository = categoryRepository,
                    userPreferences = userPreferences
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
