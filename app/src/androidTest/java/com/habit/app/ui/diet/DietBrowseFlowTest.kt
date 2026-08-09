package com.habit.app.ui.diet

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.data.photos.CameraPhotoTarget
import com.habit.app.data.photos.DietPhotoStore
import com.habit.app.domain.model.CalorieSource
import com.habit.app.domain.model.DietPhoto
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealType
import com.habit.app.ui.theme.HabitTheme
import com.habit.app.ui.theme.HabitThemeId
import java.io.File
import kotlin.io.path.createTempDirectory
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
