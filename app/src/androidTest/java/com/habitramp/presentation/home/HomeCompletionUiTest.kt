package com.habitramp.presentation.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habitramp.presentation.theme.HabitRampTheme
import com.habitramp.uiTestHabit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCompletionUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val habit = uiTestHabit(name = "Очень длинное название привычки для проверки переноса", currentGoal = "10")

    @Test
    fun doneButtonComesBeforeNotDoneAndInputField() {
        composeRule.setContent {
            HabitRampTheme(dynamicColor = false) {
                CompletionSection(
                    habit = habit,
                    actualInput = "",
                    onActualInputChange = {},
                    onDone = {},
                    onNotDone = {}
                )
            }
        }

        composeRule.onNodeWithText("Выполнено").assertIsDisplayed()
        composeRule.onNodeWithText("Не выполнено").assertIsDisplayed()

        val doneTop = composeRule.onNodeWithTag("home-completion-done")
            .fetchSemanticsNode().boundsInRoot.top
        val notDoneTop = composeRule.onNodeWithTag("home-completion-not-done")
            .fetchSemanticsNode().boundsInRoot.top
        val inputTop = composeRule.onNodeWithTag("home-completion-actual")
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue("«Выполнено» должно быть выше «Не выполнено»", doneTop < notDoneTop)
        assertTrue("Поле ввода должно быть ниже кнопок", notDoneTop < inputTop)
    }

    @Test
    fun notDoneButtonSendsTheEnteredValue() {
        var input by mutableStateOf("")
        var sentValue: String? = null
        var doneClicks = 0

        composeRule.setContent {
            HabitRampTheme(dynamicColor = false) {
                CompletionSection(
                    habit = habit,
                    actualInput = input,
                    onActualInputChange = { input = it },
                    onDone = { doneClicks++ },
                    onNotDone = { sentValue = input }
                )
            }
        }

        composeRule.onNodeWithTag("home-completion-actual").performTextInput("7")
        composeRule.onNodeWithTag("home-completion-not-done").performClick()

        composeRule.runOnIdle {
            assertEquals("7", sentValue)
            assertEquals(0, doneClicks)
        }
    }

    @Test
    fun doneButtonReportsFullCompletionWithoutUsingTheInput() {
        var input by mutableStateOf("3")
        var doneClicks = 0

        composeRule.setContent {
            HabitRampTheme(dynamicColor = false) {
                CompletionSection(
                    habit = habit,
                    actualInput = input,
                    onActualInputChange = { input = it },
                    onDone = { doneClicks++ },
                    onNotDone = {}
                )
            }
        }

        composeRule.onNodeWithTag("home-completion-done").performClick()

        composeRule.runOnIdle {
            assertEquals(1, doneClicks)
            assertEquals("3", input)
        }
    }
}
