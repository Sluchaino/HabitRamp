package com.habitramp.domain.model

import com.habitramp.core.model.CompletionStatus
import com.habitramp.core.model.HabitStatus
import com.habitramp.core.model.MeasurementUnit
import com.habitramp.core.model.MissPolicy
import com.habitramp.core.model.PauseStatus
import com.habitramp.core.model.PauseType
import com.habitramp.core.model.TargetBehavior
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

data class Habit(
    val id: Long,
    val name: String,
    val initialValue: BigDecimal,
    val resetValue: BigDecimal,
    val progressionBaseValue: BigDecimal,
    val currentGoal: BigDecimal,
    val unit: MeasurementUnit,
    val step: BigDecimal,
    val cycleLength: Int,
    val cycleIndex: Int,
    val dayIndex: Int,
    val targetValue: BigDecimal?,
    val targetBehavior: TargetBehavior,
    val weekdays: Set<DayOfWeek>,
    val missPolicy: MissPolicy,
    val missPenalty: BigDecimal,
    val status: HabitStatus,
    val startDate: LocalDate,
    val createdAt: LocalDate,
    val archivedAt: LocalDate?,
    val completedAt: LocalDate?,
    val lastProcessedDate: LocalDate,
    val pendingResetValue: BigDecimal?,
    val pendingResetDate: LocalDate?,
    val targetReached: Boolean,
    val notificationsEnabled: Boolean,
    val notificationHour: Int,
    val notificationMinute: Int
) {
    val executionIndex: Int get() = cycleIndex * cycleLength + dayIndex
    val cycleDayNumber: Int get() = dayIndex + 1

    fun isScheduledOn(date: LocalDate): Boolean =
        !date.isBefore(startDate) && date.dayOfWeek in weekdays

    fun isLive(): Boolean = status == HabitStatus.ACTIVE || status == HabitStatus.PAUSED
}

data class DailyRecord(
    val id: Long,
    val habitId: Long,
    val date: LocalDate,
    val plannedValue: BigDecimal,
    val actualValue: BigDecimal,
    val status: CompletionStatus,
    val cycleIndex: Int,
    val dayIndex: Int
) {
    val isSuccess: Boolean get() = status == CompletionStatus.COMPLETED
    val isMiss: Boolean get() = status != CompletionStatus.COMPLETED
}

data class HabitPause(
    val id: Long,
    val habitId: Long,
    val startDate: LocalDate,
    /** Exclusive end date: the habit is paused for dates in [startDate, endDate). */
    val endDate: LocalDate,
    val pauseType: PauseType,
    val processedPeriods: Int,
    val status: PauseStatus
) {
    fun covers(date: LocalDate): Boolean = !date.isBefore(startDate) && date.isBefore(endDate)
}

data class UserProgress(
    val currentPoints: Int,
    val unlockedSlots: Int,
    val lastProcessedDate: LocalDate
)

data class HabitEvent(
    val id: Long,
    val habitId: Long,
    val type: com.habitramp.core.model.EventType,
    val effectiveDate: LocalDate,
    val eventData: String,
    val createdAt: Long
)

data class SettingsSnapshot(
    val effectiveDate: LocalDate,
    val step: BigDecimal,
    val cycleLength: Int,
    val weekdays: Set<DayOfWeek>,
    val missPolicy: MissPolicy,
    val missPenalty: BigDecimal,
    val targetValue: BigDecimal?,
    val targetBehavior: TargetBehavior
)

data class ManualResetEvent(
    val effectiveDate: LocalDate,
    val newGoal: BigDecimal
)
