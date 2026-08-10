package com.habit.app.domain.ai

import com.habit.app.domain.model.AiModelConfig
import com.habit.app.domain.model.AiTestStatus
import com.habit.app.domain.model.WeeklyReportCoverage
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
}
