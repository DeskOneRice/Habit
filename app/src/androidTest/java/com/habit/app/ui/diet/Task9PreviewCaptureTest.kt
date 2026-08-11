package com.habit.app.ui.diet

import android.content.ContentUris
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.habit.app.data.photos.CameraPhotoTarget
import com.habit.app.data.photos.DietPhotoStore
import com.habit.app.domain.model.AiCalorieEstimate
import com.habit.app.domain.model.AiCalorieEstimateDraft
import com.habit.app.domain.model.AiCalorieItemEstimate
import com.habit.app.domain.model.CalorieSource
import com.habit.app.domain.model.DietCategory
import com.habit.app.domain.model.DietCategoryScope
import com.habit.app.domain.model.DietPhoto
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.FoodItem
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealRecordDraft
import com.habit.app.domain.model.MealType
import com.habit.app.domain.repository.DietCategoryRepository
import com.habit.app.domain.repository.DietRepository
import com.habit.app.ui.theme.HabitTheme
import com.habit.app.ui.theme.HabitThemeId
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Task9PreviewCaptureTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureAiEstimateConfirmationSheet() {
        val adoptedText = mutableStateOf("680")
        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                Surface(Modifier.fillMaxSize()) {}
                AiCalorieEstimateSheet(
                    estimate = estimateDraft(),
                    selectedPhotoCount = 2,
                    adoptedCaloriesText = adoptedText.value,
                    isBusy = false,
                    errorMessage = null,
                    onAdoptedCaloriesChange = { adoptedText.value = it },
                    onCancel = {},
                    onAdopt = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_estimate_adopt").performScrollTo()
        composeRule.waitForIdle()

        saveDeviceScreenshot("task9-ai-estimate-sheet.png")
    }

    @Test
    fun captureRefinedDietDetailHeroAndAiEvidence() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "task9-detail-photo")
            .apply { mkdirs() }
        val photoStore = CapturePhotoStore(root)
        createMealPreview(photoStore.file("library/meal-preview.png"))
        val record = previewRecord()
        val viewModel = DietRecordDetailViewModel(
            recordId = record.id,
            repository = CaptureDietRepository(record),
            categoryRepository = CaptureCategoryRepository,
        )

        composeRule.setContent {
            HabitTheme(HabitThemeId.SKY_BLUE) {
                DietRecordDetailScreen(
                    viewModel = viewModel,
                    photoStore = photoStore,
                    onBack = {},
                    onEdit = {},
                    onRepeat = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("diet_detail_hero").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("饮食照片").assertIsDisplayed()
        composeRule.onNodeWithText("牛肉饭配时蔬").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        saveDeviceScreenshot("task9-diet-detail-hero.png", ::hasRenderedHero)

        composeRule.onNodeWithTag("diet_detail_ai_evidence").performScrollTo()
        composeRule.waitForIdle()
        saveDeviceScreenshot("task9-diet-detail-ai-evidence.png")
    }

    private fun saveDeviceScreenshot(
        name: String,
        validator: (Bitmap) -> Boolean = { true },
    ) {
        require(name.matches(Regex("[A-Za-z0-9.-]+")))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var validatedScreenshot: Bitmap? = null
        for (attempt in 0 until 20) {
            instrumentation.waitForIdleSync()
            val candidate = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            if (validator(candidate)) {
                validatedScreenshot = candidate
                break
            }
            candidate.recycle()
            SystemClock.sleep(50)
        }
        val screenshot = checkNotNull(validatedScreenshot) { "Preview did not render non-white hero content" }
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/HabitPreviews/"
        resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
            arrayOf(name, relativePath),
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            while (cursor.moveToNext()) {
                resolver.delete(ContentUris.withAppendedId(collection, cursor.getLong(idColumn)), null, null)
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(collection, values))
        var actualDisplayName = name
        try {
            resolver.openOutputStream(uri, "w").use { output ->
                checkNotNull(output)
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
            val storedSize = resolver.query(
                uri,
                arrayOf(MediaStore.Downloads.SIZE, MediaStore.Downloads.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    actualDisplayName = cursor.getString(1)
                    cursor.getLong(0)
                } else {
                    0L
                }
            } ?: 0L
            check(storedSize > 0L)
        } catch (failure: Throwable) {
            resolver.delete(uri, null, null)
            throw failure
        } finally {
            screenshot.recycle()
        }
        println("TASK9_PREVIEW=/sdcard/Download/HabitPreviews/$actualDisplayName")
    }

    private fun hasRenderedHero(bitmap: Bitmap): Boolean {
        val left = bitmap.width / 10
        val right = bitmap.width * 9 / 10
        val top = bitmap.height * 18 / 100
        val bottom = bitmap.height / 2
        var sampled = 0
        var nonWhite = 0
        for (y in top until bottom step 6) {
            for (x in left until right step 6) {
                val pixel = bitmap.getPixel(x, y)
                sampled += 1
                if (Color.red(pixel) < 235 || Color.green(pixel) < 235 || Color.blue(pixel) < 235) {
                    nonWhite += 1
                }
            }
        }
        return sampled > 0 && nonWhite * 100 / sampled >= 30
    }

    private fun createMealPreview(file: File) {
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(720, 405, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.rgb(220, 238, 244))
        paint.color = Color.rgb(250, 249, 244)
        canvas.drawCircle(360f, 205f, 165f, paint)
        paint.color = Color.rgb(172, 118, 79)
        canvas.drawOval(235f, 135f, 420f, 280f, paint)
        paint.color = Color.rgb(244, 233, 198)
        canvas.drawOval(355f, 120f, 515f, 270f, paint)
        paint.color = Color.rgb(108, 154, 112)
        repeat(5) { index ->
            canvas.drawCircle(250f + index * 52f, 300f + (index % 2) * 18f, 28f, paint)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun previewRecord() = MealRecord(
        id = 8,
        recordType = DietRecordType.MEAL,
        mealType = MealType.LUNCH,
        occurredAt = LocalDateTime.of(2026, 8, 11, 12, 30)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toInstant()
            .toEpochMilli(),
        recordEpochDay = LocalDate.of(2026, 8, 11).toEpochDay(),
        description = "牛肉饭配时蔬",
        foodItems = listOf(
            FoodItem(1, 8, "牛肉饭", "1 份", 620, 0, 1, 1),
            FoodItem(2, 8, "时蔬", "半碗", 60, 1, 1, 1),
        ),
        calculatedCalories = 680,
        finalCalories = 680,
        calorieSource = CalorieSource.AI_ESTIMATE,
        beverage = null,
        note = "少油，米饭约一碗",
        createdAt = 1,
        updatedAt = 1,
        photos = listOf(DietPhoto(relativePath = "library/meal-preview.png", sortOrder = 0)),
        dietCategoryId = 4,
        aiCalorieEstimate = estimate(),
    )

    private fun estimateDraft() = AiCalorieEstimateDraft(
        generatedAt = 1,
        modelNameSnapshot = "Private Vision Name",
        modelIdSnapshot = "vision-1",
        items = listOf(AiCalorieItemEstimate("牛肉饭配时蔬", "1 份", 600, 760)),
        totalMinKcal = 600,
        totalMaxKcal = 760,
        suggestedKcal = 680,
        adoptedKcal = 680,
        wasModified = false,
        accuracyNote = "仅用于估算，请按实际份量调整",
    )

    private fun estimate() = AiCalorieEstimate(
        mealRecordId = 8,
        generatedAt = 1,
        modelNameSnapshot = "Private Vision Name",
        modelIdSnapshot = "vision-1",
        items = listOf(
            AiCalorieItemEstimate("牛肉饭", "1 份", 520, 640),
            AiCalorieItemEstimate("时蔬", "半碗", 80, 120),
        ),
        totalMinKcal = 600,
        totalMaxKcal = 760,
        suggestedKcal = 680,
        adoptedKcal = 680,
        wasModified = false,
        accuracyNote = "仅用于估算，请按实际份量调整",
    )
}

private class CapturePhotoStore(private val root: File) : DietPhotoStore {
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

private class CaptureDietRepository(private val record: MealRecord) : DietRepository {
    override fun observeAll(): Flow<List<MealRecord>> = MutableStateFlow(listOf(record))
    override fun observeDay(epochDay: Long): Flow<List<MealRecord>> = MutableStateFlow(listOf(record))
    override fun observeRecord(id: Long): Flow<MealRecord?> = MutableStateFlow(record.takeIf { it.id == id })
    override fun observeRange(startEpochDay: Long, endEpochDay: Long): Flow<List<MealRecord>> = MutableStateFlow(listOf(record))
    override suspend fun save(id: Long?, draft: MealRecordDraft): Long = error("unused")
    override suspend fun delete(id: Long) = Unit
}

private object CaptureCategoryRepository : DietCategoryRepository {
    private val mealCategories = listOf(
        DietCategory(
            id = 4,
            scope = DietCategoryScope.MEAL,
            name = "家常菜",
            isPreset = true,
            isHidden = false,
            sortOrder = 0,
            createdAt = 1,
            updatedAt = 1,
        ),
    )
    override fun observeAll(scope: DietCategoryScope): Flow<List<DietCategory>> =
        MutableStateFlow(mealCategories.filter { it.scope == scope })
    override fun observeVisible(scope: DietCategoryScope): Flow<List<DietCategory>> = observeAll(scope)
    override fun observeUsageCounts(scope: DietCategoryScope): Flow<Map<Long, Int>> = MutableStateFlow(emptyMap())
    override suspend fun create(scope: DietCategoryScope, name: String): Long = error("unused")
    override suspend fun rename(id: Long, name: String) = Unit
    override suspend fun setHidden(id: Long, hidden: Boolean) = Unit
    override suspend fun migrateAndDelete(sourceId: Long, targetId: Long) = Unit
}
