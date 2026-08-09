package com.habit.app.domain.model

fun DietRecordType.displayName(): String = when (this) {
    DietRecordType.MEAL -> "餐食"
    DietRecordType.BEVERAGE -> "饮品"
}

fun MealType.displayName(): String = when (this) {
    MealType.BREAKFAST -> "早餐"
    MealType.LUNCH -> "午餐"
    MealType.DINNER -> "晚餐"
    MealType.LATE_NIGHT -> "夜宵"
    MealType.SNACK -> "加餐"
}

fun BeverageCategory.displayName(): String = when (this) {
    BeverageCategory.COFFEE -> "咖啡"
    BeverageCategory.MILK_TEA -> "奶茶"
    BeverageCategory.TEA -> "茶"
    BeverageCategory.FRUIT_DRINK -> "果饮"
    BeverageCategory.DAIRY -> "乳饮"
    BeverageCategory.OTHER -> "其他饮品"
}

fun combinedTemperature(temperature: String, iceLevel: String): String {
    val temperatureValue = temperature.trim()
    val iceValue = iceLevel.trim()
    return when {
        temperatureValue.isBlank() -> iceValue
        iceValue.isBlank() || temperatureValue == iceValue -> temperatureValue
        else -> "$temperatureValue · $iceValue"
    }
}

fun BeverageDetails.displayTemperature(): String = combinedTemperature(temperature, iceLevel)

fun MealRecord.displayTitle(): String = when (recordType) {
    DietRecordType.MEAL -> description.trim().takeIf(String::isNotEmpty)
        ?: foodItems.sortedBy(FoodItem::sortOrder)
            .map(FoodItem::name)
            .filter(String::isNotBlank)
            .joinToString("、")
            .takeIf(String::isNotEmpty)
        ?: "未命名餐食"

    DietRecordType.BEVERAGE -> beverage?.beverageName?.trim()?.takeIf(String::isNotEmpty)
        ?: description.trim().takeIf(String::isNotEmpty)
        ?: "未命名饮品"
}

fun MealRecord.displayType(categoryName: String = ""): String = when (recordType) {
    DietRecordType.MEAL -> mealType?.displayName() ?: DietRecordType.MEAL.displayName()
    DietRecordType.BEVERAGE -> categoryName.trim().takeIf(String::isNotEmpty)
        ?: beverage?.category?.displayName()
        ?: DietRecordType.BEVERAGE.displayName()
}
