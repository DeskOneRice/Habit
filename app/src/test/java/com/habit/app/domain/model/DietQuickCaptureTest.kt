package com.habit.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DietQuickCaptureTest {
    @Test
    fun repeatDraftCopiesContentButNotIdentityOrPhotos() {
        val source = MealRecord(
            id = 42,
            recordType = DietRecordType.MEAL,
            mealType = MealType.LUNCH,
            occurredAt = 1_700_000_000_000,
            recordEpochDay = 19_675,
            description = "番茄鸡蛋面",
            foodItems = listOf(
                FoodItem(1, 42, "面条", "一碗", 420, 0, 1, 1),
            ),
            calculatedCalories = 420,
            finalCalories = 450,
            calorieSource = CalorieSource.MANUAL,
            beverage = null,
            note = "少盐",
            photos = listOf(DietPhoto(9, "library/source.jpg", 0)),
            createdAt = 1,
            updatedAt = 2,
        )

        val draft = source.toRepeatDraft(
            nowMillis = 1_785_687_600_000,
            epochDay = 20_671,
        )

        assertEquals(1_785_687_600_000, draft.occurredAt)
        assertEquals(20_671, draft.recordEpochDay)
        assertEquals("番茄鸡蛋面", draft.description)
        assertEquals(450, draft.manualFinalCalories)
        assertEquals(listOf(FoodItemDraft("面条", "一碗", 420)), draft.foodItems)
        assertTrue(draft.photos.isEmpty())
    }
}
