package com.habit.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.data.local.HabitDatabase
import com.habit.app.domain.model.BeverageCategory
import com.habit.app.domain.model.BeverageDetails
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.FoodItemDraft
import com.habit.app.domain.model.MealRecordDraft
import com.habit.app.domain.model.MealType
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDietRepositoryTest {
    private lateinit var database: HabitDatabase
    private lateinit var repository: RoomDietRepository

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HabitDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomDietRepository(
            database,
            Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC),
        )
    }

    @After
    fun close() = database.close()

    @Test
    fun saveAndEditMealReplacesChildren() = runTest {
        val id = repository.save(null, mealDraft("米饭", 230).copy(dietCategoryId = 2))
        repository.save(id, mealDraft("面条", 410).copy(dietCategoryId = 3))

        val saved = repository.observeRecord(id).first()!!

        assertEquals(listOf("面条"), saved.foodItems.map { it.name })
        assertEquals(410, saved.finalCalories)
        assertEquals(1_000, saved.createdAt)
        assertEquals(3, saved.dietCategoryId)
    }

    @Test
    fun beverageAttributesAndToppingsRoundTrip() = runTest {
        val draft = MealRecordDraft(
            recordType = DietRecordType.BEVERAGE,
            mealType = null,
            occurredAt = 200,
            recordEpochDay = 20,
            description = "",
            foodItems = emptyList(),
            manualFinalCalories = 260,
            beverage = BeverageDetails(
                category = BeverageCategory.MILK_TEA,
                brandOrStore = "茶铺",
                beverageName = "奶茶",
                sizeOrVolume = "中杯",
                temperature = "冷",
                iceLevel = "少冰",
                sweetness = "三分糖",
                toppings = listOf("珍珠", "椰果"),
                cupCount = 2,
            ),
            note = "",
        )

        val id = repository.save(null, draft)
        val saved = repository.observeRecord(id).first()!!

        assertEquals(2, saved.beverage!!.cupCount)
        assertEquals(listOf("珍珠", "椰果"), saved.beverage!!.toppings)
        assertNull(saved.mealType)
    }

    private fun mealDraft(name: String, calories: Int) = MealRecordDraft(
        recordType = DietRecordType.MEAL,
        mealType = MealType.LUNCH,
        occurredAt = 200,
        recordEpochDay = 20,
        description = name,
        foodItems = listOf(FoodItemDraft(name, "1 份", calories)),
        manualFinalCalories = null,
        beverage = null,
        note = "",
    )
}
