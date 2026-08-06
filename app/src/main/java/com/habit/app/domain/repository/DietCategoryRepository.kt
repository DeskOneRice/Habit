package com.habit.app.domain.repository

import com.habit.app.domain.model.DietCategory
import com.habit.app.domain.model.DietCategoryScope
import kotlinx.coroutines.flow.Flow

interface DietCategoryRepository {
    fun observeAll(scope: DietCategoryScope): Flow<List<DietCategory>>
    fun observeVisible(scope: DietCategoryScope): Flow<List<DietCategory>>
    fun observeUsageCounts(scope: DietCategoryScope): Flow<Map<Long, Int>>
    suspend fun create(scope: DietCategoryScope, name: String): Long
    suspend fun rename(id: Long, name: String)
    suspend fun setHidden(id: Long, hidden: Boolean)
    suspend fun migrateAndDelete(sourceId: Long, targetId: Long)
}

