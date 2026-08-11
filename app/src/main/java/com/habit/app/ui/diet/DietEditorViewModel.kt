package com.habit.app.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.habit.app.data.photos.CameraPhotoTarget
import com.habit.app.data.photos.DietPhotoStore
import com.habit.app.data.ai.AiCompletionClient
import com.habit.app.data.ai.AiSecretStore
import com.habit.app.data.ai.CalorieImagePreparer
import com.habit.app.data.ai.PreparedAiImage
import com.habit.app.domain.ai.CalorieEstimateParser
import com.habit.app.domain.ai.CalorieEstimateParseException
import com.habit.app.domain.model.BeverageCategory
import com.habit.app.domain.model.BeverageDetails
import com.habit.app.domain.model.AiCalorieEstimateDraft
import com.habit.app.domain.model.CalorieSource
import com.habit.app.domain.model.AiFeature
import com.habit.app.domain.model.AiModelConfig
import com.habit.app.domain.model.AiTestStatus
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.DietPhoto
import com.habit.app.data.photos.MAX_DIET_PHOTOS
import com.habit.app.domain.model.FoodItemDraft
import com.habit.app.domain.model.MealRecordDraft
import com.habit.app.domain.model.MealType
import com.habit.app.domain.model.DietTemplateDraft
import com.habit.app.domain.model.DietCategory
import com.habit.app.domain.model.DietCategoryScope
import com.habit.app.domain.model.toRepeatDraft
import com.habit.app.domain.model.displayTemperature
import com.habit.app.domain.repository.DietCategoryRepository
import com.habit.app.domain.repository.DietRepository
import com.habit.app.domain.repository.DietTemplateRepository
import com.habit.app.domain.repository.AiModelRepository
import com.habit.app.domain.stats.calculateCalories
import com.habit.app.domain.stats.suggestMealType
import com.habit.app.domain.time.DeviceDateProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.habit.app.ui.ai.AiModelOperationCoordinator

data class DietEditorUiState(
    val recordType: DietRecordType = DietRecordType.MEAL,
    val mealType: MealType = MealType.SNACK,
    val date: LocalDate,
    val time: LocalTime,
    val description: String = "",
    val foodItems: List<FoodItemDraft> = emptyList(),
    val finalCaloriesText: String = "",
    val calorieSource: CalorieSource = CalorieSource.NONE,
    val aiEstimate: AiCalorieEstimateDraft? = null,
    val beverageCategory: BeverageCategory = BeverageCategory.COFFEE,
    val brandOrStore: String = "",
    val beverageName: String = "",
    val sizeOrVolume: String = "",
    val temperature: String = "",
    val sweetness: String = "",
    val toppings: String = "",
    val cupCountText: String = "1",
    val note: String = "",
    val photos: List<DietPhoto> = emptyList(),
    val dietCategories: List<DietCategory> = emptyList(),
    val dietCategoryId: Long = 0,
    val isSaving: Boolean = false,
    val isGeneratingEstimate: Boolean = false,
    val isEstimateSheetVisible: Boolean = false,
    val selectedEstimatePhotoPaths: Set<String> = emptySet(),
    val estimatePreview: AiCalorieEstimateDraft? = null,
    val estimateAdoptedCaloriesText: String = "",
    val message: String? = null,
)

val DietEditorUiState.canAddPhoto: Boolean get() = photos.size < MAX_DIET_PHOTOS

internal fun DietEditorUiState.withAddedPhotos(newPhotos: List<DietPhoto>): DietEditorUiState {
    if (photos.size + newPhotos.size > MAX_DIET_PHOTOS) {
        return copy(message = "每条记录最多添加 3 张照片")
    }
    return copy(
        photos = (photos + newPhotos).mapIndexed { index, photo -> photo.copy(sortOrder = index) },
        message = null,
    )
}

internal fun DietEditorUiState.withDate(value: LocalDate) = copy(date = value)

internal fun DietEditorUiState.withTime(value: LocalTime) = copy(
    time = value.withSecond(0).withNano(0),
)

class DietEditorViewModel internal constructor(
    private val recordId: Long?,
    private val repository: DietRepository,
    private val dateProvider: DeviceDateProvider,
    private val clock: Clock = Clock.systemUTC(),
    private val photoStore: DietPhotoStore? = null,
    private val repeatRecordId: Long? = null,
    private val templateId: Long? = null,
    private val templateRepository: DietTemplateRepository? = null,
    private val dietCategoryRepository: DietCategoryRepository,
    private val modelRepository: AiModelRepository? = null,
    private val secretStore: AiSecretStore? = null,
    private val client: AiCompletionClient? = null,
    private val imagePreparer: CalorieImagePreparer? = null,
    private val photoFileResolver: (DietPhoto) -> File = { photo ->
        requireNotNull(photoStore) { "Photo store unavailable" }.file(photo.relativePath)
    },
    private val coordinator: AiModelOperationCoordinator = AiModelOperationCoordinator(),
) : ViewModel() {
    private var mealCategories: List<DietCategory> = emptyList()
    private var beverageCategories: List<DietCategory> = emptyList()
    private val now = Instant.now(clock).atZone(dateProvider.zoneId)
    private val mutableState = MutableStateFlow(
        DietEditorUiState(
            date = dateProvider.today(),
            time = now.toLocalTime().withSecond(0).withNano(0),
            mealType = suggestMealType(now.toLocalTime()),
        ),
    )
    val state: StateFlow<DietEditorUiState> = mutableState.asStateFlow()
    private val estimateActionGate = AtomicBoolean(false)
    private val estimateGenerationJob = AtomicReference<Job?>(null)

    init {
        viewModelScope.launch {
            dietCategoryRepository.observeAll(DietCategoryScope.MEAL).collectLatest {
                mealCategories = it
                refreshCategoryChoices()
            }
        }
        viewModelScope.launch {
            dietCategoryRepository.observeAll(DietCategoryScope.BEVERAGE).collectLatest {
                beverageCategories = it
                refreshCategoryChoices()
            }
        }
        if (recordId != null) viewModelScope.launch {
            repository.observeRecord(recordId).filterNotNull().first().let { record ->
                val occurred = Instant.ofEpochMilli(record.occurredAt).atZone(dateProvider.zoneId)
                val drink = record.beverage
                mutableState.value = DietEditorUiState(
                    recordType = record.recordType,
                    mealType = record.mealType ?: MealType.SNACK,
                    date = LocalDate.ofEpochDay(record.recordEpochDay),
                    time = occurred.toLocalTime().withSecond(0).withNano(0),
                    description = record.description,
                    foodItems = record.foodItems.map { FoodItemDraft(it.name, it.portionText, it.calories) },
                    finalCaloriesText = record.finalCalories?.toString().orEmpty(),
                    calorieSource = record.calorieSource,
                    aiEstimate = record.aiCalorieEstimate?.toDraft(),
                    beverageCategory = drink?.category ?: BeverageCategory.COFFEE,
                    brandOrStore = drink?.brandOrStore.orEmpty(),
                    beverageName = drink?.beverageName.orEmpty(),
                    sizeOrVolume = drink?.sizeOrVolume.orEmpty(),
                    temperature = drink?.displayTemperature().orEmpty(),
                    sweetness = drink?.sweetness.orEmpty(),
                    toppings = drink?.toppings?.joinToString("、").orEmpty(),
                    cupCountText = drink?.cupCount?.toString() ?: "1",
                    note = record.note,
                    photos = record.photos,
                    dietCategoryId = record.dietCategoryId,
                )
                refreshCategoryChoices()
            }
        } else if (repeatRecordId != null) viewModelScope.launch {
            val source = repository.observeRecord(repeatRecordId).filterNotNull().first()
            applyDraft(source.toRepeatDraft(clock.millis(), dateProvider.today().toEpochDay()))
        } else if (templateId != null && templateRepository != null) viewModelScope.launch {
            applyDraft(templateRepository.createMealDraft(templateId, clock.millis(), dateProvider.today().toEpochDay()))
        }
    }

    private fun applyDraft(draft: MealRecordDraft) {
        val occurred = Instant.ofEpochMilli(draft.occurredAt).atZone(dateProvider.zoneId)
        val drink = draft.beverage
        mutableState.value = DietEditorUiState(
            recordType = draft.recordType,
            mealType = draft.mealType ?: MealType.SNACK,
            date = LocalDate.ofEpochDay(draft.recordEpochDay),
            time = occurred.toLocalTime().withSecond(0).withNano(0),
            description = draft.description,
            foodItems = draft.foodItems,
            finalCaloriesText = draft.manualFinalCalories?.toString().orEmpty(),
            calorieSource = when {
                draft.aiCalorieEstimate != null && !draft.aiCalorieEstimate.wasModified -> CalorieSource.AI_ESTIMATE
                draft.aiCalorieEstimate != null -> CalorieSource.MANUAL
                else -> CalorieSource.NONE
            },
            aiEstimate = draft.aiCalorieEstimate,
            beverageCategory = drink?.category ?: BeverageCategory.COFFEE,
            brandOrStore = drink?.brandOrStore.orEmpty(),
            beverageName = drink?.beverageName.orEmpty(),
            sizeOrVolume = drink?.sizeOrVolume.orEmpty(),
            temperature = drink?.displayTemperature().orEmpty(),
            sweetness = drink?.sweetness.orEmpty(),
            toppings = drink?.toppings?.joinToString("、").orEmpty(),
            cupCountText = drink?.cupCount?.toString() ?: "1",
            note = draft.note,
            photos = draft.photos,
            dietCategoryId = draft.dietCategoryId,
        )
        refreshCategoryChoices()
    }

    fun update(block: DietEditorUiState.() -> DietEditorUiState) {
        val previous = mutableState.value
        val previousType = previous.recordType
        val updated = previous.block().copy(message = null)
        mutableState.value = if (updated.finalCaloriesText != previous.finalCaloriesText && updated.aiEstimate != null) {
            val adopted = updated.finalCaloriesText.toIntOrNull() ?: updated.aiEstimate.adoptedKcal
            updated.copy(
                calorieSource = CalorieSource.MANUAL,
                aiEstimate = updated.aiEstimate.copy(adoptedKcal = adopted, wasModified = true),
            )
        } else updated
        if (previousType != mutableState.value.recordType) refreshCategoryChoices()
    }

    fun adoptEstimate(estimate: AiCalorieEstimateDraft, adoptedKcal: Int) {
        require(adoptedKcal >= 0) { "Calories cannot be negative" }
        val adopted = estimate.copy(
            adoptedKcal = adoptedKcal,
            wasModified = adoptedKcal != estimate.suggestedKcal,
        )
        mutableState.value = mutableState.value.copy(
            finalCaloriesText = adoptedKcal.toString(),
            calorieSource = if (adopted.wasModified) CalorieSource.MANUAL else CalorieSource.AI_ESTIMATE,
            aiEstimate = adopted,
            message = null,
        )
    }

    fun selectDietCategory(id: Long) {
        val category = (mealCategories + beverageCategories).firstOrNull { it.id == id } ?: return
        if (category.scope != currentCategoryScope()) return
        mutableState.value = mutableState.value.copy(
            dietCategoryId = id,
            beverageCategory = legacyBeverageCategory(id),
            message = null,
        )
        refreshCategoryChoices()
    }

    fun createDietCategory(name: String, onCreated: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val id = dietCategoryRepository.create(currentCategoryScope(), name)
                mutableState.value = mutableState.value.copy(dietCategoryId = id, message = null)
                onCreated()
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(message = error.message ?: "无法创建分类")
            }
        }
    }

    private fun currentCategoryScope(): DietCategoryScope =
        if (mutableState.value.recordType == DietRecordType.BEVERAGE) DietCategoryScope.BEVERAGE else DietCategoryScope.MEAL

    private fun refreshCategoryChoices() {
        val scope = currentCategoryScope()
        val all = if (scope == DietCategoryScope.MEAL) mealCategories else beverageCategories
        val currentId = mutableState.value.dietCategoryId
        val choices = all.filter { !it.isHidden || it.id == currentId }
        val currentIsValid = all.any { it.id == currentId && it.scope == scope }
        val preferredId = if (scope == DietCategoryScope.MEAL) 4L else 5L
        val selectedId = if (currentIsValid) currentId else
            choices.firstOrNull { it.id == preferredId }?.id ?: choices.firstOrNull()?.id ?: 0L
        mutableState.value = mutableState.value.copy(
            dietCategories = choices,
            dietCategoryId = selectedId,
            beverageCategory = if (scope == DietCategoryScope.BEVERAGE) legacyBeverageCategory(selectedId) else mutableState.value.beverageCategory,
        )
    }

    private fun legacyBeverageCategory(id: Long): BeverageCategory = when (id) {
        5L -> BeverageCategory.COFFEE
        6L -> BeverageCategory.MILK_TEA
        7L -> BeverageCategory.TEA
        8L -> BeverageCategory.FRUIT_DRINK
        9L -> BeverageCategory.DAIRY
        else -> BeverageCategory.OTHER
    }

    fun addFoodItem() = update { copy(foodItems = foodItems + FoodItemDraft("", null, null)) }
    fun updateFood(index: Int, name: String, portion: String, calories: String) = update {
        copy(foodItems = foodItems.mapIndexed { current, item ->
            if (current == index) item.copy(name = name, portionText = portion.ifBlank { null }, calories = calories.toIntOrNull()) else item
        })
    }
    fun removeFood(index: Int) = update { copy(foodItems = foodItems.filterIndexed { current, _ -> current != index }) }
    fun addPhotos(photos: List<DietPhoto>) { mutableState.value = mutableState.value.withAddedPhotos(photos) }
    fun removePhoto(index: Int) = update {
        copy(photos = photos.filterIndexed { current, _ -> current != index }.mapIndexed { order, photo -> photo.copy(sortOrder = order) })
    }

    fun importPhotos(uris: List<Uri>) {
        val store = photoStore ?: return
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val accepted = mutableListOf<DietPhoto>()
            try {
                val available = MAX_DIET_PHOTOS - mutableState.value.photos.size
                if (uris.size > available) {
                    mutableState.value = mutableState.value.copy(message = "每条记录最多添加 3 张照片")
                    return@launch
                }
                uris.forEach { accepted += store.stage(it) }
                mutableState.value = mutableState.value.withAddedPhotos(accepted)
            } catch (cancelled: CancellationException) {
                accepted.forEach { store.discard(it.relativePath) }
                throw cancelled
            } catch (error: Exception) {
                accepted.forEach { store.discard(it.relativePath) }
                mutableState.value = mutableState.value.copy(message = error.message ?: "照片读取失败")
            }
        }
    }

    fun createCameraTarget(): CameraPhotoTarget? = try {
        photoStore?.takeIf { mutableState.value.canAddPhoto }?.createCameraTarget()
    } catch (error: Exception) {
        mutableState.value = mutableState.value.copy(message = error.message ?: "无法打开相机")
        null
    }

    fun photoFile(relativePath: String) = photoStore?.file(relativePath)

    fun acceptCameraTarget(target: CameraPhotoTarget, success: Boolean) {
        val store = photoStore ?: return
        viewModelScope.launch {
            if (!success) {
                store.discard(target.photo.relativePath)
                return@launch
            }
            try {
                val photo = store.acceptCameraTarget(target)
                mutableState.value = mutableState.value.withAddedPhotos(listOf(photo))
            } catch (error: Exception) {
                store.discard(target.photo.relativePath)
                mutableState.value = mutableState.value.copy(message = error.message ?: "照片读取失败")
            }
        }
    }

    fun cancel(onCancelled: () -> Unit) {
        val store = photoStore
        if (store == null) {
            onCancelled()
            return
        }
        viewModelScope.launch {
            mutableState.value.photos
                .filter { it.relativePath.startsWith("staging/") }
                .forEach { store.discard(it.relativePath) }
            onCancelled()
        }
    }

    fun generateEstimate(): Job = generateEstimateInternal(mutableState.value.photos) { estimate ->
        adoptEstimate(estimate, estimate.suggestedKcal)
    }

    fun generateEstimatePreview(onPreviewReady: (AiCalorieEstimateDraft) -> Unit): Job =
        generateEstimateInternal(mutableState.value.photos) { estimate ->
            mutableState.value = mutableState.value.copy(
                estimatePreview = estimate,
                estimateAdoptedCaloriesText = estimate.suggestedKcal.toString(),
            )
            onPreviewReady(estimate)
        }

    fun openEstimateConfirmation() {
        val current = mutableState.value
        if (current.recordType != DietRecordType.MEAL || current.photos.size !in 1..3) {
            mutableState.value = current.copy(message = ESTIMATE_UNAVAILABLE_MESSAGE)
            return
        }
        mutableState.value = current.copy(
            isEstimateSheetVisible = true,
            selectedEstimatePhotoPaths = current.photos.map(DietPhoto::relativePath).toSet(),
            estimatePreview = null,
            estimateAdoptedCaloriesText = current.finalCaloriesText,
            message = null,
        )
    }

    fun toggleEstimatePhoto(relativePath: String) {
        val current = mutableState.value
        if (!current.isEstimateSheetVisible || current.isGeneratingEstimate || current.estimatePreview != null) return
        if (current.photos.none { it.relativePath == relativePath }) return
        mutableState.value = current.copy(
            selectedEstimatePhotoPaths = if (relativePath in current.selectedEstimatePhotoPaths) {
                current.selectedEstimatePhotoPaths - relativePath
            } else {
                current.selectedEstimatePhotoPaths + relativePath
            },
            message = null,
        )
    }

    fun confirmEstimatePhotos(): Job {
        val current = mutableState.value
        val selectedPhotos = current.photos.filter { it.relativePath in current.selectedEstimatePhotoPaths }
        if (!current.isEstimateSheetVisible || selectedPhotos.size !in 1..3) {
            mutableState.value = current.copy(message = "请至少选择 1 张照片")
            return viewModelScope.launch { }
        }
        mutableState.value = current.copy(
            estimatePreview = null,
            estimateAdoptedCaloriesText = current.finalCaloriesText,
            message = null,
        )
        return generateEstimateInternal(selectedPhotos) { estimate ->
            mutableState.value = mutableState.value.copy(
                estimatePreview = estimate,
                estimateAdoptedCaloriesText = estimate.suggestedKcal.toString(),
                message = null,
            )
        }
    }

    fun updateEstimateAdoptedCalories(value: String) {
        mutableState.value = mutableState.value.copy(
            estimateAdoptedCaloriesText = value.filter(Char::isDigit),
        )
    }

    fun adoptEstimatePreview() {
        val current = mutableState.value
        val estimate = current.estimatePreview ?: return
        val calories = current.estimateAdoptedCaloriesText.toIntOrNull() ?: return
        adoptEstimate(estimate, calories)
        mutableState.value = mutableState.value.copy(
            isEstimateSheetVisible = false,
            selectedEstimatePhotoPaths = emptySet(),
            estimatePreview = null,
            estimateAdoptedCaloriesText = "",
        )
    }

    fun cancelEstimateFlow() {
        cancelEstimateGeneration()
        mutableState.value = mutableState.value.copy(
            isEstimateSheetVisible = false,
            selectedEstimatePhotoPaths = emptySet(),
            estimatePreview = null,
            estimateAdoptedCaloriesText = "",
            message = null,
        )
    }

    private fun generateEstimateInternal(
        selectedPhotos: List<DietPhoto>,
        onGenerated: (AiCalorieEstimateDraft) -> Unit,
    ): Job {
        val current = mutableState.value
        if (current.recordType != DietRecordType.MEAL || selectedPhotos.size !in 1..3) {
            mutableState.value = current.copy(message = ESTIMATE_UNAVAILABLE_MESSAGE)
            return viewModelScope.launch { }
        }
        val files = try {
            selectedPhotos.map(photoFileResolver).also { sourceFiles ->
                require(sourceFiles.all { it.isFile && it.canRead() }) { "Selected photo is unreadable" }
            }
        } catch (_: Exception) {
            mutableState.value = current.copy(message = ESTIMATE_UNAVAILABLE_MESSAGE)
            return viewModelScope.launch { }
        }
        val models = modelRepository
        val keys = secretStore
        val completionClient = client
        val preparer = imagePreparer
        if (models == null || keys == null || completionClient == null || preparer == null ||
            !estimateActionGate.compareAndSet(false, true)
        ) {
            mutableState.value = current.copy(message = ESTIMATE_UNAVAILABLE_MESSAGE)
            return viewModelScope.launch { }
        }
        mutableState.value = current.copy(isGeneratingEstimate = true, message = null)
        val job = viewModelScope.launch {
            val prepared = mutableListOf<PreparedAiImage>()
            try {
                val bindingId = coordinator.withBindings {
                    models.observeBindings().first()
                        .firstOrNull { it.feature == AiFeature.MEAL_CALORIE_ESTIMATE }
                        ?.modelConfigId
                } ?: throw EstimateGenerationFailure(ESTIMATE_UNAVAILABLE_MESSAGE)
                val response = coordinator.withModel(bindingId) {
                    val model = models.observeModel(bindingId).first()
                        ?: throw EstimateGenerationFailure(ESTIMATE_UNAVAILABLE_MESSAGE)
                    coordinator.register(model.id, model.externalId)
                    coordinator.withBindings {
                        val latestBinding = models.observeBindings().first()
                            .firstOrNull { it.feature == AiFeature.MEAL_CALORIE_ESTIMATE }
                            ?.modelConfigId
                        if (latestBinding != model.id || !model.isEligibleMealEstimateModel()) {
                            throw EstimateGenerationFailure(ESTIMATE_UNAVAILABLE_MESSAGE)
                        }
                        val apiKey = try { keys.get(model.externalId) } catch (_: Exception) { null }
                        if (apiKey.isNullOrBlank()) throw EstimateGenerationFailure(ESTIMATE_UNAVAILABLE_MESSAGE)
                        prepared += preparer.prepare(files)
                        model to completionClient.completeVision(
                            model = model,
                            apiKey = apiKey,
                            systemPrompt = com.habit.app.domain.ai.CalorieEstimatePrompt.systemPrompt,
                            userPrompt = com.habit.app.domain.ai.CalorieEstimatePrompt.userPrompt(current.description),
                            images = prepared.map(PreparedAiImage::asAiPreparedImage),
                        )
                    }
                }
                val estimate = CalorieEstimateParser.parse(response.second, response.first, clock.millis())
                onGenerated(estimate)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: CalorieEstimateParseException) {
                mutableState.value = mutableState.value.copy(message = ESTIMATE_RESPONSE_MESSAGE)
            } catch (failure: EstimateGenerationFailure) {
                mutableState.value = mutableState.value.copy(message = failure.safeMessage)
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(message = ESTIMATE_FAILED_MESSAGE)
            } finally {
                prepared.forEach { image -> runCatching { image.close() } }
                estimateActionGate.set(false)
                mutableState.value = mutableState.value.copy(isGeneratingEstimate = false)
            }
        }
        estimateGenerationJob.set(job)
        return job
    }

    fun cancelEstimateGeneration() {
        estimateGenerationJob.get()?.cancel()
    }

    fun save(onSaved: () -> Unit) {
        if (mutableState.value.isGeneratingEstimate) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, message = null)
            try {
                val current = mutableState.value
                require(current.dietCategoryId > 0) { "请选择分类" }
                val committedPhotos = photoStore?.commit(current.photos) ?: current.photos
                val occurredAt = current.date.atTime(current.time).atZone(dateProvider.zoneId).toInstant().toEpochMilli()
                val manual = current.finalCaloriesText.toIntOrNull()
                calculateCalories(current.foodItems, manual)
                val beverage = if (current.recordType == DietRecordType.BEVERAGE) BeverageDetails(
                    current.beverageCategory,
                    current.brandOrStore,
                    current.beverageName,
                    current.sizeOrVolume,
                    current.temperature,
                    "",
                    current.sweetness,
                    current.toppings.split('、', ',', '，').map(String::trim).filter(String::isNotBlank),
                    current.cupCountText.toIntOrNull() ?: 0,
                ) else null
                repository.save(
                    recordId,
                    MealRecordDraft(
                        current.recordType,
                        if (current.recordType == DietRecordType.MEAL) current.mealType else null,
                        occurredAt,
                        current.date.toEpochDay(),
                        current.description,
                        current.foodItems,
                        manual,
                        beverage,
                        current.note,
                        committedPhotos,
                        current.dietCategoryId,
                        if (current.recordType == DietRecordType.MEAL) current.aiEstimate else null,
                    ),
                )
                photoStore?.removeOrphans(repository.referencedPhotoPaths())
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                photoStore?.removeOrphans(repository.referencedPhotoPaths())
                mutableState.value = mutableState.value.copy(message = error.message ?: "保存失败")
            } finally {
                mutableState.value = mutableState.value.copy(isSaving = false)
            }
        }
    }

    fun saveAsTemplate(name: String, includePhotos: Boolean, onSaved: () -> Unit) {
        val templates = templateRepository ?: return
        viewModelScope.launch {
            try {
                templates.save(
                    DietTemplateDraft(
                        name = name,
                        meal = buildDraft(mutableState.value, mutableState.value.photos),
                        includePhotos = includePhotos,
                    ),
                )
                onSaved()
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(message = error.message ?: "模板保存失败")
            }
        }
    }

    private fun buildDraft(current: DietEditorUiState, photos: List<DietPhoto>): MealRecordDraft {
        val occurredAt = current.date.atTime(current.time).atZone(dateProvider.zoneId).toInstant().toEpochMilli()
        val beverage = if (current.recordType == DietRecordType.BEVERAGE) BeverageDetails(
            current.beverageCategory,
            current.brandOrStore,
            current.beverageName,
            current.sizeOrVolume,
            current.temperature,
            "",
            current.sweetness,
            current.toppings.split('、', ',', '，').map(String::trim).filter(String::isNotBlank),
            current.cupCountText.toIntOrNull() ?: 0,
        ) else null
        return MealRecordDraft(
            current.recordType,
            if (current.recordType == DietRecordType.MEAL) current.mealType else null,
            occurredAt,
            current.date.toEpochDay(),
            current.description,
            current.foodItems,
            current.finalCaloriesText.toIntOrNull(),
            beverage,
            current.note,
            photos,
            current.dietCategoryId,
            aiCalorieEstimate = null,
        )
    }

    fun delete(onDeleted: () -> Unit) {
        val id = recordId ?: return
        viewModelScope.launch {
            repository.delete(id)
            photoStore?.removeOrphans(repository.referencedPhotoPaths())
            onDeleted()
        }
    }
}

private fun com.habit.app.domain.model.AiCalorieEstimate.toDraft() = AiCalorieEstimateDraft(
    generatedAt = generatedAt,
    modelNameSnapshot = modelNameSnapshot,
    modelIdSnapshot = modelIdSnapshot,
    items = items,
    totalMinKcal = totalMinKcal,
    totalMaxKcal = totalMaxKcal,
    suggestedKcal = suggestedKcal,
    adoptedKcal = adoptedKcal,
    wasModified = wasModified,
    accuracyNote = accuracyNote,
)

private fun AiModelConfig.isEligibleMealEstimateModel(): Boolean {
    if (!enabled || !supportsVision) return false
    val visionStatus = if (lastTestMessage.startsWith(TEST_STATE_PREFIX)) {
        lastTestMessage.removePrefix(TEST_STATE_PREFIX)
            .split('|')
            .firstOrNull { it.startsWith("vision=") }
            ?.substringAfter('=')
            ?.let { runCatching { AiTestStatus.valueOf(it) }.getOrNull() }
            ?: AiTestStatus.UNTESTED
    } else {
        lastTestStatus
    }
    return visionStatus == AiTestStatus.PASSED
}

private class EstimateGenerationFailure(val safeMessage: String) : IllegalStateException()

private const val TEST_STATE_PREFIX = "habit-test-v1|"
private const val ESTIMATE_UNAVAILABLE_MESSAGE = "暂时无法使用 AI 热量估算，请检查照片和 AI 设置"
private const val ESTIMATE_RESPONSE_MESSAGE = "未能识别热量，请换一张清晰照片重试"
private const val ESTIMATE_FAILED_MESSAGE = "热量估算失败，请稍后重试"
