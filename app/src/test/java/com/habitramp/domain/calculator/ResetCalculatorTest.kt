package com.habitramp.domain.calculator

import com.habitramp.assertDecimal
import com.habitramp.core.model.Units
import java.math.BigDecimal
import org.junit.Test

class ResetCalculatorTest {

    private val reps = Units.DEFAULT
    private val minutes = Units.ALL.first { it.code == "minutes" }

    @Test
    fun `integer goals are halved and rounded up`() {
        assertDecimal("11", ResetCalculator.newGoal(BigDecimal("21"), reps))
        assertDecimal("5", ResetCalculator.newGoal(BigDecimal("10"), reps))
        assertDecimal("1", ResetCalculator.newGoal(BigDecimal("1"), reps))
    }

    @Test
    fun `fractional goals keep unit precision`() {
        assertDecimal("1.88", ResetCalculator.newGoal(BigDecimal("3.75"), minutes))
        assertDecimal("0.25", ResetCalculator.newGoal(BigDecimal("0.50"), minutes))
    }

    @Test
    fun `fractional goals never drop below the unit minimum`() {
        assertDecimal("0.01", ResetCalculator.newGoal(BigDecimal("0.01"), minutes))
    }
}
