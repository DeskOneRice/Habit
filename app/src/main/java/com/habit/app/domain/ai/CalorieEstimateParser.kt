package com.habit.app.domain.ai

import com.habit.app.domain.model.AiCalorieEstimateDraft
import com.habit.app.domain.model.AiCalorieItemEstimate
import com.habit.app.domain.model.AiModelConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

class CalorieEstimateParseException : IllegalArgumentException("Invalid calorie estimate response")

object CalorieEstimateParser {
    fun parse(rawResponse: String, model: AiModelConfig, generatedAt: Long): AiCalorieEstimateDraft {
        val value = try {
            JSON.parseToJsonElement(extractObject(rawResponse)) as? JsonObject ?: throw CalorieEstimateParseException()
        } catch (_: CalorieEstimateParseException) {
            throw CalorieEstimateParseException()
        } catch (_: Exception) {
            throw CalorieEstimateParseException()
        }
        if (value.keys != REQUIRED_FIELDS) throw CalorieEstimateParseException()
        val items = value.requiredArray("items").map { item ->
            val objectValue = item as? JsonObject ?: throw CalorieEstimateParseException()
            if (objectValue.keys != ITEM_FIELDS) throw CalorieEstimateParseException()
            AiCalorieItemEstimate(
                name = objectValue.requiredString("name"),
                portion = objectValue.requiredString("portion"),
                minKcal = objectValue.requiredInt("minKcal"),
                maxKcal = objectValue.requiredInt("maxKcal"),
            ).also { estimate ->
                if (estimate.minKcal > estimate.maxKcal) throw CalorieEstimateParseException()
            }
        }
        if (items.isEmpty()) throw CalorieEstimateParseException()
        val totalMin = value.requiredInt("totalMinKcal")
        val totalMax = value.requiredInt("totalMaxKcal")
        val suggested = value.requiredInt("suggestedKcal")
        if (totalMin > totalMax || totalMax > MAX_TOTAL_KCAL || suggested !in totalMin..totalMax) {
            throw CalorieEstimateParseException()
        }
        return AiCalorieEstimateDraft(
            generatedAt = generatedAt,
            modelNameSnapshot = model.name,
            modelIdSnapshot = model.modelId,
            items = items,
            totalMinKcal = totalMin,
            totalMaxKcal = totalMax,
            suggestedKcal = suggested,
            adoptedKcal = suggested,
            wasModified = false,
            accuracyNote = value.requiredString("accuracyNote"),
        )
    }

    private fun extractObject(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("```")) {
            return FENCED_OBJECT.matchEntire(trimmed)?.groupValues?.get(1)?.trim()
                ?: throw CalorieEstimateParseException()
        }
        if (!trimmed.startsWith('{') || !trimmed.endsWith('}')) throw CalorieEstimateParseException()
        return trimmed
    }

    private val JSON = Json { ignoreUnknownKeys = false }
    private val REQUIRED_FIELDS = setOf("items", "totalMinKcal", "totalMaxKcal", "suggestedKcal", "accuracyNote")
    private val ITEM_FIELDS = setOf("name", "portion", "minKcal", "maxKcal")
    private val FENCED_OBJECT = Regex("""\A```(?:json)?\s*([\s\S]*?)\s*```\z""", RegexOption.IGNORE_CASE)
    private const val MAX_TOTAL_KCAL = 10_000
}

private fun JsonObject.requiredArray(name: String): JsonArray = get(name) as? JsonArray ?: throw CalorieEstimateParseException()

private fun JsonObject.requiredString(name: String): String {
    val primitive = get(name) as? JsonPrimitive ?: throw CalorieEstimateParseException()
    if (!primitive.isString) throw CalorieEstimateParseException()
    return primitive.content.trim().takeIf(String::isNotEmpty) ?: throw CalorieEstimateParseException()
}

private fun JsonObject.requiredInt(name: String): Int {
    val primitive = get(name)?.jsonPrimitive ?: throw CalorieEstimateParseException()
    if (primitive.isString) throw CalorieEstimateParseException()
    val value = try { primitive.int } catch (_: Exception) { throw CalorieEstimateParseException() }
    if (value < 0) throw CalorieEstimateParseException()
    return value
}
