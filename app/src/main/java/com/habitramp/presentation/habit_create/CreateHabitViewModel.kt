package com.habitramp.presentation.habit_create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.habitramp.AppContainer
import com.habitramp.core.model.MeasurementUnit
import com.habitramp.core.model.Units
import com.habitramp.core.util.parseDecimalInput
import com.habitramp.data.repository.HabitRepository
import com.habitramp.domain.model.UserProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

data class CreateHabitUiState(
    val name: String = "",
    val initialValue: String = "",
    val unit: MeasurementUnit = Units.DEFAULT,
    val progress: UserProgress = UserProgress(0, 1, LocalDate.now()),
    val activeCount: Int = 0,
    val error: String? = null,
    val savedId: Long? = null,
    val saving: Boolean = false
) {
    val canCreate: Boolean get() = activeCount < progress.unlockedSlots
    val initialNumber: BigDecimal? get() = parseDecimalInput(initialValue)
}

class CreateHabitViewModel(
    private val repository: HabitRepository
) : ViewModel() {
    private val form = MutableStateFlow(Form())
    private val saving = MutableStateFlow(false)
    private val savedId = MutableStateFlow<Long?>(null)
    private val error = MutableStateFlow<String?>(null)

    private data class Form(
        val name: String = "",
        val initialValue: String = "",
        val unit: MeasurementUnit = Units.DEFAULT
    )

    private data class ResultState(val savedId: Long?, val error: String?)

    private val resultFlow = combine(savedId, error) { id, err -> ResultState(id, err) }

    val uiState: StateFlow<CreateHabitUiState> = combine(
        form,
        repository.observeUserProgress(),
        repository.observeActiveCount(),
        saving,
        resultFlow
    ) { f, progress, activeCount, isSaving, result ->
        CreateHabitUiState(
            name = f.name,
            initialValue = f.initialValue,
            unit = f.unit,
            progress = progress,
            activeCount = activeCount,
            saving = isSaving,
            savedId = result.savedId,
            error = result.error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CreateHabitUiState()
    )

    fun setName(value: String) {
        form.value = form.value.copy(name = value)
        error.value = null
    }

    fun setInitialValue(value: String) {
        form.value = form.value.copy(initialValue = value)
        error.value = null
    }

    fun setUnit(unit: MeasurementUnit) {
        form.value = form.value.copy(unit = unit)
        error.value = null
    }

    fun save() {
        val f = form.value
        val value = parseDecimalInput(f.initialValue)
        if (f.name.isBlank()) {
            error.value = "Укажите название привычки"
            return
        }
        if (value == null || value <= BigDecimal.ZERO) {
            error.value = "Укажите начальное значение больше нуля"
            return
        }
        saving.value = true
        viewModelScope.launch {
            runCatching {
                repository.createHabit(f.name, value, f.unit.code)
            }.onSuccess { result ->
                result.fold(
                    onSuccess = { id -> savedId.value = id },
                    onFailure = { error.value = it.message ?: "Не удалось создать привычку" }
                )
            }.onFailure {
                error.value = it.message ?: "Не удалось создать привычку"
            }
            saving.value = false
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { CreateHabitViewModel(container.repository) }
        }
    }
}
