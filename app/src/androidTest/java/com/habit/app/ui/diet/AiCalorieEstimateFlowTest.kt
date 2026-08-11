package com.habit.app.ui.diet

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.domain.model.AiCalorieEstimateDraft
import com.habit.app.domain.model.AiCalorieItemEstimate
import com.habit.app.domain.model.DietRecordType
import com.habit.app.ui.theme.HabitTheme
import com.habit.app.ui.theme.HabitThemeId
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiCalorieEstimateFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mealAndBeverageActionsRequireOneToThreePhotos() {
        var type by mutableStateOf(DietRecordType.MEAL)
        var photoCount by mutableStateOf(0)

        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                AiCalorieEstimateAction(
                    recordType = type,
                    photoCount = photoCount,
                    isBusy = false,
                    onEstimate = {},
                )
            }
        }

        composeRule.onNodeWithTag("ai_calorie_estimate").assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithText("请先添加 1–3 张饮食照片").assertIsDisplayed()

        composeRule.runOnIdle { photoCount = 1 }
        composeRule.onNodeWithText("已选择 1 张照片，将同时发送记录信息").assertIsDisplayed()

        composeRule.runOnIdle { type = DietRecordType.BEVERAGE }
        composeRule.onNodeWithTag("ai_calorie_estimate").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("已选择 1 张照片，将同时发送记录信息").assertIsDisplayed()
    }

    @Test
    fun sendConfirmationShowsMealAndDefaultsAllPhotosSelectedBeforeRequest() {
        val root = createTempDirectory("ai_send_confirm_").toFile()
        val first = createImage(File(root, "first.png"))
        val second = createImage(File(root, "second.png"))
        var selected by mutableStateOf(setOf("first", "second"))
        var confirmed = false

        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                AiCalorieEstimateSheet(
                    mealName = "牛肉饭配时蔬",
                    photos = listOf(
                        AiEstimatePhotoUi("first", first),
                        AiEstimatePhotoUi("second", second),
                    ),
                    selectedPhotoPaths = selected,
                    estimate = null,
                    adoptedCaloriesText = "",
                    isBusy = false,
                    errorMessage = null,
                    onTogglePhoto = { path ->
                        selected = if (path in selected) selected - path else selected + path
                    },
                    onConfirmPhotos = { confirmed = true },
                    onAdoptedCaloriesChange = {},
                    onCancel = {},
                    onAdopt = {},
                )
            }
        }

        composeRule.onNodeWithText("发送前确认").assertIsDisplayed()
        composeRule.onNodeWithText("牛肉饭配时蔬").assertIsDisplayed()
        composeRule.onNodeWithText("已选择 2 / 2 张照片").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_estimate_photo_first").assertIsSelected()
        composeRule.onNodeWithTag("ai_estimate_photo_second").assertIsSelected().performClick()
        composeRule.onNodeWithTag("ai_estimate_photo_second").assertIsNotSelected()
        composeRule.onNodeWithText("已选择 1 / 2 张照片").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_estimate_confirm_photos")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.runOnIdle { assertTrue(confirmed) }

        composeRule.onNodeWithTag("ai_estimate_photo_first").performClick()
        composeRule.onNodeWithText("请至少选择 1 张照片").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_estimate_confirm_photos").assertIsNotEnabled()
        root.deleteRecursively()
    }

    @Test
    fun busySheetCanBeCancelledWithoutChangingExistingCalories() {
        var showSheet by mutableStateOf(true)
        var finalCalories by mutableStateOf("680")
        var cancelled = false

        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                if (showSheet) {
                    AiCalorieEstimateSheet(
                        estimate = null,
                        selectedPhotoCount = 2,
                        adoptedCaloriesText = finalCalories,
                        isBusy = true,
                        errorMessage = null,
                        onAdoptedCaloriesChange = { finalCalories = it },
                        onCancel = {
                            cancelled = true
                            showSheet = false
                        },
                        onAdopt = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("正在识别这条饮食记录…").assertIsDisplayed()
        composeRule.onNodeWithText("已选择 2 张照片").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_estimate_cancel").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.runOnIdle {
            assertTrue(cancelled)
            assertEquals("680", finalCalories)
        }
    }

    @Test
    fun confirmationShowsEvidenceAndAdoptingOnlyChangesCalories() {
        var finalCalories by mutableStateOf("680")
        val description = "牛肉饭和青菜"
        val note = "少油"
        var adopted = false

        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                AiCalorieEstimateSheet(
                    estimate = estimate(),
                    selectedPhotoCount = 2,
                    adoptedCaloriesText = finalCalories,
                    isBusy = false,
                    errorMessage = null,
                    onAdoptedCaloriesChange = { finalCalories = it },
                    onCancel = {},
                    onAdopt = {
                        finalCalories = it.toString()
                        adopted = true
                    },
                )
            }
        }

        composeRule.onNodeWithText("识别结果").assertIsDisplayed()
        composeRule.onNodeWithText("牛肉饭").assertIsDisplayed()
        composeRule.onNodeWithText("1 份 · 520–640 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("青菜").assertIsDisplayed()
        composeRule.onNodeWithText("总计 600–760 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("仅用于估算，请按实际份量调整").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("记录信息与照片仅用于本次识别，不会展示密钥、模型标识或原始响应")
            .performScrollTo().assertIsDisplayed()

        val adoptedField = composeRule.onNodeWithTag("ai_estimate_adopted_kcal")
        adoptedField.performTextClearance()
        adoptedField.performTextInput("710")
        composeRule.onNodeWithTag("ai_estimate_adopt").performScrollTo()
            .assertTextEquals("采用 710 kcal").assertHeightIsAtLeast(48.dp).performClick()

        composeRule.runOnIdle {
            assertTrue(adopted)
            assertEquals("710", finalCalories)
            assertEquals("牛肉饭和青菜", description)
            assertEquals("少油", note)
        }
    }

    @Test
    fun safeChineseErrorDoesNotExposeRawResponseOrModelId() {
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                AiCalorieEstimateSheet(
                    estimate = null,
                    selectedPhotoCount = 1,
                    adoptedCaloriesText = "",
                    isBusy = false,
                    errorMessage = "未能识别热量，请换一张清晰照片重试",
                    onAdoptedCaloriesChange = {},
                    onCancel = {},
                    onAdopt = {},
                )
            }
        }

        composeRule.onNodeWithText("未能识别热量，请换一张清晰照片重试").assertIsDisplayed()
        composeRule.onNodeWithText("private malformed response").assertDoesNotExist()
        composeRule.onNodeWithText("vision-1").assertDoesNotExist()
    }

    private fun estimate() = AiCalorieEstimateDraft(
        generatedAt = 1,
        modelNameSnapshot = "Private Vision Name",
        modelIdSnapshot = "vision-1",
        items = listOf(
            AiCalorieItemEstimate("牛肉饭", "1 份", 520, 640),
            AiCalorieItemEstimate("青菜", "半碗", 80, 120),
        ),
        totalMinKcal = 600,
        totalMaxKcal = 760,
        suggestedKcal = 680,
        adoptedKcal = 680,
        wasModified = false,
        accuracyNote = "仅用于估算，请按实际份量调整",
    )

    private fun createImage(file: File): File {
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        return file
    }
}
