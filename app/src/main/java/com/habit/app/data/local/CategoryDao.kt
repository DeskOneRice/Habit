package com.habit.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isHidden, sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE isHidden = 0 ORDER BY sortOrder, id")
    fun observeVisible(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :categoryId")
    suspend fun getById(categoryId: Long): CategoryEntity?

    @Insert
    suspend fun insert(entity: CategoryEntity): Long

    @Update
    suspend fun update(entity: CategoryEntity): Int

    @Query("UPDATE categories SET isHidden = 1, updatedAt = :updatedAt WHERE id = :categoryId")
    suspend fun hide(categoryId: Long, updatedAt: Long): Int

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteById(categoryId: Long): Int

    @Query("UPDATE habits SET categoryId = :toCategoryId, updatedAt = :updatedAt WHERE categoryId = :fromCategoryId")
    suspend fun reassignHabits(fromCategoryId: Long, toCategoryId: Long, updatedAt: Long): Int

    @Query("DELETE FROM categories WHERE id = :categoryId AND isPreset = 0 AND NOT EXISTS (SELECT 1 FROM habits WHERE categoryId = :categoryId)")
    suspend fun deleteEmptyCustomCategory(categoryId: Long): Int

    @Transaction
    suspend fun reassignHabitsAndDeleteCustomCategory(
        fromCategoryId: Long,
        toCategoryId: Long,
        updatedAt: Long,
    ) {
        reassignHabits(fromCategoryId, toCategoryId, updatedAt)
        deleteEmptyCustomCategory(fromCategoryId)
    }
}
