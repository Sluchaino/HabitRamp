package com.habitramp.domain.calculator

import com.habitramp.assertDecimal
import com.habitramp.core.model.TargetBehavior
import com.habitramp.core.model.Units
import com.habitramp.domain.model.ProgressionState
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionCalculatorTest {

    private val reps = Units.DEFAULT

    private fun state(
        base: String = "5",
        currentGoal: String = "5",
        step: String = "1",
        cycleLength: Int = 7,
        cycleIndex: Int = 0,
        dayIndex: Int = 0,
        targetValue: String? = null,
        targetBehavior: TargetBehavior = TargetBehavior.ASK,
        targetReached: Boolean = false
    ) = ProgressionState(
        base = BigDecimal(base),
        currentGoal = BigDecimal(currentGoal),
        step = BigDecimal(step),
        cycleLength = cycleLength,
        cycleIndex = cycleIndex,
        dayIndex = dayIndex,
        targetValue = targetValue?.let(::BigDecimal),
        targetBehavior = targetBehavior,
        targetReached = targetReached
    )

    @Test
    fun `goal follows base plus step times two cycles plus day`() {
        assertDecimal("5", ProgressionCalculator.rawGoal(BigDecimal("5"), BigDecimal("1"), 0, 0))
        assertDecimal("6", ProgressionCalculator.rawGoal(BigDecimal("5"), BigDecimal("1"), 0, 1))
        assertDecimal("7", ProgressionCalculator.rawGoal(BigDecimal("5"), BigDecimal("1"), 1, 0))
        assertDecimal("12", ProgressionCalculator.rawGoal(BigDecimal("5"), BigDecimal("1"), 2, 3))
        assertDecimal("13", ProgressionCalculator.rawGoal(BigDecimal("5"), BigDecimal("2"), 1, 2))
    }

    @Test
    fun `goal uses exact decimal step`() {
        assertDecimal("5.25", ProgressionCalculator.rawGoal(BigDecimal("5"), BigDecimal("0.25"), 0, 1))
        assertDecimal("6", ProgressionCalculator.rawGoal(BigDecimal("5"), BigDecimal("0.5"), 1, 0))
    }

    @Test
    fun `advance moves to the next day inside the cycle`() {
        val next = ProgressionCalculator.advanceAfterCompletion(
            state(cycleIndex = 0, dayIndex = 0, currentGoal = "5"),
            completedGoal = BigDecimal("5"),
            unit = reps
        )
        assertEquals(0, next.cycleIndex)
        assertEquals(1, next.dayIndex)
        assertDecimal("6", next.currentGoal)
    }

    @Test
    fun `advance starts a new cycle after the last day`() {
        val next = ProgressionCalculator.advanceAfterCompletion(
            state(cycleIndex = 0, dayIndex = 6, currentGoal = "11"),
            completedGoal = BigDecimal("11"),
            unit = reps
        )
        assertEquals(1, next.cycleIndex)
        assertEquals(0, next.dayIndex)
        // Base 5 + step 1 * (2 * 1 + 0) = 7
        assertDecimal("7", next.currentGoal)
    }

    @Test
    fun `advance rounds goal to unit precision`() {
        val minutes = Units.ALL.first { it.code == "minutes" }
        val next = ProgressionCalculator.advanceAfterCompletion(
            state(base = "1", currentGoal = "1", step = "0.333", dayIndex = 0),
            completedGoal = BigDecimal("1"),
            unit = minutes
        )
        assertDecimal("1.33", next.currentGoal)
    }

    @Test
    fun `target reached is set only when completed goal reaches target and behavior is ask`() {
        val below = ProgressionCalculator.advanceAfterCompletion(
            state(targetValue = "10", targetBehavior = TargetBehavior.ASK),
            completedGoal = BigDecimal("9"),
            unit = reps
        )
        assertFalse(below.targetReached)

        val reached = ProgressionCalculator.advanceAfterCompletion(
            state(targetValue = "10", targetBehavior = TargetBehavior.ASK),
            completedGoal = BigDecimal("10"),
            unit = reps
        )
        assertTrue(reached.targetReached)
    }

    @Test
    fun `target reached is not set for maintain or continue behaviors`() {
        val maintained = ProgressionCalculator.advanceAfterCompletion(
            state(targetValue = "10", targetBehavior = TargetBehavior.MAINTAIN),
            completedGoal = BigDecimal("10"),
            unit = reps
        )
        assertFalse(maintained.targetReached)

        val continued = ProgressionCalculator.advanceAfterCompletion(
            state(targetValue = "10", targetBehavior = TargetBehavior.CONTINUE),
            completedGoal = BigDecimal("10"),
            unit = reps
        )
        assertFalse(continued.targetReached)
    }

    @Test
    fun `target cap stops growth for ask maintain and finish`() {
        for (behavior in listOf(TargetBehavior.ASK, TargetBehavior.MAINTAIN, TargetBehavior.FINISH)) {
            assertDecimal(
                "10",
                ProgressionCalculator.applyTargetCap(BigDecimal("12"), BigDecimal("10"), behavior)
            )
        }
        assertDecimal(
            "8",
            ProgressionCalculator.applyTargetCap(BigDecimal("8"), BigDecimal("10"), TargetBehavior.ASK)
        )
        assertDecimal(
            "12",
            ProgressionCalculator.applyTargetCap(BigDecimal("12"), BigDecimal("10"), TargetBehavior.CONTINUE)
        )
        assertDecimal(
            "12",
            ProgressionCalculator.applyTargetCap(BigDecimal("12"), null, TargetBehavior.MAINTAIN)
        )
    }

    @Test
    fun `advance after miss keeps progression position and resets target reached`() {
        val next = ProgressionCalculator.advanceAfterMiss(
            state(cycleIndex = 0, dayIndex = 2, targetValue = "10", targetReached = true),
            unit = reps
        )
        assertEquals(3, next.dayIndex)
        assertDecimal("8", next.currentGoal)
        assertFalse(next.targetReached)
    }

    @Test
    fun `changing step preserves the current goal and rebases progression`() {
        val rebased = ProgressionCalculator.rebaseForNewStep(
            state(base = "5", currentGoal = "8", step = "1", cycleIndex = 1, dayIndex = 1),
            newStep = BigDecimal("2"),
            unit = reps
        )
        assertDecimal("2", rebased.base)
        assertDecimal("8", rebased.currentGoal)
        assertEquals(BigDecimal("2"), rebased.step)
        // The next calculated goal is continuous: 2 + 2 * (2 * 1 + 1) = 8
        assertDecimal("8", ProgressionCalculator.rawGoal(rebased))
    }

    @Test
    fun `changing cycle length starts a new cycle from the current goal`() {
        val rebased = ProgressionCalculator.rebaseForNewCycleLength(
            state(base = "5", currentGoal = "8", cycleIndex = 2, dayIndex = 3),
            newCycleLength = 5,
            unit = reps
        )
        assertDecimal("8", rebased.base)
        assertDecimal("8", rebased.currentGoal)
        assertEquals(5, rebased.cycleLength)
        assertEquals(0, rebased.cycleIndex)
        assertEquals(0, rebased.dayIndex)
    }

    @Test
    fun `manual reset starts a new cycle at the new goal`() {
        val reset = ProgressionCalculator.resetTo(
            state(base = "5", currentGoal = "20", cycleIndex = 3, dayIndex = 4, targetReached = true),
            newGoal = BigDecimal("10"),
            unit = reps
        )
        assertDecimal("10", reset.base)
        assertDecimal("10", reset.currentGoal)
        assertEquals(0, reset.cycleIndex)
        assertEquals(0, reset.dayIndex)
        assertFalse(reset.targetReached)
    }

    @Test
    fun `continue after target resumes growth and switches behavior`() {
        val continued = ProgressionCalculator.continueAfterTarget(
            state(
                base = "5",
                currentGoal = "6",
                cycleIndex = 1,
                dayIndex = 2,
                targetValue = "6",
                targetBehavior = TargetBehavior.MAINTAIN
            ),
            unit = reps
        )
        assertEquals(TargetBehavior.CONTINUE, continued.targetBehavior)
        assertDecimal("9", continued.currentGoal)
        assertFalse(continued.targetReached)
    }

    @Test
    fun `maintain target freezes the goal at the target`() {
        val maintained = ProgressionCalculator.maintainTarget(
            state(currentGoal = "12", targetValue = "10", targetBehavior = TargetBehavior.ASK),
            unit = reps
        )
        assertDecimal("10", maintained.currentGoal)
        assertEquals(TargetBehavior.MAINTAIN, maintained.targetBehavior)
        assertFalse(maintained.targetReached)
    }
}
