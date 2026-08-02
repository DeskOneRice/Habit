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
)

@Dao
interface DietDao {
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

    @Update
    suspend fun updateRecord(record: MealRecordEntity): Int

    @Insert
    suspend fun insertFoodItems(items: List<FoodItemEntity>)

    @Insert
    suspend fun insertBeverage(details: BeverageDetailEntity)

    @Insert
    suspend fun insertToppings(items: List<BeverageToppingEntity>)

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
