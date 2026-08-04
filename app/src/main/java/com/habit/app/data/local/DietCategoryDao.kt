package com.habit.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class DietCategoryUsage(
    val categoryId: Long,
    val usageCount: Int,
)

@Dao
interface DietCategoryDao {
    @Query("SELECT * FROM diet_categories WHERE scope=:scope ORDER BY sortOrder,id")
    fun observeAll(scope: String): Flow<List<DietCategoryEntity>>

    @Query("SELECT * FROM diet_categories WHERE scope=:scope AND isHidden=0 ORDER BY sortOrder,id")
    fun observeVisible(scope: String): Flow<List<DietCategoryEntity>>

    @Query("SELECT * FROM diet_categories WHERE id=:id")
    suspend fun get(id: Long): DietCategoryEntity?

    @Query("SELECT * FROM diet_categories ORDER BY scope,sortOrder,id")
    suspend fun getAll(): List<DietCategoryEntity>

    @Insert
    suspend fun insert(entity: DietCategoryEntity): Long

    @Insert
    suspend fun insertAll(entities: List<DietCategoryEntity>)

    @Update
    suspend fun update(entity: DietCategoryEntity)

    @Query("DELETE FROM diet_categories WHERE id=:id")
    suspend fun delete(id: Long)

    @Query("UPDATE meal_records SET dietCategoryId=:target WHERE dietCategoryId=:source")
    suspend fun moveRecords(source: Long, target: Long)

    @Query("UPDATE diet_templates SET dietCategoryId=:target WHERE dietCategoryId=:source")
    suspend fun moveTemplates(source: Long, target: Long)

    @Query("SELECT dietCategoryId AS categoryId, COUNT(*) AS usageCount FROM meal_records GROUP BY dietCategoryId")
    fun observeRecordUsage(): Flow<List<DietCategoryUsage>>

    @Query("SELECT dietCategoryId AS categoryId, COUNT(*) AS usageCount FROM diet_templates GROUP BY dietCategoryId")
    fun observeTemplateUsage(): Flow<List<DietCategoryUsage>>

    @Query("DELETE FROM diet_categories")
    suspend fun deleteAll()
}

