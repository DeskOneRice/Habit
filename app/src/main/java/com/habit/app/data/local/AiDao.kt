package com.habit.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AiDao {
    @Query("SELECT * FROM ai_model_configs ORDER BY id")
    suspend fun getModels(): List<AiModelConfigEntity>

    @Query("SELECT * FROM ai_model_configs ORDER BY name COLLATE NOCASE, id")
    fun observeModels(): Flow<List<AiModelConfigEntity>>

    @Query("SELECT * FROM ai_model_configs WHERE id = :id")
    fun observeModel(id: Long): Flow<AiModelConfigEntity?>

    @Query("SELECT * FROM ai_model_configs WHERE id = :id")
    suspend fun getModel(id: Long): AiModelConfigEntity?

    @Insert
    suspend fun insertModel(model: AiModelConfigEntity): Long

    @Update
    suspend fun updateModel(model: AiModelConfigEntity): Int

    @Query("DELETE FROM ai_model_configs WHERE id = :id")
    suspend fun deleteModel(id: Long): Int

    @Query("DELETE FROM ai_model_configs")
    suspend fun deleteAllModels(): Int

    @Query("SELECT * FROM ai_feature_bindings ORDER BY feature")
    fun observeBindings(): Flow<List<AiFeatureBindingEntity>>

    @Query("SELECT * FROM ai_feature_bindings ORDER BY feature")
    suspend fun getBindings(): List<AiFeatureBindingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBinding(binding: AiFeatureBindingEntity)

    @Query("DELETE FROM ai_feature_bindings")
    suspend fun deleteAllBindings(): Int

    @Query("SELECT * FROM ai_weekly_reports ORDER BY startEpochDay DESC")
    fun observeReports(): Flow<List<AiWeeklyReportEntity>>

    @Query("SELECT * FROM ai_weekly_reports ORDER BY id")
    suspend fun getReports(): List<AiWeeklyReportEntity>

    @Query("SELECT * FROM ai_weekly_reports WHERE startEpochDay = :startEpochDay")
    fun observeReport(startEpochDay: Long): Flow<AiWeeklyReportEntity?>

    @Query("SELECT * FROM ai_weekly_reports WHERE startEpochDay = :startEpochDay")
    suspend fun getReport(startEpochDay: Long): AiWeeklyReportEntity?

    @Insert
    suspend fun insertReport(report: AiWeeklyReportEntity): Long

    @Update
    suspend fun updateReport(report: AiWeeklyReportEntity): Int

    @Query("DELETE FROM ai_weekly_reports WHERE id = :id")
    suspend fun deleteReport(id: Long): Int

    @Query("DELETE FROM ai_weekly_reports")
    suspend fun deleteAllReports(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCalorieEstimate(estimate: AiCalorieEstimateEntity)

    @Query("SELECT * FROM ai_calorie_estimates WHERE mealRecordId=:mealRecordId")
    fun observeCalorieEstimate(mealRecordId: Long): Flow<AiCalorieEstimateEntity?>
}
