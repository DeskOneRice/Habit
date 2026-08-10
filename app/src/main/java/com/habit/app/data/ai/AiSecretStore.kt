package com.habit.app.data.ai

interface AiSecretStore {
    suspend fun put(externalId: String, apiKey: String)

    suspend fun get(externalId: String): String?

    suspend fun maskedSuffix(externalId: String): String?

    suspend fun remove(externalId: String)

    suspend fun clearAll()
}
