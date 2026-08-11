package com.habit.app.ui.diet

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.HabitTestRobot
import com.habit.app.MainActivity
import com.habit.app.domain.model.DietPhoto
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

    @Before
    fun reset() {
        robot.resetDatabase()
        composeRule.activityRule.scenario.recreate()
        robot.waitForTag("welcome_screen")
    }

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
        composeRule.onNodeWithTag("diet_save").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun estimateConfirmationSelectionSurvivesActivityRecreation() {
        val relativePath = "library/config-rebuild.png"
        robot.container.dietPhotoStore.file(relativePath).also { file ->
            file.parentFile?.mkdirs()
            file.outputStream().use { output ->
                Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                    .compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
        val recordId = runBlocking {
            robot.container.dietRepository.save(
                null,
                MealRecordDraft(
                    recordType = DietRecordType.MEAL,
                    mealType = MealType.LUNCH,
                    occurredAt = 1_786_069_800_000,
                    recordEpochDay = robot.today().toEpochDay(),
                    description = "配置重建午餐",
                    foodItems = emptyList(),
                    manualFinalCalories = 320,
                    beverage = null,
                    note = "",
                    photos = listOf(DietPhoto(relativePath = relativePath, sortOrder = 0)),
                    dietCategoryId = 4,
                ),
            )
        }
        composeRule.onNodeWithTag("welcome_skip").performClick()
        robot.navigateTo("饮食日记")
        robot.waitForTag("diet_record_$recordId")
        composeRule.onNodeWithTag("diet_record_$recordId").performClick()
        composeRule.onNodeWithTag("diet_detail_edit").performClick()
        composeRule.onNodeWithTag("ai_calorie_estimate").performScrollTo().performClick()
        robot.waitForText("发送前确认")
        composeRule.onNodeWithTag("ai_estimate_photo_$relativePath").performClick()
        composeRule.onNodeWithText("已选择 0 / 1 张照片").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        robot.waitForText("发送前确认")
        composeRule.onNodeWithTag("ai_estimate_meal_name")
            .assertIsDisplayed()
            .assertTextEquals("配置重建午餐")
        composeRule.onNodeWithText("已选择 0 / 1 张照片").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_estimate_photo_$relativePath").assertIsNotSelected()
        composeRule.onNodeWithTag("ai_estimate_confirm_photos").assertIsNotEnabled()
    }
}
