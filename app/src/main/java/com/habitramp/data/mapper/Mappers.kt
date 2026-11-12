package com.habitramp.data.mapper

import com.habitramp.core.model.CompletionStatus
import com.habitramp.core.model.EventType
import com.habitramp.core.model.HabitStatus
import com.habitramp.core.model.MissPolicy
import com.habitramp.core.model.PauseStatus
import com.habitramp.core.model.PauseType
import com.habitramp.core.model.TargetBehavior
import com.habitramp.core.model.Units
import com.habitramp.core.util.toDecimalOrNull
import com.habitramp.core.util.toDecimalOrZero
import com.habitramp.core.util.toLocalDate
import com.habitramp.data.entity.HabitDailyRecordEntity
import com.habitramp.data.entity.HabitEntity
import com.habitramp.data.entity.HabitEventEntity
import com.habitramp.data.entity.HabitPauseEntity
import com.habitramp.data.entity.HabitSettingsHistoryEntity
import com.habitramp.data.entity.UserProgressEntity
import com.habitramp.domain.model.DailyRecord
import com.habitramp.domain.model.Habit
import com.habitramp.domain.model.HabitEvent
import com.habitramp.domain.model.HabitPause
import com.habitramp.domain.model.SettingsSnapshot
import com.habitramp.domain.model.UserProgress
import java.time.DayOfWeek

fun encodeWeekdays(days: Set<DayOfWeek>): String =
    days.map { it.value }.sorted().joinToString(",")

fun decodeWeekdays(value: String): Set<DayOfWeek> =
    value.split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .mapNotNull { runCatching { DayOfWeek.of(it) }.getOrNull() }
        .toSet()
        .ifEmpty { DayOfWeek.entries.toSet() }

fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    name = name,
    initialValue = initialValue.toDecimalOrZero(),
    resetValue = resetValue.toDecimalOrZero(),
    progressionBaseValue = progressionBaseValue.toDecimalOrZero(),
    currentGoal = currentGoal.toDecimalOrZero(),
    unit = Units.byCode(unit),
    step = step.toDecimalOrZero(),
    cycleLength = cycleLength.coerceAtLeast(1),
    cycleIndex = cycleIndex,
    dayIndex = dayIndex,
    targetValue = targetValue.toDecimalOrNull(),
    targetBehavior = TargetBehavior.fromCode(targetBehavior),
    weekdays = decodeWeekdays(selectedWeekdays),
    missPolicy = MissPolicy.fromCode(missPolicy),
    missPenalty = missPenalty.toDecimalOrZero(),
    status = HabitStatus.fromCode(status),
    startDate = startDate.toLocalDate(),
    createdAt = createdAt.toLocalDate(),
    archivedAt = archivedAt?.toLocalDate(),
    completedAt = completedAt?.toLocalDate(),
    lastProcessedDate = lastProcessedDate.toLocalDate(),
    pendingResetValue = pendingResetValue.toDecimalOrNull(),
    pendingResetDate = pendingResetDate?.toLocalDate(),
    targetReached = targetReached,
    notificationsEnabled = notificationsEnabled,
    notificationHour = notificationHour,
    notificationMinute = notificationMinute
)

fun HabitDailyRecordEntity.toDomain(): DailyRecord = DailyRecord(
    id = id,
    habitId = habitId,
    date = date.toLocalDate(),
    plannedValue = plannedValue.toDecimalOrZero(),
    actualValue = actualValue.toDecimalOrZero(),
    status = CompletionStatus.fromCode(completionStatus),
    cycleIndex = cycleIndex,
    dayIndex = dayIndex
)

fun HabitPauseEntity.toDomain(): HabitPause = HabitPause(
    id = id,
    habitId = habitId,
    startDate = startDate.toLocalDate(),
    endDate = endDate.toLocalDate(),
    pauseType = PauseType.fromCode(pauseType),
    processedPeriods = processedPeriods,
    status = PauseStatus.fromCode(status)
)

fun HabitEventEntity.toDomain(): HabitEvent = HabitEvent(
    id = id,
    habitId = habitId,
    type = EventType.fromCode(eventType),
    effectiveDate = effectiveDate.toLocalDate(),
    eventData = eventData,
    createdAt = createdAt
)

fun HabitSettingsHistoryEntity.toDomain(): SettingsSnapshot = SettingsSnapshot(
    effectiveDate = effectiveDate.toLocalDate(),
    step = step.toDecimalOrZero(),
    cycleLength = cycleLength,
    weekdays = decodeWeekdays(weekdays),
    missPolicy = MissPolicy.fromCode(missPolicy),
    missPenalty = missPenalty.toDecimalOrZero(),
    targetValue = targetValue.toDecimalOrNull(),
    targetBehavior = TargetBehavior.fromCode(targetBehavior)
)

fun UserProgressEntity.toDomain(): UserProgress = UserProgress(
    currentPoints = currentPoints,
    unlockedSlots = unlockedSlots,
    lastProcessedDate = lastProcessedDate.toLocalDate()
)
