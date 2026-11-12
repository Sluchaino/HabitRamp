package com.habitramp.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnlockCalculatorTest {

    @Test
    fun `first habit is always available`() {
        assertEquals(0, UnlockCalculator.requiredPointsForHabit(1))
        assertEquals(1, UnlockCalculator.slotsForPoints(0))
    }

    @Test
    fun `each additional slot costs twenty one points`() {
        assertEquals(21, UnlockCalculator.requiredPointsForHabit(2))
        assertEquals(42, UnlockCalculator.requiredPointsForHabit(3))
        assertEquals(147, UnlockCalculator.requiredPointsForHabit(8))
    }

    @Test
    fun `slots grow every twenty one points`() {
        assertEquals(1, UnlockCalculator.slotsForPoints(20))
        assertEquals(2, UnlockCalculator.slotsForPoints(21))
        assertEquals(2, UnlockCalculator.slotsForPoints(41))
        assertEquals(3, UnlockCalculator.slotsForPoints(42))
        assertEquals(7, UnlockCalculator.slotsForPoints(146))
        assertEquals(8, UnlockCalculator.slotsForPoints(147))
    }

    @Test
    fun `slots are capped at eight`() {
        assertEquals(8, UnlockCalculator.slotsForPoints(10_000))
        assertEquals(8, UnlockCalculator.slotsForPoints(Int.MAX_VALUE))
    }

    @Test
    fun `no scheduled habits means no point change`() {
        assertNull(UnlockCalculator.pointsForDay(0, 0))
        assertNull(UnlockCalculator.pointsForDay(-1, 0))
    }

    @Test
    fun `all scheduled habits completed earns one point per habit`() {
        assertEquals(1, UnlockCalculator.pointsForDay(1, 1))
        assertEquals(3, UnlockCalculator.pointsForDay(3, 3))
    }

    @Test
    fun `a single incomplete habit resets the day counter`() {
        assertEquals(0, UnlockCalculator.pointsForDay(3, 2))
        assertEquals(0, UnlockCalculator.pointsForDay(1, 0))
    }

    @Test
    fun `points needed for the next slot follows the schedule`() {
        assertEquals(21, UnlockCalculator.pointsNeededForNextSlot(1))
        assertEquals(42, UnlockCalculator.pointsNeededForNextSlot(2))
        assertEquals(147, UnlockCalculator.pointsNeededForNextSlot(7))
    }

    @Test
    fun `points needed is capped at the last slot`() {
        assertEquals(147, UnlockCalculator.pointsNeededForNextSlot(8))
        assertEquals(147, UnlockCalculator.pointsNeededForNextSlot(20))
    }
}
