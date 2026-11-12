package com.habitramp.presentation.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habitramp.presentation.theme.HabitRampTheme
import com.habitramp.uiTestHabit
import com.habitramp.uiTestSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryCalendarUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val march = YearMonth.of(2026, 3)

    @Test
    fun unscheduledDayIsMarkedAndExplainedInTheLegend() {
        // Current schedule allows every day, but the snapshot in effect in March
        // allows only Mondays. March 3 (Tuesday) must be shown as unscheduled.
        val habit = uiTestHabit()
        val state = HistoryUiState(
            loading = false,
            habits = listOf(habit),
            habit = habit,
            month = march,
            settings = listOf(uiTestSnapshot(LocalDate.of(2026, 3, 1), setOf(DayOfWeek.MONDAY)))
        )

        composeRule.setContent {
            HabitRampTheme(dynamicColor = false) {
                Calendar(month = march, state = state, onSelectDay = {})
            }
        }

        composeRule.onNodeWithContentDescription("3 марта 2026: Не запланировано")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("2 марта 2026: Нет записи")
            .assertIsDisplayed()
    }

    @Test
    fun legendExplainsUnscheduledDays() {
        composeRule.setContent {
            HabitRampTheme(dynamicColor = false) {
                Legend()
            }
        }

        composeRule.onNodeWithText("Не запланировано").assertIsDisplayed()
    }

    @Test
    fun currentScheduleIsUsedWhenSettingsHistoryIsEmpty() {
        val habit = uiTestHabit(weekdays = setOf(DayOfWeek.MONDAY))
        val state = HistoryUiState(
            loading = false,
            habits = listOf(habit),
            habit = habit,
            month = march
        )

        composeRule.setContent {
            HabitRampTheme(dynamicColor = false) {
                Calendar(month = march, state = state, onSelectDay = {})
            }
        }

        composeRule.onNodeWithContentDescription("3 марта 2026: Не запланировано")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("2 марта 2026: Нет записи")
            .assertIsDisplayed()
    }
}
