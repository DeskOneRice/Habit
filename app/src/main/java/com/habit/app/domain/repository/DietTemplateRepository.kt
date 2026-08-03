package com.habit.app.domain.repository

import com.habit.app.domain.model.DietTemplate
import com.habit.app.domain.model.DietTemplateDraft
import com.habit.app.domain.model.MealRecordDraft
import kotlinx.coroutines.flow.Flow

interface DietTemplateRepository {
    fun observeAll(): Flow<List<DietTemplate>>
    suspend fun save(draft: DietTemplateDraft): Long
    suspend fun delete(id: Long)
    suspend fun rename(id: Long, name: String)
    suspend fun reorder(ids: List<Long>)
    suspend fun createMealDraft(id: Long, nowMillis: Long, epochDay: Long): MealRecordDraft
}
