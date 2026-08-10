package com.habit.app.data.repository

import androidx.room.withTransaction
import com.habit.app.data.local.AiFeatureBindingEntity
import com.habit.app.data.local.AiModelConfigEntity
import com.habit.app.data.local.HabitDatabase
import com.habit.app.data.local.toDomain
import com.habit.app.data.local.toDomainOrNull
import com.habit.app.domain.model.AiFeature
import com.habit.app.domain.model.AiModelConfig
import com.habit.app.domain.model.AiModelConfigDraft
import com.habit.app.domain.model.AiTestStatus
import com.habit.app.domain.repository.AiModelRepository
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAiModelRepository(
    private val database: HabitDatabase,
    private val clock: Clock,
) : AiModelRepository {
    private val dao = database.aiDao()

    override fun observeModels(): Flow<List<AiModelConfig>> = dao.observeModels().map { rows ->
        rows.map(AiModelConfigEntity::toDomain)
    }

    override fun observeBindings() = dao.observeBindings().map { rows ->
        rows.mapNotNull(AiFeatureBindingEntity::toDomainOrNull)
    }

    override fun observeModel(id: Long): Flow<AiModelConfig?> = dao.observeModel(id).map { it?.toDomain() }

    override suspend fun saveModel(id: Long?, draft: AiModelConfigDraft): Long = database.withTransaction {
        val now = clock.millis()
        val existing = id?.let { requireNotNull(dao.getModel(it)) { "AI model does not exist" } }
        val entity = AiModelConfigEntity(
            id = existing?.id ?: 0,
            externalId = existing?.externalId ?: UUID.randomUUID().toString(),
            name = draft.name,
            baseUrl = draft.baseUrl,
            modelId = draft.modelId,
            supportsText = draft.supportsText,
            supportsVision = draft.supportsVision,
            allowInsecureHttp = draft.allowInsecureHttp,
            enabled = draft.enabled,
            lastTestedAt = existing?.lastTestedAt,
            lastTestStatus = existing?.lastTestStatus ?: AiTestStatus.UNTESTED.name,
            lastTestMessage = existing?.lastTestMessage.orEmpty(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        if (existing == null) dao.insertModel(entity) else {
            dao.updateModel(entity)
            existing.id
        }
    }

    override suspend fun recordTest(id: Long, status: AiTestStatus, message: String, testedAt: Long) {
        database.withTransaction {
            val existing = requireNotNull(dao.getModel(id)) { "AI model does not exist" }
            dao.updateModel(
                existing.copy(
                    lastTestedAt = testedAt,
                    lastTestStatus = status.name,
                    lastTestMessage = message,
                    updatedAt = clock.millis(),
                ),
            )
        }
    }

    override suspend fun bind(feature: AiFeature, modelId: Long?) {
        dao.upsertBinding(AiFeatureBindingEntity(feature.name, modelId, clock.millis()))
    }

    override suspend fun deleteModel(id: Long) {
        database.withTransaction {
            dao.deleteModel(id)
        }
    }
}
