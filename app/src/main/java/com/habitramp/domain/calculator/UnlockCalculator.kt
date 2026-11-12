package com.habitramp.domain.calculator

object UnlockCalculator {
    const val POINTS_PER_SLOT = 21
    const val MAX_SLOTS = 8

    fun requiredPointsForHabit(habitNumber: Int): Int =
        POINTS_PER_SLOT * (habitNumber - 1)

    fun slotsForPoints(points: Int): Int =
        (1 + points / POINTS_PER_SLOT).coerceAtMost(MAX_SLOTS)

    /**
     * Points earned for one calendar day.
     * Returns null when no habits were scheduled (counter must not change).
     * Returns 0 when at least one scheduled habit was not completed.
     */
    fun pointsForDay(scheduled: Int, completed: Int): Int? = when {
        scheduled <= 0 -> null
        completed >= scheduled -> completed
        else -> 0
    }

    fun pointsNeededForNextSlot(unlockedSlots: Int): Int {
        val next = (unlockedSlots + 1).coerceAtMost(MAX_SLOTS)
        return requiredPointsForHabit(next)
    }
}
