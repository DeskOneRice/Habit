package com.habit.app.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.ui.theme.HabitTheme
import com.habit.app.ui.theme.HabitThemeId
import java.time.LocalDate
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitDatePickerAlignmentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun weekdayLabelsAndDatesUseTheSameColumnCenters() {
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                HabitDatePickerDialog(
                    selectedDate = LocalDate.of(2026, 8, 3),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        assertSameCenter("habit_weekday_1", "habit_date_3")
        assertSameCenter("habit_weekday_7", "habit_date_2")
    }

    private fun assertSameCenter(firstTag: String, secondTag: String) {
        val first = composeRule.onNodeWithTag(firstTag).fetchSemanticsNode().boundsInRoot.center.x
        val second = composeRule.onNodeWithTag(secondTag).fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("$firstTag and $secondTag should share a column center", abs(first - second) < 1f)
    }
}
