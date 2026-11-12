package com.habitramp.presentation.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.habitramp.AppContainer
import com.habitramp.core.model.HabitStatus
import com.habitramp.data.repository.HabitRepository
import com.habitramp.domain.model.Habit
import com.habitramp.domain.model.UserProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class ManageUiState(
    val loading: Boolean = true,
    val active: List<Habit> = emptyList(),
    val archived: List<Habit> = emptyList(),
    val completed: List<Habit> = emptyList(),
    val progress: UserProgress = UserProgress(0, 1, LocalDate.now()),
    val activeCount: Int = 0
) {
    val canCreate: Boolean get() = activeCount < progress.unlockedSlots
}

class ManageHabitsViewModel(
    repository: HabitRepository
) : ViewModel() {
    val uiState: StateFlow<ManageUiState> = combine(
        repository.observeAllHabits(),
        repository.observeUserProgress(),
        repository.observeActiveCount()
    ) { habits, progress, activeCount ->
        ManageUiState(
            loading = false,
            active = habits.filter { it.status == HabitStatus.ACTIVE || it.status == HabitStatus.PAUSED },
            archived = habits.filter { it.status == HabitStatus.ARCHIVED },
            completed = habits.filter { it.status == HabitStatus.COMPLETED },
            progress = progress,
            activeCount = activeCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ManageUiState()
    )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ManageHabitsViewModel(container.repository) }
        }
    }
}
