package com.habitramp.presentation.home

import com.habitramp.domain.model.DailyRecord
import com.habitramp.domain.model.Habit
import com.habitramp.domain.model.HabitPause
import com.habitramp.domain.model.SettingsSnapshot
import com.habitramp.presentation.history.isScheduledOn
import java.time.LocalDate

internal fun calculateStreak(
    habit: Habit,
    records: List<DailyRecord>,
    pauses: List<HabitPause>,
    today: LocalDate,
    settings: List<SettingsSnapshot>
): Int {
    var date = today
    var streak = 0
    var guard = 0
    while (guard < 4000 && !date.isBefore(habit.startDate)) {
        if (pauses.any { it.covers(date) } || !isScheduledOn(habit, settings, date)) {
            date = date.minusDays(1)
            guard++
            continue
        }
        val record = records.firstOrNull { it.date == date }
        if (record == null) {
            if (date == today) {
                date = date.minusDays(1)
                guard++
                continue
            }
            break
        }
        if (!record.isSuccess) break
        streak++
        date = date.minusDays(1)
        guard++
    }
    return streak
}
