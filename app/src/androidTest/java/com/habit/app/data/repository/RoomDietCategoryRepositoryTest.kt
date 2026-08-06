package com.habit.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.data.local.DietCategoryEntity
import com.habit.app.data.local.DietTemplateEntity
import com.habit.app.data.local.HabitDatabase
import com.habit.app.data.local.MealRecordEntity
import com.habit.app.domain.model.DietCategoryScope
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDietCategoryRepositoryTest {
    private lateinit var database: HabitDatabase
    private lateinit var repository: RoomDietCategoryRepository

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HabitDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomDietCategoryRepository(
            database,
            Clock.fixed(Instant.ofEpochMilli(500), ZoneOffset.UTC),
        )
    }

    @After
    fun close() = database.close()

    @Test
    fun migrateAndDeleteMovesRecordsAndTemplatesAtomically() = runTest {
        val source = repository.create(DietCategoryScope.MEAL, "外卖")
        val target = repository.create(DietCategoryScope.MEAL, "快手餐")
        database.dietDao().insertRecord(mealRecord(source))
        database.dietDao().insertTemplate(dietTemplate(source))

        repository.migrateAndDelete(source, target)

        assertNull(database.dietCategoryDao().get(source))
        assertEquals(target, database.dietDao().getRecordEntity(1)!!.dietCategoryId)
        assertEquals(target, database.dietDao().getTemplateEntity(1)!!.dietCategoryId)
    }

    @Test
    fun crossScopeMigrationIsRejectedWithoutChangingData() = runTest {
        val meal = repository.create(DietCategoryScope.MEAL, "外卖")
        val beverage = repository.create(DietCategoryScope.BEVERAGE, "苏打水")
        database.dietDao().insertRecord(mealRecord(meal))

        val error = runCatching { repository.migrateAndDelete(meal, beverage) }.exceptionOrNull()

        assertEquals("只能迁移到同类型分类", error?.message)
        assertEquals(meal, database.dietDao().getRecordEntity(1)!!.dietCategoryId)
        assertTrue(repository.observeAll(DietCategoryScope.MEAL).first().any { it.id == meal })
    }

    @Test
    fun presetCanBeRenamedHiddenAndRestored() = runTest {
        database.dietCategoryDao().insert(
            DietCategoryEntity(
                scope = DietCategoryScope.BEVERAGE.name,
                name = "其他饮品",
                isPreset = true,
                isHidden = false,
                sortOrder = 1,
                createdAt = 100,
                updatedAt = 100,
            ),
        )
        val id = database.dietCategoryDao().insert(
            DietCategoryEntity(
                scope = DietCategoryScope.BEVERAGE.name,
                name = "咖啡",
                isPreset = true,
                isHidden = false,
                sortOrder = 0,
                createdAt = 100,
                updatedAt = 100,
            ),
        )

        repository.rename(id, "手冲咖啡")
        repository.setHidden(id, true)
        assertTrue(repository.observeVisible(DietCategoryScope.BEVERAGE).first().none { it.id == id })
        repository.setHidden(id, false)

        val restored = repository.observeVisible(DietCategoryScope.BEVERAGE).first().single { it.id == id }
        assertEquals("手冲咖啡", restored.name)
        assertTrue(restored.isPreset)
    }

    private fun mealRecord(categoryId: Long) = MealRecordEntity(
        id = 1, recordType = "MEAL", mealType = "LUNCH", occurredAt = 100,
        recordEpochDay = 1, description = "午餐", calculatedCalories = null,
        finalCalories = null, calorieSource = "NONE", note = "", createdAt = 100,
        updatedAt = 100, dietCategoryId = categoryId,
    )

    private fun dietTemplate(categoryId: Long) = DietTemplateEntity(
        id = 1, name = "常用午餐", recordType = "MEAL", mealType = "LUNCH",
        description = "午餐", manualFinalCalories = null, beverageCategory = null,
        brandOrStore = null, beverageName = null, sizeOrVolume = null, temperature = null,
        iceLevel = null, sweetness = null, cupCount = null, note = "", sortOrder = 0,
        createdAt = 100, updatedAt = 100, dietCategoryId = categoryId,
    )
}
