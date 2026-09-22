package com.habit1.app.ui.habits.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

/**
 * ViewModel coordinating habit management: listing, reordering, pause, archive, and deletion.
 */
class HabitListViewModel(
    private val habitRepository: HabitRepository,
    private val reminderCoordinator: com.habit1.app.domain.reminder.HabitReminderCoordinator? = null,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope

    private val showingArchivedFlow = MutableStateFlow(false)
    private val pendingDeletionFlow = MutableStateFlow<HabitListItem?>(null)

    val uiState: StateFlow<HabitListUiState> = combine(
        habitRepository.observeActiveHabits(),
        habitRepository.observeArchivedHabits(),
        showingArchivedFlow,
        pendingDeletionFlow
    ) { activeEntities, archivedEntities, showingArchived, pendingDeletion ->
        val activeItems = mapEntitiesToUiItems(activeEntities)
        val archivedItems = mapEntitiesToUiItems(archivedEntities)

        HabitListUiState(
            activeHabits = activeItems,
            archivedHabits = archivedItems,
            showingArchived = showingArchived,
            habitPendingDeletion = pendingDeletion,
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
            }
        }
    }

    private suspend fun handleReorder(habitId: String, moveUp: Boolean) {
        val currentActive = uiState.value.activeHabits.map { it.id }.toMutableList()
        val index = currentActive.indexOf(habitId)
        if (index == -1) return

        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex in currentActive.indices) {
            val item = currentActive.removeAt(index)
            currentActive.add(targetIndex, item)
            habitRepository.reorderHabits(currentActive)
        }
    }

    private fun mapEntitiesToUiItems(entities: List<HabitEntity>): List<HabitListItem> {
        val size = entities.size
        return entities.mapIndexed { index, entity ->
            val domain = entity.toDomain()
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

            val reminderSummary = domain.reminderTime?.let {
                "🔔 " + com.habit1.app.core.util.DateTimeUtils.format12HourTime(it)
            }

            HabitListItem(
                id = domain.id,
                name = domain.name,
                description = domain.description,
                measurementSummary = measurementSummary,
                scheduleSummary = scheduleSummary,
                isPaused = domain.isPaused,
                isArchived = domain.isArchived,
                displayOrder = domain.displayOrder,
                canMoveUp = index > 0,
                canMoveDown = index < size - 1,
                reminderSummary = reminderSummary
            )
        }
    }

    class Factory(
        private val habitRepository: HabitRepository,
        private val reminderCoordinator: com.habit1.app.domain.reminder.HabitReminderCoordinator? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HabitListViewModel::class.java)) {
                return HabitListViewModel(habitRepository, reminderCoordinator) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
