package com.habit.app.data.local

import com.habit.app.domain.model.Category
import com.habit.app.domain.model.CheckIn
import com.habit.app.domain.model.Habit

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
