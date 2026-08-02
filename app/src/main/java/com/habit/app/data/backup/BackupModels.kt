package com.habit.app.data.backup

const val HABIT_BACKUP_FORMAT = "habit-backup"
const val HABIT_BACKUP_SCHEMA_VERSION = 1

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
)

class InvalidBackupException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

class UnsupportedBackupVersionException(val version: Int) :
    IllegalArgumentException("不支持的备份版本：$version")
