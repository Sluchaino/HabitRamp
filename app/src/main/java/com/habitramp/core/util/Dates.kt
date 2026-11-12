package com.habitramp.core.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val RU: Locale = Locale("ru", "RU")

val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", RU)
val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", RU)
val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", RU)

data class DatePeriod(val from: LocalDate, val to: LocalDate) {
    val days: Int get() = (ChronoUnit.DAYS.between(from, to).toInt() + 1).coerceAtLeast(0)
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)
}

fun LocalDate.toEpochDayLong(): Long = toEpochDay()
fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(this)

fun LocalDate.formatFull(): String = format(FULL_DATE)
fun LocalDate.formatShort(): String = format(SHORT_DATE)
fun LocalDate.formatMonthYear(): String = format(MONTH_YEAR)

fun DayOfWeek.russianShort(): String =
    getDisplayName(TextStyle.SHORT, RU).replace(".", "").replaceFirstChar { it.uppercase(RU) }

fun DayOfWeek.russianFull(): String =
    getDisplayName(TextStyle.FULL, RU).replaceFirstChar { it.uppercase(RU) }

fun currentWeekPeriod(today: LocalDate): DatePeriod {
    val monday = today.with(DayOfWeek.MONDAY)
    return DatePeriod(monday, today)
}

fun previousWeekComparablePeriod(today: LocalDate): DatePeriod {
    val current = currentWeekPeriod(today)
    val previousStart = current.from.minusWeeks(1)
    return DatePeriod(previousStart, previousStart.plusDays((current.days - 1).toLong()))
}

fun currentMonthPeriod(today: LocalDate): DatePeriod {
    val start = today.withDayOfMonth(1)
    return DatePeriod(start, today)
}

/**
 * Previous month period with the same number of elapsed days as the current month.
 * If the previous month is shorter, the actual last day is used.
 */
fun previousMonthComparablePeriod(today: LocalDate): DatePeriod {
    val currentStart = today.withDayOfMonth(1)
    val previousStart = currentStart.minusMonths(1)
    val requestedEnd = previousStart.plusDays((today.dayOfMonth - 1).toLong())
    val actualEnd = if (requestedEnd.isAfter(previousStart.withDayOfMonth(previousStart.lengthOfMonth()))) {
        previousStart.withDayOfMonth(previousStart.lengthOfMonth())
    } else {
        requestedEnd
    }
    return DatePeriod(previousStart, actualEnd)
}

/** Number of full calendar months between [start] and [date], respecting month lengths. */
fun fullMonthsBetween(start: LocalDate, date: LocalDate): Int {
    if (date.isBefore(start)) return 0
    var months = ChronoUnit.MONTHS.between(start, date).toInt()
    if (start.plusMonths(months.toLong()).isAfter(date)) months -= 1
    while (start.plusMonths((months + 1).toLong()).let { !it.isAfter(date) }) {
        months += 1
    }
    return months.coerceAtLeast(0)
}

/** Number of full 7-day periods between [start] and [date]. */
fun fullWeeksBetween(start: LocalDate, date: LocalDate): Int {
    if (date.isBefore(start)) return 0
    return (ChronoUnit.DAYS.between(start, date) / 7).toInt()
}
