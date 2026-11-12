package com.habitramp

import com.habitramp.core.model.HabitStatus
import com.habitramp.core.model.MeasurementUnit
import com.habitramp.core.model.MissPolicy
import com.habitramp.core.model.TargetBehavior
import com.habitramp.core.model.Units
import com.habitramp.domain.model.Habit
import com.habitramp.domain.model.SettingsSnapshot
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

/** Minimal habit for Compose UI tests; the fields match [Habit] defaults. */
fun uiTestHabit(
    id: Long = 1L,
    name: String = "Тестовая привычка",
    currentGoal: String = "10",
    unit: MeasurementUnit = Units.DEFAULT,
    weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    status: HabitStatus = HabitStatus.ACTIVE,
    startDate: LocalDate = LocalDate.of(2026, 1, 1)
): Habit = Habit(
    id = id,
    name = name,
    initialValue = BigDecimal.ONE,
    resetValue = BigDecimal.ONE,
    progressionBaseValue = BigDecimal.ONE,
    currentGoal = BigDecimal(currentGoal),
    unit = unit,
    step = BigDecimal.ONE,
    cycleLength = 7,
    cycleIndex = 0,
    dayIndex = 0,
    targetValue = null,
    targetBehavior = TargetBehavior.ASK,
    weekdays = weekdays,
    missPolicy = MissPolicy.KEEP,
    missPenalty = BigDecimal.ONE,
    status = status,
    startDate = startDate,
    createdAt = startDate,
    archivedAt = null,
    completedAt = null,
    lastProcessedDate = startDate,
    pendingResetValue = null,
    pendingResetDate = null,
    targetReached = false,
    notificationsEnabled = false,
    notificationHour = 9,
    notificationMinute = 0
)

fun uiTestSnapshot(
    effectiveDate: LocalDate,
    weekdays: Set<DayOfWeek>
): SettingsSnapshot = SettingsSnapshot(
    effectiveDate = effectiveDate,
    step = BigDecimal.ONE,
    cycleLength = 7,
    weekdays = weekdays,
    missPolicy = MissPolicy.KEEP,
    missPenalty = BigDecimal.ONE,
    targetValue = null,
    targetBehavior = TargetBehavior.ASK
)
