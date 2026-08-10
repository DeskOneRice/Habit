package com.habit.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class AiEntityMappersTest {
    @Test
    fun malformedCalorieItemsJsonMapsToEmptyItems() {
        val estimate = AiCalorieEstimateEntity(
            mealRecordId = 1,
            generatedAt = 1,
            modelNameSnapshot = "Primary",
            modelIdSnapshot = "vision",
            itemsJson = "{\"items\":[]}",
            totalMinKcal = 100,
            totalMaxKcal = 200,
            suggestedKcal = 150,
            adoptedKcal = 150,
            wasModified = false,
            accuracyNote = "",
        )

        assertEquals(emptyList<Any>(), estimate.toDomain().items)
    }
}
