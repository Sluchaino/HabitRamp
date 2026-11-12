package com.habitramp.data.mapper

import com.habitramp.core.util.toStorage
import com.habitramp.data.entity.HabitEntity
import com.habitramp.domain.model.Habit

fun Habit.toEntity(): HabitEntity = HabitEntity(
    id = id,
    name = name,
    initialValue = initialValue.toStorage(),
    resetValue = resetValue.toStorage(),
    progressionBaseValue = progressionBaseValue.toStorage(),
    currentGoal = currentGoal.toStorage(),
    unit = unit.code,
    step = step.toStorage(),
    cycleLength = cycleLength,
    cycleIndex = cycleIndex,
    dayIndex = dayIndex,
    targetValue = targetValue?.toStorage(),
    targetBehavior = targetBehavior.code,
    selectedWeekdays = encodeWeekdays(weekdays),
    missPolicy = missPolicy.code,
    missPenalty = missPenalty.toStorage(),
    status = status.code,
    startDate = startDate.toEpochDay(),
    createdAt = createdAt.toEpochDay(),
    archivedAt = archivedAt?.toEpochDay(),
    completedAt = completedAt?.toEpochDay(),
    lastProcessedDate = lastProcessedDate.toEpochDay(),
    pendingResetValue = pendingResetValue?.toStorage(),
    pendingResetDate = pendingResetDate?.toEpochDay(),
    targetReached = targetReached,
    notificationsEnabled = notificationsEnabled,
    notificationHour = notificationHour,
    notificationMinute = notificationMinute
)
