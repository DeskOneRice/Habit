package com.habit.app.data.ai

import com.habit.app.domain.ai.normalizedChatCompletionsUrl
import com.habit.app.domain.model.AiModelConfig
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class OpenAiCompatibleClient(
    private val transport: AiHttpTransport,
) : AiCompletionClient {
    override suspend fun completeText(
        model: AiModelConfig,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
    ): String = complete(
        model = model,
        apiKey = apiKey,
        requestBody = textRequest(model.modelId, systemPrompt, userPrompt),
    )

    override suspend fun completeVision(
        model: AiModelConfig,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        images: List<AiPreparedImage>,
    ): String {
        if (images.size !in 1..3 || images.any { it.mimeType != JPEG_MIME_TYPE }) {
            throw invalidResponse("Invalid vision image payload")
        }
        return complete(
            model = model,
            apiKey = apiKey,
            requestBody = visionRequest(model.modelId, systemPrompt, userPrompt, images),
        )
    }

    private suspend fun complete(
        model: AiModelConfig,
        apiKey: String,
        requestBody: ByteArray,
    ): String {
        val url = try {
            normalizedChatCompletionsUrl(model.baseUrl, model.allowInsecureHttp)
        } catch (_: IllegalArgumentException) {
            throw invalidResponse("Invalid endpoint configuration")
        }
        val response = try {
            transport.execute(
                AiHttpRequest(
                    url = url,
                    headers = mapOf(
                        "Authorization" to "Bearer $apiKey",
                        "Content-Type" to "application/json",
                        "Accept" to "application/json",
                    ),
                    body = requestBody,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: AiServiceFailure) {
            throw AiServiceFailure(
                kind = failure.kind,
                userMessage = messageFor(failure.kind),
                safeDiagnostic = redactAiDiagnostic(failure.safeDiagnostic, apiKey),
            )
        } catch (_: SocketTimeoutException) {
            throw AiServiceFailure(AiFailureKind.TIMEOUT, TIMEOUT_MESSAGE, "Socket timeout")
        } catch (_: IOException) {
            throw AiServiceFailure(AiFailureKind.OFFLINE, OFFLINE_MESSAGE, "Network I/O failure")
        } catch (_: Exception) {
            throw invalidResponse("Transport failure")
        }

        if (response.statusCode !in 200..299) {
            throw mapAiHttpFailure(
                statusCode = response.statusCode,
                responseBody = response.body.decodeToString(),
                apiKey = apiKey,
            )
        }
        if (response.body.size > MAX_AI_HTTP_RESPONSE_BYTES) {
            throw invalidResponse("Response exceeds size limit")
        }
        return parseResponse(response.body)
    }

    private fun textRequest(modelId: String, systemPrompt: String, userPrompt: String): ByteArray =
        buildJsonObject {
            put("model", modelId)
            put("temperature", 0.2)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
        }.toString().encodeToByteArray()

    private fun visionRequest(
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
        images: List<AiPreparedImage>,
    ): ByteArray = buildJsonObject {
        put("model", modelId)
        put("temperature", 0.2)
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", systemPrompt)
            })
            add(buildJsonObject {
                put("role", "user")
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", userPrompt)
                    })
                    images.forEach { image ->
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject {
                                val base64 = Base64.getEncoder().encodeToString(image.bytes)
                                put("url", "data:image/jpeg;base64,$base64")
                            })
                        })
                    }
                })
            })
        })
    }.toString().encodeToByteArray()

    private fun parseResponse(body: ByteArray): String {
        val content = try {
            JSON.parseToJsonElement(body.decodeToString())
                .jsonObject.getValue("choices")
                .jsonArray.first()
                .jsonObject.getValue("message")
                .jsonObject.getValue("content")
                .jsonPrimitive.content
        } catch (_: Exception) {
            throw invalidResponse("Malformed Chat Completions response")
        }
        if (content.isBlank() || content.encodeToByteArray().size > MAX_AI_HTTP_RESPONSE_BYTES) {
            throw invalidResponse("Empty or oversized completion content")
        }
        return content
    }

    private fun invalidResponse(diagnostic: String) = AiServiceFailure(
        AiFailureKind.INVALID_RESPONSE,
        MALFORMED_RESPONSE_MESSAGE,
        diagnostic,
    )

    private fun messageFor(kind: AiFailureKind): String = when (kind) {
        AiFailureKind.AUTH -> AUTH_MESSAGE
        AiFailureKind.QUOTA -> QUOTA_MESSAGE
        AiFailureKind.NOT_FOUND -> NOT_FOUND_MESSAGE
        AiFailureKind.UNSUPPORTED -> VISION_UNSUPPORTED_MESSAGE
        AiFailureKind.OFFLINE -> OFFLINE_MESSAGE
        AiFailureKind.TIMEOUT -> TIMEOUT_MESSAGE
        AiFailureKind.SERVER -> SERVER_MESSAGE
        AiFailureKind.INVALID_RESPONSE -> MALFORMED_RESPONSE_MESSAGE
    }

    private companion object {
        const val JPEG_MIME_TYPE = "image/jpeg"
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
