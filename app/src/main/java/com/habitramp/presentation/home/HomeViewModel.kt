package com.habitramp.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.habitramp.AppContainer
import com.habitramp.core.model.PauseType
import com.habitramp.core.model.TargetBehavior
import com.habitramp.core.notification.NotificationScheduler
import com.habitramp.core.util.parseDecimalInput
import com.habitramp.data.prefs.AppPreferences
import com.habitramp.data.repository.HabitRepository
import com.habitramp.domain.calculator.UnlockCalculator
import com.habitramp.domain.model.DailyRecord
import com.habitramp.domain.model.Habit
import com.habitramp.domain.model.HabitPause
import com.habitramp.domain.model.UserProgress
import com.habitramp.presentation.components.manualResetMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

data class HomeUiState(
    val loading: Boolean = true,
    val habits: List<Habit> = emptyList(),
    val selected: Habit? = null,
    val todayRecord: DailyRecord? = null,
    val activePause: HabitPause? = null,
    val streak: Int = 0,
    val progress: UserProgress = UserProgress(0, 1, LocalDate.now()),
    val activeCount: Int = 0,
    val targetPrompt: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val today: LocalDate = LocalDate.now()
) {
    val plannedToday: Boolean
        get() {
            val habit = selected ?: return false
            return habit.status == com.habitramp.core.model.HabitStatus.ACTIVE &&
                habit.isScheduledOn(today) && activePause == null
        }
    val canRecord: Boolean
        get() = selected != null && selected.status == com.habitramp.core.model.HabitStatus.ACTIVE &&
            plannedToday && todayRecord == null
    val nextSlotPoints: Int
        get() = UnlockCalculator.pointsNeededForNextSlot(progress.unlockedSlots)
}

class HomeViewModel(
    private val repository: HabitRepository,
    private val preferences: AppPreferences,
    private val notificationScheduler: NotificationScheduler,
    private val initialHabitId: Long? = null
) : ViewModel() {
    private val selectedId = MutableStateFlow<Long?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)

    /** Re-emitted at midnight and on resume so the screen never shows yesterday's state. */
    private val today = MutableStateFlow(LocalDate.now())

    private data class Base(
        val habits: List<Habit>,
        val selected: Habit?,
        val progress: UserProgress,
        val activeCount: Int
    )

    private val baseFlow = combine(
        repository.observeLiveHabits(),
        selectedId,
        repository.observeUserProgress(),
        repository.observeActiveCount()
    ) { habits, id, progress, activeCount ->
        Base(
            habits = habits,
            selected = habits.firstOrNull { it.id == id } ?: habits.firstOrNull(),
            progress = progress,
            activeCount = activeCount
        )
    }

    private val selectedHabitId = baseFlow
        .map { it.selected?.id }
        .distinctUntilChanged()

    private val recordsFlow = selectedHabitId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeRecords(id)
    }

    private val pausesFlow = selectedHabitId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observePauses(id)
    }

    private val settingsFlow = selectedHabitId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeSettings(id)
    }

    private val clockFlow = combine(error, message, today) { err, msg, date ->
        Triple(err, msg, date)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        baseFlow,
        recordsFlow,
        pausesFlow,
        settingsFlow,
        clockFlow
    ) { base, records, pauses, settings, clock ->
        val todayDate = clock.third
        val habit = base.selected
        val todayRecord = records.firstOrNull { it.date == todayDate }
        val pause = pauses.firstOrNull {
            it.status == com.habitramp.core.model.PauseStatus.ACTIVE && it.covers(todayDate)
        }
        HomeUiState(
            loading = false,
            habits = base.habits,
            selected = habit,
            todayRecord = todayRecord,
            activePause = pause,
            streak = if (habit == null) 0 else calculateStreak(habit, records, pauses, todayDate, settings),
            progress = base.progress,
            activeCount = base.activeCount,
            targetPrompt = habit?.targetReached == true,
            error = clock.first,
            message = clock.second,
            today = todayDate
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    init {
        viewModelScope.launch {
            while (true) {
                today.value = LocalDate.now()
                delay(60_000)
            }
        }
        viewModelScope.launch {
            repository.initialize()
            val saved = initialHabitId ?: preferences.lastSelectedHabitId.first()
            if (saved != null) selectedId.value = saved
            repository.observeLiveHabits().collect { habits ->
                val current = selectedId.value
                if (current == null || habits.none { it.id == current }) {
                    val next = habits.firstOrNull()?.id
                    selectedId.value = next
                    preferences.setLastSelectedHabitId(next)
                }
            }
        }
    }

    fun selectHabit(id: Long) {
        selectedId.value = id
        viewModelScope.launch { preferences.setLastSelectedHabitId(id) }
    }

    fun refresh() {
        today.value = LocalDate.now()
        viewModelScope.launch {
            runCatching { repository.runDailyProcessing() }
        }
    }

    fun markDone() {
        val habit = uiState.value.selected ?: return
        mutate { repository.recordCompletion(habit.id, null) }
    }

    fun markNotDone(input: String) {
        val habit = uiState.value.selected ?: return
        val actual = parseDecimalInput(input) ?: BigDecimal.ZERO
        mutate { repository.recordCompletion(habit.id, actual) }
    }

    fun manualReset() {
        val habit = uiState.value.selected ?: return
        viewModelScope.launch {
            runCatching {
                val newGoal = repository.manualReset(habit.id)
                val pendingDate = repository.getHabit(habit.id)?.pendingResetDate
                message.value = manualResetMessage(newGoal, pendingDate, habit.unit)
            }.onFailure { error.value = it.message ?: "Не удалось выполнить сброс" }
        }
    }

    fun startPause(type: PauseType) {
        val habit = uiState.value.selected ?: return
        viewModelScope.launch {
            runCatching {
                if (repository.startPause(habit.id, type)) {
                    notificationScheduler.scheduleAll()
                    message.value = "Пауза начата"
                }
            }.onFailure { error.value = it.message ?: "Не удалось начать паузу" }
        }
    }

    fun extendPause() {
        val habit = uiState.value.selected ?: return
        viewModelScope.launch {
            runCatching {
                if (repository.extendPause(habit.id)) {
                    notificationScheduler.scheduleAll()
                    message.value = "Пауза продлена"
                }
            }.onFailure { error.value = it.message ?: "Не удалось продлить паузу" }
        }
    }

    fun resolveTarget(behavior: TargetBehavior) {
        val habit = uiState.value.selected ?: return
        viewModelScope.launch {
            runCatching {
                repository.resolveTarget(habit.id, behavior)
                notificationScheduler.scheduleAll()
            }.onFailure { error.value = it.message ?: "Не удалось сохранить выбор" }
        }
    }

    fun consumeMessages() {
        error.value = null
        message.value = null
    }

    private fun mutate(block: suspend () -> Boolean) {
        val habit = uiState.value.selected ?: return
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { repository.runDailyProcessing() }
                .onFailure { error.value = it.message ?: "Не удалось сохранить результат" }
        }
    }

    companion object {
        fun factory(container: AppContainer, initialHabitId: Long? = null): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    HomeViewModel(
                        container.repository,
                        container.preferences,
                        container.notificationScheduler,
                        initialHabitId
                    )
                }
            }
    }
}
