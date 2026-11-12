package com.habitramp.presentation.history

import com.habitramp.domain.model.Habit
import com.habitramp.domain.model.SettingsSnapshot
import java.time.LocalDate

/**
 * Returns whether [date] was a planned day according to the settings snapshot
 * that was in effect on that date. When settings history is unavailable (for
 * example, old data without snapshots), the habit's current schedule is used.
 */
fun isScheduledOn(
    habit: Habit,
    settings: List<SettingsSnapshot>,
    date: LocalDate
): Boolean {
    if (date.isBefore(habit.startDate)) return false
    val snapshot = settings
        .withIndex()
        .filter { !it.value.effectiveDate.isAfter(date) }
        .maxWithOrNull(compareBy({ it.value.effectiveDate }, { it.index }))
        ?.value
    val weekdays = snapshot?.weekdays ?: habit.weekdays
    return date.dayOfWeek in weekdays
}
