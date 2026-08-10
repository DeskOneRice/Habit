package com.habit.app.data.ai

enum class AiFailureKind { AUTH, QUOTA, NOT_FOUND, UNSUPPORTED, OFFLINE, TIMEOUT, SERVER, INVALID_RESPONSE }

class AiServiceFailure(
    val kind: AiFailureKind,
    val userMessage: String,
    val safeDiagnostic: String = "",
) : Exception(userMessage)

const val AUTH_MESSAGE = "API Key 无效或没有模型权限"
const val QUOTA_MESSAGE = "请求过于频繁或额度不足，请稍后重试"
const val NOT_FOUND_MESSAGE = "API 地址或模型 ID 不存在"
const val VISION_UNSUPPORTED_MESSAGE = "当前模型不支持图片分析"
const val OFFLINE_MESSAGE = "当前网络不可用"
const val TIMEOUT_MESSAGE = "AI 请求超时，请重试"
const val SERVER_MESSAGE = "AI 服务暂时不可用"
const val MALFORMED_RESPONSE_MESSAGE = "模型返回内容无法解析，可更换模型或重试"

fun mapAiHttpFailure(statusCode: Int, responseBody: String, apiKey: String): AiServiceFailure {
    val kindAndMessage = when {
        statusCode == 401 || statusCode == 403 -> AiFailureKind.AUTH to AUTH_MESSAGE
        statusCode == 402 || statusCode == 429 -> AiFailureKind.QUOTA to QUOTA_MESSAGE
        statusCode == 404 -> AiFailureKind.NOT_FOUND to NOT_FOUND_MESSAGE
        statusCode == 415 || isVisionRejection(statusCode, responseBody) -> {
            AiFailureKind.UNSUPPORTED to VISION_UNSUPPORTED_MESSAGE
        }
        statusCode in 500..599 -> AiFailureKind.SERVER to SERVER_MESSAGE
        else -> AiFailureKind.INVALID_RESPONSE to MALFORMED_RESPONSE_MESSAGE
    }
    return AiServiceFailure(
        kind = kindAndMessage.first,
        userMessage = kindAndMessage.second,
        // The provider body is used transiently for classification and is never retained.
        safeDiagnostic = redactAiDiagnostic("HTTP $statusCode", apiKey),
    )
}

fun redactAiDiagnostic(diagnostic: String, apiKey: String): String {
    var safe = diagnostic
    if (apiKey.isNotEmpty()) safe = safe.replace(apiKey, "[REDACTED]")
    safe = AUTHORIZATION_SHAPE.replace(safe, "[REDACTED]")
    safe = BEARER_SHAPE.replace(safe, "[REDACTED]")
    return safe
}

private fun isVisionRejection(statusCode: Int, responseBody: String): Boolean {
    if (statusCode !in setOf(400, 422)) return false
    val normalized = responseBody.lowercase()
    val mentionsVision = listOf("image", "image_url", "vision", "multimodal", "图片")
        .any(normalized::contains)
    val rejects = listOf("unsupported", "not support", "does not support", "不支持")
        .any(normalized::contains)
    return mentionsVision && rejects
}

private val AUTHORIZATION_SHAPE = Regex(
    pattern = "(?i)authorization\\s*[:=]\\s*(?:bearer\\s+)?[^\\s,;]+",
)
private val BEARER_SHAPE = Regex(pattern = "(?i)bearer\\s+[^\\s,;]+")
