package com.habit.app.data.backup

import androidx.room.withTransaction
import com.habit.app.data.local.CategoryEntity
import com.habit.app.data.local.CheckInEntity
import com.habit.app.data.local.HabitDatabase
import com.habit.app.data.local.HabitEntity
import com.habit.app.data.local.MealRecordEntity
import com.habit.app.data.local.FoodItemEntity
import com.habit.app.data.local.BeverageDetailEntity
import com.habit.app.data.local.BeverageToppingEntity
import com.habit.app.data.local.DietPhotoEntity
import com.habit.app.data.local.DietTemplateEntity
import com.habit.app.data.local.DietTemplateFoodItemEntity
import com.habit.app.data.local.DietTemplateToppingEntity

enum class ImportMode { REPLACE, MERGE }

data class BackupDatabaseSnapshot(
    val categories: List<BackupCategory>,
    val habits: List<BackupHabit>,
    val checkIns: List<BackupCheckIn>,
    val mealRecords: List<BackupMealRecord>,
    val foodItems: List<BackupFoodItem>,
    val beverageDetails: List<BackupBeverageDetail>,
    val beverageToppings: List<BackupBeverageTopping>,
    val dietPhotos: List<BackupDietPhoto>,
    val dietTemplates: List<BackupDietTemplate>,
    val dietTemplateFoodItems: List<BackupDietTemplateFoodItem>,
    val dietTemplateToppings: List<BackupDietTemplateTopping>,
)

data class ImportSummary(
    val categories: Int,
    val habits: Int,
    val checkIns: Int,
    val mealRecords: Int = 0,
    val beverages: Int = 0,
)

class RoomBackupRepository(private val database: HabitDatabase) {
    suspend fun referencedPhotoPaths(): Set<String> = database.dietDao().getAllPhotoPaths().toSet()
    suspend fun exportDatabase(): BackupDatabaseSnapshot = database.withTransaction {
        val diet = database.dietDao().getAll()
        val templates = database.dietDao().getAllTemplates()
        BackupDatabaseSnapshot(
            categories = database.categoryDao().getAll().map(CategoryEntity::toBackup),
            habits = database.habitDao().getAll().map(HabitEntity::toBackup),
            checkIns = database.checkInDao().getAll().map(CheckInEntity::toBackup),
            mealRecords = diet.map { it.record.toBackup() },
            foodItems = diet.flatMap { it.foodItems }.map(FoodItemEntity::toBackup),
            beverageDetails = diet.mapNotNull { it.beverageDetails }.map(BeverageDetailEntity::toBackup),
            beverageToppings = diet.flatMap { it.toppings }.map(BeverageToppingEntity::toBackup),
            dietPhotos = (diet.flatMap { it.photos } + templates.flatMap { it.photos }).distinctBy { it.id }.map(DietPhotoEntity::toBackup),
            dietTemplates = templates.map { it.template.toBackup() },
            dietTemplateFoodItems = templates.flatMap { it.foodItems }.map(DietTemplateFoodItemEntity::toBackup),
            dietTemplateToppings = templates.flatMap { it.toppings }.map(DietTemplateToppingEntity::toBackup),
        )
    }

    suspend fun importDatabase(backup: HabitBackup, mode: ImportMode): ImportSummary {
        HabitBackupCodec.validate(backup)
        return database.withTransaction {
            val target = when (mode) {
                ImportMode.REPLACE -> backup
                ImportMode.MERGE -> {
                    val current = exportDatabase().toHabitBackup(backup)
                    BackupMerger.merge(current, backup)
                }
            }
            database.checkInDao().deleteAll()
            database.dietDao().deleteAll()
            database.dietDao().deleteAllTemplates()
            database.habitDao().deleteAll()
            database.categoryDao().deleteAll()
            database.categoryDao().insertAll(target.categories.map(BackupCategory::toEntity))
            database.habitDao().insertAll(target.habits.map(BackupHabit::toEntity))
            database.checkInDao().insertAll(target.checkIns.map(BackupCheckIn::toEntity))
            database.dietDao().insertRecords(target.mealRecords.map(BackupMealRecord::toEntity))
            database.dietDao().insertFoodItems(target.foodItems.map(BackupFoodItem::toEntity))
            database.dietDao().insertBeverages(target.beverageDetails.map(BackupBeverageDetail::toEntity))
            database.dietDao().insertToppings(target.beverageToppings.map(BackupBeverageTopping::toEntity))
            database.dietDao().insertTemplates(target.dietTemplates.map(BackupDietTemplate::toEntity))
            database.dietDao().insertTemplateFoodItems(target.dietTemplateFoodItems.map(BackupDietTemplateFoodItem::toEntity))
            database.dietDao().insertTemplateToppings(target.dietTemplateToppings.map(BackupDietTemplateTopping::toEntity))
            database.dietDao().insertPhotos(target.dietPhotos.map(BackupDietPhoto::toEntity))
            ImportSummary(target.categories.size, target.habits.size, target.checkIns.size, target.mealRecords.size, target.beverageDetails.size)
        }
    }
}

private fun BackupDatabaseSnapshot.toHabitBackup(template: HabitBackup) = template.copy(
    categories = categories,
    habits = habits,
    checkIns = checkIns,
    mealRecords = mealRecords,
    foodItems = foodItems,
    beverageDetails = beverageDetails,
    beverageToppings = beverageToppings,
    dietPhotos = dietPhotos,
    dietTemplates = dietTemplates,
    dietTemplateFoodItems = dietTemplateFoodItems,
    dietTemplateToppings = dietTemplateToppings,
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
private fun MealRecordEntity.toBackup() = BackupMealRecord(id, recordType, mealType, occurredAt, recordEpochDay, description, calculatedCalories, finalCalories, calorieSource, note, createdAt, updatedAt)
private fun FoodItemEntity.toBackup() = BackupFoodItem(id, mealRecordId, name, portionText, calories, sortOrder, createdAt, updatedAt)
private fun BeverageDetailEntity.toBackup() = BackupBeverageDetail(mealRecordId, category, brandOrStore, beverageName, sizeOrVolume, temperature, iceLevel, sweetness, cupCount)
private fun BeverageToppingEntity.toBackup() = BackupBeverageTopping(id, mealRecordId, name, sortOrder, createdAt, updatedAt)
private fun DietPhotoEntity.toBackup() = BackupDietPhoto(id, mealRecordId, templateId, relativePath, sortOrder, createdAt)
private fun DietTemplateEntity.toBackup() = BackupDietTemplate(id, name, recordType, mealType, description, manualFinalCalories, beverageCategory, brandOrStore, beverageName, sizeOrVolume, temperature, iceLevel, sweetness, cupCount, note, sortOrder, createdAt, updatedAt)
private fun DietTemplateFoodItemEntity.toBackup() = BackupDietTemplateFoodItem(id, templateId, name, portionText, calories, sortOrder, createdAt, updatedAt)
private fun DietTemplateToppingEntity.toBackup() = BackupDietTemplateTopping(id, templateId, name, sortOrder, createdAt, updatedAt)

private fun BackupCategory.toEntity() = CategoryEntity(
    id, name, isPreset, isHidden, sortOrder, createdAt, updatedAt,
)

private fun BackupHabit.toEntity() = HabitEntity(
    id, name, iconKey, themeColor, categoryId, startEpochDay, archivedEpochDay, sortOrder, createdAt, updatedAt,
)

private fun BackupCheckIn.toEntity() = CheckInEntity(
    id, habitId, checkInEpochDay, createdAt, updatedAt,
)
private fun BackupMealRecord.toEntity() = MealRecordEntity(id, recordType, mealType, occurredAt, recordEpochDay, description, calculatedCalories, finalCalories, calorieSource, note, createdAt, updatedAt)
private fun BackupFoodItem.toEntity() = FoodItemEntity(id, mealRecordId, name, portionText, calories, sortOrder, createdAt, updatedAt)
private fun BackupBeverageDetail.toEntity() = BeverageDetailEntity(mealRecordId, category, brandOrStore, beverageName, sizeOrVolume, temperature, iceLevel, sweetness, cupCount)
private fun BackupBeverageTopping.toEntity() = BeverageToppingEntity(id, mealRecordId, name, sortOrder, createdAt, updatedAt)
private fun BackupDietPhoto.toEntity() = DietPhotoEntity(id, mealRecordId, templateId, relativePath, sortOrder, createdAt)
private fun BackupDietTemplate.toEntity() = DietTemplateEntity(id, name, recordType, mealType, description, manualFinalCalories, beverageCategory, brandOrStore, beverageName, sizeOrVolume, temperature, iceLevel, sweetness, cupCount, note, sortOrder, createdAt, updatedAt)
private fun BackupDietTemplateFoodItem.toEntity() = DietTemplateFoodItemEntity(id, templateId, name, portionText, calories, sortOrder, createdAt, updatedAt)
private fun BackupDietTemplateTopping.toEntity() = DietTemplateToppingEntity(id, templateId, name, sortOrder, createdAt, updatedAt)
