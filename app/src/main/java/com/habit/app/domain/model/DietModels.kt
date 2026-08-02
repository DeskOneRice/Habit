package com.habit.app.domain.model

enum class DietRecordType { MEAL, BEVERAGE }

enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }

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
)

data class CalorieCalculation(
    val calculatedCalories: Int?,
    val finalCalories: Int?,
    val source: CalorieSource,
)

data class RankedValue(val label: String, val count: Int)

data class DietRangeSummary(
    val startEpochDay: Long,
    val endEpochDay: Long,
    val recordCount: Int,
    val recordedDays: Int,
    val totalCalories: Int?,
    val beverageCups: Int,
    val categoryRanking: List<RankedValue>,
    val brandRanking: List<RankedValue>,
    val sweetnessRanking: List<RankedValue>,
    val iceRanking: List<RankedValue>,
)
