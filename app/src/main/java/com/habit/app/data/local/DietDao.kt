package com.habit.app.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class MealRecordWithDetails(
    @Embedded val record: MealRecordEntity,
    @Relation(parentColumn = "id", entityColumn = "mealRecordId")
    val foodItems: List<FoodItemEntity>,
    @Relation(parentColumn = "id", entityColumn = "mealRecordId")
    val beverageDetails: BeverageDetailEntity?,
    @Relation(parentColumn = "id", entityColumn = "mealRecordId")
    val toppings: List<BeverageToppingEntity>,
    @Relation(parentColumn = "id", entityColumn = "mealRecordId")
    val photos: List<DietPhotoEntity>,
)

data class DietTemplateWithDetails(
    @Embedded val template: DietTemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "templateId")
    val foodItems: List<DietTemplateFoodItemEntity>,
    @Relation(parentColumn = "id", entityColumn = "templateId")
    val toppings: List<DietTemplateToppingEntity>,
    @Relation(parentColumn = "id", entityColumn = "templateId")
    val photos: List<DietPhotoEntity>,
)

@Dao
interface DietDao {
    @Transaction
    @Query("SELECT * FROM meal_records ORDER BY recordEpochDay DESC, occurredAt ASC, id ASC")
    fun observeAllRecords(): Flow<List<MealRecordWithDetails>>

    @Transaction
    @Query("SELECT * FROM meal_records WHERE recordEpochDay = :epochDay ORDER BY occurredAt, id")
    fun observeDay(epochDay: Long): Flow<List<MealRecordWithDetails>>

    @Transaction
    @Query("SELECT * FROM meal_records WHERE id = :id")
    fun observeRecord(id: Long): Flow<MealRecordWithDetails?>

    @Transaction
    @Query("SELECT * FROM meal_records WHERE recordEpochDay BETWEEN :startDay AND :endDay ORDER BY recordEpochDay, occurredAt, id")
    fun observeRange(startDay: Long, endDay: Long): Flow<List<MealRecordWithDetails>>

    @Query("SELECT * FROM meal_records WHERE id = :id")
    suspend fun getRecordEntity(id: Long): MealRecordEntity?

    @Transaction
    @Query("SELECT * FROM meal_records ORDER BY id")
    suspend fun getAll(): List<MealRecordWithDetails>

    @Insert
    suspend fun insertRecord(record: MealRecordEntity): Long

    @Insert
    suspend fun insertRecords(records: List<MealRecordEntity>)

    @Update
    suspend fun updateRecord(record: MealRecordEntity): Int

    @Insert
    suspend fun insertFoodItems(items: List<FoodItemEntity>)

    @Insert
    suspend fun insertBeverage(details: BeverageDetailEntity)

    @Insert
    suspend fun insertBeverages(details: List<BeverageDetailEntity>)

    @Insert
    suspend fun insertToppings(items: List<BeverageToppingEntity>)

    @Insert
    suspend fun insertPhotos(items: List<DietPhotoEntity>)

    @Query("DELETE FROM diet_photos WHERE mealRecordId = :recordId")
    suspend fun deleteRecordPhotos(recordId: Long)

    @Query("SELECT relativePath FROM diet_photos")
    suspend fun getAllPhotoPaths(): List<String>

    @Transaction
    @Query("SELECT * FROM diet_templates ORDER BY recordType, sortOrder, id")
    fun observeTemplates(): Flow<List<DietTemplateWithDetails>>

    @Transaction
    @Query("SELECT * FROM diet_templates WHERE id = :id")
    suspend fun getTemplate(id: Long): DietTemplateWithDetails?

    @Query("SELECT * FROM diet_templates WHERE id = :id")
    suspend fun getTemplateEntity(id: Long): DietTemplateEntity?

    @Transaction
    @Query("SELECT * FROM diet_templates ORDER BY id")
    suspend fun getAllTemplates(): List<DietTemplateWithDetails>

    @Insert
    suspend fun insertTemplate(template: DietTemplateEntity): Long

    @Insert
    suspend fun insertTemplates(templates: List<DietTemplateEntity>)

    @Update
    suspend fun updateTemplate(template: DietTemplateEntity): Int

    @Query("UPDATE diet_templates SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameTemplate(id: Long, name: String, updatedAt: Long): Int

    @Query("UPDATE diet_templates SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTemplateSortOrder(id: Long, sortOrder: Int, updatedAt: Long): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM diet_templates WHERE recordType = :recordType")
    suspend fun nextTemplateSortOrder(recordType: String): Int

    @Insert
    suspend fun insertTemplateFoodItems(items: List<DietTemplateFoodItemEntity>)

    @Insert
    suspend fun insertTemplateToppings(items: List<DietTemplateToppingEntity>)

    @Query("DELETE FROM diet_template_food_items WHERE templateId = :templateId")
    suspend fun deleteTemplateFoodItems(templateId: Long)

    @Query("DELETE FROM diet_template_toppings WHERE templateId = :templateId")
    suspend fun deleteTemplateToppings(templateId: Long)

    @Query("DELETE FROM diet_photos WHERE templateId = :templateId")
    suspend fun deleteTemplatePhotos(templateId: Long)

    @Query("DELETE FROM diet_templates WHERE id = :templateId")
    suspend fun deleteTemplate(templateId: Long): Int

    @Query("DELETE FROM diet_templates")
    suspend fun deleteAllTemplates(): Int

    @Query("DELETE FROM food_items WHERE mealRecordId = :recordId")
    suspend fun deleteFoodItems(recordId: Long)

    @Query("DELETE FROM beverage_details WHERE mealRecordId = :recordId")
    suspend fun deleteBeverage(recordId: Long)

    @Query("DELETE FROM beverage_toppings WHERE mealRecordId = :recordId")
    suspend fun deleteToppings(recordId: Long)

    @Query("DELETE FROM meal_records WHERE id = :recordId")
    suspend fun deleteRecord(recordId: Long): Int

    @Query("DELETE FROM meal_records")
    suspend fun deleteAll(): Int
}
