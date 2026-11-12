package com.habitramp.domain.calculator

import com.habitramp.core.model.MeasurementUnit
import com.habitramp.core.model.MissPolicy
import com.habitramp.core.util.roundToUnit
import com.habitramp.domain.model.ProgressionState
import java.math.BigDecimal
import java.math.RoundingMode

object MissCalculator {

    /**
     * Applies the selected miss policy to the progression state.
     * Every missed planned day consumes one cycle position.
     */
    fun applyMiss(
        state: ProgressionState,
        policy: MissPolicy,
        resetValue: BigDecimal,
        penalty: BigDecimal,
        unit: MeasurementUnit
    ): ProgressionState = when (policy) {
        MissPolicy.RESTART -> {
            val goal = resetValue.roundToUnit(unit, RoundingMode.HALF_UP)
            state.copy(
                base = goal,
                currentGoal = ProgressionCalculator.applyTargetCap(
                    goal,
                    state.targetValue,
                    state.targetBehavior
                ).roundToUnit(unit),
                cycleIndex = 0,
                dayIndex = 0,
                targetReached = false
            )
        }

        MissPolicy.REDUCE -> {
            val reduced = state.currentGoal.subtract(penalty)
            val goal = reduced.coerceAtLeastMin(unit).roundToUnit(unit, RoundingMode.HALF_UP)
            state.copy(
                base = goal,
                currentGoal = ProgressionCalculator.applyTargetCap(
                    goal,
                    state.targetValue,
                    state.targetBehavior
                ).roundToUnit(unit),
                cycleIndex = 0,
                dayIndex = 0,
                targetReached = false
            )
        }

        MissPolicy.KEEP -> ProgressionCalculator.advanceAfterMiss(state, unit)
    }

    private fun BigDecimal.coerceAtLeastMin(unit: MeasurementUnit): BigDecimal =
        if (this < unit.minGoal) unit.minGoal else this
}
