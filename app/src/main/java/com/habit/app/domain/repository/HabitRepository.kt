package com.habit.app.domain.repository

import com.habit.app.domain.model.Habit
import com.habit.app.domain.model.HabitDraft
import kotlinx.coroutines.flow.Flow

interface HabitRepository {
    fun observeAll(): Flow<List<Habit>>
    fun observeById(id: Long): Flow<Habit?>
    suspend fun create(draft: HabitDraft): Long
    suspend fun update(id: Long, draft: HabitDraft)
    suspend fun archive(id: Long, archivedEpochDay: Long)
    suspend fun delete(id: Long)
}
