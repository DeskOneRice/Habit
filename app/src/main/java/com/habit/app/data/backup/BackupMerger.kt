package com.habit.app.data.backup

import java.util.Locale

object BackupMerger {
    fun merge(current: HabitBackup, imported: HabitBackup): HabitBackup {
        HabitBackupCodec.validate(current)
        HabitBackupCodec.validate(imported)

        val categories = current.categories.toMutableList()
        val categoryIds = categories.mapTo(mutableSetOf(), BackupCategory::id)
        var nextCategoryId = (categoryIds.maxOrNull() ?: 0L) + 1
        val categoryMap = mutableMapOf<Long, Long>()

        imported.categories.forEach { incoming ->
            val matchIndex = categories.indexOfFirst { existing ->
                if (incoming.isPreset) {
                    existing.isPreset && existing.name.normalized() == incoming.name.normalized()
                } else {
                    (existing.id == incoming.id && existing.createdAt == incoming.createdAt) ||
                        (!existing.isPreset && existing.name.normalized() == incoming.name.normalized())
                }
            }
            if (matchIndex >= 0) {
                val existing = categories[matchIndex]
                categoryMap[incoming.id] = existing.id
                if (incoming.updatedAt > existing.updatedAt) {
                    categories[matchIndex] = incoming.copy(id = existing.id)
                }
            } else {
                val targetId = availableId(incoming.id, categoryIds) { nextCategoryId++ }
                categories += incoming.copy(id = targetId)
                categoryMap[incoming.id] = targetId
            }
        }

        val habits = current.habits.toMutableList()
        val habitIds = habits.mapTo(mutableSetOf(), BackupHabit::id)
        var nextHabitId = (habitIds.maxOrNull() ?: 0L) + 1
        val habitMap = mutableMapOf<Long, Long>()

        imported.habits.forEach { incoming ->
            val mappedCategoryId = categoryMap.getValue(incoming.categoryId)
            val matchIndex = habits.indexOfFirst { existing ->
                existing.id == incoming.id && existing.createdAt == incoming.createdAt
            }
            if (matchIndex >= 0) {
                val existing = habits[matchIndex]
                habitMap[incoming.id] = existing.id
                if (incoming.updatedAt > existing.updatedAt) {
                    habits[matchIndex] = incoming.copy(id = existing.id, categoryId = mappedCategoryId)
                }
            } else {
                val targetId = availableId(incoming.id, habitIds) { nextHabitId++ }
                habits += incoming.copy(id = targetId, categoryId = mappedCategoryId)
                habitMap[incoming.id] = targetId
            }
        }

        val checkIns = current.checkIns.toMutableList()
        val checkInIds = checkIns.mapTo(mutableSetOf(), BackupCheckIn::id)
        var nextCheckInId = (checkInIds.maxOrNull() ?: 0L) + 1

        imported.checkIns.forEach { incoming ->
            val mappedHabitId = habitMap.getValue(incoming.habitId)
            val matchIndex = checkIns.indexOfFirst { existing ->
                existing.habitId == mappedHabitId && existing.checkInEpochDay == incoming.checkInEpochDay
            }
            if (matchIndex >= 0) {
                val existing = checkIns[matchIndex]
                if (incoming.updatedAt > existing.updatedAt) {
                    checkIns[matchIndex] = incoming.copy(id = existing.id, habitId = mappedHabitId)
                }
            } else {
                val targetId = availableId(incoming.id, checkInIds) { nextCheckInId++ }
                checkIns += incoming.copy(id = targetId, habitId = mappedHabitId)
            }
        }

        val importedPreferencesAreNewer = imported.preferencesUpdatedAt > current.preferencesUpdatedAt
        return current.copy(
            appVersion = imported.appVersion,
            exportedAt = maxOf(current.exportedAt, imported.exportedAt),
            preferencesUpdatedAt = if (importedPreferencesAreNewer) {
                imported.preferencesUpdatedAt
            } else {
                current.preferencesUpdatedAt
            },
            categories = categories.sortedBy(BackupCategory::id),
            habits = habits.sortedBy(BackupHabit::id),
            checkIns = checkIns.sortedBy(BackupCheckIn::id),
            preferences = if (importedPreferencesAreNewer) imported.preferences else current.preferences,
        )
    }
}

private inline fun availableId(requested: Long, used: MutableSet<Long>, next: () -> Long): Long {
    if (requested > 0 && used.add(requested)) return requested
    var candidate: Long
    do candidate = next() while (!used.add(candidate))
    return candidate
}

private fun String.normalized(): String = trim().lowercase(Locale.CHINA)
