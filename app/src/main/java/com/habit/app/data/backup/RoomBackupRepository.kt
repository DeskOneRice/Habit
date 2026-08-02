package com.habit.app.data.backup

import androidx.room.withTransaction
import com.habit.app.data.local.CategoryEntity
import com.habit.app.data.local.CheckInEntity
import com.habit.app.data.local.HabitDatabase
import com.habit.app.data.local.HabitEntity

enum class ImportMode { REPLACE, MERGE }

data class BackupDatabaseSnapshot(
    val categories: List<BackupCategory>,
    val habits: List<BackupHabit>,
    val checkIns: List<BackupCheckIn>,
)

data class ImportSummary(
    val categories: Int,
    val habits: Int,
    val checkIns: Int,
)

class RoomBackupRepository(private val database: HabitDatabase) {
    suspend fun exportDatabase(): BackupDatabaseSnapshot = database.withTransaction {
        BackupDatabaseSnapshot(
            categories = database.categoryDao().getAll().map(CategoryEntity::toBackup),
            habits = database.habitDao().getAll().map(HabitEntity::toBackup),
            checkIns = database.checkInDao().getAll().map(CheckInEntity::toBackup),
        )
    }

    suspend fun importDatabase(backup: HabitBackup, mode: ImportMode): ImportSummary {
        HabitBackupCodec.validate(backup)
        return database.withTransaction {
            val target = when (mode) {
                ImportMode.REPLACE -> backup
                ImportMode.MERGE -> {
                    val current = BackupDatabaseSnapshot(
                        categories = database.categoryDao().getAll().map(CategoryEntity::toBackup),
                        habits = database.habitDao().getAll().map(HabitEntity::toBackup),
                        checkIns = database.checkInDao().getAll().map(CheckInEntity::toBackup),
                    ).toHabitBackup(backup)
                    BackupMerger.merge(current, backup)
                }
            }
            database.checkInDao().deleteAll()
            database.habitDao().deleteAll()
            database.categoryDao().deleteAll()
            database.categoryDao().insertAll(target.categories.map(BackupCategory::toEntity))
            database.habitDao().insertAll(target.habits.map(BackupHabit::toEntity))
            database.checkInDao().insertAll(target.checkIns.map(BackupCheckIn::toEntity))
            ImportSummary(target.categories.size, target.habits.size, target.checkIns.size)
        }
    }
}

private fun BackupDatabaseSnapshot.toHabitBackup(template: HabitBackup) = template.copy(
    categories = categories,
    habits = habits,
    checkIns = checkIns,
)

private fun CategoryEntity.toBackup() = BackupCategory(
    id, name, isPreset, isHidden, sortOrder, createdAt, updatedAt,
)

private fun HabitEntity.toBackup() = BackupHabit(
    id, name, iconKey, themeColor, categoryId, startEpochDay, archivedEpochDay, sortOrder, createdAt, updatedAt,
)

private fun CheckInEntity.toBackup() = BackupCheckIn(
    id, habitId, checkInEpochDay, createdAt, updatedAt,
)

private fun BackupCategory.toEntity() = CategoryEntity(
    id, name, isPreset, isHidden, sortOrder, createdAt, updatedAt,
)

private fun BackupHabit.toEntity() = HabitEntity(
    id, name, iconKey, themeColor, categoryId, startEpochDay, archivedEpochDay, sortOrder, createdAt, updatedAt,
)

private fun BackupCheckIn.toEntity() = CheckInEntity(
    id, habitId, checkInEpochDay, createdAt, updatedAt,
)
