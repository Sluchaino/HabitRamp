package com.habitramp.presentation.home

import com.habitramp.core.model.CompletionStatus
import com.habitramp.core.model.MissPolicy
import com.habitramp.core.model.TargetBehavior
import com.habitramp.dailyRecord
import com.habitramp.domain.model.SettingsSnapshot
import com.habitramp.habit
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeStreakTest {
    private fun date(day: Int) = LocalDate.of(2026, 3, day)
    private fun snapshot(day: Int, weekdays: Set<DayOfWeek>) = SettingsSnapshot(
        date(day), BigDecimal.ONE, 7, weekdays, MissPolicy.KEEP, BigDecimal.ONE,
        null, TargetBehavior.ASK)

    @Test
    fun `removing weekdays does not hide past misses or completions`() {
        val habit = habit(startDate = date(2), weekdays = setOf(DayOfWeek.MONDAY))
        val records = (2..6).map { day -> dailyRecord(date = date(day),
            status = if (day == 3) CompletionStatus.MISSED else CompletionStatus.COMPLETED) }
        val settings = listOf(snapshot(2, DayOfWeek.entries.take(5).toSet()),
            snapshot(9, habit.weekdays))
        assertEquals(3, calculateStreak(habit, records, emptyList(), date(9), settings))
    }

    @Test
    fun `adding weekdays does not turn past rest days into misses`() {
        val habit = habit(startDate = date(2))
        val settings = listOf(snapshot(2, setOf(DayOfWeek.MONDAY)),
            snapshot(9, DayOfWeek.entries.toSet()))
        val records = listOf(dailyRecord(date = date(2), status = CompletionStatus.COMPLETED))
        assertEquals(1, calculateStreak(habit, records, emptyList(), date(9), settings))
    }

    @Test
    fun `partial completion breaks streak and unfinished today does not`() {
        val habit = habit(startDate = date(2))
        val records = listOf(
            dailyRecord(date = date(2), status = CompletionStatus.PARTIAL),
            dailyRecord(date = date(3), status = CompletionStatus.COMPLETED))
        assertEquals(1, calculateStreak(habit, records, emptyList(), date(4), emptyList()))
    }
}
