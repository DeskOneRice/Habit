package com.habit.app.domain.stats

import com.habit.app.domain.model.CalorieCalculation
import com.habit.app.domain.model.CalorieSource
import com.habit.app.domain.model.DietRangeSummary
import com.habit.app.domain.model.DietDailyTotal
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.FoodItemDraft
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealType
import com.habit.app.domain.model.RankedValue
import com.habit.app.domain.model.displayName
import com.habit.app.domain.model.displayTemperature
import java.time.LocalTime

fun suggestMealType(time: LocalTime): MealType = when (time.hour) {
    in 5..9 -> MealType.BREAKFAST
    in 10..14 -> MealType.LUNCH
    in 17..21 -> MealType.DINNER
    else -> MealType.SNACK
}

fun calculateCalories(
    items: List<FoodItemDraft>,
    manualFinalCalories: Int?,
): CalorieCalculation {
    require(manualFinalCalories == null || manualFinalCalories >= 0) { "热量不能小于 0" }
    require(items.all { it.calories == null || it.calories >= 0 }) { "食物热量不能小于 0" }
    val values = items.mapNotNull(FoodItemDraft::calories)
    val calculated = if (values.isEmpty()) null else values.sum()
    return when {
        manualFinalCalories != null -> CalorieCalculation(calculated, manualFinalCalories, CalorieSource.MANUAL)
        calculated != null -> CalorieCalculation(calculated, calculated, CalorieSource.ITEM_SUM)
        else -> CalorieCalculation(null, null, CalorieSource.NONE)
    }
}

fun summarizeDiet(
    records: List<MealRecord>,
    startEpochDay: Long,
    endEpochDay: Long,
    categoryNames: Map<Long, String> = emptyMap(),
): DietRangeSummary {
    require(startEpochDay <= endEpochDay)
    val included = records.filter { it.recordEpochDay in startEpochDay..endEpochDay }
    val calories = included.mapNotNull(MealRecord::finalCalories)
    val drinks = included.mapNotNull(MealRecord::beverage)
    val dailyTotals = (startEpochDay..endEpochDay).map { epochDay ->
        val dayRecords = included.filter { it.recordEpochDay == epochDay }
        val dayCalories = dayRecords.mapNotNull(MealRecord::finalCalories)
        DietDailyTotal(
            epochDay = epochDay,
            recordCount = dayRecords.size,
            totalCalories = dayCalories.takeIf(List<Int>::isNotEmpty)?.sum(),
            calorieRecordCount = dayCalories.size,
        )
    }
    return DietRangeSummary(
        startEpochDay = startEpochDay,
        endEpochDay = endEpochDay,
        recordCount = included.size,
        recordedDays = included.map(MealRecord::recordEpochDay).distinct().size,
        mealRecordCount = included.count { it.recordType == DietRecordType.MEAL },
        beverageRecordCount = included.count { it.recordType == DietRecordType.BEVERAGE },
        totalCalories = if (calories.isEmpty()) null else calories.sum(),
        beverageCups = drinks.sumOf { it.cupCount },
        categoryRanking = rank(
            included.mapNotNull { record ->
                record.beverage?.let { beverage ->
                    categoryNames[record.dietCategoryId]
                        .orEmpty()
                        .ifBlank { beverage.category.displayName() } to beverage.cupCount
                }
            },
        ),
        brandRanking = rank(drinks.filter { it.brandOrStore.isNotBlank() }.map { it.brandOrStore to it.cupCount }),
        sweetnessRanking = rank(drinks.filter { it.sweetness.isNotBlank() }.map { it.sweetness to it.cupCount }),
        temperatureRanking = rank(drinks.map { it.displayTemperature() }.filter(String::isNotBlank).map { it to 1 }),
        dailyTotals = dailyTotals,
    )
}

private fun rank(values: List<Pair<String, Int>>): List<RankedValue> = values
    .groupingBy(Pair<String, Int>::first)
    .fold(0) { total, item -> total + item.second }
    .map { RankedValue(it.key, it.value) }
    .sortedWith(compareByDescending<RankedValue> { it.count }.thenBy { it.label })
