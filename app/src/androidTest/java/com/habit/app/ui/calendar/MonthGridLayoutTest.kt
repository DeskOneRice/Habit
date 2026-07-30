package com.habit.app.ui.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.domain.model.MonthSnapshot
import com.habit.app.domain.stats.MonthStats
import com.habit.app.ui.theme.HabitTheme
import com.habit.app.ui.theme.HabitThemeId
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonthGridLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sixWeekMonthAndSummaryAreReachableAt480DpInShortViewport() {
        val month = YearMonth.of(2031, 3)
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                Column(
                    Modifier
                        .requiredWidth(480.dp),
                ) {
                    Box(Modifier.testTag("layout_test_grid")) {
                        MonthGrid(
                            snapshot = MonthSnapshot(
                                month = month,
                                marksByEpochDay = emptyMap(),
                                stats = MonthStats(0, 0, 0, 0f),
                            ),
                            selectedDate = LocalDate.of(2031, 3, 1),
                            onDateSelected = {},
                        )
                    }
                    Text("统计区域", Modifier.testTag("layout_test_summary"))
                }
            }
        }

        composeRule.onNodeWithTag("layout_test_grid").assertHeightIsAtLeast(400.dp)
        composeRule.onNodeWithTag("day_2031-03-31").assertIsDisplayed()
        composeRule.onNodeWithTag("layout_test_summary")
            .assertIsDisplayed()
    }

    @Test
    fun cellsRemainLegibleAndReachableAt360DpAnd130PercentFontScale() {
        val month = YearMonth.of(2031, 3)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.3f),
            ) {
                HabitTheme(HabitThemeId.SKY_BLUE) {
                    Box(
                        Modifier
                            .requiredWidth(360.dp)
                            .padding(horizontal = 12.dp),
                    ) {
                        MonthGrid(
                            snapshot = MonthSnapshot(
                                month = month,
                                marksByEpochDay = emptyMap(),
                                stats = MonthStats(0, 0, 0, 0f),
                            ),
                            selectedDate = LocalDate.of(2031, 3, 1),
                            onDateSelected = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("day_2031-03-01")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("day_2031-03-31").assertIsDisplayed()
    }
}
