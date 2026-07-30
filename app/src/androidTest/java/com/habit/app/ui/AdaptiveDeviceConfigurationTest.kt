package com.habit.app.ui

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.habit.app.HabitTestRobot
import com.habit.app.MainActivity
import java.io.FileInputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveDeviceConfigurationTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val robot by lazy { HabitTestRobot(composeRule) }

    @Test
    fun primaryActionsAndCalendarFitTrue480DpShortViewportAt130PercentFontScale() {
        robot.resetDatabase()
        robot.seedHabit(name = "四百八十宽度审计", iconKey = "book")
        val original = captureDisplayConfiguration()
        var scenario: ActivityScenario<MainActivity>? = null

        try {
            shell("wm size 960x1280")
            shell("wm density 320")
            shell("settings put system font_scale 1.3")
            shell("am wait-for-broadcast-idle")
            instrumentation.waitForIdleSync()

            scenario = ActivityScenario.launch(MainActivity::class.java)
            robot.waitForTag("calendar_screen")
            scenario.onActivity { activity ->
                val configuration = activity.resources.configuration
                assertEquals(480, configuration.screenWidthDp)
                assertTrue(
                    "The 480dp viewport must remain short enough to exercise scrolling",
                    configuration.screenHeightDp <= 640,
                )
                assertEquals(1.3f, configuration.fontScale, 0.01f)
            }

            assertPrimaryAction("calendar_previous_month")
            assertPrimaryAction("calendar_next_month")
            assertCalendarGridEdgesFit(robot.today())

            val previousMonth = YearMonth.from(robot.today()).minusMonths(1)
            composeRule.onNodeWithTag("calendar_previous_month").performClick()
            robot.waitForTag("day_${previousMonth.atDay(1)}")
            composeRule.onNodeWithTag("calendar_next_month").performClick()
            robot.waitForTag("day_${YearMonth.from(robot.today()).atDay(1)}")

            robot.navigateTo("习惯")
            assertPrimaryAction("create_habit")
            composeRule.onNodeWithText("四百八十宽度审计").performClick()
            assertPrimaryAction("edit_habit", scrollTo = true)
            composeRule.onNodeWithTag("edit_habit").performClick()
            assertPrimaryAction("save_habit")

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            robot.waitForTag("habit_detail_screen")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            robot.waitForTag("habit_list_screen")

            robot.navigateTo("设置")
            assertPrimaryAction("settings_categories", scrollTo = true)
        } finally {
            scenario?.close()
            restoreDisplayConfiguration(original)
        }
    }

    private fun assertPrimaryAction(tag: String, scrollTo: Boolean = false) {
        val node = composeRule.onNodeWithTag(tag)
        if (scrollTo) node.performScrollTo()
        node.assertHeightIsAtLeast(48.dp).assertIsDisplayed()
    }

    private fun assertCalendarGridEdgesFit(today: LocalDate) {
        val month = YearMonth.from(today)
        val leftEdgeDate = firstDateWithDayOfWeek(month, DayOfWeek.MONDAY)
        val rightEdgeDate = firstDateWithDayOfWeek(month, DayOfWeek.SUNDAY)
        val calendarBounds = composeRule.onNodeWithTag("calendar_screen")
            .fetchSemanticsNode()
            .boundsInRoot
        val leftBounds = composeRule.onNodeWithTag("day_$leftEdgeDate")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val rightBounds = composeRule.onNodeWithTag("day_$rightEdgeDate")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue("Monday cell overflowed the left edge", leftBounds.left >= calendarBounds.left)
        assertTrue("Sunday cell overflowed the right edge", rightBounds.right <= calendarBounds.right)
    }

    private fun firstDateWithDayOfWeek(month: YearMonth, dayOfWeek: DayOfWeek): LocalDate {
        var date = month.atDay(1)
        while (date.dayOfWeek != dayOfWeek) date = date.plusDays(1)
        return date
    }

    private fun captureDisplayConfiguration(): DisplayConfiguration {
        val sizeOutput = shell("wm size")
        val densityOutput = shell("wm density")
        return DisplayConfiguration(
            overrideSize = Regex("""Override size:\s*(\d+x\d+)""")
                .find(sizeOutput)
                ?.groupValues
                ?.get(1),
            overrideDensity = Regex("""Override density:\s*(\d+)""")
                .find(densityOutput)
                ?.groupValues
                ?.get(1),
            fontScale = shell("settings get system font_scale").trim(),
        )
    }

    private fun restoreDisplayConfiguration(original: DisplayConfiguration) {
        shell(original.overrideSize?.let { "wm size $it" } ?: "wm size reset")
        shell(original.overrideDensity?.let { "wm density $it" } ?: "wm density reset")
        shell("settings put system font_scale ${original.fontScale}")
        shell("am wait-for-broadcast-idle")
        instrumentation.waitForIdleSync()
    }

    private fun shell(command: String): String {
        val descriptor: ParcelFileDescriptor = instrumentation.uiAutomation
            .executeShellCommand(command)
        return try {
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        } finally {
            descriptor.close()
        }
    }

    private data class DisplayConfiguration(
        val overrideSize: String?,
        val overrideDensity: String?,
        val fontScale: String,
    )
}
