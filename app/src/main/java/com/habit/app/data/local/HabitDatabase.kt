package com.habit.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Clock

val PRESET_CATEGORIES = listOf("学习", "运动", "生活", "健康", "其他")

@Database(
    entities = [
        CategoryEntity::class,
        HabitEntity::class,
        CheckInEntity::class,
        MealRecordEntity::class,
        FoodItemEntity::class,
        BeverageDetailEntity::class,
        BeverageToppingEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class HabitDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun habitDao(): HabitDao
    abstract fun checkInDao(): CheckInDao
    abstract fun dietDao(): DietDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `meal_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recordType` TEXT NOT NULL, `mealType` TEXT, `occurredAt` INTEGER NOT NULL, `recordEpochDay` INTEGER NOT NULL, `description` TEXT NOT NULL, `calculatedCalories` INTEGER, `finalCalories` INTEGER, `calorieSource` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_records_recordEpochDay` ON `meal_records` (`recordEpochDay`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_records_occurredAt` ON `meal_records` (`occurredAt`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `food_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mealRecordId` INTEGER NOT NULL, `name` TEXT NOT NULL, `portionText` TEXT, `calories` INTEGER, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`mealRecordId`) REFERENCES `meal_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_items_mealRecordId` ON `food_items` (`mealRecordId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `beverage_details` (`mealRecordId` INTEGER NOT NULL, `category` TEXT NOT NULL, `brandOrStore` TEXT NOT NULL, `beverageName` TEXT NOT NULL, `sizeOrVolume` TEXT NOT NULL, `temperature` TEXT NOT NULL, `iceLevel` TEXT NOT NULL, `sweetness` TEXT NOT NULL, `cupCount` INTEGER NOT NULL, PRIMARY KEY(`mealRecordId`), FOREIGN KEY(`mealRecordId`) REFERENCES `meal_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `beverage_toppings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mealRecordId` INTEGER NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`mealRecordId`) REFERENCES `meal_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_beverage_toppings_mealRecordId` ON `beverage_toppings` (`mealRecordId`)")
    }
}

class PresetCategoryCallback(
    private val clock: Clock,
) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val now = clock.millis()
        PRESET_CATEGORIES.forEachIndexed { index, name ->
            db.execSQL(
                "INSERT INTO categories(name,isPreset,isHidden,sortOrder,createdAt,updatedAt) VALUES(?,1,0,?,?,?)",
                arrayOf<Any>(name, index, now, now),
            )
        }
    }
}
