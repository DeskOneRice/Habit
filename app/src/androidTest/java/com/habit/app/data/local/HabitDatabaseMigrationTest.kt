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
    fun migrateTwoToThreePreservesMealAndCreatesQuickCaptureTables() {
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
        ).addMigrations(MIGRATION_2_3).build()
        val sqlite = database.openHelper.writableDatabase

        sqlite.query("SELECT description FROM meal_records WHERE id = 1").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("旧版本午餐", cursor.getString(0))
        }
        assertNotNull(sqlite.query("SELECT * FROM diet_templates"))
        assertNotNull(sqlite.query("SELECT * FROM diet_photos"))
        database.close()
    }
}
