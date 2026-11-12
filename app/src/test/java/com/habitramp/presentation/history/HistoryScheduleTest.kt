package com.habitramp.presentation.history

import com.habitramp.core.model.MissPolicy
import com.habitramp.core.model.TargetBehavior
import com.habitramp.domain.model.SettingsSnapshot
import com.habitramp.habit
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryScheduleTest {

    private val mondayOnly = setOf(DayOfWeek.MONDAY)
    private val tuesdayOnly = setOf(DayOfWeek.TUESDAY)

    @Test
    fun `snapshot is used for dates on and after its effective date`() {
        val settings = listOf(
            snapshot(LocalDate.of(2026, 3, 2), mondayOnly)
        )

        assertTrue(isScheduledOn(habit(), settings, LocalDate.of(2026, 3, 2)))
        assertFalse(isScheduledOn(habit(), settings, LocalDate.of(2026, 3, 3)))
    }

    @Test
    fun `latest snapshot before the date wins`() {
        val settings = listOf(
            snapshot(LocalDate.of(2026, 3, 2), mondayOnly),
            snapshot(LocalDate.of(2026, 3, 9), tuesdayOnly)
        )

        assertFalse(isScheduledOn(habit(), settings, LocalDate.of(2026, 3, 3)))
        assertFalse(isScheduledOn(habit(), settings, LocalDate.of(2026, 3, 9)))
        assertTrue(isScheduledOn(habit(), settings, LocalDate.of(2026, 3, 10)))
    }

    @Test
    fun `snapshot with the same effective date does not hide the newer one`() {
        val effective = LocalDate.of(2026, 3, 2)
        val settings = listOf(
            snapshot(effective, mondayOnly),
            snapshot(effective, tuesdayOnly)
        )

        assertFalse(isScheduledOn(habit(), settings, LocalDate.of(2026, 3, 2)))
        assertTrue(isScheduledOn(habit(), settings, LocalDate.of(2026, 3, 3)))
    }

    @Test
    fun `falls back to the current schedule when history is unavailable`() {
        val habit = habit(weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))

        assertTrue(isScheduledOn(habit, emptyList(), LocalDate.of(2026, 3, 2)))
        assertFalse(isScheduledOn(habit, emptyList(), LocalDate.of(2026, 3, 3)))
        assertTrue(isScheduledOn(habit, emptyList(), LocalDate.of(2026, 3, 6)))
    }

    @Test
    fun `dates before the habit start are never scheduled`() {
        val habit = habit(startDate = LocalDate.of(2026, 3, 5))

        assertFalse(isScheduledOn(habit, emptyList(), LocalDate.of(2026, 3, 2)))
        assertTrue(isScheduledOn(habit, emptyList(), LocalDate.of(2026, 3, 5)))
    }

    private fun snapshot(effectiveDate: LocalDate, weekdays: Set<DayOfWeek>) =
        SettingsSnapshot(
            effectiveDate = effectiveDate,
            step = BigDecimal.ONE,
            cycleLength = 7,
            weekdays = weekdays,
            missPolicy = MissPolicy.KEEP,
            missPenalty = BigDecimal.ONE,
            targetValue = null,
            targetBehavior = TargetBehavior.ASK
        )
}
