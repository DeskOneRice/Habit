package com.habit.app.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DietPreferencePolicyTest {
    @Test
    fun disabledGoalStoresNoCalories() {
        val result = validateDietGoal(enabled = false, kcal = 1800)
        assertNull(result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun enabledGoalRejectsZero() {
        validateDietGoal(enabled = true, kcal = 0)
    }

    @Test
    fun enabledGoalKeepsPositiveCalories() {
        assertEquals(1800, validateDietGoal(enabled = true, kcal = 1800))
    }
}
