package com.habit.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Clock

val PRESET_CATEGORIES = listOf("学习", "运动", "生活", "健康", "其他")

@Database(
    entities = [CategoryEntity::class, HabitEntity::class, CheckInEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class HabitDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun habitDao(): HabitDao
    abstract fun checkInDao(): CheckInDao
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
