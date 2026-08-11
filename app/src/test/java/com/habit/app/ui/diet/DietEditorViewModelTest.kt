package com.habit.app.ui.diet

import java.time.LocalDate
import java.time.LocalTime
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import com.habit.app.domain.model.DietCategory
import com.habit.app.domain.model.DietCategoryScope
import com.habit.app.domain.model.DietPhoto
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.AiCalorieEstimateDraft
import com.habit.app.domain.model.AiCalorieItemEstimate
import com.habit.app.domain.model.CalorieSource
import com.habit.app.domain.model.AiFeature
import com.habit.app.domain.model.AiFeatureBinding
import com.habit.app.domain.model.AiModelConfig
import com.habit.app.domain.model.AiModelConfigDraft
import com.habit.app.domain.model.AiTestStatus
import com.habit.app.domain.repository.AiModelRepository
import com.habit.app.data.ai.AiCompletionClient
import com.habit.app.data.ai.AiPreparedImage
import com.habit.app.data.ai.AiSecretStore
import com.habit.app.data.ai.CalorieImagePreparer
import com.habit.app.data.ai.PreparedAiImage
import com.habit.app.ui.ai.AiModelOperationCoordinator
import java.io.File
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealRecordDraft
import com.habit.app.domain.model.MealType
import com.habit.app.domain.model.mealTypeDisplayOrder
import com.habit.app.domain.repository.DietCategoryRepository
import com.habit.app.domain.repository.DietRepository
import com.habit.app.domain.time.DeviceDateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DietEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun lateNightIsBetweenDinnerAndSnack() {
        assertEquals(
            listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.LATE_NIGHT, MealType.SNACK),
            mealTypeDisplayOrder,
        )
    }

    @Test
    fun savingMealPreservesSelectedDietCategory() = runTest(dispatcher) {
        val repository = RecordingDietRepository()
        val viewModel = DietEditorViewModel(
            recordId = null,
            repository = repository,
            dateProvider = FixedDateProvider,
            clock = Clock.fixed(Instant.parse("2026-08-03T04:00:00Z"), ZoneId.of("UTC")),
            dietCategoryRepository = FakeDietCategoryRepository(),
        )
        advanceUntilIdle()

        viewModel.selectDietCategory(3L)
        viewModel.save {}
        advanceUntilIdle()

        assertEquals(3L, repository.lastSaved?.dietCategoryId)
    }

    @Test
    fun savingBeverageUsesOneTemperatureFieldAndClearsLegacyIceLevel() = runTest(dispatcher) {
        val repository = RecordingDietRepository()
        val viewModel = DietEditorViewModel(
            recordId = null,
            repository = repository,
            dateProvider = FixedDateProvider,
            clock = Clock.fixed(Instant.parse("2026-08-03T04:00:00Z"), ZoneId.of("UTC")),
            dietCategoryRepository = FakeDietCategoryRepository(),
        )
        advanceUntilIdle()

        viewModel.update {
            copy(
                recordType = DietRecordType.BEVERAGE,
                beverageName = "奶茶",
                temperature = "正常冰",
            )
        }
        advanceUntilIdle()
        viewModel.save {}
        advanceUntilIdle()

        assertEquals("正常冰", repository.lastSaved?.beverage?.temperature)
        assertEquals("", repository.lastSaved?.beverage?.iceLevel)
    }

    @Test
    fun selectingTimeKeepsDateAndNormalizesSeconds() {
        val initial = state(
            date = LocalDate.of(2026, 8, 3),
            time = LocalTime.of(8, 0),
        )

        val changed = initial.withTime(LocalTime.of(21, 45, 36))

        assertEquals(LocalDate.of(2026, 8, 3), changed.date)
        assertEquals(LocalTime.of(21, 45), changed.time)
    }

    @Test
    fun selectingDateKeepsTime() {
        val initial = state(
            date = LocalDate.of(2026, 8, 3),
            time = LocalTime.of(8, 20),
        )

        val changed = initial.withDate(LocalDate.of(2027, 1, 15))

        assertEquals(LocalDate.of(2027, 1, 15), changed.date)
        assertEquals(LocalTime.of(8, 20), changed.time)
    }

    @Test
    fun fourthPhotoIsRejectedWithoutChangingDraft() {
        val initial = state(LocalDate.of(2026, 8, 3), LocalTime.NOON).copy(
            photos = (1..3).map { DietPhoto(relativePath = "staging/$it.jpg", sortOrder = it - 1) },
        )

        val changed = initial.withAddedPhotos(
            listOf(DietPhoto(relativePath = "staging/4.jpg", sortOrder = 0)),
        )

        assertEquals(initial.photos, changed.photos)
        assertEquals("每条记录最多添加 3 张照片", changed.message)
        assertFalse(changed.canAddPhoto)
    }

    @Test
    fun adoptingEstimateTracksAiOrManualSourceAndModification() = runTest(dispatcher) {
        val viewModel = editor()
        advanceUntilIdle()
        val estimate = validEstimate()

        viewModel.adoptEstimate(estimate, adoptedKcal = estimate.suggestedKcal)
        assertEquals(CalorieSource.AI_ESTIMATE, viewModel.state.value.calorieSource)
        viewModel.adoptEstimate(estimate, adoptedKcal = estimate.suggestedKcal + 20)
        assertEquals(CalorieSource.MANUAL, viewModel.state.value.calorieSource)
        assertTrue(viewModel.state.value.aiEstimate!!.wasModified)
    }

    @Test
    fun cancelDoesNotMutateAdoptedCalories() = runTest(dispatcher) {
        val viewModel = editor()
        advanceUntilIdle()
        viewModel.adoptEstimate(validEstimate(), 520)

        viewModel.cancel {}
        advanceUntilIdle()

        assertEquals("520", viewModel.state.value.finalCaloriesText)
        assertEquals(CalorieSource.AI_ESTIMATE, viewModel.state.value.calorieSource)
    }

    @Test
    fun savingTemplateDoesNotCopyAiEvidence() = runTest(dispatcher) {
        val templates = RecordingTemplateRepository()
        val viewModel = editor(templates)
        advanceUntilIdle()
        viewModel.adoptEstimate(validEstimate(), 520)

        viewModel.saveAsTemplate("Lunch", includePhotos = false) {}
        advanceUntilIdle()

        assertEquals(null, templates.lastSaved?.meal?.aiCalorieEstimate)
    }

    @Test
    fun generationSendsOnlyCurrentDescriptionAndSelectedPhotosThenClosesPreparedImages() = runTest(dispatcher) {
        val prepared = kotlin.io.path.createTempFile("diet-ai", ".jpg").toFile()
            .apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val client = RecordingVisionClient()
        val viewModel = DietEditorViewModel(
            recordId = null,
            repository = RecordingDietRepository(),
            dateProvider = FixedDateProvider,
            clock = Clock.fixed(Instant.parse("2026-08-03T04:00:00Z"), ZoneId.of("UTC")),
            dietCategoryRepository = FakeDietCategoryRepository(),
            modelRepository = FakeAiModelRepository(),
            secretStore = FakeAiSecretStore(),
            client = client,
            imagePreparer = FakeCalorieImagePreparer(prepared),
            photoFileResolver = { prepared },
            coordinator = AiModelOperationCoordinator(),
        )
        advanceUntilIdle()
        viewModel.update {
            copy(description = "current lunch only", photos = listOf(DietPhoto(relativePath = "selected.jpg", sortOrder = 0)))
        }

        viewModel.generateEstimate()
        advanceUntilIdle()

        assertTrue(client.lastPrompt!!.contains("current lunch only"))
        assertEquals(1, client.lastImages.size)
        assertFalse(prepared.exists())
        assertEquals(CalorieSource.AI_ESTIMATE, viewModel.state.value.calorieSource)
    }

    @Test
    fun generationRejectsSelectedPhotoThatIsNotReadable() = runTest(dispatcher) {
        val source = UnreadableImageFile()
        val prepared = kotlin.io.path.createTempFile("diet-ai", ".jpg").toFile()
            .apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val preparer = FakeCalorieImagePreparer(prepared)
        val client = RecordingVisionClient()
        val viewModel = DietEditorViewModel(
            recordId = null,
            repository = RecordingDietRepository(),
            dateProvider = FixedDateProvider,
            clock = Clock.fixed(Instant.parse("2026-08-03T04:00:00Z"), ZoneId.of("UTC")),
            dietCategoryRepository = FakeDietCategoryRepository(),
            modelRepository = FakeAiModelRepository(),
            secretStore = FakeAiSecretStore(),
            client = client,
            imagePreparer = preparer,
            photoFileResolver = { source },
            coordinator = AiModelOperationCoordinator(),
        )
        advanceUntilIdle()
        viewModel.update {
            copy(description = "lunch", photos = listOf(DietPhoto(relativePath = "unreadable.jpg", sortOrder = 0)))
        }

        try {
            assertTrue(source.isFile)
            assertFalse(source.canRead())
            assertTrue(prepared.isFile)

            viewModel.generateEstimate()
            advanceUntilIdle()

            assertEquals(0, preparer.prepareCalls)
            assertEquals(null, client.lastPrompt)
            assertFalse(viewModel.state.value.isGeneratingEstimate)
        } finally {
            prepared.delete()
        }
    }

    @Test
    fun invalidGenerationResponseUsesSafeErrorAndClosesPreparedImage() = runTest(dispatcher) {
        val prepared = kotlin.io.path.createTempFile("diet-ai", ".jpg").toFile()
            .apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val client = RecordingVisionClient(response = "private malformed response")
        val viewModel = DietEditorViewModel(
            recordId = null,
            repository = RecordingDietRepository(),
            dateProvider = FixedDateProvider,
            clock = Clock.fixed(Instant.parse("2026-08-03T04:00:00Z"), ZoneId.of("UTC")),
            dietCategoryRepository = FakeDietCategoryRepository(),
            modelRepository = FakeAiModelRepository(),
            secretStore = FakeAiSecretStore(),
            client = client,
            imagePreparer = FakeCalorieImagePreparer(prepared),
            photoFileResolver = { prepared },
            coordinator = AiModelOperationCoordinator(),
        )
        advanceUntilIdle()
        viewModel.update {
            copy(description = "lunch", photos = listOf(DietPhoto(relativePath = "selected.jpg", sortOrder = 0)))
        }

        viewModel.generateEstimate()
        advanceUntilIdle()

        assertFalse(prepared.exists())
        assertEquals(CalorieSource.NONE, viewModel.state.value.calorieSource)
        assertFalse(viewModel.state.value.message.orEmpty().contains("private malformed response"))
        assertTrue(viewModel.state.value.message.orEmpty().isNotBlank())
    }

    @Test
    fun cancellingGenerationKeepsAdoptedCaloriesAndClosesPreparedImage() = runTest(dispatcher) {
        val prepared = kotlin.io.path.createTempFile("diet-ai", ".jpg").toFile()
            .apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val client = BlockingVisionClient()
        val viewModel = DietEditorViewModel(
            recordId = null,
            repository = RecordingDietRepository(),
            dateProvider = FixedDateProvider,
            clock = Clock.fixed(Instant.parse("2026-08-03T04:00:00Z"), ZoneId.of("UTC")),
            dietCategoryRepository = FakeDietCategoryRepository(),
            modelRepository = FakeAiModelRepository(),
            secretStore = FakeAiSecretStore(),
            client = client,
            imagePreparer = FakeCalorieImagePreparer(prepared),
            photoFileResolver = { prepared },
            coordinator = AiModelOperationCoordinator(),
        )
        advanceUntilIdle()
        viewModel.adoptEstimate(validEstimate(), 520)
        viewModel.update {
            copy(description = "lunch", photos = listOf(DietPhoto(relativePath = "selected.jpg", sortOrder = 0)))
        }

        viewModel.generateEstimate()
        advanceUntilIdle()
        assertTrue(client.started.isCompleted)
        assertTrue(viewModel.state.value.isGeneratingEstimate)

        viewModel.cancelEstimateGeneration()
        advanceUntilIdle()

        assertTrue(client.wasCancelled)
        assertFalse(prepared.exists())
        assertFalse(viewModel.state.value.isGeneratingEstimate)
        assertEquals("520", viewModel.state.value.finalCaloriesText)
        assertEquals(CalorieSource.AI_ESTIMATE, viewModel.state.value.calorieSource)
    }

    private fun editor(templates: RecordingTemplateRepository? = null) = DietEditorViewModel(
        recordId = null,
        repository = RecordingDietRepository(),
        dateProvider = FixedDateProvider,
        clock = Clock.fixed(Instant.parse("2026-08-03T04:00:00Z"), ZoneId.of("UTC")),
        templateRepository = templates,
        dietCategoryRepository = FakeDietCategoryRepository(),
    )

    private fun validEstimate() = AiCalorieEstimateDraft(
        generatedAt = 1,
        modelNameSnapshot = "Vision",
        modelIdSnapshot = "vision-1",
        items = listOf(AiCalorieItemEstimate("rice", "1 bowl", 420, 650)),
        totalMinKcal = 420,
        totalMaxKcal = 650,
        suggestedKcal = 520,
        adoptedKcal = 520,
        wasModified = false,
        accuracyNote = "Approximate",
    )

    private fun state(date: LocalDate, time: LocalTime) = DietEditorUiState(
        date = date,
        time = time,
    )
}

private object FixedDateProvider : DeviceDateProvider {
    override fun today(): LocalDate = LocalDate.of(2026, 8, 3)
    override val zoneId: ZoneId = ZoneId.of("Asia/Shanghai")
}

private class RecordingDietRepository : DietRepository {
    var lastSaved: MealRecordDraft? = null

    override fun observeAll(): Flow<List<MealRecord>> = MutableStateFlow(emptyList())
    override fun observeDay(epochDay: Long): Flow<List<MealRecord>> = MutableStateFlow(emptyList())
    override fun observeRecord(id: Long): Flow<MealRecord?> = MutableStateFlow(null)
    override fun observeRange(startEpochDay: Long, endEpochDay: Long): Flow<List<MealRecord>> = MutableStateFlow(emptyList())
    override suspend fun save(id: Long?, draft: MealRecordDraft): Long {
        lastSaved = draft
        return id ?: 1L
    }
    override suspend fun delete(id: Long) = Unit
}

private class FakeDietCategoryRepository : DietCategoryRepository {
    private val categories = listOf(
        category(1, DietCategoryScope.MEAL, "家常菜"),
        category(3, DietCategoryScope.MEAL, "零食"),
        category(4, DietCategoryScope.MEAL, "其他餐食"),
        category(5, DietCategoryScope.BEVERAGE, "咖啡"),
    )

    override fun observeAll(scope: DietCategoryScope): Flow<List<DietCategory>> =
        MutableStateFlow(categories.filter { it.scope == scope })
    override fun observeVisible(scope: DietCategoryScope): Flow<List<DietCategory>> = observeAll(scope)
    override fun observeUsageCounts(scope: DietCategoryScope): Flow<Map<Long, Int>> = MutableStateFlow(emptyMap())
    override suspend fun create(scope: DietCategoryScope, name: String): Long = 99L
    override suspend fun rename(id: Long, name: String) = Unit
    override suspend fun setHidden(id: Long, hidden: Boolean) = Unit
    override suspend fun migrateAndDelete(sourceId: Long, targetId: Long) = Unit

    private companion object {
        fun category(id: Long, scope: DietCategoryScope, name: String) = DietCategory(
            id = id,
            scope = scope,
            name = name,
            isPreset = true,
            isHidden = false,
            sortOrder = id.toInt(),
            createdAt = 1,
            updatedAt = 1,
        )
    }
}

private class FakeAiModelRepository : AiModelRepository {
    private val model = AiModelConfig(
        id = 7, externalId = "vision", name = "Vision", baseUrl = "https://example.com", modelId = "vision-1",
        supportsText = false, supportsVision = true, allowInsecureHttp = false, enabled = true,
        lastTestedAt = 1, lastTestStatus = AiTestStatus.PASSED, lastTestMessage = "habit-test-v1|vision=PASSED",
        createdAt = 1, updatedAt = 1,
    )
    override fun observeModels(): Flow<List<AiModelConfig>> = MutableStateFlow(listOf(model))
    override fun observeBindings(): Flow<List<AiFeatureBinding>> = MutableStateFlow(
        listOf(AiFeatureBinding(AiFeature.MEAL_CALORIE_ESTIMATE, model.id, 1)),
    )
    override fun observeModel(id: Long): Flow<AiModelConfig?> = MutableStateFlow(model.takeIf { it.id == id })
    override suspend fun saveModel(id: Long?, draft: AiModelConfigDraft): Long = 7
    override suspend fun recordTest(id: Long, status: AiTestStatus, message: String, testedAt: Long?) = Unit
    override suspend fun bind(feature: AiFeature, modelId: Long?) = Unit
    override suspend fun deleteModel(id: Long) = Unit
}

private class FakeAiSecretStore : AiSecretStore {
    override suspend fun put(externalId: String, apiKey: String) = Unit
    override suspend fun get(externalId: String): String? = "secret"
    override suspend fun maskedSuffix(externalId: String): String? = "cret"
    override suspend fun remove(externalId: String) = Unit
    override suspend fun clearAll() = Unit
}

private class FakeCalorieImagePreparer(private val file: File) : CalorieImagePreparer {
    var prepareCalls: Int = 0
    override suspend fun prepare(sourceFiles: List<File>): List<PreparedAiImage> {
        prepareCalls += 1
        return listOf(PreparedAiImage(file))
    }
}

private class UnreadableImageFile : File("unreadable.jpg") {
    override fun isFile(): Boolean = true
    override fun canRead(): Boolean = false
}

private class RecordingVisionClient(
    private val response: String = """{"items":[{"name":"rice","portion":"1 bowl","minKcal":420,"maxKcal":650}],"totalMinKcal":420,"totalMaxKcal":650,"suggestedKcal":520,"accuracyNote":"Approximate"}""",
) : AiCompletionClient {
    var lastPrompt: String? = null
    var lastImages: List<AiPreparedImage> = emptyList()
    override suspend fun completeText(model: AiModelConfig, apiKey: String, systemPrompt: String, userPrompt: String): String = error("unused")
    override suspend fun completeVision(
        model: AiModelConfig, apiKey: String, systemPrompt: String, userPrompt: String, images: List<AiPreparedImage>,
    ): String {
        lastPrompt = userPrompt
        lastImages = images
        return response
    }
}

private class BlockingVisionClient : AiCompletionClient {
    val started = CompletableDeferred<Unit>()
    var wasCancelled: Boolean = false

    override suspend fun completeText(
        model: AiModelConfig,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
    ): String = error("unused")

    override suspend fun completeVision(
        model: AiModelConfig,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        images: List<AiPreparedImage>,
    ): String {
        started.complete(Unit)
        return try {
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            wasCancelled = true
            throw cancelled
        }
    }
}

private class RecordingTemplateRepository : com.habit.app.domain.repository.DietTemplateRepository {
    var lastSaved: com.habit.app.domain.model.DietTemplateDraft? = null
    override fun observeAll(): Flow<List<com.habit.app.domain.model.DietTemplate>> = MutableStateFlow(emptyList())
    override suspend fun save(draft: com.habit.app.domain.model.DietTemplateDraft): Long {
        lastSaved = draft
        return 1
    }
    override suspend fun delete(id: Long) = Unit
    override suspend fun rename(id: Long, name: String) = Unit
    override suspend fun reorder(ids: List<Long>) = Unit
    override suspend fun createMealDraft(id: Long, nowMillis: Long, epochDay: Long): MealRecordDraft = error("unused")
}
