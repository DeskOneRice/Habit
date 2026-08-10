package com.habit.app.domain.ai

import com.habit.app.data.ai.MALFORMED_RESPONSE_MESSAGE
import com.habit.app.domain.model.AiModelConfig
import com.habit.app.domain.model.AiWeeklyReportDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class WeeklyReportParseException : IllegalArgumentException(MALFORMED_RESPONSE_MESSAGE)

object WeeklyReportParser {
    fun parse(
        rawResponse: String,
        input: WeeklyReportInput,
        model: AiModelConfig,
        generatedAt: Long,
    ): AiWeeklyReportDraft {
        val jsonText = extractSingleObject(rawResponse)
        val objectValue = try {
            JSON.parseToJsonElement(jsonText) as? JsonObject ?: throw WeeklyReportParseException()
        } catch (_: WeeklyReportParseException) {
            throw WeeklyReportParseException()
        } catch (_: Exception) {
            throw WeeklyReportParseException()
        }
        if (objectValue.keys != REQUIRED_FIELDS) throw WeeklyReportParseException()

        val title = objectValue.requiredChineseString("title")
        val overview = objectValue.requiredChineseString("overview")
        val habitAnalysis = objectValue.requiredChineseString("habitAnalysis")
        val dietAnalysis = objectValue.requiredChineseString("dietAnalysis")
        val correlationFinding = objectValue.requiredChineseString("correlationFinding")
        val suggestions = objectValue.requiredChineseStrings("suggestions")
        if (suggestions.size != 3) throw WeeklyReportParseException()
        val cautions = objectValue.requiredChineseStrings("cautions")

        return AiWeeklyReportDraft(
            startEpochDay = input.startEpochDay,
            endEpochDay = input.endEpochDay,
            generatedAt = generatedAt,
            modelNameSnapshot = model.name,
            modelIdSnapshot = model.modelId,
            title = title,
            overview = overview,
            habitAnalysis = habitAnalysis,
            dietAnalysis = dietAnalysis,
            correlationFinding = correlationFinding,
            suggestions = suggestions,
            cautions = cautions,
            coverage = input.coverage,
        )
    }

    private fun extractSingleObject(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("```")) {
            val match = FENCED_OBJECT.matchEntire(trimmed) ?: throw WeeklyReportParseException()
            return match.groupValues[1].trim()
        }
        if (!trimmed.startsWith('{') || !trimmed.endsWith('}')) throw WeeklyReportParseException()
        return trimmed
    }

    private val JSON = Json { ignoreUnknownKeys = false }
    private val REQUIRED_FIELDS = setOf(
        "title",
        "overview",
        "habitAnalysis",
        "dietAnalysis",
        "correlationFinding",
        "suggestions",
        "cautions",
    )
    private val FENCED_OBJECT = Regex("""\A```(?:json)?\s*([\s\S]*?)\s*```\z""", RegexOption.IGNORE_CASE)
}

private fun JsonObject.requiredChineseString(name: String): String {
    val primitive = get(name) as? JsonPrimitive ?: throw WeeklyReportParseException()
    if (!primitive.isString) throw WeeklyReportParseException()
    val value = primitive.content.trim()
    if (value.isEmpty() || !value.contains(Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF]"))) {
        throw WeeklyReportParseException()
    }
    return value
}

private fun JsonObject.requiredChineseStrings(name: String): List<String> {
    val array = get(name) as? JsonArray ?: throw WeeklyReportParseException()
    return array.map { element ->
        val primitive = element as? JsonPrimitive ?: throw WeeklyReportParseException()
        if (!primitive.isString) throw WeeklyReportParseException()
        val value = primitive.content.trim()
        if (value.isEmpty() || !value.contains(Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF]"))) {
            throw WeeklyReportParseException()
        }
        value
    }
}
