package com.habitramp.domain.calculator

import com.habitramp.assertDecimal
import com.habitramp.core.model.PauseType
import com.habitramp.core.model.Units
import com.habitramp.domain.model.ProgressionState
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PauseCalculatorTest {

    private val reps = Units.DEFAULT
    private val minutes = Units.ALL.first { it.code == "minutes" }

    private fun state(
        base: String = "10",
        currentGoal: String = "20",
        cycleIndex: Int = 2,
        dayIndex: Int = 4
    ) = ProgressionState(
        base = BigDecimal(base),
        currentGoal = BigDecimal(currentGoal),
        step = BigDecimal.ONE,
        cycleLength = 7,
        cycleIndex = cycleIndex,
        dayIndex = dayIndex,
        targetValue = null,
        targetBehavior = com.habitramp.core.model.TargetBehavior.ASK,
        targetReached = true
    )

    @Test
    fun `weekly pause ends after seven days`() {
        val start = LocalDate.of(2026, 1, 1)
        assertEquals(LocalDate.of(2026, 1, 8), PauseCalculator.endDate(start, PauseType.WEEKLY))
    }

    @Test
    fun `monthly pause ends on the same day next month`() {
        val start = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), PauseCalculator.endDate(start, PauseType.MONTHLY))
    }

    @Test
    fun `weekly periods are counted in full weeks`() {
        val start = LocalDate.of(2026, 1, 1)
        assertEquals(0, PauseCalculator.periodsElapsed(start, start.plusDays(6), PauseType.WEEKLY))
        assertEquals(1, PauseCalculator.periodsElapsed(start, start.plusDays(7), PauseType.WEEKLY))
        assertEquals(2, PauseCalculator.periodsElapsed(start, start.plusDays(14), PauseType.WEEKLY))
    }

    @Test
    fun `monthly periods are counted in full calendar months`() {
        val start = LocalDate.of(2026, 1, 31)
        assertEquals(0, PauseCalculator.periodsElapsed(start, LocalDate.of(2026, 2, 27), PauseType.MONTHLY))
        assertEquals(1, PauseCalculator.periodsElapsed(start, LocalDate.of(2026, 2, 28), PauseType.MONTHLY))
        assertEquals(2, PauseCalculator.periodsElapsed(start, LocalDate.of(2026, 3, 31), PauseType.MONTHLY))
    }

    @Test
    fun `periods before the pause starts are ignored`() {
        val start = LocalDate.of(2026, 1, 10)
        assertEquals(0, PauseCalculator.periodsElapsed(start, LocalDate.of(2026, 1, 1), PauseType.WEEKLY))
    }

    @Test
    fun `weekly pause reduces progress by forty percent`() {
        val result = PauseCalculator.applyPeriods(state(), periods = 1, type = PauseType.WEEKLY, unit = minutes)
        // progress 10, remaining 60% -> 10 + 6
        assertDecimal("16", result.currentGoal)
        assertFalse(result.targetReached)
    }

    @Test
    fun `monthly pause reduces progress by seventy five percent`() {
        val result = PauseCalculator.applyPeriods(state(), periods = 1, type = PauseType.MONTHLY, unit = minutes)
        // progress 10, remaining 25% -> 10 + 2.5
        assertDecimal("12.5", result.currentGoal)
    }

    @Test
    fun `multiple pause periods are applied exponentially`() {
        val result = PauseCalculator.applyPeriods(state(), periods = 2, type = PauseType.WEEKLY, unit = minutes)
        // 10 + 10 * 0.6^2 = 13.6
        assertDecimal("13.6", result.currentGoal)
    }

    @Test
    fun `pause reduction respects unit precision`() {
        val result = PauseCalculator.applyPeriods(state(), periods = 2, type = PauseType.WEEKLY, unit = reps)
        // 13.6 rounded half-up to an integer -> 14
        assertDecimal("14", result.currentGoal)
    }

    @Test
    fun `no reduction when there is no accumulated progress`() {
        val unchanged = state(base = "10", currentGoal = "10")
        val result = PauseCalculator.applyPeriods(unchanged, periods = 3, type = PauseType.MONTHLY, unit = reps)
        assertEquals(unchanged, result)
    }

    @Test
    fun `zero or negative periods do not change the state`() {
        val unchanged = state()
        assertEquals(unchanged, PauseCalculator.applyPeriods(unchanged, 0, PauseType.WEEKLY, reps))
        assertEquals(unchanged, PauseCalculator.applyPeriods(unchanged, -2, PauseType.WEEKLY, reps))
    }

    @Test
    fun `finishing a pause starts a new cycle from the reduced goal`() {
        val result = PauseCalculator.finish(state(base = "10", currentGoal = "13.6"), minutes)
        assertDecimal("13.6", result.base)
        assertDecimal("13.6", result.currentGoal)
        assertEquals(0, result.cycleIndex)
        assertEquals(0, result.dayIndex)
        assertFalse(result.targetReached)
    }

    @Test
    fun `reduction percentages match the multipliers`() {
        assertEquals(40.0, PauseCalculator.totalReductionPercent(PauseType.WEEKLY, 1), 0.0001)
        assertEquals(75.0, PauseCalculator.totalReductionPercent(PauseType.MONTHLY, 1), 0.0001)
        assertEquals(0.0, PauseCalculator.totalReductionPercent(PauseType.WEEKLY, 0), 0.0001)
    }

    @Test
    fun `multipliers match the spec`() {
        assertDecimal("0.60", PauseCalculator.remainingMultiplier(PauseType.WEEKLY))
        assertDecimal("0.25", PauseCalculator.remainingMultiplier(PauseType.MONTHLY))
        assertDecimal("0.40", PauseCalculator.reductionRate(PauseType.WEEKLY))
        assertDecimal("0.75", PauseCalculator.reductionRate(PauseType.MONTHLY))
    }
}
