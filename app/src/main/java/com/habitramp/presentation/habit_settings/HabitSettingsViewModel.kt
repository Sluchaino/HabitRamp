package com.habitramp.presentation.habit_settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.habitramp.AppContainer
import com.habitramp.core.model.MissPolicy
import com.habitramp.core.model.PauseType
import com.habitramp.core.model.TargetBehavior
import com.habitramp.core.notification.NotificationScheduler
import com.habitramp.core.util.parseDecimalInput
import com.habitramp.data.repository.HabitRepository
import com.habitramp.domain.calculator.ResetCalculator
import com.habitramp.domain.model.DailyRecord
import com.habitramp.domain.model.Habit
import com.habitramp.domain.model.HabitPause
import com.habitramp.presentation.components.manualResetMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.DayOfWeek

data class SettingsUiState(
    val habit: Habit? = null,
    val records: List<DailyRecord> = emptyList(),
    val pauses: List<HabitPause> = emptyList(),
    val resetPreview: BigDecimal? = null,
    val activePause: HabitPause? = null,
    val hasExecutions: Boolean = false,
    val targetPrompt: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val closed: Boolean = false
)

class HabitSettingsViewModel(
    private val repository: HabitRepository,
    private val notificationScheduler: NotificationScheduler,
    private val habitId: Long
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val closed = MutableStateFlow(false)

    private data class Notices(val error: String?, val message: String?, val closed: Boolean)

    private val notices = combine(error, message, closed) { e, m, c -> Notices(e, m, c) }

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.observeHabit(habitId),
        repository.observeRecords(habitId),
        repository.observePauses(habitId),
        notices
    ) { habit, records, pauses, notice ->
        SettingsUiState(
            habit = habit,
            records = records,
            pauses = pauses,
            resetPreview = habit?.let { ResetCalculator.newGoal(it.currentGoal, it.unit) },
            activePause = pauses.firstOrNull { it.status == com.habitramp.core.model.PauseStatus.ACTIVE },
            hasExecutions = records.any { it.status.isExecution },
            targetPrompt = habit?.targetReached == true,
            error = notice.error,
            message = notice.message,
            closed = notice.closed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    init {
        viewModelScope.launch { runCatching { repository.runDailyProcessing() } }
    }

    fun updateName(name: String) = runAction {
        repository.updateName(habitId, name)
    }

    fun updateStep(text: String) {
        val value = parseDecimalInput(text)
        if (value == null || value <= BigDecimal.ZERO) {
            error.value = "Шаг должен быть больше нуля"
            return
        }
        runAction { repository.updateStep(habitId, value) }
    }

    fun updateCycleLength(text: String) {
        val value = text.trim().toIntOrNull()
        if (value == null || value < 1) {
            error.value = "Длительность цикла должна быть не меньше 1"
            return
        }
        runAction { repository.updateCycleLength(habitId, value) }
    }

    fun updateResetValue(text: String) {
        val value = parseDecimalInput(text)
        if (value == null || value <= BigDecimal.ZERO) {
            error.value = "Значение полного сброса должно быть больше нуля"
            return
        }
        runAction { repository.updateResetValue(habitId, value) }
    }

    fun updatePenalty(text: String) {
        val value = parseDecimalInput(text)
        if (value == null || value <= BigDecimal.ZERO) {
            error.value = "Штраф должен быть больше нуля"
            return
        }
        runAction { repository.updateMissPolicy(habitId, currentHabit()?.missPolicy ?: MissPolicy.KEEP, value) }
    }

    fun setMissPolicy(policy: MissPolicy) {
        val habit = currentHabit() ?: return
        runAction { repository.updateMissPolicy(habitId, policy, habit.missPenalty) }
    }

    fun updateTarget(text: String) {
        if (text.isBlank()) {
            runAction { repository.updateTarget(habitId, null, TargetBehavior.ASK) }
            return
        }
        val value = parseDecimalInput(text)
        if (value == null || value <= BigDecimal.ZERO) {
            error.value = "Целевое значение должно быть больше нуля"
            return
        }
        val behavior = currentHabit()?.targetBehavior ?: TargetBehavior.ASK
        runAction { repository.updateTarget(habitId, value, behavior) }
    }

    fun updateUnit(code: String) {
        if (uiState.value.hasExecutions) {
            error.value = "Единицу нельзя изменить после первого выполнения"
            return
        }
        runAction {
            if (!repository.updateUnit(habitId, code)) {
                error.value = "Единицу нельзя изменить после первого выполнения"
            }
        }
    }

    fun toggleWeekday(day: DayOfWeek) {
        val habit = currentHabit() ?: return
        val days = habit.weekdays.toMutableSet()
        if (day in days) days.remove(day) else days.add(day)
        if (days.isEmpty()) {
            error.value = "Выберите хотя бы один день недели"
            return
        }
        runAction { repository.updateWeekdays(habitId, days) }
    }

    fun setNotifications(enabled: Boolean, hour: Int, minute: Int) {
        runAction {
            repository.updateNotifications(habitId, enabled, hour, minute)
            notificationScheduler.scheduleAll()
        }
    }

    fun manualReset() {
        runAction {
            val goal = repository.manualReset(habitId)
            val updated = repository.getHabit(habitId)
            val unit = updated?.unit ?: currentHabit()?.unit
            message.value = if (unit != null) {
                manualResetMessage(goal, updated?.pendingResetDate, unit)
            } else {
                "Сброс недоступен"
            }
        }
    }

    fun startPause(type: PauseType) {
        runAction { repository.startPause(habitId, type) }
    }

    fun extendPause() {
        runAction { repository.extendPause(habitId) }
    }

    fun archive() {
        runAction {
            if (repository.archiveHabit(habitId)) closed.value = true
        }
    }

    fun complete() {
        runAction {
            if (repository.completeHabit(habitId)) closed.value = true
        }
    }

    fun delete() {
        runAction {
            repository.deleteHabit(habitId)
            closed.value = true
        }
    }

    fun resolveTarget(behavior: TargetBehavior) {
        runAction { repository.resolveTarget(habitId, behavior) }
    }

    fun consumeMessages() {
        error.value = null
        message.value = null
    }

    private fun currentHabit(): Habit? = uiState.value.habit

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess {
                    notificationScheduler.scheduleAll()
                }
                .onFailure { error.value = it.message ?: "Не удалось сохранить изменения" }
        }
    }

    companion object {
        fun factory(container: AppContainer, habitId: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    HabitSettingsViewModel(
                        container.repository,
                        container.notificationScheduler,
                        habitId
                    )
                }
            }
    }
}
