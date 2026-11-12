package com.habitramp

import com.habitramp.core.model.HabitStatus
import com.habitramp.core.model.MeasurementUnit
import com.habitramp.core.model.MissPolicy
import com.habitramp.core.model.TargetBehavior
import com.habitramp.core.model.Units
import com.habitramp.domain.model.DailyRecord
import com.habitramp.domain.model.Habit
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals

/** Compares decimals by value, ignoring scale (1 == 1.00). */
fun assertDecimal(expected: String, actual: BigDecimal) {
    assertEquals(
        "expected $expected but was ${actual.toPlainString()}",
        0,
        actual.compareTo(BigDecimal(expected))
    )
}

fun habit(
    id: Long = 1L,
    name: String = "Тестовая привычка",
    initialValue: String = "1",
    resetValue: String = "1",
    progressionBaseValue: String = "1",
    currentGoal: String = "1",
    unit: MeasurementUnit = Units.DEFAULT,
    step: String = "1",
    cycleLength: Int = 7,
    cycleIndex: Int = 0,
    dayIndex: Int = 0,
    targetValue: String? = null,
    targetBehavior: TargetBehavior = TargetBehavior.ASK,
    weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    missPolicy: MissPolicy = MissPolicy.KEEP,
    missPenalty: String = "1",
    status: HabitStatus = HabitStatus.ACTIVE,
    startDate: LocalDate = LocalDate.of(2026, 1, 1),
    createdAt: LocalDate = startDate,
    archivedAt: LocalDate? = null,
    completedAt: LocalDate? = null,
    lastProcessedDate: LocalDate = startDate,
    pendingResetValue: String? = null,
    pendingResetDate: LocalDate? = null,
    targetReached: Boolean = false
): Habit = Habit(
    id = id,
    name = name,
    initialValue = BigDecimal(initialValue),
    resetValue = BigDecimal(resetValue),
    progressionBaseValue = BigDecimal(progressionBaseValue),
    currentGoal = BigDecimal(currentGoal),
    unit = unit,
    step = BigDecimal(step),
    cycleLength = cycleLength,
    cycleIndex = cycleIndex,
    dayIndex = dayIndex,
    targetValue = targetValue?.let(::BigDecimal),
    targetBehavior = targetBehavior,
    weekdays = weekdays,
    missPolicy = missPolicy,
    missPenalty = BigDecimal(missPenalty),
    status = status,
    startDate = startDate,
    createdAt = createdAt,
    archivedAt = archivedAt,
    completedAt = completedAt,
    lastProcessedDate = lastProcessedDate,
    pendingResetValue = pendingResetValue?.let(::BigDecimal),
    pendingResetDate = pendingResetDate,
    targetReached = targetReached,
    notificationsEnabled = false,
    notificationHour = 9,
    notificationMinute = 0
)

fun dailyRecord(
    id: Long = 0L,
    habitId: Long = 1L,
    date: LocalDate,
    plannedValue: String = "1",
    actualValue: String = "0",
    status: com.habitramp.core.model.CompletionStatus = com.habitramp.core.model.CompletionStatus.MISSED,
    cycleIndex: Int = 0,
    dayIndex: Int = 0
): DailyRecord = DailyRecord(
    id = id,
    habitId = habitId,
    date = date,
    plannedValue = BigDecimal(plannedValue),
    actualValue = BigDecimal(actualValue),
    status = status,
    cycleIndex = cycleIndex,
    dayIndex = dayIndex
)
