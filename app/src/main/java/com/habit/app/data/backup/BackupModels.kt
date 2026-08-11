package com.habit.app.data.backup

import com.habit.app.domain.model.DIET_CATEGORY_PRESETS
import com.habit.app.domain.model.DietCategoryScope

const val HABIT_BACKUP_FORMAT = "habit-backup"
const val HABIT_BACKUP_SCHEMA_VERSION = 5

data class HabitBackup(
    val format: String = HABIT_BACKUP_FORMAT,
    val schemaVersion: Int = HABIT_BACKUP_SCHEMA_VERSION,
    val appVersion: String,
    val exportedAt: Long,
    val preferencesUpdatedAt: Long,
    val categories: List<BackupCategory>,
    val habits: List<BackupHabit>,
    val checkIns: List<BackupCheckIn>,
    val preferences: BackupPreferences,
    val mealRecords: List<BackupMealRecord> = emptyList(),
    val foodItems: List<BackupFoodItem> = emptyList(),
    val beverageDetails: List<BackupBeverageDetail> = emptyList(),
    val beverageToppings: List<BackupBeverageTopping> = emptyList(),
    val dietPhotos: List<BackupDietPhoto> = emptyList(),
    val dietTemplates: List<BackupDietTemplate> = emptyList(),
    val dietTemplateFoodItems: List<BackupDietTemplateFoodItem> = emptyList(),
    val dietTemplateToppings: List<BackupDietTemplateTopping> = emptyList(),
    val dietCategories: List<BackupDietCategory> = emptyList(),
    val aiModelConfigs: List<BackupAiModelConfig> = emptyList(),
    val aiFeatureBindings: List<BackupAiFeatureBinding> = emptyList(),
    val aiWeeklyReports: List<BackupAiWeeklyReport> = emptyList(),
    val aiCalorieEstimates: List<BackupAiCalorieEstimate> = emptyList(),
)

data class BackupAiModelConfig(
    val id: Long,
    val externalId: String,
    val name: String,
    val baseUrl: String,
    val modelId: String,
    val supportsText: Boolean,
    val supportsVision: Boolean,
    val allowInsecureHttp: Boolean,
    val enabled: Boolean,
    val lastTestedAt: Long?,
    val lastTestStatus: String,
    val lastTestMessage: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupAiFeatureBinding(
    val feature: String,
    val modelConfigId: Long?,
    val updatedAt: Long,
)

data class BackupAiWeeklyReport(
    val id: Long,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val generatedAt: Long,
    val modelNameSnapshot: String,
    val modelIdSnapshot: String,
    val title: String,
    val overview: String,
    val habitAnalysis: String,
    val dietAnalysis: String,
    val correlationFinding: String,
    val suggestionsJson: String,
    val cautionsJson: String,
    val coverageJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupAiCalorieEstimate(
    val mealRecordId: Long,
    val generatedAt: Long,
    val modelNameSnapshot: String,
    val modelIdSnapshot: String,
    val itemsJson: String,
    val totalMinKcal: Int,
    val totalMaxKcal: Int,
    val suggestedKcal: Int,
    val adoptedKcal: Int,
    val wasModified: Boolean,
    val accuracyNote: String,
)

data class BackupDietCategory(
    val id: Long,
    val scope: String,
    val name: String,
    val isPreset: Boolean,
    val isHidden: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupCategory(
    val id: Long,
    val name: String,
    val isPreset: Boolean,
    val isHidden: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupHabit(
    val id: Long,
    val name: String,
    val iconKey: String,
    val themeColor: Long,
    val categoryId: Long,
    val startEpochDay: Long,
    val archivedEpochDay: Long?,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupCheckIn(
    val id: Long,
    val habitId: Long,
    val checkInEpochDay: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupPreferences(
    val themeId: String,
    val recentEmojiKeys: List<String>,
    val dailyCalorieGoalEnabled: Boolean = false,
    val dailyCalorieGoalKcal: Int? = null,
)

data class BackupMealRecord(
    val id: Long, val recordType: String, val mealType: String?, val occurredAt: Long,
    val recordEpochDay: Long, val description: String, val calculatedCalories: Int?,
    val finalCalories: Int?, val calorieSource: String, val note: String,
    val createdAt: Long, val updatedAt: Long,
    val dietCategoryId: Long? = null,
)

data class BackupFoodItem(
    val id: Long, val mealRecordId: Long, val name: String, val portionText: String?,
    val calories: Int?, val sortOrder: Int, val createdAt: Long, val updatedAt: Long,
)

data class BackupBeverageDetail(
    val mealRecordId: Long, val category: String, val brandOrStore: String,
    val beverageName: String, val sizeOrVolume: String, val temperature: String,
    val iceLevel: String, val sweetness: String, val cupCount: Int,
)

data class BackupBeverageTopping(
    val id: Long, val mealRecordId: Long, val name: String, val sortOrder: Int,
    val createdAt: Long, val updatedAt: Long,
)

data class BackupDietPhoto(
    val id: Long,
    val mealRecordId: Long?,
    val templateId: Long?,
    val relativePath: String,
    val sortOrder: Int,
    val createdAt: Long,
)

data class BackupDietTemplate(
    val id: Long,
    val name: String,
    val recordType: String,
    val mealType: String?,
    val description: String,
    val manualFinalCalories: Int?,
    val beverageCategory: String?,
    val brandOrStore: String?,
    val beverageName: String?,
    val sizeOrVolume: String?,
    val temperature: String?,
    val iceLevel: String?,
    val sweetness: String?,
    val cupCount: Int?,
    val note: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val dietCategoryId: Long? = null,
)

data class BackupDietTemplateFoodItem(
    val id: Long,
    val templateId: Long,
    val name: String,
    val portionText: String?,
    val calories: Int?,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupDietTemplateTopping(
    val id: Long,
    val templateId: Long,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

class InvalidBackupException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

class UnsupportedBackupVersionException(val version: Int) :
    IllegalArgumentException("不支持的备份版本：$version")

fun HabitBackup.normalizeDietCategories(): HabitBackup {
    val normalizedCategories = dietCategories.toMutableList()
    val existingIds = normalizedCategories.mapTo(mutableSetOf(), BackupDietCategory::id)
    DIET_CATEGORY_PRESETS.forEachIndexed { index, preset ->
        if (existingIds.add(preset.id)) {
            normalizedCategories += BackupDietCategory(
                id = preset.id,
                scope = preset.scope.name,
                name = preset.name,
                isPreset = true,
                isHidden = false,
                sortOrder = index,
                createdAt = 0,
                updatedAt = 0,
            )
        }
    }
    val byId = normalizedCategories.associateBy(BackupDietCategory::id)
    val beverageCategoryByRecord = beverageDetails.associate {
        it.mealRecordId to legacyBeverageCategoryId(it.category)
    }
    fun validCategoryId(id: Long?, scope: DietCategoryScope): Long? =
        id?.takeIf { byId[it]?.scope == scope.name }

    return copy(
        schemaVersion = HABIT_BACKUP_SCHEMA_VERSION,
        dietCategories = normalizedCategories.sortedWith(compareBy(BackupDietCategory::scope, BackupDietCategory::sortOrder, BackupDietCategory::id)),
        mealRecords = mealRecords.map { record ->
            val scope = if (record.recordType == "BEVERAGE") DietCategoryScope.BEVERAGE else DietCategoryScope.MEAL
            val fallback = if (scope == DietCategoryScope.MEAL) 4L else beverageCategoryByRecord[record.id] ?: 10L
            record.copy(dietCategoryId = validCategoryId(record.dietCategoryId, scope) ?: fallback)
        },
        dietTemplates = dietTemplates.map { template ->
            val scope = if (template.recordType == "BEVERAGE") DietCategoryScope.BEVERAGE else DietCategoryScope.MEAL
            val fallback = if (scope == DietCategoryScope.MEAL) 4L else legacyBeverageCategoryId(template.beverageCategory)
            template.copy(dietCategoryId = validCategoryId(template.dietCategoryId, scope) ?: fallback)
        },
    )
}

private fun legacyBeverageCategoryId(category: String?): Long = when (category) {
    "COFFEE" -> 5L
    "MILK_TEA" -> 6L
    "TEA" -> 7L
    "FRUIT_DRINK" -> 8L
    "DAIRY" -> 9L
    else -> 10L
}
