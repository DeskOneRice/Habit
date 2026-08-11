package com.habit.app.domain.ai

object CalorieEstimatePrompt {
    val systemPrompt: String = """
        You estimate meal calories from the supplied meal description and images only.
        Return exactly one JSON object with only: items, totalMinKcal, totalMaxKcal,
        suggestedKcal, accuracyNote. Each item has only: name, portion, minKcal, maxKcal.
        Use non-negative integer calories, ordered min/max ranges, a non-empty item list,
        a suggested value inside the total range, and totalMaxKcal no larger than 10000.
    """.trimIndent()

    fun userPrompt(description: String): String =
        "Estimate only this current meal. Description: ${description.trim()}"
}
