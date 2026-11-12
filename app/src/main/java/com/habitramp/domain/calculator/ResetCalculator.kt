package com.habitramp.domain.calculator

import com.habitramp.core.model.MeasurementUnit
import com.habitramp.core.util.roundToUnit
import java.math.BigDecimal
import java.math.RoundingMode

object ResetCalculator {
    /**
     * Manual reset reduces the goal by 50%.
     * Integer units are rounded up (21 -> 11); fractional units use their precision.
     */
    fun newGoal(currentGoal: BigDecimal, unit: MeasurementUnit): BigDecimal {
        val half = currentGoal.multiply(BigDecimal("0.5"))
        val mode = if (unit.isInteger) RoundingMode.CEILING else RoundingMode.HALF_UP
        return half.roundToUnit(unit, mode)
    }
}
