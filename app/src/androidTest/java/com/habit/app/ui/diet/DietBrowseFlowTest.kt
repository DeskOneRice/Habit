package com.habit.app.ui.diet

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.habit.app.data.local.HabitDatabase
import com.habit.app.data.repository.RoomDietRepository
import com.habit.app.data.photos.CameraPhotoTarget
import com.habit.app.data.photos.DietPhotoStore
import com.habit.app.domain.model.CalorieSource
import com.habit.app.domain.model.AiCalorieEstimate
import com.habit.app.domain.model.AiCalorieEstimateDraft
import com.habit.app.domain.model.AiCalorieItemEstimate
import com.habit.app.domain.model.BeverageCategory
import com.habit.app.domain.model.BeverageDetails
import com.habit.app.domain.model.DietPhoto
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.FoodItem
import com.habit.app.domain.model.FoodItemDraft
import com.habit.app.domain.model.MealRecordDraft
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealType
import com.habit.app.ui.theme.HabitTheme
import com.habit.app.ui.theme.HabitThemeId
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class DietBrowseFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun thumbnailUsesFirstValidPhoto() {
        val root = createTempDirectory("diet_thumbnail_").toFile()
        val store = TestPhotoStore(root)
        val image = store.file("library/meal.png").apply { parentFile?.mkdirs() }
        image.outputStream().use { output ->
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                DietRecordThumbnail(record(DietPhoto(relativePath = "library/meal.png", sortOrder = 0)), store)
            }
        }

        composeRule.onNodeWithContentDescription("饮食照片").assertIsDisplayed()
        root.deleteRecursively()
    }

    @Test
    fun missingOrInvalidPhotoUsesEmojiFallback() {
        val root = createTempDirectory("diet_thumbnail_").toFile()
        val store = TestPhotoStore(root)
        store.file("library/broken.png").apply {
            parentFile?.mkdirs()
            writeText("not an image")
        }

        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                DietRecordThumbnail(record(DietPhoto(relativePath = "library/broken.png", sortOrder = 0)), store)
            }
        }

        composeRule.onNodeWithContentDescription("默认饮食图标").assertIsDisplayed()
        root.deleteRecursively()
    }

    @Test
    fun validThumbnailOpensLargePhotoPreview() {
        val root = createTempDirectory("diet_preview_").toFile()
        val store = TestPhotoStore(root)
        val image = store.file("library/meal.png").apply { parentFile?.mkdirs() }
        image.outputStream().use { output ->
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        val previewFile = mutableStateOf<File?>(null)

        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                DietRecordThumbnail(
                    record(DietPhoto(relativePath = "library/meal.png", sortOrder = 0)),
                    store,
                    onPhotoClick = { previewFile.value = it },
                )
                previewFile.value?.let { file -> DietPhotoPreviewDialog(file) { previewFile.value = null } }
            }
        }

        composeRule.onNodeWithContentDescription("饮食照片").performClick()
        composeRule.onNodeWithTag("diet_photo_preview").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(previewFile.value?.isFile == true) }
        root.deleteRecursively()
    }

    @Test
    fun refinedMealDetailShowsApprovedHierarchyAndPersistedAiEvidence() {
        val root = createTempDirectory("diet_detail_").toFile()
        val store = TestPhotoStore(root)
        createImage(store.file("library/detail.png"))

        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                DietDetailContent(
                    record = detailedMeal(DietPhoto(relativePath = "library/detail.png", sortOrder = 0)),
                    categoryName = "家常菜",
                    photoStore = store,
                    aiEvidence = estimate(),
                    onRepeat = {},
                )
            }
        }

        listOf(
            "diet_detail_hero",
            "diet_detail_header",
            "diet_detail_facts",
            "diet_detail_food_card",
            "diet_detail_ai_evidence",
            "diet_detail_ai_adopted",
            "diet_detail_note",
            "diet_detail_repeat",
        ).forEach { tag -> composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed() }
        composeRule.onNodeWithText("牛肉饭和青菜").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("餐食 · 家常菜").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("已采用 680 kcal").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("AI 热量依据").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("最终来源  AI 估算").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("采用状态  未修改").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("估算模型  Private Vision Name").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("生成时间  北京时间 2026-08-11 09:30").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("vision-1").assertDoesNotExist()
        root.deleteRecursively()
    }

    @Test
    fun detailHidesAiCardWithoutEvidence() {
        val root = createTempDirectory("diet_detail_no_ai_").toFile()
        val store = TestPhotoStore(root)

        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                DietDetailContent(
                    record = detailedMeal(photo = null).copy(aiCalorieEstimate = null),
                    categoryName = "家常菜",
                    photoStore = store,
                    aiEvidence = null,
                    onRepeat = {},
                )
            }
        }
        composeRule.onNodeWithTag("diet_detail_ai_evidence").assertDoesNotExist()
        root.deleteRecursively()
    }

    @Test
    fun beverageDetailNeverShowsAiCard() {
        val root = createTempDirectory("diet_detail_beverage_").toFile()
        val store = TestPhotoStore(root)
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                DietDetailContent(
                    record = detailedBeverage().copy(aiCalorieEstimate = estimate()),
                    categoryName = "咖啡",
                    photoStore = store,
                    aiEvidence = null,
                    onRepeat = {},
                )
            }
        }
        composeRule.onNodeWithTag("diet_detail_beverage_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("diet_detail_ai_evidence").assertDoesNotExist()
        root.deleteRecursively()
    }

    @Test
    fun detailHeroOpensFullScreenPhotoPreview() {
        val root = createTempDirectory("diet_detail_preview_").toFile()
        val store = TestPhotoStore(root)
        createImage(store.file("library/detail.png"))

        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                DietDetailContent(
                    record = detailedMeal(DietPhoto(relativePath = "library/detail.png", sortOrder = 0)),
                    categoryName = "家常菜",
                    photoStore = store,
                    aiEvidence = estimate(),
                    onRepeat = {},
                )
            }
        }

        composeRule.onNodeWithTag("diet_detail_hero").performClick()
        composeRule.onNodeWithTag("diet_photo_preview").assertIsDisplayed()
        root.deleteRecursively()
    }

    @Test
    fun multiPhotoHeroSwitchesPositionAndPreviewsCurrentPhoto() {
        val root = createTempDirectory("diet_detail_multi_").toFile()
        val store = TestPhotoStore(root)
        val photos = (1..3).map { index ->
            val relativePath = "library/detail-$index.png"
            createImage(store.file(relativePath))
            DietPhoto(relativePath = relativePath, sortOrder = index - 1)
        }
        val record = detailedMeal(photo = null).copy(photos = photos)

        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                DietDetailContent(
                    record = record,
                    categoryName = "家常菜",
                    photoStore = store,
                    aiEvidence = record.aiCalorieEstimate,
                    onRepeat = {},
                )
            }
        }

        composeRule.onNodeWithTag("diet_detail_photo_position", useUnmergedTree = true).assertTextEquals("1 / 3")
        composeRule.onNodeWithTag("diet_detail_photo_previous").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("diet_detail_photo_next").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithTag("diet_detail_photo_position", useUnmergedTree = true).assertTextEquals("2 / 3")
        composeRule.onNodeWithTag("diet_detail_hero").performClick()
        composeRule.onNodeWithTag("diet_photo_preview_detail-2.png").assertIsDisplayed()
        root.deleteRecursively()
    }

    @Test
    fun detailWithoutPhotosUsesCompactPlaceholderAndVmEvidenceControlsCard() {
        val root = createTempDirectory("diet_detail_empty_hero_").toFile()
        val store = TestPhotoStore(root)
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                DietDetailContent(
                    record = detailedMeal(photo = null),
                    categoryName = "家常菜",
                    photoStore = store,
                    aiEvidence = null,
                    onRepeat = {},
                )
            }
        }

        composeRule.onNodeWithTag("diet_detail_empty_hero").assertIsDisplayed()
        composeRule.onNodeWithText("暂无照片").assertIsDisplayed()
        composeRule.onNodeWithTag("diet_detail_ai_evidence").assertDoesNotExist()
        root.deleteRecursively()
    }

    @Test
    fun adoptedEvidenceIsVisibleAfterSavingAndReopeningRecord() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HabitDatabase::class.java,
        ).allowMainThreadQueries().build()
        val repository = RoomDietRepository(
            database,
            Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC),
        )
        val saved = runBlocking {
            val evidence = AiCalorieEstimateDraft(
                generatedAt = 300,
                modelNameSnapshot = "Private Vision Name",
                modelIdSnapshot = "vision-1",
                items = listOf(AiCalorieItemEstimate("牛肉饭", "1 份", 600, 760)),
                totalMinKcal = 600,
                totalMaxKcal = 760,
                suggestedKcal = 680,
                adoptedKcal = 710,
                wasModified = true,
                accuracyNote = "仅用于估算，请按实际份量调整",
            )
            val id = repository.save(
                null,
                MealRecordDraft(
                    recordType = DietRecordType.MEAL,
                    mealType = MealType.LUNCH,
                    occurredAt = 1_786_411_800_000,
                    recordEpochDay = 20_676,
                    description = "牛肉饭",
                    foodItems = listOf(FoodItemDraft("牛肉饭", "1 份", 710)),
                    manualFinalCalories = 710,
                    beverage = null,
                    note = "少油",
                    dietCategoryId = 4,
                    aiCalorieEstimate = evidence,
                ),
            )
            requireNotNull(repository.observeRecord(id).first())
        }
        database.close()
        val root = createTempDirectory("diet_reopen_").toFile()
        val store = TestPhotoStore(root)

        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                DietDetailContent(
                    record = saved,
                    categoryName = "家常菜",
                    photoStore = store,
                    aiEvidence = saved.aiCalorieEstimate,
                    onRepeat = {},
                )
            }
        }

        composeRule.onNodeWithTag("diet_detail_ai_evidence").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("已采用 710 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("最终来源  手动记录").assertIsDisplayed()
        composeRule.onNodeWithText("采用状态  手动修正").assertIsDisplayed()
        root.deleteRecursively()
    }

    private fun createImage(file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun detailedMeal(photo: DietPhoto?) = MealRecord(
        id = 8,
        recordType = DietRecordType.MEAL,
        mealType = MealType.LUNCH,
        occurredAt = 1_786_411_800_000,
        recordEpochDay = 20_676,
        description = "牛肉饭和青菜",
        foodItems = listOf(
            FoodItem(1, 8, "牛肉饭", "1 份", 620, 0, 1, 1),
            FoodItem(2, 8, "青菜", "半碗", 60, 1, 1, 1),
        ),
        calculatedCalories = 680,
        finalCalories = 680,
        calorieSource = CalorieSource.AI_ESTIMATE,
        beverage = null,
        note = "少油",
        createdAt = 1,
        updatedAt = 1,
        photos = listOfNotNull(photo),
        dietCategoryId = 4,
        aiCalorieEstimate = estimate(),
    )

    private fun detailedBeverage() = MealRecord(
        id = 9,
        recordType = DietRecordType.BEVERAGE,
        mealType = null,
        occurredAt = 1_786_411_800_000,
        recordEpochDay = 20_676,
        description = "",
        foodItems = emptyList(),
        calculatedCalories = null,
        finalCalories = 120,
        calorieSource = CalorieSource.MANUAL,
        beverage = BeverageDetails(
            category = BeverageCategory.COFFEE,
            brandOrStore = "街角咖啡",
            beverageName = "拿铁",
            sizeOrVolume = "中杯",
            temperature = "热",
            iceLevel = "",
            sweetness = "无糖",
            toppings = emptyList(),
            cupCount = 1,
        ),
        note = "",
        createdAt = 1,
        updatedAt = 1,
        dietCategoryId = 5,
    )

    private fun estimate() = AiCalorieEstimate(
        mealRecordId = 8,
        generatedAt = 1_786_411_800_000,
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

    private fun record(photo: DietPhoto) = MealRecord(
        id = 1,
        recordType = DietRecordType.MEAL,
        mealType = MealType.LUNCH,
        occurredAt = 1,
        recordEpochDay = 1,
        description = "午餐",
        foodItems = emptyList(),
        calculatedCalories = null,
        finalCalories = null,
        calorieSource = CalorieSource.NONE,
        beverage = null,
        note = "",
        createdAt = 1,
        updatedAt = 1,
        photos = listOf(photo),
        dietCategoryId = 4,
    )
}

private class TestPhotoStore(private val root: File) : DietPhotoStore {
    override fun file(relativePath: String): File = File(root, relativePath)
    override suspend fun stage(uri: Uri): DietPhoto = error("unused")
    override fun createCameraTarget(): CameraPhotoTarget = error("unused")
    override suspend fun acceptCameraTarget(target: CameraPhotoTarget): DietPhoto = error("unused")
    override suspend fun commit(photos: List<DietPhoto>): List<DietPhoto> = photos
    override suspend fun copy(relativePath: String): DietPhoto = error("unused")
    override suspend fun importFile(source: File): DietPhoto = error("unused")
    override suspend fun discard(relativePath: String) = Unit
    override suspend fun delete(relativePath: String) = Unit
    override suspend fun removeOrphans(referencedPaths: Set<String>) = Unit
}
