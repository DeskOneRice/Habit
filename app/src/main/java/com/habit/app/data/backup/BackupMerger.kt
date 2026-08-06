package com.habit.app.data.backup

import java.util.Locale

object BackupMerger {
    fun merge(current: HabitBackup, imported: HabitBackup): HabitBackup {
        HabitBackupCodec.validate(current)
        HabitBackupCodec.validate(imported)

        return mergeNormalized(current.normalizeDietCategories(), imported.normalizeDietCategories())
    }

    private fun mergeNormalized(current: HabitBackup, imported: HabitBackup): HabitBackup {

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

        val dietCategoryMerge = mergeDietCategories(current, imported)
        val importedWithMappedDietCategories = imported.copy(
            mealRecords = imported.mealRecords.map { record ->
                record.copy(dietCategoryId = record.dietCategoryId?.let { dietCategoryMerge.importedIdMap[it] ?: it })
            },
            dietTemplates = imported.dietTemplates.map { template ->
                template.copy(dietCategoryId = template.dietCategoryId?.let { dietCategoryMerge.importedIdMap[it] ?: it })
            },
        )
        val diet = mergeDiet(current, importedWithMappedDietCategories)
        val quick = mergeQuickCapture(current, importedWithMappedDietCategories, diet.records)
        val importedPreferencesAreNewer = imported.preferencesUpdatedAt > current.preferencesUpdatedAt
        return current.copy(
            schemaVersion = HABIT_BACKUP_SCHEMA_VERSION,
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
            dietCategories = dietCategoryMerge.categories,
            mealRecords = diet.records,
            foodItems = diet.foodItems,
            beverageDetails = diet.beverages,
            beverageToppings = diet.toppings,
            dietPhotos = quick.photos,
            dietTemplates = quick.templates,
            dietTemplateFoodItems = quick.foodItems,
            dietTemplateToppings = quick.toppings,
            preferences = if (importedPreferencesAreNewer) imported.preferences else current.preferences,
        )
    }
}

private data class MergedDietCategories(
    val categories: List<BackupDietCategory>,
    val importedIdMap: Map<Long, Long>,
)

private fun mergeDietCategories(current: HabitBackup, imported: HabitBackup): MergedDietCategories {
    val categories = current.dietCategories.toMutableList()
    val usedIds = categories.mapTo(mutableSetOf(), BackupDietCategory::id)
    var nextId = (usedIds.maxOrNull() ?: 0L) + 1
    val importedIdMap = mutableMapOf<Long, Long>()

    imported.dietCategories.forEach { incoming ->
        val matchIndex = categories.indexOfFirst { existing ->
            if (incoming.isPreset) {
                existing.isPreset && existing.id == incoming.id
            } else {
                (existing.id == incoming.id && existing.createdAt == incoming.createdAt) ||
                    (existing.scope == incoming.scope && existing.name.normalized() == incoming.name.normalized())
            }
        }
        if (matchIndex >= 0) {
            val existing = categories[matchIndex]
            importedIdMap[incoming.id] = existing.id
            if (incoming.updatedAt > existing.updatedAt) {
                categories[matchIndex] = incoming.copy(id = existing.id)
            }
        } else {
            val targetId = availableId(incoming.id, usedIds) { nextId++ }
            categories += incoming.copy(id = targetId)
            importedIdMap[incoming.id] = targetId
        }
    }

    return MergedDietCategories(
        categories = categories.sortedWith(compareBy(BackupDietCategory::scope, BackupDietCategory::sortOrder, BackupDietCategory::id)),
        importedIdMap = importedIdMap,
    )
}

private data class MergedQuickCapture(
    val photos: List<BackupDietPhoto>,
    val templates: List<BackupDietTemplate>,
    val foodItems: List<BackupDietTemplateFoodItem>,
    val toppings: List<BackupDietTemplateTopping>,
)

private fun mergeQuickCapture(
    current: HabitBackup,
    imported: HabitBackup,
    mergedRecords: List<BackupMealRecord>,
): MergedQuickCapture {
    val templates = current.dietTemplates.toMutableList()
    val usedTemplateIds = templates.mapTo(mutableSetOf(), BackupDietTemplate::id)
    var nextTemplateId = (usedTemplateIds.maxOrNull() ?: 0L) + 1
    val templateMap = mutableMapOf<Long, Long>()
    val replacedTemplates = mutableSetOf<Long>()
    imported.dietTemplates.forEach { incoming ->
        val index = templates.indexOfFirst { it.id == incoming.id && it.createdAt == incoming.createdAt }
        if (index >= 0) {
            val existing = templates[index]
            templateMap[incoming.id] = existing.id
            if (incoming.updatedAt >= existing.updatedAt) {
                templates[index] = incoming.copy(id = existing.id)
                replacedTemplates += existing.id
            }
        } else {
            val id = availableId(incoming.id, usedTemplateIds) { nextTemplateId++ }
            templates += incoming.copy(id = id)
            templateMap[incoming.id] = id
            replacedTemplates += id
        }
    }

    val foodItems = current.dietTemplateFoodItems.filterNot { it.templateId in replacedTemplates }.toMutableList()
    val usedFoodIds = foodItems.mapTo(mutableSetOf(), BackupDietTemplateFoodItem::id)
    var nextFoodId = (usedFoodIds.maxOrNull() ?: 0L) + 1
    imported.dietTemplateFoodItems.forEach { incoming ->
        val parent = templateMap[incoming.templateId] ?: return@forEach
        if (parent in replacedTemplates) {
            foodItems += incoming.copy(
                id = availableId(incoming.id, usedFoodIds) { nextFoodId++ },
                templateId = parent,
            )
        }
    }

    val toppings = current.dietTemplateToppings.filterNot { it.templateId in replacedTemplates }.toMutableList()
    val usedToppingIds = toppings.mapTo(mutableSetOf(), BackupDietTemplateTopping::id)
    var nextToppingId = (usedToppingIds.maxOrNull() ?: 0L) + 1
    imported.dietTemplateToppings.forEach { incoming ->
        val parent = templateMap[incoming.templateId] ?: return@forEach
        if (parent in replacedTemplates) {
            toppings += incoming.copy(
                id = availableId(incoming.id, usedToppingIds) { nextToppingId++ },
                templateId = parent,
            )
        }
    }

    val recordMap = imported.mealRecords.mapNotNull { incoming ->
        mergedRecords.firstOrNull {
            it.createdAt == incoming.createdAt && it.occurredAt == incoming.occurredAt && it.description == incoming.description
        }?.id?.let { incoming.id to it }
    }.toMap()
    val replacedRecords = recordMap.values.toSet()
    val photos = current.dietPhotos.filterNot {
        it.mealRecordId in replacedRecords || it.templateId in replacedTemplates
    }.toMutableList()
    val usedPhotoIds = photos.mapTo(mutableSetOf(), BackupDietPhoto::id)
    var nextPhotoId = (usedPhotoIds.maxOrNull() ?: 0L) + 1
    imported.dietPhotos.forEach { incoming ->
        val mealId = incoming.mealRecordId?.let(recordMap::get)
        val templateId = incoming.templateId?.let(templateMap::get)
        if ((mealId != null) == (templateId != null)) return@forEach
        photos += incoming.copy(
            id = availableId(incoming.id, usedPhotoIds) { nextPhotoId++ },
            mealRecordId = mealId,
            templateId = templateId,
        )
    }
    return MergedQuickCapture(
        photos = photos.sortedBy(BackupDietPhoto::id),
        templates = templates.sortedBy(BackupDietTemplate::id),
        foodItems = foodItems.sortedBy(BackupDietTemplateFoodItem::id),
        toppings = toppings.sortedBy(BackupDietTemplateTopping::id),
    )
}

private data class MergedDiet(
    val records: List<BackupMealRecord>,
    val foodItems: List<BackupFoodItem>,
    val beverages: List<BackupBeverageDetail>,
    val toppings: List<BackupBeverageTopping>,
)

private fun mergeDiet(current: HabitBackup, imported: HabitBackup): MergedDiet {
    val records = current.mealRecords.toMutableList()
    val usedRecordIds = records.mapTo(mutableSetOf(), BackupMealRecord::id)
    var nextRecordId = (usedRecordIds.maxOrNull() ?: 0L) + 1
    val recordMap = mutableMapOf<Long, Long>()
    val replaced = mutableSetOf<Long>()
    imported.mealRecords.forEach { incoming ->
        val index = records.indexOfFirst { it.id == incoming.id && it.createdAt == incoming.createdAt }
        if (index >= 0) {
            val existing = records[index]
            recordMap[incoming.id] = existing.id
            if (incoming.updatedAt > existing.updatedAt) {
                records[index] = incoming.copy(id = existing.id)
                replaced += existing.id
            }
        } else {
            val target = availableId(incoming.id, usedRecordIds) { nextRecordId++ }
            records += incoming.copy(id = target)
            recordMap[incoming.id] = target
            replaced += target
        }
    }

    val food = current.foodItems.filterNot { it.mealRecordId in replaced }.toMutableList()
    val usedFoodIds = food.mapTo(mutableSetOf(), BackupFoodItem::id)
    var nextFoodId = (usedFoodIds.maxOrNull() ?: 0L) + 1
    imported.foodItems.forEach { incoming ->
        val parent = recordMap[incoming.mealRecordId] ?: return@forEach
        if (parent in replaced) {
            val id = availableId(incoming.id, usedFoodIds) { nextFoodId++ }
            food += incoming.copy(id = id, mealRecordId = parent)
        }
    }

    val beverages = current.beverageDetails.filterNot { it.mealRecordId in replaced }.toMutableList()
    imported.beverageDetails.forEach { incoming ->
        val parent = recordMap[incoming.mealRecordId] ?: return@forEach
        if (parent in replaced) beverages += incoming.copy(mealRecordId = parent)
    }

    val toppings = current.beverageToppings.filterNot { it.mealRecordId in replaced }.toMutableList()
    val usedToppingIds = toppings.mapTo(mutableSetOf(), BackupBeverageTopping::id)
    var nextToppingId = (usedToppingIds.maxOrNull() ?: 0L) + 1
    imported.beverageToppings.forEach { incoming ->
        val parent = recordMap[incoming.mealRecordId] ?: return@forEach
        if (parent in replaced) {
            val id = availableId(incoming.id, usedToppingIds) { nextToppingId++ }
            toppings += incoming.copy(id = id, mealRecordId = parent)
        }
    }
    return MergedDiet(
        records.sortedBy(BackupMealRecord::id),
        food.sortedBy(BackupFoodItem::id),
        beverages.sortedBy(BackupBeverageDetail::mealRecordId),
        toppings.sortedBy(BackupBeverageTopping::id),
    )
}

private inline fun availableId(requested: Long, used: MutableSet<Long>, next: () -> Long): Long {
    if (requested > 0 && used.add(requested)) return requested
    var candidate: Long
    do candidate = next() while (!used.add(candidate))
    return candidate
}

private fun String.normalized(): String = trim().lowercase(Locale.CHINA)
