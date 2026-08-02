package com.habit.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isPreset: Boolean,
    val isHidden: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "habits",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId")],
)
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(
    tableName = "check_ins",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["habitId", "checkInEpochDay"], unique = true)],
)
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val checkInEpochDay: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "meal_records",
    indices = [Index("recordEpochDay"), Index("occurredAt")],
)
data class MealRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordType: String,
    val mealType: String?,
    val occurredAt: Long,
    val recordEpochDay: Long,
    val description: String,
    val calculatedCalories: Int?,
    val finalCalories: Int?,
    val calorieSource: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "food_items",
    foreignKeys = [
        ForeignKey(
            entity = MealRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealRecordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mealRecordId")],
)
data class FoodItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealRecordId: Long,
    val name: String,
    val portionText: String?,
    val calories: Int?,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "beverage_details",
    foreignKeys = [
        ForeignKey(
            entity = MealRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealRecordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BeverageDetailEntity(
    @PrimaryKey val mealRecordId: Long,
    val category: String,
    val brandOrStore: String,
    val beverageName: String,
    val sizeOrVolume: String,
    val temperature: String,
    val iceLevel: String,
    val sweetness: String,
    val cupCount: Int,
)

@Entity(
    tableName = "beverage_toppings",
    foreignKeys = [
        ForeignKey(
            entity = MealRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealRecordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mealRecordId")],
)
data class BeverageToppingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealRecordId: Long,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)
