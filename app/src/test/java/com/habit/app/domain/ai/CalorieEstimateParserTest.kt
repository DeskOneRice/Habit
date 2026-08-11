package com.habit.app.domain.ai

import com.habit.app.domain.model.AiModelConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class CalorieEstimateParserTest {
    @Test
    fun parsesNonNegativeOrderedItemsAndTotalInsideAllowedRange() {
        val parsed = CalorieEstimateParser.parse(validJson(), model(), generatedAt = 123L)

        assertEquals(2, parsed.items.size)
        assertEquals(420, parsed.totalMinKcal)
        assertEquals(650, parsed.totalMaxKcal)
        assertEquals(520, parsed.suggestedKcal)
    }

    @Test
    fun rejectsInvalidEstimateBoundaries() {
        listOf(
            validJson().replace("\"minKcal\":200", "\"minKcal\":-1"),
            validJson().replace("\"minKcal\":200,\"maxKcal\":320", "\"minKcal\":321,\"maxKcal\":320"),
            validJson().replace("\"totalMinKcal\":420,\"totalMaxKcal\":650", "\"totalMinKcal\":651,\"totalMaxKcal\":650"),
            validJson().replace("\"suggestedKcal\":520", "\"suggestedKcal\":700"),
            validJson().replace("\"name\":\"rice\"", "\"name\":\" \""),
            validJson().replace("\"portion\":\"1 bowl\"", "\"portion\":\" \""),
            validJson().replace("\"totalMaxKcal\":650", "\"totalMaxKcal\":10001"),
        ).forEach { json ->
            try {
                CalorieEstimateParser.parse(json, model(), generatedAt = 123L)
                throw AssertionError("Expected parser to reject $json")
            } catch (_: CalorieEstimateParseException) {
                // Expected boundary rejection.
            }
        }
    }

    private fun validJson() = """{"items":[{"name":"rice","portion":"1 bowl","minKcal":200,"maxKcal":320},{"name":"chicken","portion":"100g","minKcal":220,"maxKcal":330}],"totalMinKcal":420,"totalMaxKcal":650,"suggestedKcal":520,"accuracyNote":"Approximate estimate"}"""

    private fun model() = AiModelConfig(
        id = 1,
        externalId = "model-1",
        name = "Vision model",
        baseUrl = "https://example.com",
        modelId = "vision-1",
        supportsText = false,
        supportsVision = true,
        allowInsecureHttp = false,
        enabled = true,
        lastTestedAt = 1,
        lastTestStatus = com.habit.app.domain.model.AiTestStatus.PASSED,
        lastTestMessage = "habit-test-v1|vision=PASSED",
        createdAt = 1,
        updatedAt = 1,
    )
}
