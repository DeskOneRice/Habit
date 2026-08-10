package com.habit.app.data.ai

const val MAX_AI_HTTP_RESPONSE_BYTES = 2 * 1024 * 1024

data class AiHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: ByteArray,
    val connectTimeoutMillis: Int = 15_000,
    val readTimeoutMillis: Int = 60_000,
)

data class AiHttpResponse(val statusCode: Int, val body: ByteArray)

interface AiHttpTransport {
    suspend fun execute(request: AiHttpRequest): AiHttpResponse
}
