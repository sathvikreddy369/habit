package com.habit1.app.ui.habits.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.habit1.app.data.local.db.entity.HabitEntity
import com.habit1.app.data.repository.HabitRepository
import com.habit1.app.domain.mapper.EntityMappers.toDomain
import com.habit1.app.domain.mapper.ScheduleConfigSerializer
import com.habit1.app.domain.model.HabitSchedule
import com.habit1.app.domain.model.MeasurementType
import com.habit1.app.domain.validation.HabitValidationError
import com.habit1.app.domain.validation.HabitValidator
import com.habit1.app.domain.validation.MeasurementKind
import com.habit1.app.domain.validation.ScheduleKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

/**
 * ViewModel coordinating habit creation and editing.
 * Guarantees that editing only affects the future definition and leaves historical records untouched.
 */
class HabitFormViewModel(
    private val habitRepository: HabitRepository,
    private val reminderCoordinator: com.habit1.app.domain.reminder.HabitReminderCoordinator? = null,
    private val initialHabitId: String? = null,
    private val initialTemplateId: String? = null,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(
        HabitFormUiState(
            habitId = initialHabitId,
            isEditMode = initialHabitId != null
        )
    )
    val uiState: StateFlow<HabitFormUiState> = _uiState.asStateFlow()

    init {
        if (initialHabitId != null) {
            loadHabit(initialHabitId)
        } else if (initialTemplateId != null) {
            loadTemplate(initialTemplateId)
        }
    }

    private fun loadTemplate(templateId: String) {
        val template = com.habit1.app.domain.template.HabitTemplatesProvider.getTemplateById(templateId) ?: return
        val measurementKind = when (template.measurementType) {
            "COUNT" -> MeasurementKind.COUNT
            "DURATION" -> MeasurementKind.DURATION
            "QUANTITY" -> MeasurementKind.QUANTITY
            else -> MeasurementKind.BOOLEAN
        }
        val targetInput = if (measurementKind == MeasurementKind.DURATION || measurementKind == MeasurementKind.COUNT) {
            template.targetValue.toInt().toString()
        } else {
            template.targetValue.toString()
        }
        val reminderStr = template.suggestedReminderTime?.let { com.habit1.app.core.util.DateTimeUtils.formatTime(it) } ?: ""

        _uiState.update {
            it.copy(
                name = template.title,
                description = template.description ?: "",
                measurementKind = measurementKind,
                targetInput = targetInput,
                unitInput = template.unit ?: "",
                reminderTimeInput = reminderStr
            )
        }
    }

    private fun loadHabit(habitId: String) {
        scope.launch {
            try {
                val entity = habitRepository.getHabitById(habitId) ?: return@launch
                val domainHabit = entity.toDomain()

                val measurementKind = when (domainHabit.measurement) {
                    is MeasurementType.BooleanChoice -> MeasurementKind.BOOLEAN
                    is MeasurementType.Count -> MeasurementKind.COUNT
                    is MeasurementType.Duration -> MeasurementKind.DURATION
                    is MeasurementType.Quantity -> MeasurementKind.QUANTITY
                }

                val targetInput = when (val m = domainHabit.measurement) {
                    is MeasurementType.BooleanChoice -> "1"
                    is MeasurementType.Count -> m.target.toString()
                    is MeasurementType.Duration -> m.targetMinutes.toString()
                    is MeasurementType.Quantity -> m.target.toString()
                }

                val unitInput = when (val m = domainHabit.measurement) {
                    is MeasurementType.BooleanChoice -> ""
                    is MeasurementType.Count -> m.unit ?: ""
                    is MeasurementType.Duration -> ""
                    is MeasurementType.Quantity -> m.unit
                }

                val scheduleKind = when (domainHabit.schedule) {
                    is HabitSchedule.Daily -> ScheduleKind.DAILY
                    is HabitSchedule.SpecificDays -> ScheduleKind.SPECIFIC_DAYS
                    is HabitSchedule.Interval -> ScheduleKind.INTERVAL
                }

                val selectedDays = when (val s = domainHabit.schedule) {
                    is HabitSchedule.SpecificDays -> s.days
                    else -> setOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY
                    )
                }

                val intervalDays = when (val s = domainHabit.schedule) {
                    is HabitSchedule.Interval -> s.everyNDays.toString()
                    else -> "2"
                }

                val anchorDate = when (val s = domainHabit.schedule) {
                    is HabitSchedule.Interval -> s.anchorDate
                    else -> LocalDate.now()
                }

                _uiState.update {
                    it.copy(
                        habitId = habitId,
                        name = domainHabit.name,
                        description = domainHabit.description ?: "",
                        measurementKind = measurementKind,
                        targetInput = targetInput,
                        unitInput = unitInput,
                        scheduleKind = scheduleKind,
                        selectedDays = selectedDays,
                        intervalDaysInput = intervalDays,
                        anchorDate = anchorDate,
                        reminderTimeInput = entity.reminderTime ?: "",
                        colorHex = entity.colorHex ?: com.habit1.app.ui.theme.HabitColors.DEFAULT_COLOR_HEX,
                        originalCreatedAt = entity.createdAt,
                        originalDisplayOrder = entity.displayOrder,
                        originalIsPaused = entity.isPaused,
                        originalIsArchived = entity.isArchived,
                        isEditMode = true
                    )
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    fun onEvent(event: HabitFormUiEvent) {
        when (event) {
            is HabitFormUiEvent.UpdateName -> {
                _uiState.update { it.copy(name = event.name, errors = it.errors - HabitValidationError.NameBlank - HabitValidationError.NameTooLong()) }
            }
            is HabitFormUiEvent.UpdateDescription -> {
                _uiState.update { it.copy(description = event.description) }
            }
            is HabitFormUiEvent.SelectMeasurementKind -> {
                _uiState.update {
                    val isFromBoolean = it.measurementKind == MeasurementKind.BOOLEAN
                    val defaultTarget = when (event.kind) {
                        MeasurementKind.BOOLEAN -> "1"
                        MeasurementKind.COUNT -> {
                            val current = it.targetInput.trim().toIntOrNull()
                            if (isFromBoolean || current == null || current <= 0) "10" else current.toString()
                        }
                        MeasurementKind.DURATION -> {
                            val current = it.targetInput.trim().toIntOrNull()
                            if (isFromBoolean || current == null || current <= 0) "30" else current.toString()
                        }
                        MeasurementKind.QUANTITY -> {
                            val current = HabitValidator.parseDecimal(it.targetInput)
                            if (isFromBoolean || current == null || current <= 0.0) "2.0" else it.targetInput.trim()
                        }
                    }
                    val updatedUnit = when (event.kind) {
                        MeasurementKind.BOOLEAN -> ""
                        MeasurementKind.DURATION -> "mins"
                        MeasurementKind.COUNT -> if (isFromBoolean || it.unitInput == "L" || it.unitInput == "km") "reps" else it.unitInput
                        MeasurementKind.QUANTITY -> if (it.unitInput == "reps" || it.unitInput == "mins" || it.unitInput == "pages") "" else it.unitInput
                    }
                    it.copy(
                        measurementKind = event.kind,
                        targetInput = defaultTarget,
                        unitInput = updatedUnit,
                        errors = it.errors - HabitValidationError.TargetMustBePositive - HabitValidationError.UnitRequired
                    )
                }
            }
            is HabitFormUiEvent.UpdateTarget -> {
                _uiState.update { it.copy(targetInput = event.target, errors = it.errors - HabitValidationError.TargetMustBePositive) }
            }
            is HabitFormUiEvent.UpdateUnit -> {
                _uiState.update { it.copy(unitInput = event.unit, errors = it.errors - HabitValidationError.UnitRequired) }
            }
            is HabitFormUiEvent.SelectScheduleKind -> {
                _uiState.update { it.copy(scheduleKind = event.kind) }
            }
            is HabitFormUiEvent.ToggleDay -> {
                _uiState.update {
                    val updatedDays = if (it.selectedDays.contains(event.day)) {
                        it.selectedDays - event.day
                    } else {
                        it.selectedDays + event.day
                    }
                    it.copy(selectedDays = updatedDays, errors = it.errors - HabitValidationError.SpecificDaysEmpty)
                }
            }
            is HabitFormUiEvent.UpdateIntervalDays -> {
                _uiState.update { it.copy(intervalDaysInput = event.days, errors = it.errors - HabitValidationError.IntervalTooSmall()) }
            }
            is HabitFormUiEvent.UpdateAnchorDate -> {
                _uiState.update { it.copy(anchorDate = event.date) }
            }
            is HabitFormUiEvent.UpdateReminderTime -> {
                _uiState.update { it.copy(reminderTimeInput = event.reminderTime) }
            }
            is HabitFormUiEvent.UpdateColor -> {
                _uiState.update { it.copy(colorHex = event.colorHex) }
            }
            is HabitFormUiEvent.SaveHabit -> saveHabit()
            is HabitFormUiEvent.ResetSaveState -> {
                _uiState.update { it.copy(isSaved = false) }
            }
            is HabitFormUiEvent.ArchiveHabit -> {
                val id = _uiState.value.habitId ?: return
                viewModelScope.launch {
                    habitRepository.archiveHabit(id, isArchived = true)
                    reminderCoordinator?.onHabitArchived(id)
                    _uiState.update { it.copy(isSaved = true) }
                }
            }
            is HabitFormUiEvent.UnarchiveHabit -> {
                val id = _uiState.value.habitId ?: return
                viewModelScope.launch {
                    habitRepository.archiveHabit(id, isArchived = false)
                    val updated = habitRepository.getHabitById(id)
                    if (updated != null) {
                        reminderCoordinator?.onHabitCreated(updated.toDomain())
                    }
                    _uiState.update { it.copy(isSaved = true) }
                }
            }
            is HabitFormUiEvent.TogglePauseHabit -> {
                val id = _uiState.value.habitId ?: return
                val newPausedState = !_uiState.value.originalIsPaused
                viewModelScope.launch {
                    habitRepository.pauseHabit(id, isPaused = newPausedState)
                    if (newPausedState) {
                        reminderCoordinator?.onHabitPaused(id)
                    } else {
                        val updated = habitRepository.getHabitById(id)
                        if (updated != null) {
                            reminderCoordinator?.onHabitCreated(updated.toDomain())
                        }
                    }
                    _uiState.update { it.copy(isSaved = true) }
                }
            }
            is HabitFormUiEvent.DeleteHabit -> {
                val id = _uiState.value.habitId ?: return
                viewModelScope.launch {
                    habitRepository.deleteHabit(id)
                    reminderCoordinator?.onHabitDeleted(id)
                    _uiState.update { it.copy(isSaved = true) }
                }
            }
        }
    }

    private fun saveHabit() {
        val state = _uiState.value
        val errors = mutableSetOf<HabitValidationError>()

        HabitValidator.validateName(state.name)?.let { errors.add(it) }
        errors.addAll(
            HabitValidator.validateRawMeasurement(
                kind = state.measurementKind,
                targetStr = state.targetInput,
                unitStr = state.unitInput
            )
        )
        errors.addAll(
            HabitValidator.validateRawSchedule(
                kind = state.scheduleKind,
                selectedDays = state.selectedDays,
                intervalDaysStr = state.intervalDaysInput
            )
        )

        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }

        scope.launch {
            try {
                _uiState.update { it.copy(isSaving = true) }

                val measurement: MeasurementType = when (state.measurementKind) {
                    MeasurementKind.BOOLEAN -> MeasurementType.BooleanChoice
                    MeasurementKind.COUNT -> MeasurementType.Count(
                        target = state.targetInput.trim().toInt(),
                        unit = state.unitInput.trim().ifEmpty { null }
                    )
                    MeasurementKind.DURATION -> MeasurementType.Duration(
                        targetMinutes = state.targetInput.trim().toInt()
                    )
                    MeasurementKind.QUANTITY -> MeasurementType.Quantity(
                        target = HabitValidator.parseDecimal(state.targetInput) ?: 1.0,
                        unit = state.unitInput.trim()
                    )
                }

                val schedule: HabitSchedule = when (state.scheduleKind) {
                    ScheduleKind.DAILY -> HabitSchedule.Daily
                    ScheduleKind.SPECIFIC_DAYS -> HabitSchedule.SpecificDays(days = state.selectedDays)
                    ScheduleKind.INTERVAL -> HabitSchedule.Interval(
                        everyNDays = state.intervalDaysInput.trim().toInt(),
                        anchorDate = state.anchorDate
                    )
                }

                val (scheduleTypeStr, scheduleConfigStr) = ScheduleConfigSerializer.serialize(schedule)
                val now = System.currentTimeMillis()

                val targetValue = when (measurement) {
                    is MeasurementType.BooleanChoice -> 1.0
                    is MeasurementType.Count -> measurement.target.toDouble()
                    is MeasurementType.Duration -> measurement.targetMinutes.toDouble()
                    is MeasurementType.Quantity -> measurement.target
                }

                val unit = when (measurement) {
                    is MeasurementType.BooleanChoice -> null
                    is MeasurementType.Count -> measurement.unit
                    is MeasurementType.Duration -> "mins"
                    is MeasurementType.Quantity -> measurement.unit
                }

                val reminderTime = state.reminderTimeInput.trim().ifEmpty { null }

                if (state.habitId == null) {
                    // Creation
                    val currentCount = habitRepository.getActiveHabitsList().size
                    val newEntity = HabitEntity(
                        id = UUID.randomUUID().toString(),
                        name = state.name.trim(),
                        description = state.description.trim().ifEmpty { null },
                        measurementType = measurement.typeName,
                        targetValue = targetValue,
                        unit = unit,
                        scheduleType = scheduleTypeStr,
                        scheduleConfig = scheduleConfigStr,
                        reminderTime = reminderTime,
                        displayOrder = currentCount,
                        isPaused = false,
                        isArchived = false,
                        colorHex = state.colorHex,
                        createdAt = now,
                        updatedAt = now
                    )
                    habitRepository.createHabit(newEntity)
                    reminderCoordinator?.onHabitCreated(newEntity.toDomain())
                } else {
                    // Edit - preserving createdAt, isPaused, isArchived, displayOrder
                    val updatedEntity = HabitEntity(
                        id = state.habitId,
                        name = state.name.trim(),
                        description = state.description.trim().ifEmpty { null },
                        measurementType = measurement.typeName,
                        targetValue = targetValue,
                        unit = unit,
                        scheduleType = scheduleTypeStr,
                        scheduleConfig = scheduleConfigStr,
                        reminderTime = reminderTime,
                        displayOrder = state.originalDisplayOrder,
                        isPaused = state.originalIsPaused,
                        isArchived = state.originalIsArchived,
                        colorHex = state.colorHex,
                        createdAt = state.originalCreatedAt ?: now,
                        updatedAt = now
                    )
                    habitRepository.updateHabit(updatedEntity)
                    reminderCoordinator?.onHabitUpdated(updatedEntity.toDomain())
                }

                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    class Factory(
        private val habitRepository: HabitRepository,
        private val reminderCoordinator: com.habit1.app.domain.reminder.HabitReminderCoordinator? = null,
        private val habitId: String? = null,
        private val templateId: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HabitFormViewModel::class.java)) {
                return HabitFormViewModel(habitRepository, reminderCoordinator, habitId, templateId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
