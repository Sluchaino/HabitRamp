package com.habitramp.core.util

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatesTest {

    private val monday = LocalDate.of(2026, 3, 9)
    private val sunday = LocalDate.of(2026, 3, 15)

    @Test
    fun `date period length is inclusive`() {
        assertEquals(7, DatePeriod(monday, sunday).days)
        assertEquals(1, DatePeriod(monday, monday).days)
        assertEquals(0, DatePeriod(sunday, monday).days)
    }

    @Test
    fun `date period contains its boundaries`() {
        val period = DatePeriod(monday, sunday)
        assertTrue(monday in period)
        assertTrue(sunday in period)
        assertTrue(LocalDate.of(2026, 3, 12) in period)
        assertFalse(LocalDate.of(2026, 3, 8) in period)
        assertFalse(LocalDate.of(2026, 3, 16) in period)
    }

    @Test
    fun `current week starts on monday`() {
        val period = currentWeekPeriod(LocalDate.of(2026, 3, 12))
        assertEquals(LocalDate.of(2026, 3, 9), period.from)
        assertEquals(LocalDate.of(2026, 3, 12), period.to)
        assertEquals(4, period.days)
    }

    @Test
    fun `previous week has the same number of elapsed days`() {
        val current = currentWeekPeriod(LocalDate.of(2026, 3, 12))
        val previous = previousWeekComparablePeriod(LocalDate.of(2026, 3, 12))
        assertEquals(LocalDate.of(2026, 3, 2), previous.from)
        assertEquals(LocalDate.of(2026, 3, 5), previous.to)
        assertEquals(current.days, previous.days)
    }

    @Test
    fun `current month starts on the first day`() {
        val period = currentMonthPeriod(LocalDate.of(2026, 3, 31))
        assertEquals(LocalDate.of(2026, 3, 1), period.from)
        assertEquals(LocalDate.of(2026, 3, 31), period.to)
        assertEquals(31, period.days)
    }

    @Test
    fun `previous month is clamped to the real last day`() {
        val period = previousMonthComparablePeriod(LocalDate.of(2026, 3, 31))
        assertEquals(LocalDate.of(2026, 2, 1), period.from)
        assertEquals(LocalDate.of(2026, 2, 28), period.to)
    }

    @Test
    fun `previous month matches elapsed days when possible`() {
        val period = previousMonthComparablePeriod(LocalDate.of(2026, 3, 15))
        assertEquals(LocalDate.of(2026, 2, 1), period.from)
        assertEquals(LocalDate.of(2026, 2, 15), period.to)
        assertEquals(15, period.days)
    }

    @Test
    fun `full weeks are counted between dates`() {
        val start = LocalDate.of(2026, 1, 1)
        assertEquals(0, fullWeeksBetween(start, start.plusDays(6)))
        assertEquals(1, fullWeeksBetween(start, start.plusDays(7)))
        assertEquals(2, fullWeeksBetween(start, start.plusDays(14)))
        assertEquals(0, fullWeeksBetween(start, start.minusDays(1)))
    }

    @Test
    fun `full months respect month lengths`() {
        assertEquals(0, fullMonthsBetween(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 27)))
        assertEquals(1, fullMonthsBetween(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28)))
        assertEquals(2, fullMonthsBetween(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 31)))
        assertEquals(0, fullMonthsBetween(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun `epoch day conversion is reversible`() {
        val date = LocalDate.of(2026, 11, 12)
        assertEquals(date, date.toEpochDayLong().toLocalDate())
    }

    @Test
    fun `weekday names are russian`() {
        assertEquals("Пн", DayOfWeek.MONDAY.russianShort())
        assertEquals("Понедельник", DayOfWeek.MONDAY.russianFull())
    }
}
