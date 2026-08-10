package com.habit.app.domain.ai

import com.habit.app.domain.model.AiModelConfig
import com.habit.app.domain.model.AiTestStatus
import com.habit.app.domain.model.WeeklyReportCoverage
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReportParserTest {
    private val input = WeeklyReportInput(
        startEpochDay = 10,
        endEpochDay = 16,
        habits = emptyList(),
        dietRecords = emptyList(),
        coverage = WeeklyReportCoverage(7, 3, 2, 2, 1, 1),
        json = "{\"safe\":true}",
    )
    private val model = AiModelConfig(
        id = 4,
        externalId = "external",
        name = "周报模型",
        baseUrl = "https://example.test/v1",
        modelId = "text-model",
        supportsText = true,
        supportsVision = false,
        allowInsecureHttp = false,
        enabled = true,
        lastTestedAt = 1,
        lastTestStatus = AiTestStatus.PASSED,
        lastTestMessage = "通过",
        createdAt = 1,
        updatedAt = 1,
    )
    private val valid = """
        {
          "title":"上周习惯与饮食回顾",
          "overview":"记录显示本周保持了部分习惯。",
          "habitAnalysis":"七次计划中完成三次。",
          "dietAnalysis":"饮食记录覆盖两天，其中一条缺少热量。",
          "correlationFinding":"现有覆盖不足以判断稳定关联。",
          "suggestions":["保持可执行的小目标。","补充遗漏的饮食记录。","下周继续观察同一指标。"],
          "cautions":["这不是医疗诊断。"]
        }
    """.trimIndent()

    @Test
    fun acceptsOneRawOrSingleFencedChineseObjectAndKeepsLocalMetadata() {
        listOf(valid, "```json\n$valid\n```").forEach { raw ->
            val draft = WeeklyReportParser.parse(raw, input, model, generatedAt = 1234)
            assertEquals("上周习惯与饮食回顾", draft.title)
            assertEquals(3, draft.suggestions.size)
            assertEquals(input.coverage, draft.coverage)
            assertEquals(input.startEpochDay, draft.startEpochDay)
            assertEquals("周报模型", draft.modelNameSnapshot)
            assertEquals("text-model", draft.modelIdSnapshot)
            assertEquals(1234, draft.generatedAt)
        }
    }

    @Test
    fun rejectsExtraTextMultipleObjectsMissingOrWrongTypedFieldsAndWrongSuggestionCounts() {
        val invalid = listOf(
            "说明如下：$valid",
            "$valid\n$valid",
            "```json\n$valid\n```\n额外说明",
            valid.replace("\"title\":\"上周习惯与饮食回顾\",", ""),
            valid.replace("\"overview\":\"记录显示本周保持了部分习惯。\"", "\"overview\":7"),
            valid.replace("[\"保持可执行的小目标。\",\"补充遗漏的饮食记录。\",\"下周继续观察同一指标。\"]", "[\"建议一。\",\"建议二。\"]"),
            valid.replace("[\"保持可执行的小目标。\",\"补充遗漏的饮食记录。\",\"下周继续观察同一指标。\"]", "[\"建议一。\",\"建议二。\",\"建议三。\",\"建议四。\"]"),
            valid.replace("\"cautions\":[\"这不是医疗诊断。\"]", "\"cautions\":\"这不是医疗诊断。\""),
            valid.dropLast(1) + ",\"extra\":\"不允许\"}",
        )

        invalid.forEach { raw ->
            val thrown = assertThrows(WeeklyReportParseException::class.java) {
                WeeklyReportParser.parse(raw, input, model, 1234)
            }
            assertFalse(thrown.message.orEmpty().contains(raw))
        }
    }

    @Test
    fun rejectsBlankOrNonChineseContent() {
        val blank = valid
            .replace(Regex(":\"[^\"]*\""), ":\"   \"")
            .replace(Regex("""\["[^]]*]"""), "[\" \",\" \",\" \"]")
        val english = valid
            .replace("上周习惯与饮食回顾", "Weekly report")
            .replace("记录显示本周保持了部分习惯。", "Some habits were recorded.")

        assertThrows(WeeklyReportParseException::class.java) {
            WeeklyReportParser.parse(blank, input, model, 1234)
        }
        assertThrows(WeeklyReportParseException::class.java) {
            WeeklyReportParser.parse(english, input, model, 1234)
        }
    }

    @Test
    fun acceptsEveryExactFieldBoundaryAndExactlyTenThousandDisplayCharacters() {
        val boundaryObjects = listOf(
            reportJson(title = chinese(80)),
            reportJson(overview = chinese(2_000)),
            reportJson(habitAnalysis = chinese(2_000)),
            reportJson(dietAnalysis = chinese(2_000)),
            reportJson(correlationFinding = chinese(2_000)),
            reportJson(suggestions = List(3) { chinese(300) }),
            reportJson(cautions = List(8) { chinese(300) }),
            reportJson(
                title = chinese(80),
                overview = chinese(2_000),
                habitAnalysis = chinese(2_000),
                dietAnalysis = chinese(2_000),
                correlationFinding = chinese(2_000),
                suggestions = List(3) { chinese(300) },
                cautions = listOf(chinese(300), chinese(300), chinese(300), chinese(120)),
            ),
        )

        boundaryObjects.forEach { raw ->
            WeeklyReportParser.parse(raw, input, model, 1234)
        }
    }

    @Test
    fun rejectsEachFieldItemAndCautionCountOnePastItsLimitWithSafeFailure() {
        val overLimitObjects = listOf(
            reportJson(title = chinese(81)),
            reportJson(overview = chinese(2_001)),
            reportJson(habitAnalysis = chinese(2_001)),
            reportJson(dietAnalysis = chinese(2_001)),
            reportJson(correlationFinding = chinese(2_001)),
            reportJson(suggestions = listOf(chinese(301), chinese(1), chinese(1))),
            reportJson(cautions = listOf(chinese(301))),
            reportJson(cautions = List(9) { chinese(1) }),
        )

        overLimitObjects.forEach { raw -> assertSafeParseFailure(raw) }
    }

    @Test
    fun rejectsTenThousandAndOneTotalDisplayCharactersEvenWhenEveryFieldIsWithinItsLimit() {
        val raw = reportJson(
            title = chinese(80),
            overview = chinese(2_000),
            habitAnalysis = chinese(2_000),
            dietAnalysis = chinese(2_000),
            correlationFinding = chinese(2_000),
            suggestions = List(3) { chinese(300) },
            cautions = listOf(chinese(300), chinese(300), chinese(300), chinese(121)),
        )

        assertSafeParseFailure(raw)
    }

    @Test
    fun promptForbidsFabricationDiagnosisAndMetricOverridesWhileRequiringCoverageAwareChineseJson() {
        val system = WeeklyReportPrompt.systemPrompt
        val user = WeeklyReportPrompt.userPrompt(input)

        assertTrue(system.contains("不得编造"))
        assertTrue(system.contains("不得诊断"))
        assertTrue(system.contains("缺失"))
        assertTrue(system.contains("覆盖"))
        assertTrue(system.contains("中文"))
        assertTrue(system.contains("JSON"))
        assertTrue(system.contains("不得修改"))
        assertEquals(input.json, user.substringAfterLast("\n"))
        assertFalse(user.contains("API Key"))
    }

    private fun assertSafeParseFailure(raw: String) {
        val thrown = assertThrows(WeeklyReportParseException::class.java) {
            WeeklyReportParser.parse(raw, input, model, 1234)
        }
        assertEquals("模型返回内容无法解析，可更换模型或重试", thrown.message)
        assertFalse(thrown.message.orEmpty().contains(raw.take(100)))
    }

    private fun reportJson(
        title: String = chinese(1),
        overview: String = chinese(1),
        habitAnalysis: String = chinese(1),
        dietAnalysis: String = chinese(1),
        correlationFinding: String = chinese(1),
        suggestions: List<String> = List(3) { chinese(1) },
        cautions: List<String> = listOf(chinese(1)),
    ): String = buildJsonObject {
        put("title", title)
        put("overview", overview)
        put("habitAnalysis", habitAnalysis)
        put("dietAnalysis", dietAnalysis)
        put("correlationFinding", correlationFinding)
        put("suggestions", buildJsonArray { suggestions.forEach { add(JsonPrimitive(it)) } })
        put("cautions", buildJsonArray { cautions.forEach { add(JsonPrimitive(it)) } })
    }.toString()

    private fun chinese(length: Int): String = "中".repeat(length)
}
