package com.habit.app.ui.ai

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Application-scoped serialization for every mutation or request involving an AI model. */
internal class AiModelOperationCoordinator {
    private val monitor = Any()
    private val modelLocks = mutableMapOf<Long, Mutex>()
    private val externalLocks = mutableMapOf<String, Mutex>()
    private val createLock = Mutex()
    private val bindingLock = Mutex()

    suspend fun <T> withCreate(block: suspend () -> T): T = createLock.withLock { block() }

    suspend fun <T> withModel(
        modelId: Long,
        externalId: String? = null,
        block: suspend () -> T,
    ): T = mutexFor(modelId, externalId).withLock { block() }

    suspend fun <T> withExternal(externalId: String, block: suspend () -> T): T =
        mutexFor(modelId = null, externalId = externalId).withLock { block() }

    suspend fun <T> withBindings(block: suspend () -> T): T = bindingLock.withLock { block() }

    fun register(modelId: Long, externalId: String): Mutex = mutexFor(modelId, externalId)

    private fun mutexFor(modelId: Long?, externalId: String?): Mutex = synchronized(monitor) {
        val existing = modelId?.let(modelLocks::get) ?: externalId?.let(externalLocks::get)
        val mutex = existing ?: Mutex()
        if (modelId != null) modelLocks[modelId] = mutex
        if (externalId != null) externalLocks[externalId] = mutex
        mutex
    }
}
