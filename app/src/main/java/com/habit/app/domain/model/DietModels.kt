package com.habit.app.domain.model

enum class DietRecordType { MEAL, BEVERAGE }

enum class MealType { BREAKFAST, LUNCH, DINNER, LATE_NIGHT, SNACK }

val mealTypeDisplayOrder = listOf(
    MealType.BREAKFAST,
    MealType.LUNCH,
    MealType.DINNER,
    MealType.LATE_NIGHT,
    MealType.SNACK,
)

enum class CalorieSource { NONE, ITEM_SUM, MANUAL, AI_ESTIMATE }

enum class BeverageCategory { COFFEE, MILK_TEA, TEA, FRUIT_DRINK, DAIRY, OTHER }

data class FoodItemDraft(
    val name: String,
    val portionText: String?,
    val calories: Int?,
)

data class FoodItem(
    val id: Long,
    val mealRecordId: Long,
    val name: String,
    val portionText: String?,
    val calories: Int?,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BeverageDetails(
    val category: BeverageCategory,
    val brandOrStore: String,
    val beverageName: String,
    val sizeOrVolume: String,
    val temperature: String,
    val iceLevel: String,
    val sweetness: String,
    val toppings: List<String>,
    val cupCount: Int,
)

data class MealRecordDraft(
    val recordType: DietRecordType,
    val mealType: MealType?,
    val occurredAt: Long,
    val recordEpochDay: Long,
    val description: String,
    val foodItems: List<FoodItemDraft>,
    val manualFinalCalories: Int?,
    val beverage: BeverageDetails?,
    val note: String,
    val photos: List<DietPhoto> = emptyList(),
    val dietCategoryId: Long = 0,
)

data class MealRecord(
    val id: Long,
    val recordType: DietRecordType,
    val mealType: MealType?,
    val occurredAt: Long,
    val recordEpochDay: Long,
    val description: String,
    val foodItems: List<FoodItem>,
    val calculatedCalories: Int?,
    val finalCalories: Int?,
    val calorieSource: CalorieSource,
    val beverage: BeverageDetails?,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val photos: List<DietPhoto> = emptyList(),
    val dietCategoryId: Long = 0,
)

data class DietPhoto(
    val id: Long = 0,
    val relativePath: String,
    val sortOrder: Int,
)

data class DietTemplate(
    val id: Long,
    val name: String,
    val draft: MealRecordDraft,
    val photos: List<DietPhoto>,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class DietTemplateDraft(
    val id: Long? = null,
    val name: String,
    val meal: MealRecordDraft,
    val includePhotos: Boolean = false,
    val sortOrder: Int = 0,
)

fun MealRecord.toRepeatDraft(nowMillis: Long, epochDay: Long): MealRecordDraft = MealRecordDraft(
    recordType = recordType,
    mealType = mealType,
    occurredAt = nowMillis,
    recordEpochDay = epochDay,
    description = description,
    foodItems = foodItems.sortedBy(FoodItem::sortOrder).map {
        FoodItemDraft(it.name, it.portionText, it.calories)
    },
    manualFinalCalories = finalCalories,
    beverage = beverage,
    note = note,
    photos = emptyList(),
    dietCategoryId = dietCategoryId,
)

data class CalorieCalculation(
    val calculatedCalories: Int?,
    val finalCalories: Int?,
    val source: CalorieSource,
)

data class RankedValue(val label: String, val count: Int)

data class DietDailyTotal(
    val epochDay: Long,
    val recordCount: Int,
    val totalCalories: Int?,
)

data class DietRangeSummary(
    val startEpochDay: Long,
    val endEpochDay: Long,
    val recordCount: Int,
    val recordedDays: Int,
    val mealRecordCount: Int,
    val beverageRecordCount: Int,
    val totalCalories: Int?,
    val beverageCups: Int,
    val categoryRanking: List<RankedValue>,
    val brandRanking: List<RankedValue>,
    val sweetnessRanking: List<RankedValue>,
    val temperatureRanking: List<RankedValue>,
    val dailyTotals: List<DietDailyTotal>,
)
