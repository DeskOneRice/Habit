package com.habit.app.data.ai

import com.habit.app.domain.model.AiModelConfig

data class AiPreparedImage(val mimeType: String, val bytes: ByteArray)

interface AiCompletionClient {
    suspend fun completeText(
        model: AiModelConfig,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
    ): String

    suspend fun completeVision(
        model: AiModelConfig,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        images: List<AiPreparedImage>,
    ): String
}
