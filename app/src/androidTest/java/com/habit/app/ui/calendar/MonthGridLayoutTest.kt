package com.habit.app.ui.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.habit.app.domain.model.CalendarMark
import com.habit.app.domain.model.MonthSnapshot
import com.habit.app.domain.stats.MonthStats
import com.habit.app.ui.theme.HabitTheme
import com.habit.app.ui.theme.HabitThemeId
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonthGridLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sixWeekMonthAndSummaryAreReachableAt360DpInShortViewport() {
        val month = YearMonth.of(2031, 3)
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                Column(Modifier.fillMaxWidth()) {
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

        composeRule.onNodeWithTag("layout_test_grid").assertHeightIsAtLeast(336.dp)
        composeRule.onNodeWithTag("day_2031-03-31").assertIsDisplayed()
        composeRule.onNodeWithTag("layout_test_summary")
            .assertIsDisplayed()
    }

    @Test
    fun cellsRemainLegibleAndReachableAt360DpAnd130PercentFontScale() {
        val month = YearMonth.of(2031, 3)
        val markedDate = LocalDate.of(2031, 3, 3)
        val marks = listOf("book", "sprout", "run", "heart", "water")
            .mapIndexed { index, iconKey ->
                CalendarMark(iconKey = iconKey, habitId = index.toLong(), sortOrder = index)
            }
        assertEquals(
            360,
            InstrumentationRegistry.getInstrumentation()
                .targetContext
                .resources
                .configuration
                .screenWidthDp,
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.3f),
            ) {
                HabitTheme(HabitThemeId.SKY_BLUE) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        MonthGrid(
                            snapshot = MonthSnapshot(
                                month = month,
                                marksByEpochDay = mapOf(markedDate.toEpochDay() to marks),
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
        composeRule.onNodeWithTag("day_2031-03-03").assertIsDisplayed()
        composeRule.onNodeWithTag("day_2031-03-02").assertIsDisplayed()

        composeRule.onNodeWithTag("day_${markedDate}_mark_0", useUnmergedTree = true)
            .assertTextEquals("📚")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("day_${markedDate}_overflow", useUnmergedTree = true)
            .assertTextEquals("+4")
            .assertIsDisplayed()

        val leftEdge = composeRule.onNodeWithTag("day_2031-03-03")
            .fetchSemanticsNode()
            .boundsInRoot
        val rightEdge = composeRule.onNodeWithTag("day_2031-03-02")
            .fetchSemanticsNode()
            .boundsInRoot
        val viewportWidthPx = with(composeRule.density) { 360.dp.toPx() }
        assertTrue("Monday cell must stay inside the left grid edge", leftEdge.left >= 0f)
        assertTrue(
            "Sunday cell must stay inside the right grid edge",
            rightEdge.right <= viewportWidthPx,
        )
    }

    @Test
    fun weekdayCellsShareDateCentersAndKeepBreathingRoom() {
        val month = YearMonth.of(2026, 8)
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                MonthGrid(
                    snapshot = MonthSnapshot(
                        month = month,
                        marksByEpochDay = emptyMap(),
                        stats = MonthStats(0, 0, 0, 0f),
                    ),
                    selectedDate = LocalDate.of(2026, 8, 3),
                    onDateSelected = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        composeRule.onNodeWithTag("month_weekday_1")
            .assertHeightIsAtLeast(40.dp)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("month_weekday_7")
            .assertHeightIsAtLeast(40.dp)
            .assertIsDisplayed()

        assertSameHorizontalCenter("month_weekday_1", "day_2026-08-03")
        assertSameHorizontalCenter("month_weekday_7", "day_2026-08-02")
    }

    private fun assertSameHorizontalCenter(firstTag: String, secondTag: String) {
        val first = composeRule.onNodeWithTag(firstTag).fetchSemanticsNode().boundsInRoot.center.x
        val second = composeRule.onNodeWithTag(secondTag).fetchSemanticsNode().boundsInRoot.center.x
        assertEquals(first, second, 1f)
    }
}
