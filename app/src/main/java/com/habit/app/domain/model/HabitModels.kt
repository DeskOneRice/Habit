package com.habit.app.domain.model

data class Habit(
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

data class Category(
    val id: Long,
    val name: String,
    val isPreset: Boolean,
    val isHidden: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class CheckIn(
    val id: Long,
    val habitId: Long,
    val checkInEpochDay: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

data class HabitDraft(
    val name: String,
    val iconKey: String,
    val themeColor: Long,
    val categoryId: Long,
    val startEpochDay: Long,
)
