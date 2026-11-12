package com.habitramp.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.habitramp.AppContainer
import com.habitramp.core.model.CompletionStatus
import com.habitramp.core.model.EventType
import com.habitramp.data.repository.HabitRepository
import com.habitramp.domain.model.DailyRecord
import com.habitramp.domain.model.Habit
import com.habitramp.domain.model.HabitEvent
import com.habitramp.domain.model.HabitPause
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

data class DayDetail(
    val date: LocalDate,
    val status: CompletionStatus?,
    val plannedValue: BigDecimal?,
    val actualValue: BigDecimal?,
    val isPause: Boolean,
    val pauseEnd: LocalDate?,
    val isManualReset: Boolean
)

data class HistoryUiState(
    val loading: Boolean = true,
    val habits: List<Habit> = emptyList(),
    val habit: Habit? = null,
    val month: YearMonth = YearMonth.now(),
    val records: List<DailyRecord> = emptyList(),
    val pauses: List<HabitPause> = emptyList(),
    val resets: List<HabitEvent> = emptyList(),
    val detail: DayDetail? = null
)

class HistoryViewModel(
    private val repository: HabitRepository,
    initialHabitId: Long?
) : ViewModel() {
    private val selectedId = MutableStateFlow(initialHabitId)
    private val month = MutableStateFlow(YearMonth.now())
    private val detailDate = MutableStateFlow<LocalDate?>(null)

    private data class Base(val habits: List<Habit>, val habit: Habit?, val month: YearMonth)
    private data class Data(
        val records: List<DailyRecord>,
        val pauses: List<HabitPause>,
        val resets: List<HabitEvent>
    )

    private val baseFlow = combine(
        repository.observeAllHabits(),
        selectedId,
        month
    ) { habits, id, m ->
        Base(habits, habits.firstOrNull { it.id == id } ?: habits.firstOrNull(), m)
    }

    private val selectedHabitId = baseFlow.map { it.habit?.id }.distinctUntilChanged()

    private val dataFlow = selectedHabitId.flatMapLatest { id ->
        if (id == null) {
            flowOf(Data(emptyList(), emptyList(), emptyList()))
        } else {
            combine(
                repository.observeRecords(id),
                repository.observePauses(id),
                repository.observeEvents(id)
            ) { records, pauses, events ->
                Data(records, pauses, events.filter { it.type == EventType.MANUAL_RESET })
            }
        }
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        baseFlow,
        dataFlow,
        detailDate
    ) { base, data, detail ->
        val detailInfo = detail?.let { date ->
            val record = data.records.firstOrNull { it.date == date }
            val pause = data.pauses.firstOrNull { it.covers(date) }
            DayDetail(
                date = date,
                status = record?.status,
                plannedValue = record?.plannedValue,
                actualValue = record?.actualValue,
                isPause = pause != null,
                pauseEnd = pause?.endDate,
                isManualReset = data.resets.any { it.effectiveDate == date }
            )
        }
        HistoryUiState(
            loading = false,
            habits = base.habits,
            habit = base.habit,
            month = base.month,
            records = data.records,
            pauses = data.pauses,
            resets = data.resets,
            detail = detailInfo
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState()
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
        detailDate.value = null
    }

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }

    fun selectDay(date: LocalDate) {
        detailDate.value = date
    }

    fun clearDetail() {
        detailDate.value = null
    }

    companion object {
        fun factory(container: AppContainer, habitId: Long?): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { HistoryViewModel(container.repository, habitId) }
            }
    }
}
