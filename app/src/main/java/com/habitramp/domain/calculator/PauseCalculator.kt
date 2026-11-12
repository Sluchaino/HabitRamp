package com.habitramp.domain.calculator

import com.habitramp.core.model.MeasurementUnit
import com.habitramp.core.model.PauseType
import com.habitramp.core.util.DECIMAL_CONTEXT
import com.habitramp.core.util.fullWeeksBetween
import com.habitramp.core.util.roundToUnit
import com.habitramp.domain.model.ProgressionState
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

object PauseCalculator {
    const val WEEKLY_REDUCTION = 0.4
    const val MONTHLY_REDUCTION = 0.75

    fun reductionRate(type: PauseType): BigDecimal = when (type) {
        PauseType.WEEKLY -> BigDecimal("0.40")
        PauseType.MONTHLY -> BigDecimal("0.75")
    }

    fun remainingMultiplier(type: PauseType): BigDecimal = when (type) {
        PauseType.WEEKLY -> BigDecimal("0.60")
        PauseType.MONTHLY -> BigDecimal("0.25")
    }

    /** Start date is inclusive, end date is exclusive. */
    fun endDate(startDate: LocalDate, type: PauseType): LocalDate = when (type) {
        PauseType.WEEKLY -> startDate.plusWeeks(1)
        PauseType.MONTHLY -> startDate.plusMonths(1)
    }

    fun periodsElapsed(startDate: LocalDate, date: LocalDate, type: PauseType): Int = when (type) {
        PauseType.WEEKLY -> fullWeeksBetween(startDate, date)
        PauseType.MONTHLY -> {
            // Extensions use the previous end date, including short-month clamping.
            var boundary = endDate(startDate, type)
            var periods = 0
            while (!boundary.isAfter(date)) {
                periods++
                boundary = endDate(boundary, type)
            }
            periods
        }
    }

    /**
     * Reduces accumulated progress for [periods] full pause periods.
     * Progress is measured relative to the base of the current stage.
     */
    fun applyPeriods(
        state: ProgressionState,
        periods: Int,
        type: PauseType,
        unit: MeasurementUnit
    ): ProgressionState {
        if (periods <= 0) return state
        val progress = state.currentGoal.subtract(state.base)
        if (progress <= BigDecimal.ZERO) return state
        val multiplier = remainingMultiplier(type).pow(periods, DECIMAL_CONTEXT)
        val remaining = progress.multiply(multiplier)
        val newGoal = state.base.add(remaining).roundToUnit(unit, RoundingMode.HALF_UP)
        return state.copy(currentGoal = newGoal, targetReached = false)
    }

    /** After the pause ends the reduced value becomes the start of a new cycle. */
    fun finish(state: ProgressionState, unit: MeasurementUnit): ProgressionState {
        val goal = state.currentGoal.roundToUnit(unit)
        return state.copy(
            base = goal,
            currentGoal = goal,
            cycleIndex = 0,
            dayIndex = 0,
            targetReached = false
        )
    }

    fun totalReductionPercent(type: PauseType, periods: Int): Double {
        if (periods <= 0) return 0.0
        return (1.0 - remainingMultiplier(type).pow(periods).toDouble()) * 100.0
    }
}
