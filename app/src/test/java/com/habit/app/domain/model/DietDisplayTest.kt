package com.habit.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DietDisplayTest {
    @Test
    fun beverageCategoriesAlwaysUseChineseLabels() {
        assertEquals(
            listOf("咖啡", "奶茶", "茶", "果饮", "乳饮", "其他饮品"),
            BeverageCategory.entries.map(BeverageCategory::displayName),
        )
    }

    @Test
    fun recordAndMealTypesShareOneDisplayVocabulary() {
        assertEquals("餐食", DietRecordType.MEAL.displayName())
        assertEquals("饮品", DietRecordType.BEVERAGE.displayName())
        assertEquals("早餐", MealType.BREAKFAST.displayName())
        assertEquals("午餐", MealType.LUNCH.displayName())
        assertEquals("晚餐", MealType.DINNER.displayName())
        assertEquals("夜宵", MealType.LATE_NIGHT.displayName())
        assertEquals("加餐", MealType.SNACK.displayName())
    }

    @Test
    fun oldTemperatureAndIceLevelMergeWithoutLosingInformation() {
        assertEquals("", combinedTemperature("", ""))
        assertEquals("热", combinedTemperature(" 热 ", ""))
        assertEquals("少冰", combinedTemperature("", "少冰"))
        assertEquals("常温", combinedTemperature("常温", "常温"))
        assertEquals("冷 · 少冰", combinedTemperature("冷", "少冰"))
    }

    @Test
    fun cardTitleAndTypeFollowSharedReadingRules() {
        val meal = TestDietRecords.meal(
            description = "番茄炒蛋",
            mealType = MealType.DINNER,
        )
        val beverage = TestDietRecords.beverage(
            beverageName = "生椰拿铁",
            category = BeverageCategory.COFFEE,
        )

        assertEquals("番茄炒蛋", meal.displayTitle())
        assertEquals("晚餐", meal.displayType())
        assertEquals("生椰拿铁", beverage.displayTitle())
        assertEquals("自定义咖啡", beverage.displayType("自定义咖啡"))
        assertEquals("咖啡", beverage.displayType())
    }
}

private object TestDietRecords {
    fun meal(description: String, mealType: MealType) = MealRecord(
        id = 1,
        recordType = DietRecordType.MEAL,
        mealType = mealType,
        occurredAt = 1,
        recordEpochDay = 1,
        description = description,
        foodItems = emptyList(),
        calculatedCalories = null,
        finalCalories = null,
        calorieSource = CalorieSource.NONE,
        beverage = null,
        note = "",
        createdAt = 1,
        updatedAt = 1,
    )

    fun beverage(beverageName: String, category: BeverageCategory) = meal("", MealType.SNACK).copy(
        recordType = DietRecordType.BEVERAGE,
        mealType = null,
        beverage = BeverageDetails(
            category = category,
            brandOrStore = "",
            beverageName = beverageName,
            sizeOrVolume = "",
            temperature = "",
            iceLevel = "",
            sweetness = "",
            toppings = emptyList(),
            cupCount = 1,
        ),
    )
}
