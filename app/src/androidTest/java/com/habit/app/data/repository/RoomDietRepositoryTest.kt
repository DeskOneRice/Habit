package com.habit.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.data.local.HabitDatabase
import com.habit.app.domain.model.BeverageCategory
import com.habit.app.domain.model.BeverageDetails
import com.habit.app.domain.model.CalorieSource
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.FoodItemDraft
import com.habit.app.domain.model.MealRecordDraft
import com.habit.app.domain.model.MealType
import com.habit.app.domain.model.AiCalorieEstimateDraft
import com.habit.app.domain.model.AiCalorieItemEstimate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
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
            aiCalorieEstimate = estimate("vision-1", 260),
        )

        val id = repository.save(null, draft)
        val saved = repository.observeRecord(id).first()!!
        val beverage = requireNotNull(saved.beverage)

        assertEquals(2, beverage.cupCount)
        assertEquals(listOf("珍珠", "椰果"), beverage.toppings)
        assertNull(saved.mealType)
        assertEquals(260, requireNotNull(saved.aiCalorieEstimate).adoptedKcal)
        assertEquals(CalorieSource.AI_ESTIMATE, saved.calorieSource)
    }

    @Test
    fun saveAndManualEditRetainsMealAiEvidenceWithModifiedAdoption() = runTest {
        val estimate = AiCalorieEstimateDraft(
            generatedAt = 300,
            modelNameSnapshot = "Vision",
            modelIdSnapshot = "vision-1",
            items = listOf(AiCalorieItemEstimate("rice", "1 bowl", 400, 600)),
            totalMinKcal = 400,
            totalMaxKcal = 600,
            suggestedKcal = 500,
            adoptedKcal = 500,
            wasModified = false,
            accuracyNote = "Approximate",
        )
        val id = repository.save(null, mealDraft("rice", 500).copy(aiCalorieEstimate = estimate))
        repository.save(id, mealDraft("rice", 540))

        val saved = repository.observeRecord(id).first()!!
        val savedEstimate = requireNotNull(saved.aiCalorieEstimate)

        assertEquals(540, saved.finalCalories)
        assertEquals(540, savedEstimate.adoptedKcal)
        assertEquals(true, savedEstimate.wasModified)
    }

    @Test
    fun attachedEvidenceNormalizesToModifiedManualFinalCalories() = runTest {
        val id = repository.save(
            null,
            mealDraft("rice", 400).copy(
                manualFinalCalories = 540,
                aiCalorieEstimate = estimate("vision-1", 500),
            ),
        )

        val saved = repository.observeRecord(id).first()!!
        val savedEstimate = requireNotNull(saved.aiCalorieEstimate)
        assertEquals(540, saved.finalCalories)
        assertEquals(540, savedEstimate.adoptedKcal)
        assertEquals(true, savedEstimate.wasModified)
        assertEquals(CalorieSource.MANUAL, saved.calorieSource)
    }

    @Test
    fun clearingManualFinalCaloriesNormalizesAttachedEvidenceToCalculatedCalories() = runTest {
        val initial = estimate("vision-1", 500)
        val id = repository.save(
            null,
            mealDraft("rice", 480).copy(
                manualFinalCalories = 500,
                aiCalorieEstimate = initial,
            ),
        )

        repository.save(
            id,
            mealDraft("rice", 480).copy(
                manualFinalCalories = null,
                aiCalorieEstimate = initial.copy(wasModified = true),
            ),
        )

        val saved = repository.observeRecord(id).first()!!
        val savedEstimate = requireNotNull(saved.aiCalorieEstimate)
        assertEquals(480, saved.finalCalories)
        assertEquals(480, savedEstimate.adoptedKcal)
        assertEquals(true, savedEstimate.wasModified)
        assertEquals(CalorieSource.MANUAL, saved.calorieSource)
    }

    @Test
    fun retainedEvidenceReturnsToAiSourceWhenCaloriesReturnToSuggestion() = runTest {
        val modified = estimate("vision-1", 500).copy(adoptedKcal = 540, wasModified = true)
        val id = repository.save(
            null,
            mealDraft("rice", 400).copy(
                manualFinalCalories = 540,
                aiCalorieEstimate = modified,
            ),
        )

        repository.save(id, mealDraft("rice", 500))

        val saved = repository.observeRecord(id).first()!!
        val savedEstimate = requireNotNull(saved.aiCalorieEstimate)
        assertEquals(500, saved.finalCalories)
        assertEquals(500, savedEstimate.adoptedKcal)
        assertEquals(false, savedEstimate.wasModified)
        assertEquals(CalorieSource.AI_ESTIMATE, saved.calorieSource)
    }

    @Test
    fun savingNewMealAiEstimateReplacesPreviousEvidence() = runTest {
        val id = repository.save(
            null,
            mealDraft("rice", 500).copy(aiCalorieEstimate = estimate("vision-1", 500)),
        )

        repository.save(
            id,
            mealDraft("noodles", 620).copy(aiCalorieEstimate = estimate("vision-2", 620)),
        )

        val saved = repository.observeRecord(id).first()!!
        val savedEstimate = requireNotNull(saved.aiCalorieEstimate)
        assertEquals("noodles", saved.description)
        assertEquals("vision-2", savedEstimate.modelIdSnapshot)
        assertEquals(620, savedEstimate.adoptedKcal)
    }

    @Test
    fun failedAiEvidenceReplacementRollsBackMealAndPreviousEvidence() = runTest {
        val id = repository.save(
            null,
            mealDraft("before", 500).copy(aiCalorieEstimate = estimate("vision-1", 500)),
        )
        database.openHelper.writableDatabase.execSQL(
            """
                CREATE TRIGGER fail_ai_estimate_insert
                BEFORE INSERT ON ai_calorie_estimates
                BEGIN
                    SELECT RAISE(ABORT, 'forced estimate failure');
                END
            """.trimIndent(),
        )

        try {
            repository.save(
                id,
                mealDraft("after", 620).copy(aiCalorieEstimate = estimate("vision-2", 620)),
            )
            fail("Expected the SQLite trigger to abort AI evidence replacement")
        } catch (_: Exception) {
            // The real SQLite failure is the behavior under test.
        }

        val saved = repository.observeRecord(id).first()!!
        assertEquals("before", saved.description)
        assertEquals(500, saved.finalCalories)
        assertEquals("vision-1", saved.aiCalorieEstimate!!.modelIdSnapshot)
        assertEquals(listOf("before"), saved.foodItems.map { it.name })
        assertEquals(false, saved.foodItems.any { it.name == "after" })
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

    private fun estimate(modelId: String, calories: Int) = AiCalorieEstimateDraft(
        generatedAt = 300,
        modelNameSnapshot = "Vision",
        modelIdSnapshot = modelId,
        items = listOf(AiCalorieItemEstimate("rice", "1 bowl", calories, calories)),
        totalMinKcal = calories,
        totalMaxKcal = calories,
        suggestedKcal = calories,
        adoptedKcal = calories,
        wasModified = false,
        accuracyNote = "Approximate",
    )
}
