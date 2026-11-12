package com.habitramp.domain.model

import com.habitramp.core.model.MeasurementUnit
import com.habitramp.core.model.TargetBehavior
import java.math.BigDecimal

/** Mutable part of a habit that describes progression. */
data class ProgressionState(
    val base: BigDecimal,
    val currentGoal: BigDecimal,
    val step: BigDecimal,
    val cycleLength: Int,
    val cycleIndex: Int,
    val dayIndex: Int,
    val targetValue: BigDecimal?,
    val targetBehavior: TargetBehavior,
    val targetReached: Boolean = false
) {
    companion object {
        fun from(habit: Habit): ProgressionState = ProgressionState(
            base = habit.progressionBaseValue,
            currentGoal = habit.currentGoal,
            step = habit.step,
            cycleLength = habit.cycleLength,
            cycleIndex = habit.cycleIndex,
            dayIndex = habit.dayIndex,
            targetValue = habit.targetValue,
            targetBehavior = habit.targetBehavior,
            targetReached = habit.targetReached
        )
    }
}
