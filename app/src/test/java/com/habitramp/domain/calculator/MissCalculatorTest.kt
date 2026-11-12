package com.habitramp.domain.calculator

import com.habitramp.assertDecimal
import com.habitramp.core.model.MissPolicy
import com.habitramp.core.model.TargetBehavior
import com.habitramp.core.model.Units
import com.habitramp.domain.model.ProgressionState
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MissCalculatorTest {

    private val reps = Units.DEFAULT

    private fun state(
        base: String = "5",
        currentGoal: String = "10",
        step: String = "1",
        cycleIndex: Int = 2,
        dayIndex: Int = 3,
        targetValue: String? = null,
        targetBehavior: TargetBehavior = TargetBehavior.ASK
    ) = ProgressionState(
        base = BigDecimal(base),
        currentGoal = BigDecimal(currentGoal),
        step = BigDecimal(step),
        cycleLength = 7,
        cycleIndex = cycleIndex,
        dayIndex = dayIndex,
        targetValue = targetValue?.let(::BigDecimal),
        targetBehavior = targetBehavior,
        targetReached = true
    )

    @Test
    fun `keep policy advances one position and keeps growth`() {
        val result = MissCalculator.applyMiss(
            state = state(cycleIndex = 0, dayIndex = 2, currentGoal = "7"),
            policy = MissPolicy.KEEP,
            resetValue = BigDecimal("1"),
            penalty = BigDecimal("1"),
            unit = reps
        )
        assertEquals(0, result.cycleIndex)
        assertEquals(3, result.dayIndex)
        assertDecimal("8", result.currentGoal)
        assertFalse(result.targetReached)
    }

    @Test
    fun `keep policy starts a new cycle after the last day`() {
        val result = MissCalculator.applyMiss(
            state = state(cycleIndex = 0, dayIndex = 6, currentGoal = "11"),
            policy = MissPolicy.KEEP,
            resetValue = BigDecimal("1"),
            penalty = BigDecimal("1"),
            unit = reps
        )
        assertEquals(1, result.cycleIndex)
        assertEquals(0, result.dayIndex)
        assertDecimal("7", result.currentGoal)
    }

    @Test
    fun `restart policy returns to the reset value and a new cycle`() {
        val result = MissCalculator.applyMiss(
            state = state(cycleIndex = 3, dayIndex = 4, currentGoal = "20"),
            policy = MissPolicy.RESTART,
            resetValue = BigDecimal("3"),
            penalty = BigDecimal("1"),
            unit = reps
        )
        assertDecimal("3", result.base)
        assertDecimal("3", result.currentGoal)
        assertEquals(0, result.cycleIndex)
        assertEquals(0, result.dayIndex)
        assertFalse(result.targetReached)
    }

    @Test
    fun `restart policy respects target cap`() {
        val result = MissCalculator.applyMiss(
            state = state(currentGoal = "20", targetValue = "10"),
            policy = MissPolicy.RESTART,
            resetValue = BigDecimal("15"),
            penalty = BigDecimal("1"),
            unit = reps
        )
        assertDecimal("15", result.base)
        assertDecimal("10", result.currentGoal)
    }

    @Test
    fun `reduce policy subtracts the penalty and starts a new cycle`() {
        val result = MissCalculator.applyMiss(
            state = state(cycleIndex = 3, dayIndex = 4, currentGoal = "10"),
            policy = MissPolicy.REDUCE,
            resetValue = BigDecimal("1"),
            penalty = BigDecimal("3"),
            unit = reps
        )
        assertDecimal("7", result.base)
        assertDecimal("7", result.currentGoal)
        assertEquals(0, result.cycleIndex)
        assertEquals(0, result.dayIndex)
        assertFalse(result.targetReached)
    }

    @Test
    fun `reduce policy never goes below the unit minimum`() {
        val result = MissCalculator.applyMiss(
            state = state(currentGoal = "2"),
            policy = MissPolicy.REDUCE,
            resetValue = BigDecimal("1"),
            penalty = BigDecimal("100"),
            unit = reps
        )
        assertDecimal("1", result.currentGoal)
    }

    @Test
    fun `reduce policy keeps fractional precision and minimum for fractional units`() {
        val minutes = Units.ALL.first { it.code == "minutes" }
        val result = MissCalculator.applyMiss(
            state = state(currentGoal = "0.50"),
            policy = MissPolicy.REDUCE,
            resetValue = BigDecimal("1"),
            penalty = BigDecimal("2"),
            unit = minutes
        )
        assertDecimal("0.01", result.currentGoal)
    }

    @Test
    fun `reduce policy respects target cap`() {
        val result = MissCalculator.applyMiss(
            state = state(currentGoal = "12", targetValue = "5"),
            policy = MissPolicy.REDUCE,
            resetValue = BigDecimal("1"),
            penalty = BigDecimal("1"),
            unit = reps
        )
        assertDecimal("5", result.currentGoal)
    }
}
