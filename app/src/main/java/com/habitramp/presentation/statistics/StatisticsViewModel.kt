package com.habitramp.presentation.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.habitramp.AppContainer
import com.habitramp.data.repository.HabitRepository
import com.habitramp.data.repository.StatisticsPeriod
import com.habitramp.domain.calculator.PeriodStatistics
import com.habitramp.domain.model.Habit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StatisticsUiState(
    val loading: Boolean = true,
    val habits: List<Habit> = emptyList(),
    val habit: Habit? = null,
    val period: StatisticsPeriod = StatisticsPeriod.WEEK,
    val stats: PeriodStatistics? = null,
    val error: String? = null
)

class StatisticsViewModel(
    private val repository: HabitRepository,
    initialHabitId: Long?
) : ViewModel() {
    private val selectedId = MutableStateFlow(initialHabitId)
    private val period = MutableStateFlow(StatisticsPeriod.WEEK)

    private val request = combine(selectedId, period) { id, p -> id to p }

    val uiState: StateFlow<StatisticsUiState> = combine(
        repository.observeAllHabits(),
        request
    ) { habits, req ->
        habits to req
    }.flatMapLatest { (habits, req) ->
        flow {
            val habit = habits.firstOrNull { it.id == req.first } ?: habits.firstOrNull()
            emit(
                StatisticsUiState(
                    loading = true,
                    habits = habits,
                    habit = habit,
                    period = req.second
                )
            )
            val stats = try {
                habit?.let { repository.getStatistics(it.id, req.second) }
            } catch (t: Throwable) {
                emit(
                    StatisticsUiState(
                        loading = false,
                        habits = habits,
                        habit = habit,
                        period = req.second,
                        error = t.message ?: "Не удалось рассчитать статистику"
                    )
                )
                return@flow
            }
            emit(
                StatisticsUiState(
                    loading = false,
                    habits = habits,
                    habit = habit,
                    period = req.second,
                    stats = stats
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatisticsUiState()
    )

    init {
        viewModelScope.launch {
            repository.observeAllHabits().collect { habits ->
                val current = selectedId.value
                if (current == null || habits.none { it.id == current }) {
                    selectedId.value = habits.firstOrNull()?.id
                }
            }
        }
    }

    fun selectHabit(id: Long) {
        selectedId.value = id
    }

    fun setPeriod(value: StatisticsPeriod) {
        period.value = value
    }

    companion object {
        fun factory(container: AppContainer, habitId: Long?): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { StatisticsViewModel(container.repository, habitId) }
            }
    }
}
