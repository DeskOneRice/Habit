package com.habit.app.domain.ai

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CalorieEstimateFoodInput(
    val name: String,
    val portion: String?,
    val recordedKcal: Int?,
)

data class CalorieEstimateBeverageInput(
    val brandOrStore: String,
    val name: String,
    val sizeOrVolume: String,
    val temperature: String,
    val sweetness: String,
    val toppings: List<String>,
    val cupCount: Int,
)

data class CalorieEstimateRecordInput(
    val recordType: String,
    val category: String,
    val mealType: String?,
    val description: String,
    val foodItems: List<CalorieEstimateFoodInput>,
    val beverage: CalorieEstimateBeverageInput?,
    val note: String,
    val selectedPhotoCount: Int,
)

object CalorieEstimatePrompt {
    val systemPrompt: String = """
        You estimate calories for one dietary record using both its structured fields and supplied images.
        The record may be a meal or a beverage. Treat user-entered portions, drink size, cup count,
        sweetness, toppings and other structured attributes as evidence; use images as additional evidence.
        Return exactly one JSON object with only: items, totalMinKcal, totalMaxKcal,
        suggestedKcal, accuracyNote. Each item has only: name, portion, minKcal, maxKcal.
        Use non-negative integer calories, ordered min/max ranges, a non-empty item list,
        a suggested value inside the total range, and totalMaxKcal no larger than 10000.
    """.trimIndent()

    fun userPrompt(input: CalorieEstimateRecordInput): String = buildString {
        append("Estimate only this current dietary record. Structured record:\n")
        append(input.toSafeJson())
    }
}

private fun CalorieEstimateRecordInput.toSafeJson(): String = buildJsonObject {
    put("recordType", recordType)
    put("category", category)
    put("mealType", mealType?.let(::JsonPrimitive) ?: JsonNull)
    put("description", description)
    put("foodItems", buildJsonArray {
        foodItems.forEach { food ->
            add(buildJsonObject {
                put("name", food.name)
                put("portion", food.portion?.let(::JsonPrimitive) ?: JsonNull)
                put("recordedKcal", food.recordedKcal?.let(::JsonPrimitive) ?: JsonNull)
            })
        }
    })
    put("beverage", beverage?.let { drink ->
        buildJsonObject {
            put("brandOrStore", drink.brandOrStore)
            put("name", drink.name)
            put("sizeOrVolume", drink.sizeOrVolume)
            put("temperature", drink.temperature)
            put("sweetness", drink.sweetness)
            put("toppings", buildJsonArray { drink.toppings.forEach { add(JsonPrimitive(it)) } })
            put("cupCount", drink.cupCount)
        }
    } ?: JsonNull)
    put("note", note)
    put("selectedPhotoCount", selectedPhotoCount)
}.toString()
