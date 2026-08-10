package com.habit.app.domain.repository

import com.habit.app.domain.model.AiFeature
import com.habit.app.domain.model.AiFeatureBinding
import com.habit.app.domain.model.AiModelConfig
import com.habit.app.domain.model.AiModelConfigDraft
import com.habit.app.domain.model.AiTestStatus
import com.habit.app.domain.model.AiWeeklyReport
import kotlinx.coroutines.flow.Flow

interface AiModelRepository {
    fun observeModels(): Flow<List<AiModelConfig>>
    fun observeBindings(): Flow<List<AiFeatureBinding>>
    fun observeModel(id: Long): Flow<AiModelConfig?>
    suspend fun saveModel(id: Long?, draft: AiModelConfigDraft): Long
    suspend fun recordTest(id: Long, status: AiTestStatus, message: String, testedAt: Long)
    suspend fun bind(feature: AiFeature, modelId: Long?)
    suspend fun deleteModel(id: Long)
}

interface AiWeeklyReportRepository {
    fun observeAll(): Flow<List<AiWeeklyReport>>
    fun observeWeek(startEpochDay: Long): Flow<AiWeeklyReport?>
    suspend fun save(report: AiWeeklyReport): Long
    suspend fun delete(id: Long)
}
