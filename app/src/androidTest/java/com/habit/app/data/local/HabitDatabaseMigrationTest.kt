package com.habit.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitDatabaseMigrationTest {
    private val databaseName = "habit-migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HabitDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After
    fun cleanUp() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .deleteDatabase(databaseName)
    }

    @Test
    fun migrateTwoToFourPreservesMealAndCreatesQuickCaptureTables() {
        helper.createDatabase(databaseName, 2).apply {
            execSQL(
                "INSERT INTO meal_records (id,recordType,mealType,occurredAt,recordEpochDay,description,calculatedCalories,finalCalories,calorieSource,note,createdAt,updatedAt) VALUES (1,'MEAL','LUNCH',100,10,'旧版本午餐',320,320,'ITEM_SUM','',100,100)",
            )
            close()
        }

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HabitDatabase::class.java,
            databaseName,
        ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
        val sqlite = database.openHelper.writableDatabase

        sqlite.query("SELECT description FROM meal_records WHERE id = 1").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("旧版本午餐", cursor.getString(0))
        }
        sqlite.query(
            "SELECT name FROM diet_categories WHERE id=(SELECT dietCategoryId FROM meal_records WHERE id=1)",
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("其他餐食", cursor.getString(0))
        }
        assertNotNull(sqlite.query("SELECT * FROM diet_templates"))
        assertNotNull(sqlite.query("SELECT * FROM diet_photos"))
        database.close()
    }

    @Test
    fun migrateThreeToFourPreservesDietDataAndMapsCategories() {
        helper.createDatabase(databaseName, 3).apply {
            execSQL(
                "INSERT INTO meal_records (id,recordType,mealType,occurredAt,recordEpochDay,description,calculatedCalories,finalCalories,calorieSource,note,createdAt,updatedAt) VALUES (1,'MEAL','DINNER',1000,1,'旧版本晚餐',500,500,'MANUAL','',10,20)",
            )
            execSQL(
                "INSERT INTO meal_records (id,recordType,mealType,occurredAt,recordEpochDay,description,calculatedCalories,finalCalories,calorieSource,note,createdAt,updatedAt) VALUES (2,'BEVERAGE',NULL,2000,1,'旧版本咖啡',NULL,NULL,'NONE','',11,21)",
            )
            execSQL(
                "INSERT INTO beverage_details (mealRecordId,category,brandOrStore,beverageName,sizeOrVolume,temperature,iceLevel,sweetness,cupCount) VALUES (2,'COFFEE','咖啡店','美式','大杯','热','','',1)",
            )
            execSQL(
                "INSERT INTO diet_templates (id,name,recordType,mealType,description,manualFinalCalories,beverageCategory,brandOrStore,beverageName,sizeOrVolume,temperature,iceLevel,sweetness,cupCount,note,sortOrder,createdAt,updatedAt) VALUES (1,'常用奶茶','BEVERAGE',NULL,'',NULL,'MILK_TEA','店','奶茶','中杯','冰','少冰','半糖',1,'',0,12,22)",
            )
            close()
        }

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HabitDatabase::class.java,
            databaseName,
        ).addMigrations(MIGRATION_3_4, MIGRATION_4_5).build()
        val sqlite = database.openHelper.writableDatabase

        sqlite.query(
            "SELECT name FROM diet_categories WHERE id=(SELECT dietCategoryId FROM meal_records WHERE id=1)",
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("其他餐食", cursor.getString(0))
        }
        sqlite.query(
            "SELECT name FROM diet_categories WHERE id=(SELECT dietCategoryId FROM meal_records WHERE id=2)",
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("咖啡", cursor.getString(0))
        }
        sqlite.query(
            "SELECT name FROM diet_categories WHERE id=(SELECT dietCategoryId FROM diet_templates WHERE id=1)",
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("奶茶", cursor.getString(0))
        }
        sqlite.query("SELECT createdAt,updatedAt FROM meal_records WHERE id=1").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(10L, cursor.getLong(0))
            assertEquals(20L, cursor.getLong(1))
        }
        database.close()
    }

    @Test
    fun migrateFourToFivePreservesExistingDataAndCreatesAiTables() {
        helper.createDatabase(databaseName, 4).apply {
            execSQL("INSERT INTO categories (id,name,isPreset,isHidden,sortOrder,createdAt,updatedAt) VALUES (1,'Health',0,0,0,1,1)")
            execSQL("INSERT INTO habits (id,name,iconKey,themeColor,categoryId,startEpochDay,archivedEpochDay,sortOrder,createdAt,updatedAt) VALUES (1,'Walk','walk',0,1,1,NULL,0,1,1)")
            execSQL("INSERT INTO check_ins (id,habitId,checkInEpochDay,createdAt,updatedAt) VALUES (1,1,1,1,1)")
            execSQL("INSERT INTO meal_records (id,recordType,mealType,occurredAt,recordEpochDay,description,calculatedCalories,finalCalories,calorieSource,note,createdAt,updatedAt,dietCategoryId) VALUES (1,'MEAL','LUNCH',1,1,'Lunch',100,100,'ITEM_SUM','',1,1,4)")
            execSQL("INSERT INTO diet_photos (id,mealRecordId,templateId,relativePath,sortOrder,createdAt) VALUES (1,1,NULL,'photos/one.jpg',0,1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(databaseName, 5, true, MIGRATION_4_5)

        assertEquals(1, db.query("SELECT COUNT(*) FROM categories").singleInt())
        assertEquals(1, db.query("SELECT COUNT(*) FROM habits").singleInt())
        assertEquals(1, db.query("SELECT COUNT(*) FROM check_ins").singleInt())
        assertEquals(1, db.query("SELECT COUNT(*) FROM meal_records").singleInt())
        assertEquals(1, db.query("SELECT COUNT(*) FROM diet_photos").singleInt())
        assertEquals(0, db.query("SELECT COUNT(*) FROM ai_model_configs").singleInt())
        assertEquals(0, db.query("SELECT COUNT(*) FROM ai_weekly_reports").singleInt())
        db.close()
    }

    private fun android.database.Cursor.singleInt(): Int = use {
        check(moveToFirst())
        getInt(0)
    }
}
