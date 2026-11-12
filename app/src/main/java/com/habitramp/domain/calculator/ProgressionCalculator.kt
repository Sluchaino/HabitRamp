package com.habitramp.domain.calculator

import com.habitramp.core.model.MeasurementUnit
import com.habitramp.core.model.TargetBehavior
import com.habitramp.core.util.roundToUnit
import com.habitramp.domain.model.ProgressionState
import java.math.BigDecimal
import java.math.RoundingMode

object ProgressionCalculator {

    /** Goal = Base + Step × (2 × Cycle + Day). */
    fun rawGoal(base: BigDecimal, step: BigDecimal, cycleIndex: Int, dayIndex: Int): BigDecimal {
        val multiplier = BigDecimal(2 * cycleIndex + dayIndex)
        return base.add(step.multiply(multiplier))
    }

    fun rawGoal(state: ProgressionState): BigDecimal =
        rawGoal(state.base, state.step, state.cycleIndex, state.dayIndex)

    /**
     * Advances the position after a completed day and computes the next goal.
     * [completedGoal] is the goal the user has just completed, used to detect
     * that the target value has been reached.
     */
    fun advanceAfterCompletion(
        state: ProgressionState,
        completedGoal: BigDecimal,
        unit: MeasurementUnit
    ): ProgressionState {
        var day = state.dayIndex + 1
        var cycle = state.cycleIndex
        if (day >= state.cycleLength) {
            day = 0
            cycle += 1
        }
        val raw = rawGoal(state.base, state.step, cycle, day).roundToUnit(unit)
        val reachedNow = state.targetValue != null && completedGoal >= state.targetValue
        val nextGoal = if (reachedNow && state.targetBehavior == TargetBehavior.MAINTAIN) {
            state.targetValue!!
        } else applyTargetCap(raw, state.targetValue, state.targetBehavior)
        return state.copy(
            currentGoal = nextGoal,
            cycleIndex = cycle,
            dayIndex = day,
            targetReached = reachedNow && state.targetBehavior == TargetBehavior.ASK
        )
    }

    /** Used when a day is missed and the policy is [com.habitramp.core.model.MissPolicy.KEEP]. */
    fun advanceAfterMiss(state: ProgressionState, unit: MeasurementUnit): ProgressionState {
        var day = state.dayIndex + 1
        var cycle = state.cycleIndex
        if (day >= state.cycleLength) {
            day = 0
            cycle += 1
        }
        val raw = rawGoal(state.base, state.step, cycle, day).roundToUnit(unit)
        return state.copy(
            currentGoal = if (state.targetBehavior == TargetBehavior.MAINTAIN &&
                state.targetValue != null && state.currentGoal >= state.targetValue
            ) state.targetValue else applyTargetCap(raw, state.targetValue, state.targetBehavior),
            cycleIndex = cycle,
            dayIndex = day,
            targetReached = false
        )
    }

    fun applyTargetCap(
        goal: BigDecimal,
        target: BigDecimal?,
        behavior: TargetBehavior
    ): BigDecimal {
        if (target == null) return goal
        return when (behavior) {
            TargetBehavior.CONTINUE -> goal
            TargetBehavior.ASK,
            TargetBehavior.MAINTAIN,
            TargetBehavior.FINISH -> if (goal > target) target else goal
        }
    }

    /** Recomputes the current goal after changing the step, preserving continuity. */
    fun rebaseForNewStep(state: ProgressionState, newStep: BigDecimal, unit: MeasurementUnit): ProgressionState {
        val position = BigDecimal(2 * state.cycleIndex + state.dayIndex)
        val newBase = state.currentGoal.subtract(newStep.multiply(position))
        val goal = applyTargetCap(state.currentGoal, state.targetValue, state.targetBehavior)
        return state.copy(base = newBase, step = newStep, currentGoal = goal.roundToUnit(unit))
    }

    /**
     * Changing the cycle length starts a new cycle at the current future goal.
     */
    fun rebaseForNewCycleLength(
        state: ProgressionState,
        newCycleLength: Int,
        unit: MeasurementUnit
    ): ProgressionState = state.copy(
        base = state.currentGoal,
        currentGoal = state.currentGoal.roundToUnit(unit),
        cycleLength = newCycleLength,
        cycleIndex = 0,
        dayIndex = 0
    )

    /** Manual reset: new cycle starts at [newGoal]. */
    fun resetTo(state: ProgressionState, newGoal: BigDecimal, unit: MeasurementUnit): ProgressionState {
        val goal = newGoal.roundToUnit(unit)
        return state.copy(
            base = goal,
            currentGoal = goal,
            cycleIndex = 0,
            dayIndex = 0,
            targetReached = false
        )
    }

    /** Continue growth after the user has reached the target. */
    fun continueAfterTarget(state: ProgressionState, unit: MeasurementUnit): ProgressionState {
        val raw = rawGoal(state).roundToUnit(unit)
        return state.copy(
            currentGoal = raw,
            targetBehavior = TargetBehavior.CONTINUE,
            targetReached = false
        )
    }

    fun maintainTarget(state: ProgressionState, unit: MeasurementUnit): ProgressionState {
        val target = state.targetValue ?: state.currentGoal
        return state.copy(
            currentGoal = target.roundToUnit(unit, RoundingMode.HALF_UP),
            targetBehavior = TargetBehavior.MAINTAIN,
            targetReached = false
        )
    }
}
