package com.habit1.app.ui.habits.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.habit1.app.core.util.DateTimeUtils
import com.habit1.app.data.local.db.entity.CategoryEntity
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.local.db.entity.HabitRecordEntity
import com.habit1.app.data.repository.CategoryRepository
import com.habit1.app.data.repository.HabitRecordRepository
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.Category
import com.habit1.app.domain.model.HabitRecordStatus
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.usecase.CalculateStreaksUseCase
import com.habit1.app.domain.usecase.ComputeHabitAnalyticsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/**
 * ViewModel coordinating habit management: listing, searching, filtering,
 * presentation sorting, atomic manual reordering, category management, pause,
 * archive, and deletion (Decisions 6.1, 6.2, 6.3).
 */
class HabitListViewModel(
    private val habitRepository: HabitRepository,
    private val categoryRepository: CategoryRepository? = null,
    private val habitRecordRepository: HabitRecordRepository? = null,
    private val reminderCoordinator: com.habit1.app.domain.reminder.HabitReminderCoordinator? = null,
    private val calculateStreaks: CalculateStreaksUseCase = CalculateStreaksUseCase(),
    private val computeHabitAnalytics: ComputeHabitAnalyticsUseCase = ComputeHabitAnalyticsUseCase(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope

    private val searchQueryFlow = MutableStateFlow("")
    private val statusFilterFlow = MutableStateFlow(HabitStatusFilter.ALL)
    private val selectedCategoryFlow = MutableStateFlow<String?>(null)
    private val sortModeFlow = MutableStateFlow(HabitSortMode.MANUAL)
    private val showingArchivedFlow = MutableStateFlow(false)
    private val pendingDeletionFlow = MutableStateFlow<HabitListItem?>(null)
    private val editingCategoryFlow = MutableStateFlow<Category?>(null)
    private val isCategoryEditorOpenFlow = MutableStateFlow(false)
    private val isManageCategoriesDialogOpenFlow = MutableStateFlow(false)

    private val categoriesFlow = categoryRepository?.observeAllCategories() ?: flowOf(emptyList())

    private val today = LocalDate.now(zoneId)
    private val recordsFlow = habitRecordRepository?.observeRecordsForDateRange(
        today.minusDays(90).toString(),
        today.toString()
    ) ?: flowOf(emptyList())

    @Volatile
    private var lastActiveEntities: List<HabitEntity> = emptyList()

    @Volatile
    private var lastCategories: List<Category> = emptyList()

    val uiState: StateFlow<HabitListUiState> = combine(
        habitRepository.observeActiveHabits(),
        habitRepository.observeArchivedHabits(),
        categoriesFlow,
        recordsFlow,
        searchQueryFlow,
        statusFilterFlow,
        selectedCategoryFlow,
        sortModeFlow,
        showingArchivedFlow,
        pendingDeletionFlow,
        editingCategoryFlow,
        isCategoryEditorOpenFlow,
        isManageCategoriesDialogOpenFlow
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val activeEntities = args[0] as List<HabitEntity>
        @Suppress("UNCHECKED_CAST")
        val archivedEntities = args[1] as List<HabitEntity>
        @Suppress("UNCHECKED_CAST")
        val categoryEntities = args[2] as List<CategoryEntity>
        @Suppress("UNCHECKED_CAST")
        val recordEntities = args[3] as List<HabitRecordEntity>
        val searchQuery = args[4] as String
        val statusFilter = args[5] as HabitStatusFilter
        val selectedCategoryId = args[6] as String?
        val sortMode = args[7] as HabitSortMode
        val showingArchived = args[8] as Boolean
        val pendingDeletion = args[9] as HabitListItem?
        val editingCategory = args[10] as Category?
        val isCategoryEditorOpen = args[11] as Boolean
        val isManageCategoriesDialogOpen = args[12] as Boolean

        lastActiveEntities = activeEntities
        val domainCategories: List<Category> = categoryEntities.map { entity: CategoryEntity -> entity.toDomain() }
        lastCategories = domainCategories
        val categoriesMap: Map<String, Category> = domainCategories.associateBy { it.id }
        val recordsByHabit = recordEntities.groupBy { it.habitId }

        val activeItems = mapEntitiesToUiItems(
            entities = activeEntities,
            categoriesMap = categoriesMap,
            recordsByHabit = recordsByHabit,
            isManualSort = sortMode == HabitSortMode.MANUAL
        )

        val archivedItems = mapEntitiesToUiItems(
            entities = archivedEntities,
            categoriesMap = categoriesMap,
            recordsByHabit = recordsByHabit,
            isManualSort = false
        )

        val allItems = activeItems + archivedItems

        // Filter by Status, Category, and Search query (Decision 6.2)
        val filtered = allItems.filter { item ->
            val matchesStatus = when (statusFilter) {
                HabitStatusFilter.ALL -> true
                HabitStatusFilter.ACTIVE -> !item.isArchived && !item.isPaused
                HabitStatusFilter.PAUSED -> !item.isArchived && item.isPaused
                HabitStatusFilter.ARCHIVED -> item.isArchived
            }

            val matchesCategory = selectedCategoryId == null || item.categoryId == selectedCategoryId

            val matchesSearch = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                (item.description?.contains(searchQuery, ignoreCase = true) == true)

            matchesStatus && matchesCategory && matchesSearch
        }

        // Presentation Sorting Presets (Decision 6.2)
        val sortedList = when (sortMode) {
            HabitSortMode.MANUAL -> {
                // Preserves natural order by display_order
                filtered.sortedWith(compareBy<HabitListItem> { if (it.isArchived) 1 else 0 }.thenBy { it.displayOrder })
            }
            HabitSortMode.NAME_ASC -> {
                filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
            }
            HabitSortMode.STREAK_DESC -> {
                filtered.sortedWith(
                    compareByDescending<HabitListItem> { it.currentStreak }
                        .thenBy { it.name.lowercase(Locale.getDefault()) }
                )
            }
            HabitSortMode.ADHERENCE_ASC -> {
                filtered.sortedWith(
                    compareBy<HabitListItem> { it.adherenceRate }
                        .thenBy { it.name.lowercase(Locale.getDefault()) }
                )
            }
            HabitSortMode.SCHEDULE_FREQ -> {
                filtered.sortedWith(
                    compareByDescending<HabitListItem> { it.scheduleDaysCount }
                        .thenBy { it.name.lowercase(Locale.getDefault()) }
                )
            }
        }

        HabitListUiState(
            activeHabits = activeItems,
            archivedHabits = archivedItems,
            filteredHabits = sortedList,
            categories = domainCategories,
            searchQuery = searchQuery,
            statusFilter = statusFilter,
            selectedCategoryId = selectedCategoryId,
            sortMode = sortMode,
            showingArchived = showingArchived,
            habitPendingDeletion = pendingDeletion,
            editingCategory = editingCategory,
            isCategoryEditorOpen = isCategoryEditorOpen,
            isManageCategoriesDialogOpen = isManageCategoriesDialogOpen,
            isLoading = false
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HabitListUiState(isLoading = true)
    )

    fun onEvent(event: HabitListUiEvent) {
        scope.launch {
            when (event) {
                is HabitListUiEvent.ToggleShowArchived -> {
                    showingArchivedFlow.value = event.showArchived
                }
                is HabitListUiEvent.PauseHabit -> {
                    habitRepository.pauseHabit(event.habitId, isPaused = true)
                    reminderCoordinator?.onHabitPaused(event.habitId)
                }
                is HabitListUiEvent.ResumeHabit -> {
                    habitRepository.pauseHabit(event.habitId, isPaused = false)
                    reminderCoordinator?.onHabitResumed(event.habitId)
                }
                is HabitListUiEvent.ArchiveHabit -> {
                    habitRepository.archiveHabit(event.habitId, isArchived = true)
                    reminderCoordinator?.onHabitArchived(event.habitId)
                }
                is HabitListUiEvent.UnarchiveHabit -> {
                    habitRepository.archiveHabit(event.habitId, isArchived = false)
                    reminderCoordinator?.onHabitUnarchived(event.habitId)
                }
                is HabitListUiEvent.MoveUp -> {
                    handleReorder(event.habitId, moveUp = true)
                }
                is HabitListUiEvent.MoveDown -> {
                    handleReorder(event.habitId, moveUp = false)
                }
                is HabitListUiEvent.RequestDeleteHabit -> {
                    pendingDeletionFlow.value = event.habit
                }
                is HabitListUiEvent.ConfirmDeleteHabit -> {
                    pendingDeletionFlow.value?.let { habit ->
                        habitRepository.deleteHabit(habit.id)
                        reminderCoordinator?.onHabitDeleted(habit.id)
                        pendingDeletionFlow.value = null
                    }
                }
                is HabitListUiEvent.CancelDeleteHabit -> {
                    pendingDeletionFlow.value = null
                }
                is HabitListUiEvent.UpdateSearchQuery -> {
                    searchQueryFlow.value = event.query
                }
                is HabitListUiEvent.SelectStatusFilter -> {
                    statusFilterFlow.value = event.filter
                    if (event.filter == HabitStatusFilter.ARCHIVED) {
                        showingArchivedFlow.value = true
                    }
                }
                is HabitListUiEvent.SelectCategoryFilter -> {
                    selectedCategoryFlow.value = event.categoryId
                }
                is HabitListUiEvent.SelectSortMode -> {
                    sortModeFlow.value = event.sortMode
                }
                is HabitListUiEvent.OpenCategoryEditor -> {
                    editingCategoryFlow.value = event.category
                    isCategoryEditorOpenFlow.value = true
                }
                is HabitListUiEvent.DismissCategoryEditor -> {
                    editingCategoryFlow.value = null
                    isCategoryEditorOpenFlow.value = false
                }
                is HabitListUiEvent.SaveCategory -> {
                    val repo = categoryRepository ?: return@launch
                    if (event.id == null) {
                        val newEntity = CategoryEntity(
                            id = UUID.randomUUID().toString(),
                            name = event.name,
                            colorHex = event.colorHex,
                            icon = event.icon,
                            displayOrder = lastCategories.size,
                            createdAt = System.currentTimeMillis()
                        )
                        repo.createCategory(newEntity)
                    } else {
                        val existing = repo.getCategoryById(event.id)
                        if (existing != null) {
                            repo.updateCategory(
                                existing.copy(
                                    name = event.name,
                                    colorHex = event.colorHex,
                                    icon = event.icon
                                )
                            )
                        }
                    }
                    editingCategoryFlow.value = null
                    isCategoryEditorOpenFlow.value = false
                }
                is HabitListUiEvent.DeleteCategory -> {
                    categoryRepository?.deleteCategory(event.id)
                    if (selectedCategoryFlow.value == event.id) {
                        selectedCategoryFlow.value = null
                    }
                }
                is HabitListUiEvent.ToggleManageCategoriesDialog -> {
                    isManageCategoriesDialogOpenFlow.value = event.open
                }
            }
        }
    }

    private suspend fun handleReorder(habitId: String, moveUp: Boolean) {
        if (sortModeFlow.value != HabitSortMode.MANUAL) {
            sortModeFlow.value = HabitSortMode.MANUAL
        }
        val currentActive = lastActiveEntities.map { it.id }.toMutableList()
        val index = currentActive.indexOf(habitId)
        if (index == -1) return

        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex in currentActive.indices) {
            val item = currentActive.removeAt(index)
            currentActive.add(targetIndex, item)
            habitRepository.reorderHabits(currentActive)
        }
    }

    private fun mapEntitiesToUiItems(
        entities: List<HabitEntity>,
        categoriesMap: Map<String, Category>,
        recordsByHabit: Map<String, List<HabitRecordEntity>>,
        isManualSort: Boolean
    ): List<HabitListItem> {
        val size = entities.size
        return entities.mapIndexed { index, entity ->
            val domain = entity.toDomain()
            val category = entity.categoryId?.let { categoriesMap[it] }

            val measurementSummary = when (val m = domain.measurement) {
                is MeasurementType.BooleanChoice -> "Done / Not Done"
                is MeasurementType.Count -> "${m.target} ${m.unit ?: "reps"}"
                is MeasurementType.Duration -> "${m.targetMinutes} mins"
                is MeasurementType.Quantity -> "${m.target} ${m.unit}"
            }

            val scheduleSummary = when (val s = domain.schedule) {
                is HabitSchedule.Daily -> "Daily"
                is HabitSchedule.SpecificDays -> s.days.sorted()
                    .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
                is HabitSchedule.Interval -> "Every ${s.everyNDays} days"
            }

            val scheduleDaysCount = when (val s = domain.schedule) {
                is HabitSchedule.Daily -> 7
                is HabitSchedule.SpecificDays -> s.days.size
                is HabitSchedule.Interval -> (7 / s.everyNDays.coerceAtLeast(1)).coerceAtLeast(1)
            }

            val reminderSummary = domain.reminderTime?.let {
                "Reminder: " + DateTimeUtils.format12HourTime(it)
            }

            val habitRecords = recordsByHabit[domain.id]?.map { it.toDomain() } ?: emptyList()
            val streak = calculateStreaks.execute(domain, habitRecords, today, zoneId)

            val totalScheduledDays = habitRecords.count { it.status == HabitRecordStatus.DONE || it.status == HabitRecordStatus.PARTIAL || it.status == HabitRecordStatus.MISSED }
            val doneDays = habitRecords.count { it.status == HabitRecordStatus.DONE }
            val adherence = if (totalScheduledDays > 0) (doneDays.toDouble() / totalScheduledDays) else 0.0

            HabitListItem(
                id = domain.id,
                name = domain.name,
                description = domain.description,
                measurementSummary = measurementSummary,
                scheduleSummary = scheduleSummary,
                isPaused = domain.isPaused,
                isArchived = domain.isArchived,
                displayOrder = domain.displayOrder,
                canMoveUp = isManualSort && index > 0,
                canMoveDown = isManualSort && index < size - 1,
                reminderSummary = reminderSummary,
                colorHex = domain.colorHex,
                categoryId = category?.id,
                categoryName = category?.name,
                categoryColorHex = category?.colorHex,
                categoryIcon = category?.icon,
                currentStreak = streak.currentStreak,
                adherenceRate = adherence,
                scheduleDaysCount = scheduleDaysCount
            )
        }
    }

    class Factory(
        private val habitRepository: HabitRepository,
        private val categoryRepository: CategoryRepository? = null,
        private val habitRecordRepository: HabitRecordRepository? = null,
        private val reminderCoordinator: com.habit1.app.domain.reminder.HabitReminderCoordinator? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HabitListViewModel::class.java)) {
                return HabitListViewModel(
                    habitRepository = habitRepository,
                    categoryRepository = categoryRepository,
                    habitRecordRepository = habitRecordRepository,
                    reminderCoordinator = reminderCoordinator
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
