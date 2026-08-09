package com.habit.app.domain.stats

import com.habit.app.domain.model.BeverageCategory
import com.habit.app.domain.model.BeverageDetails
import com.habit.app.domain.model.CalorieSource
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.FoodItem
import com.habit.app.domain.model.FoodItemDraft
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealType
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DietStatisticsTest {
    @Test
    fun suggestsMealTypeFromTime() {
        assertEquals(MealType.BREAKFAST, suggestMealType(LocalTime.of(7, 0)))
        assertEquals(MealType.LUNCH, suggestMealType(LocalTime.of(12, 0)))
        assertEquals(MealType.DINNER, suggestMealType(LocalTime.of(18, 0)))
        assertEquals(MealType.SNACK, suggestMealType(LocalTime.of(23, 0)))
    }

    @Test
    fun manualCaloriesOverrideItemSum() {
        val result = calculateCalories(
            listOf(FoodItemDraft("米饭", "1 碗", 230), FoodItemDraft("青菜", null, 80)),
            manualFinalCalories = 350,
        )

        assertEquals(310, result.calculatedCalories)
        assertEquals(350, result.finalCalories)
        assertEquals(CalorieSource.MANUAL, result.source)
    }

    @Test
    fun missingCaloriesRemainDifferentFromZero() {
        assertNull(calculateCalories(listOf(FoodItemDraft("水", null, null)), null).finalCalories)
        assertEquals(0, calculateCalories(listOf(FoodItemDraft("无糖茶", null, 0)), null).finalCalories)
    }

    @Test
    fun summaryCountsMealsAndRanksDrinkAttributes() {
        val records = listOf(
            record(id = 1, day = 10, calories = 600),
            record(
                id = 2,
                day = 10,
                calories = null,
                type = DietRecordType.BEVERAGE,
                mealType = null,
                beverage = BeverageDetails(
                    category = BeverageCategory.MILK_TEA,
                    brandOrStore = "茶铺",
                    beverageName = "奶茶",
                    sizeOrVolume = "中杯",
                    temperature = "冷",
                    iceLevel = "少冰",
                    sweetness = "三分糖",
                    toppings = listOf("珍珠"),
                    cupCount = 2,
                ),
            ),
        )

        val summary = summarizeDiet(records, 10, 10)

        assertEquals(2, summary.recordCount)
        assertEquals(1, summary.mealRecordCount)
        assertEquals(1, summary.beverageRecordCount)
        assertEquals(summary.recordCount, summary.mealRecordCount + summary.beverageRecordCount)
        assertEquals(1, summary.recordedDays)
        assertEquals(600, summary.totalCalories)
        assertEquals(2, summary.beverageCups)
        assertEquals("奶茶", summary.categoryRanking.single().label)
        assertEquals("冷 · 少冰", summary.temperatureRanking.single().label)
        assertEquals("茶铺", summary.brandRanking.single().label)
        assertEquals(2, summary.brandRanking.single().count)
        assertEquals(listOf(10L), summary.dailyTotals.map { it.epochDay })
        assertEquals(2, summary.dailyTotals.single().recordCount)
        assertEquals(1, summary.dailyTotals.single().calorieRecordCount)
    }

    @Test
    fun summaryUsesManagedCategoryNameBeforeLegacyBeverageCategory() {
        val records = listOf(
            record(
                id = 3,
                day = 10,
                calories = null,
                type = DietRecordType.BEVERAGE,
                mealType = null,
                beverage = BeverageDetails(
                    category = BeverageCategory.OTHER,
                    brandOrStore = "",
                    beverageName = "气泡水",
                    sizeOrVolume = "",
                    temperature = "常温",
                    iceLevel = "",
                    sweetness = "",
                    toppings = emptyList(),
                    cupCount = 1,
                ),
                dietCategoryId = 42,
            ),
        )

        val summary = summarizeDiet(records, 10, 10, categoryNames = mapOf(42L to "气泡饮"))

        assertEquals("气泡饮", summary.categoryRanking.single().label)
    }

    private fun record(
        id: Long,
        day: Long,
        calories: Int?,
        type: DietRecordType = DietRecordType.MEAL,
        mealType: MealType? = MealType.LUNCH,
        beverage: BeverageDetails? = null,
        dietCategoryId: Long = 0,
    ) = MealRecord(
        id = id,
        recordType = type,
        mealType = mealType,
        occurredAt = 100,
        recordEpochDay = day,
        description = "测试",
        foodItems = listOf(FoodItem(1, id, "食物", null, calories, 0, 100, 100)),
        calculatedCalories = calories,
        finalCalories = calories,
        calorieSource = if (calories == null) CalorieSource.NONE else CalorieSource.ITEM_SUM,
        beverage = beverage,
        note = "",
        createdAt = 100,
        updatedAt = 100,
        dietCategoryId = dietCategoryId,
    )
}
