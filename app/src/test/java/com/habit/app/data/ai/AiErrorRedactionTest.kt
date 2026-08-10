package com.habit.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiErrorRedactionTest {
    @Test
    fun rateLimitMessageDoesNotLeakBodyOrKey() {
        val failure = mapAiHttpFailure(429, "provider diagnostic sk-private", "sk-private")

        assertEquals("请求过于频繁或额度不足，请稍后重试", failure.userMessage)
        assertEquals(AiFailureKind.QUOTA, failure.kind)
        assertFalse(failure.toString().contains("sk-private"))
        assertFalse(failure.safeDiagnostic.contains("sk-private"))
        assertFalse(failure.safeDiagnostic.contains("provider diagnostic"))
    }

    @Test
    fun statusCodesMapToStableChineseMessages() {
        val expected = mapOf(
            401 to (AiFailureKind.AUTH to "API Key 无效或没有模型权限"),
            403 to (AiFailureKind.AUTH to "API Key 无效或没有模型权限"),
            402 to (AiFailureKind.QUOTA to "请求过于频繁或额度不足，请稍后重试"),
            429 to (AiFailureKind.QUOTA to "请求过于频繁或额度不足，请稍后重试"),
            404 to (AiFailureKind.NOT_FOUND to "API 地址或模型 ID 不存在"),
            415 to (AiFailureKind.UNSUPPORTED to "当前模型不支持图片分析"),
            500 to (AiFailureKind.SERVER to "AI 服务暂时不可用"),
            503 to (AiFailureKind.SERVER to "AI 服务暂时不可用"),
        )

        expected.forEach { (status, expectation) ->
            val failure = mapAiHttpFailure(status, "raw provider body", "secret")
            assertEquals(expectation.first, failure.kind)
            assertEquals(expectation.second, failure.userMessage)
            assertEquals("HTTP $status", failure.safeDiagnostic)
        }
    }

    @Test
    fun providerVisionRejectionMapsWithoutRetainingBody() {
        val failure = mapAiHttpFailure(
            400,
            "This model does not support image_url input with Bearer sk-private",
            "sk-private",
        )

        assertEquals(AiFailureKind.UNSUPPORTED, failure.kind)
        assertEquals("当前模型不支持图片分析", failure.userMessage)
        assertEquals("HTTP 400", failure.safeDiagnostic)
    }

    @Test
    fun redactionRemovesCurrentKeyAuthorizationAndBearerShapes() {
        val redacted = redactAiDiagnostic(
            "apiKey=sk-private Authorization: Bearer sk-private bearer other-token harmless",
            "sk-private",
        )

        assertFalse(redacted.contains("sk-private"))
        assertFalse(redacted.contains("other-token"))
        assertFalse(redacted.contains("Authorization", ignoreCase = true))
        assertFalse(redacted.contains("Bearer", ignoreCase = true))
        assertTrue(redacted.contains("harmless"))
    }

    @Test
    fun redactionCoversAuthorizationAndBearerSyntaxMatrix() {
        val diagnostics = listOf(
            "Authorization: Basic credential",
            "authorization = Basic credential",
            "AUTHORIZATION:\tBEARER\tcredential",
            "\"Authorization\":\"Bearer credential\"",
            "{ \"authorization\" : \"Basic credential\" }",
            "bare bEaReR   credential tail",
            "current-key-value",
        )

        diagnostics.forEach { diagnostic ->
            val redacted = redactAiDiagnostic(diagnostic, "current-key-value")
            assertFalse("credential leaked from: $diagnostic", redacted.contains("credential"))
            assertFalse("current key leaked", redacted.contains("current-key-value"))
        }
    }
}
