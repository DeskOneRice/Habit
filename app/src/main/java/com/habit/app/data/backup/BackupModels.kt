package com.habit.app.data.backup

const val HABIT_BACKUP_FORMAT = "habit-backup"
const val HABIT_BACKUP_SCHEMA_VERSION = 2

data class HabitBackup(
    val format: String = HABIT_BACKUP_FORMAT,
    val schemaVersion: Int = HABIT_BACKUP_SCHEMA_VERSION,
    val appVersion: String,
    val exportedAt: Long,
    val preferencesUpdatedAt: Long,
    val categories: List<BackupCategory>,
    val habits: List<BackupHabit>,
    val checkIns: List<BackupCheckIn>,
    val preferences: BackupPreferences,
    val mealRecords: List<BackupMealRecord> = emptyList(),
    val foodItems: List<BackupFoodItem> = emptyList(),
    val beverageDetails: List<BackupBeverageDetail> = emptyList(),
    val beverageToppings: List<BackupBeverageTopping> = emptyList(),
)

data class BackupCategory(
    val id: Long,
    val name: String,
    val isPreset: Boolean,
    val isHidden: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupHabit(
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

data class BackupCheckIn(
    val id: Long,
    val habitId: Long,
    val checkInEpochDay: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupPreferences(
    val themeId: String,
    val recentEmojiKeys: List<String>,
    val dailyCalorieGoalEnabled: Boolean = false,
    val dailyCalorieGoalKcal: Int? = null,
)

data class BackupMealRecord(
    val id: Long, val recordType: String, val mealType: String?, val occurredAt: Long,
    val recordEpochDay: Long, val description: String, val calculatedCalories: Int?,
    val finalCalories: Int?, val calorieSource: String, val note: String,
    val createdAt: Long, val updatedAt: Long,
)

data class BackupFoodItem(
    val id: Long, val mealRecordId: Long, val name: String, val portionText: String?,
    val calories: Int?, val sortOrder: Int, val createdAt: Long, val updatedAt: Long,
)

data class BackupBeverageDetail(
    val mealRecordId: Long, val category: String, val brandOrStore: String,
    val beverageName: String, val sizeOrVolume: String, val temperature: String,
    val iceLevel: String, val sweetness: String, val cupCount: Int,
)

data class BackupBeverageTopping(
    val id: Long, val mealRecordId: Long, val name: String, val sortOrder: Int,
    val createdAt: Long, val updatedAt: Long,
)

class InvalidBackupException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

class UnsupportedBackupVersionException(val version: Int) :
    IllegalArgumentException("不支持的备份版本：$version")
