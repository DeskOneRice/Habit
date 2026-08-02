package com.habit.app.data.local

import com.habit.app.domain.model.Category
import com.habit.app.domain.model.CheckIn
import com.habit.app.domain.model.Habit
import com.habit.app.domain.model.BeverageCategory
import com.habit.app.domain.model.BeverageDetails
import com.habit.app.domain.model.CalorieSource
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.FoodItem
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealType

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
)
