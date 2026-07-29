package com.habit.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE archivedEpochDay IS NULL ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :habitId")
    fun observeById(habitId: Long): Flow<HabitEntity?>

    @Query("SELECT * FROM habits WHERE id = :habitId")
    suspend fun getById(habitId: Long): HabitEntity?

    @Insert
    suspend fun insert(entity: HabitEntity): Long

    @Update
    suspend fun update(entity: HabitEntity): Int

    @Query("UPDATE habits SET archivedEpochDay = :epochDay, updatedAt = :updatedAt WHERE id = :habitId")
    suspend fun archive(habitId: Long, epochDay: Long, updatedAt: Long): Int

    @Query("UPDATE habits SET categoryId = :categoryId, updatedAt = :updatedAt WHERE id = :habitId")
    suspend fun reassignCategory(habitId: Long, categoryId: Long, updatedAt: Long): Int

    @Query("DELETE FROM habits WHERE id = :habitId")
    suspend fun deleteById(habitId: Long): Int
}
