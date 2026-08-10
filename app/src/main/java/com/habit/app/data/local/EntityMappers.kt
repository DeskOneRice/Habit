package com.habit.app.data.local

import com.habit.app.domain.model.Category
import com.habit.app.domain.model.CheckIn
import com.habit.app.domain.model.Habit
import com.habit.app.domain.model.BeverageCategory
import com.habit.app.domain.model.BeverageDetails
import com.habit.app.domain.model.CalorieSource
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.DietCategory
import com.habit.app.domain.model.DietCategoryScope
import com.habit.app.domain.model.DietPhoto
import com.habit.app.domain.model.DietTemplate
import com.habit.app.domain.model.FoodItemDraft
import com.habit.app.domain.model.MealRecordDraft
import com.habit.app.domain.model.FoodItem
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealType
import com.habit.app.domain.model.AiCalorieEstimate
import com.habit.app.domain.model.AiCalorieItemEstimate
import com.habit.app.domain.model.AiFeature
import com.habit.app.domain.model.AiFeatureBinding
import com.habit.app.domain.model.AiModelConfig
import com.habit.app.domain.model.AiTestStatus
import com.habit.app.domain.model.AiWeeklyReport
import com.habit.app.domain.model.WeeklyReportCoverage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    isPreset = isPreset,
    isHidden = isHidden,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    isPreset = isPreset,
    isHidden = isHidden,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun DietCategoryEntity.toDomain(): DietCategory = DietCategory(
    id = id,
    scope = DietCategoryScope.valueOf(scope),
    name = name,
    isPreset = isPreset,
    isHidden = isHidden,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun DietCategory.toEntity(): DietCategoryEntity = DietCategoryEntity(
    id = id,
    scope = scope.name,
    name = name,
    isPreset = isPreset,
    isHidden = isHidden,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    name = name,
    iconKey = iconKey,
    themeColor = themeColor,
    categoryId = categoryId,
    startEpochDay = startEpochDay,
    archivedEpochDay = archivedEpochDay,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Habit.toEntity(): HabitEntity = HabitEntity(
    id = id,
    name = name,
    iconKey = iconKey,
    themeColor = themeColor,
    categoryId = categoryId,
    startEpochDay = startEpochDay,
    archivedEpochDay = archivedEpochDay,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun CheckInEntity.toDomain(): CheckIn = CheckIn(
    id = id,
    habitId = habitId,
    checkInEpochDay = checkInEpochDay,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun CheckIn.toEntity(): CheckInEntity = CheckInEntity(
    id = id,
    habitId = habitId,
    checkInEpochDay = checkInEpochDay,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MealRecordWithDetails.toDomain(): MealRecord = MealRecord(
    id = record.id,
    recordType = DietRecordType.valueOf(record.recordType),
    mealType = record.mealType?.let(MealType::valueOf),
    occurredAt = record.occurredAt,
    recordEpochDay = record.recordEpochDay,
    description = record.description,
    foodItems = foodItems.sortedBy(FoodItemEntity::sortOrder).map {
        FoodItem(it.id, it.mealRecordId, it.name, it.portionText, it.calories, it.sortOrder, it.createdAt, it.updatedAt)
    },
    calculatedCalories = record.calculatedCalories,
    finalCalories = record.finalCalories,
    calorieSource = CalorieSource.valueOf(record.calorieSource),
    beverage = beverageDetails?.let { details ->
        BeverageDetails(
            category = BeverageCategory.valueOf(details.category),
            brandOrStore = details.brandOrStore,
            beverageName = details.beverageName,
            sizeOrVolume = details.sizeOrVolume,
            temperature = details.temperature,
            iceLevel = details.iceLevel,
            sweetness = details.sweetness,
            toppings = toppings.sortedBy(BeverageToppingEntity::sortOrder).map(BeverageToppingEntity::name),
            cupCount = details.cupCount,
        )
    },
    note = record.note,
    createdAt = record.createdAt,
    updatedAt = record.updatedAt,
    photos = photos.sortedBy(DietPhotoEntity::sortOrder).map {
        DietPhoto(it.id, it.relativePath, it.sortOrder)
    },
    dietCategoryId = record.dietCategoryId,
)

fun DietTemplateWithDetails.toDomain(): DietTemplate {
    val beverage = template.beverageCategory?.let {
        BeverageDetails(
            category = BeverageCategory.valueOf(it),
            brandOrStore = template.brandOrStore.orEmpty(),
            beverageName = template.beverageName.orEmpty(),
            sizeOrVolume = template.sizeOrVolume.orEmpty(),
            temperature = template.temperature.orEmpty(),
            iceLevel = template.iceLevel.orEmpty(),
            sweetness = template.sweetness.orEmpty(),
            toppings = toppings.sortedBy(DietTemplateToppingEntity::sortOrder).map(DietTemplateToppingEntity::name),
            cupCount = template.cupCount ?: 1,
        )
    }
    return DietTemplate(
        id = template.id,
        name = template.name,
        draft = MealRecordDraft(
            recordType = DietRecordType.valueOf(template.recordType),
            mealType = template.mealType?.let(MealType::valueOf),
            occurredAt = 0,
            recordEpochDay = 0,
            description = template.description,
            foodItems = foodItems.sortedBy(DietTemplateFoodItemEntity::sortOrder).map {
                FoodItemDraft(it.name, it.portionText, it.calories)
            },
            manualFinalCalories = template.manualFinalCalories,
            beverage = beverage,
            note = template.note,
            photos = photos.sortedBy(DietPhotoEntity::sortOrder).map {
                DietPhoto(it.id, it.relativePath, it.sortOrder)
            },
            dietCategoryId = template.dietCategoryId,
        ),
        photos = photos.sortedBy(DietPhotoEntity::sortOrder).map {
            DietPhoto(it.id, it.relativePath, it.sortOrder)
        },
        sortOrder = template.sortOrder,
        createdAt = template.createdAt,
        updatedAt = template.updatedAt,
    )
}

private val aiJson = Json

fun AiModelConfigEntity.toDomain(): AiModelConfig = AiModelConfig(
    id = id,
    externalId = externalId,
    name = name,
    baseUrl = baseUrl,
    modelId = modelId,
    supportsText = supportsText,
    supportsVision = supportsVision,
    allowInsecureHttp = allowInsecureHttp,
    enabled = enabled,
    lastTestedAt = lastTestedAt,
    lastTestStatus = AiTestStatus.valueOf(lastTestStatus),
    lastTestMessage = lastTestMessage,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun AiFeatureBindingEntity.toDomain(): AiFeatureBinding = AiFeatureBinding(
    feature = AiFeature.valueOf(feature),
    modelConfigId = modelConfigId,
    updatedAt = updatedAt,
)

fun AiWeeklyReportEntity.toDomain(): AiWeeklyReport = AiWeeklyReport(
    id = id,
    startEpochDay = startEpochDay,
    endEpochDay = endEpochDay,
    generatedAt = generatedAt,
    modelNameSnapshot = modelNameSnapshot,
    modelIdSnapshot = modelIdSnapshot,
    title = title,
    overview = overview,
    habitAnalysis = habitAnalysis,
    dietAnalysis = dietAnalysis,
    correlationFinding = correlationFinding,
    suggestions = suggestionsJson.toStringList(),
    cautions = cautionsJson.toStringList(),
    coverage = coverageJson.toCoverage(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun AiWeeklyReport.toEntity(createdAt: Long, updatedAt: Long, id: Long = this.id): AiWeeklyReportEntity =
    AiWeeklyReportEntity(
        id = id,
        startEpochDay = startEpochDay,
        endEpochDay = endEpochDay,
        generatedAt = generatedAt,
        modelNameSnapshot = modelNameSnapshot,
        modelIdSnapshot = modelIdSnapshot,
        title = title,
        overview = overview,
        habitAnalysis = habitAnalysis,
        dietAnalysis = dietAnalysis,
        correlationFinding = correlationFinding,
        suggestionsJson = suggestions.toJsonArray(),
        cautionsJson = cautions.toJsonArray(),
        coverageJson = coverage.toJsonObject(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun AiCalorieEstimateEntity.toDomain(): AiCalorieEstimate = AiCalorieEstimate(
    mealRecordId = mealRecordId,
    generatedAt = generatedAt,
    modelNameSnapshot = modelNameSnapshot,
    modelIdSnapshot = modelIdSnapshot,
    items = itemsJson.toCalorieItems(),
    totalMinKcal = totalMinKcal,
    totalMaxKcal = totalMaxKcal,
    suggestedKcal = suggestedKcal,
    adoptedKcal = adoptedKcal,
    wasModified = wasModified,
    accuracyNote = accuracyNote,
)

fun AiCalorieEstimate.toEntity(): AiCalorieEstimateEntity = AiCalorieEstimateEntity(
    mealRecordId = mealRecordId,
    generatedAt = generatedAt,
    modelNameSnapshot = modelNameSnapshot,
    modelIdSnapshot = modelIdSnapshot,
    itemsJson = items.toCalorieItemsJson(),
    totalMinKcal = totalMinKcal,
    totalMaxKcal = totalMaxKcal,
    suggestedKcal = suggestedKcal,
    adoptedKcal = adoptedKcal,
    wasModified = wasModified,
    accuracyNote = accuracyNote,
)

private fun List<String>.toJsonArray(): String = aiJson.encodeToString(
    JsonArray.serializer(),
    buildJsonArray { this@toJsonArray.forEach { add(JsonPrimitive(it)) } },
)

private fun String.toStringList(): List<String> = aiJson.parseToJsonElement(this).jsonArray.map {
    it.jsonPrimitive.content
}

private fun WeeklyReportCoverage.toJsonObject(): String = aiJson.encodeToString(
    JsonObject.serializer(),
    buildJsonObject {
        put("scheduledHabitCount", JsonPrimitive(scheduledHabitCount))
        put("completedHabitCount", JsonPrimitive(completedHabitCount))
        put("dietRecordCount", JsonPrimitive(dietRecordCount))
        put("dietRecordDays", JsonPrimitive(dietRecordDays))
        put("knownCalorieRecords", JsonPrimitive(knownCalorieRecords))
        put("missingCalorieRecords", JsonPrimitive(missingCalorieRecords))
    },
)

private fun String.toCoverage(): WeeklyReportCoverage {
    val json = aiJson.parseToJsonElement(this).jsonObject
    return WeeklyReportCoverage(
        scheduledHabitCount = json.requiredInt("scheduledHabitCount"),
        completedHabitCount = json.requiredInt("completedHabitCount"),
        dietRecordCount = json.requiredInt("dietRecordCount"),
        dietRecordDays = json.requiredInt("dietRecordDays"),
        knownCalorieRecords = json.requiredInt("knownCalorieRecords"),
        missingCalorieRecords = json.requiredInt("missingCalorieRecords"),
    )
}

private fun JsonObject.requiredInt(name: String): Int = getValue(name).jsonPrimitive.int

private fun String.toCalorieItems(): List<AiCalorieItemEstimate> =
    aiJson.parseToJsonElement(this).jsonArray.map { item ->
        val json = item.jsonObject
        AiCalorieItemEstimate(
            name = json.getValue("name").jsonPrimitive.content,
            portion = json.getValue("portion").jsonPrimitive.content,
            minKcal = json.requiredInt("minKcal"),
            maxKcal = json.requiredInt("maxKcal"),
        )
    }

private fun List<AiCalorieItemEstimate>.toCalorieItemsJson(): String = aiJson.encodeToString(
    JsonArray.serializer(),
    buildJsonArray {
        this@toCalorieItemsJson.forEach { item ->
            add(
                buildJsonObject {
                    put("name", JsonPrimitive(item.name))
                    put("portion", JsonPrimitive(item.portion))
                    put("minKcal", JsonPrimitive(item.minKcal))
                    put("maxKcal", JsonPrimitive(item.maxKcal))
                },
            )
        }
    },
)
