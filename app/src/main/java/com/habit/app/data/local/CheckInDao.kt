package com.habit.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_ins ORDER BY id")
    suspend fun getAll(): List<CheckInEntity>

    @Insert
    suspend fun insert(entity: CheckInEntity): Long

    @Insert
    suspend fun insertAll(entities: List<CheckInEntity>): List<Long>

    @Query("DELETE FROM check_ins WHERE habitId = :habitId AND checkInEpochDay = :epochDay")
    suspend fun delete(habitId: Long, epochDay: Long): Int

    @Query("SELECT * FROM check_ins WHERE habitId = :habitId ORDER BY checkInEpochDay")
    suspend fun getForHabit(habitId: Long): List<CheckInEntity>

    @Query("SELECT * FROM check_ins WHERE habitId = :habitId ORDER BY checkInEpochDay")
    fun observeForHabit(habitId: Long): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins WHERE checkInEpochDay BETWEEN :start AND :end ORDER BY checkInEpochDay, habitId")
    fun observeRange(start: Long, end: Long): Flow<List<CheckInEntity>>

    @Query("DELETE FROM check_ins")
    suspend fun deleteAll(): Int
}
