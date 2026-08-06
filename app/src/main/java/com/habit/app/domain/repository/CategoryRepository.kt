package com.habit.app.domain.repository

import com.habit.app.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeVisible(): Flow<List<Category>>
    fun observeAll(): Flow<List<Category>>
    suspend fun create(name: String): Long
    suspend fun rename(id: Long, name: String)
    suspend fun setHidden(id: Long, hidden: Boolean)
    suspend fun migrateAndDelete(sourceId: Long, targetId: Long)
}
