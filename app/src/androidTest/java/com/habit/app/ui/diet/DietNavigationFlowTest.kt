package com.habit.app.ui.diet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.HabitTestRobot
import com.habit.app.MainActivity
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.MealRecordDraft
import com.habit.app.domain.model.MealType
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DietNavigationFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val robot by lazy { HabitTestRobot(composeRule) }

    @Before fun reset() = robot.resetDatabase()

    @Test
    fun diaryOpensReadOnlyDetailBeforeEditor() {
        val recordId = runBlocking {
            robot.container.dietRepository.save(
                null,
                MealRecordDraft(
                    recordType = DietRecordType.MEAL,
                    mealType = MealType.LUNCH,
                    occurredAt = 1_786_069_800_000,
                    recordEpochDay = robot.today().toEpochDay(),
                    description = "测试午餐",
                    foodItems = emptyList(),
                    manualFinalCalories = 320,
                    beverage = null,
                    note = "",
                    dietCategoryId = 4,
                ),
            )
        }
        composeRule.onNodeWithTag("welcome_skip").performClick()
        robot.waitForTag("open_drawer")
        composeRule.onNodeWithTag("open_drawer").performClick()
        composeRule.onNodeWithText("饮食日记").performClick()
        robot.waitForTag("diet_record_$recordId")

        composeRule.onNodeWithTag("diet_record_$recordId").performClick()

        composeRule.onNodeWithTag("diet_detail_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("diet_save").assertDoesNotExist()
        composeRule.onNodeWithTag("diet_detail_edit").performClick()
        composeRule.onNodeWithTag("diet_save").assertIsDisplayed()
    }
}
