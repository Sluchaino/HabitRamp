package com.habitramp.domain.calculator

import com.habitramp.assertDecimal
import com.habitramp.core.model.CompletionStatus
import com.habitramp.core.model.MissPolicy
import com.habitramp.core.model.PauseStatus
import com.habitramp.core.model.PauseType
import com.habitramp.core.model.TargetBehavior
import com.habitramp.core.util.DatePeriod
import com.habitramp.dailyRecord
import com.habitramp.domain.model.HabitPause
import com.habitramp.domain.model.ManualResetEvent
import com.habitramp.domain.model.SettingsSnapshot
import com.habitramp.habit
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsCalculatorTest {

    private fun date(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    private fun snapshot(
        effectiveDate: LocalDate,
        step: String = "1",
        cycleLength: Int = 7,
        weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
        targetValue: String? = null,
        targetBehavior: TargetBehavior = TargetBehavior.ASK
    ) = SettingsSnapshot(
        effectiveDate = effectiveDate,
        step = BigDecimal(step),
        cycleLength = cycleLength,
        weekdays = weekdays,
        missPolicy = MissPolicy.KEEP,
        missPenalty = BigDecimal.ONE,
        targetValue = targetValue?.let(::BigDecimal),
        targetBehavior = targetBehavior
    )

    @Test
    fun `potential goals grow every scheduled day`() {
        val goals = StatisticsCalculator.potentialGoals(
            habit = habit(),
            settings = emptyList(),
            manualResets = emptyList(),
            to = date(1, 7)
        )
        assertEquals(7, goals.size)
        assertDecimal("1", goals.getValue(date(1, 1)))
        assertDecimal("2", goals.getValue(date(1, 2)))
        assertDecimal("7", goals.getValue(date(1, 7)))
    }

    @Test
    fun `potential goals start on the habit start date`() {
        val goals = StatisticsCalculator.potentialGoals(
            habit = habit(startDate = date(1, 3)),
            settings = emptyList(),
            manualResets = emptyList(),
            to = date(1, 7)
        )
        assertEquals(5, goals.size)
        assertNull(goals[date(1, 1)])
        assertNull(goals[date(1, 2)])
        assertDecimal("1", goals.getValue(date(1, 3)))
    }

    @Test
    fun `potential goals skip non scheduled weekdays`() {
        val goals = StatisticsCalculator.potentialGoals(
            habit = habit(weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
            settings = emptyList(),
            manualResets = emptyList(),
            to = date(1, 7)
        )
        assertNull(goals[date(1, 1)]) // Thursday
        assertDecimal("1", goals.getValue(date(1, 2))) // Friday
        assertDecimal("2", goals.getValue(date(1, 5))) // Monday
        assertDecimal("3", goals.getValue(date(1, 7))) // Wednesday
        assertEquals(3, goals.size)
    }

    @Test
    fun `step changes are applied from their effective date and preserve continuity`() {
        val goals = StatisticsCalculator.potentialGoals(
            habit = habit(),
            settings = listOf(snapshot(effectiveDate = date(1, 3), step = "2")),
            manualResets = emptyList(),
            to = date(1, 5)
        )
        assertDecimal("1", goals.getValue(date(1, 1)))
        assertDecimal("2", goals.getValue(date(1, 2)))
        assertDecimal("3", goals.getValue(date(1, 3)))
        assertDecimal("5", goals.getValue(date(1, 4)))
        assertDecimal("7", goals.getValue(date(1, 5)))
    }

    @Test
    fun `manual reset restarts the simulated progression from the new goal`() {
        val goals = StatisticsCalculator.potentialGoals(
            habit = habit(),
            settings = emptyList(),
            manualResets = listOf(ManualResetEvent(effectiveDate = date(1, 4), newGoal = BigDecimal("10"))),
            to = date(1, 6)
        )
        assertDecimal("1", goals.getValue(date(1, 1)))
        assertDecimal("2", goals.getValue(date(1, 2)))
        assertDecimal("3", goals.getValue(date(1, 3)))
        assertDecimal("10", goals.getValue(date(1, 4)))
        assertDecimal("11", goals.getValue(date(1, 5)))
        assertDecimal("12", goals.getValue(date(1, 6)))
    }

    @Test
    fun `target cap is respected by potential goals`() {
        val goals = StatisticsCalculator.potentialGoals(
            habit = habit(targetValue = "3", targetBehavior = TargetBehavior.MAINTAIN),
            settings = emptyList(),
            manualResets = emptyList(),
            to = date(1, 6)
        )
        assertDecimal("1", goals.getValue(date(1, 1)))
        assertDecimal("2", goals.getValue(date(1, 2)))
        assertDecimal("3", goals.getValue(date(1, 3)))
        assertDecimal("3", goals.getValue(date(1, 4)))
        assertDecimal("3", goals.getValue(date(1, 5)))
        assertDecimal("3", goals.getValue(date(1, 6)))
    }

    @Test
    fun `calculate compares actual values with potential values`() {
        val habit = habit()
        val records = listOf(
            dailyRecord(
                date = date(1, 1), plannedValue = "1", actualValue = "1",
                status = CompletionStatus.COMPLETED
            ),
            dailyRecord(
                date = date(1, 2), plannedValue = "2", actualValue = "1",
                status = CompletionStatus.PARTIAL
            ),
            dailyRecord(
                date = date(1, 3), plannedValue = "3", actualValue = "0",
                status = CompletionStatus.MISSED
            )
        )
        val result = StatisticsCalculator.calculate(
            habit = habit,
            records = records,
            pauses = emptyList(),
            settings = emptyList(),
            manualResets = emptyList(),
            period = DatePeriod(date(1, 1), date(1, 3)),
            previousPeriod = DatePeriod(date(12, 29), date(12, 31))
        )

        assertDecimal("6", result.potential)
        assertDecimal("2", result.actual)
        assertDecimal("4", result.difference)
        assertEquals(1, result.completedDays)
        assertEquals(1, result.partialDays)
        assertEquals(1, result.missedDays)
        assertNull(result.changePercent)
        assertEquals(3, result.chart.size)
        assertTrue(result.chart[0].planned)
        assertDecimal("1", result.chart[0].potential)
        assertEquals(CompletionStatus.MISSED, result.chart[2].status)
    }

    @Test
    fun `calculate reports change against the comparable previous period`() {
        val result = StatisticsCalculator.calculate(
            habit = habit(),
            records = listOf(
                dailyRecord(
                    date = date(1, 1), actualValue = "1",
                    status = CompletionStatus.COMPLETED
                ),
                dailyRecord(
                    date = date(1, 2), actualValue = "2",
                    status = CompletionStatus.COMPLETED
                ),
                dailyRecord(
                    date = date(12, 31), actualValue = "2",
                    status = CompletionStatus.COMPLETED
                )
            ),
            pauses = emptyList(),
            settings = emptyList(),
            manualResets = emptyList(),
            period = DatePeriod(date(1, 1), date(1, 2)),
            previousPeriod = DatePeriod(date(12, 30), date(12, 31))
        )

        assertDecimal("3", result.actual)
        assertDecimal("2", result.previousActual)
        assertDecimal("1", result.changeAbsolute)
        assertEquals(50.0, result.changePercent!!, 0.0001)
    }

    @Test
    fun `archived habit is only counted up to the day before archiving`() {
        val result = StatisticsCalculator.calculate(
            habit = habit(archivedAt = date(1, 3)),
            records = listOf(
                dailyRecord(
                    date = date(1, 1), actualValue = "1",
                    status = CompletionStatus.COMPLETED
                ),
                dailyRecord(
                    date = date(1, 2), actualValue = "2",
                    status = CompletionStatus.COMPLETED
                )
            ),
            pauses = emptyList(),
            settings = emptyList(),
            manualResets = emptyList(),
            period = DatePeriod(date(1, 1), date(1, 5)),
            previousPeriod = DatePeriod(date(12, 29), date(12, 31))
        )

        assertDecimal("3", result.potential)
        assertDecimal("3", result.actual)
        assertEquals(5, result.chart.size)
        assertTrue(result.chart[0].planned)
        assertTrue(result.chart[1].planned)
        assertFalse(result.chart[2].planned)
        assertDecimal("0", result.chart[2].potential)
        assertFalse(result.chart[4].planned)
    }

    @Test
    fun `archived habit has no potential in a later period`() {
        val result = StatisticsCalculator.calculate(
            habit = habit(archivedAt = date(1, 3)),
            records = emptyList(),
            pauses = emptyList(),
            settings = emptyList(),
            manualResets = emptyList(),
            period = DatePeriod(date(1, 10), date(1, 12)),
            previousPeriod = DatePeriod(date(1, 7), date(1, 9))
        )

        assertDecimal("0", result.potential)
        assertDecimal("0", result.previousPotential)
        assertDecimal("0", result.difference)
        assertTrue(result.chart.all { !it.planned })
    }

    @Test
    fun `completed habit includes the completion day in potential`() {
        val result = StatisticsCalculator.calculate(
            habit = habit(completedAt = date(1, 2)),
            records = emptyList(),
            pauses = emptyList(),
            settings = emptyList(),
            manualResets = emptyList(),
            period = DatePeriod(date(1, 1), date(1, 4)),
            previousPeriod = DatePeriod(date(12, 29), date(12, 31))
        )

        assertDecimal("3", result.potential)
        assertTrue(result.chart[1].planned)
        assertFalse(result.chart[2].planned)
    }

    @Test
    fun `pause count only includes pauses overlapping the period`() {
        val pauses = listOf(
            HabitPause(
                id = 1,
                habitId = 1,
                startDate = date(1, 2),
                endDate = date(1, 5),
                pauseType = PauseType.WEEKLY,
                processedPeriods = 0,
                status = PauseStatus.ACTIVE
            ),
            HabitPause(
                id = 2,
                habitId = 1,
                startDate = date(1, 10),
                endDate = date(1, 17),
                pauseType = PauseType.WEEKLY,
                processedPeriods = 0,
                status = PauseStatus.ENDED
            )
        )
        val result = StatisticsCalculator.calculate(
            habit = habit(),
            records = emptyList(),
            pauses = pauses,
            settings = emptyList(),
            manualResets = emptyList(),
            period = DatePeriod(date(1, 1), date(1, 3)),
            previousPeriod = DatePeriod(date(12, 29), date(12, 31))
        )
        assertEquals(1, result.pauseCount)
    }

    @Test
    fun `settings weekday snapshots affect potential goals`() {
        val goals = StatisticsCalculator.potentialGoals(
            habit = habit(),
            settings = listOf(
                snapshot(
                    effectiveDate = date(1, 3),
                    weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
                )
            ),
            manualResets = emptyList(),
            to = date(1, 7)
        )
        // Jan 1-2 still use the original all-day schedule.
        assertDecimal("1", goals.getValue(date(1, 1)))
        assertDecimal("2", goals.getValue(date(1, 2)))
        // Jan 3-4 are weekend days and become unplanned.
        assertNull(goals[date(1, 3)])
        assertNull(goals[date(1, 4)])
        assertDecimal("3", goals.getValue(date(1, 5)))
        assertDecimal("4", goals.getValue(date(1, 7)))
    }
}
