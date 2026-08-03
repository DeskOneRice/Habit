package com.habit.app.ui.diet

import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.DietTemplate
import com.habit.app.domain.model.MealRecordDraft
import com.habit.app.domain.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DietTemplateViewModelTest {
    @Test
    fun templatesAreGroupedByRecordTypeAndKeepSortOrder() {
        val meal = template(1, "午餐", DietRecordType.MEAL, 2)
        val drink = template(2, "咖啡", DietRecordType.BEVERAGE, 0)
        val breakfast = template(3, "早餐", DietRecordType.MEAL, 0)

        val state = DietTemplateUiState(templates = listOf(meal, drink, breakfast))

        assertEquals(listOf("早餐", "午餐"), state.mealTemplates.map { it.name })
        assertEquals(listOf("咖啡"), state.beverageTemplates.map { it.name })
        assertTrue(state.mealExpanded)
        assertFalse(state.toggleMeal().mealExpanded)
    }

    private fun template(id: Long, name: String, type: DietRecordType, order: Int) = DietTemplate(
        id = id,
        name = name,
        draft = MealRecordDraft(type, if (type == DietRecordType.MEAL) MealType.LUNCH else null, 0, 0, name, emptyList(), null, null, ""),
        photos = emptyList(),
        sortOrder = order,
        createdAt = 0,
        updatedAt = 0,
    )
}
