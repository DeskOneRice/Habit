package com.habit.app.domain.repository

import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealRecordDraft
import kotlinx.coroutines.flow.Flow

interface DietRepository {
    fun observeAll(): Flow<List<MealRecord>>
    fun observeDay(epochDay: Long): Flow<List<MealRecord>>
    fun observeRecord(id: Long): Flow<MealRecord?>
    fun observeRange(startEpochDay: Long, endEpochDay: Long): Flow<List<MealRecord>>
    suspend fun save(id: Long?, draft: MealRecordDraft): Long
    suspend fun delete(id: Long)
    suspend fun referencedPhotoPaths(): Set<String> = emptySet()
}
