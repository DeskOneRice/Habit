package com.habit.app

import com.habit.app.domain.model.Habit

fun testHabit(
    id: Long = 1,
    start: Long = 1,
    archived: Long? = null,
    sortOrder: Int = 0,
): Habit = Habit(
    id = id,
    name = "测试习惯$id",
    iconKey = "book",
    themeColor = 0xFF8DB9CC,
    categoryId = 1,
    startEpochDay = start,
    archivedEpochDay = archived,
    sortOrder = sortOrder,
    createdAt = 100,
    updatedAt = 100,
)
