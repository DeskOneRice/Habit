package com.habit.app.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.habit.app.data.local.CheckInEntity
import com.habit.app.data.local.HabitDatabase
import com.habit.app.data.local.toDomain
import com.habit.app.domain.repository.CheckInRepository
import com.habit.app.domain.repository.ToggleResult
import com.habit.app.domain.stats.DatePolicy
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomCheckInRepository(
    private val database: HabitDatabase,
    private val clock: Clock,
) : CheckInRepository {
    private val habitLocks = ConcurrentHashMap<Long, Mutex>()

    override suspend fun toggle(habitId: Long, date: LocalDate, today: LocalDate): ToggleResult {
        return habitLocks.getOrPut(habitId) { Mutex() }.withLock {
            database.withTransaction {
                if (date.isAfter(today)) {
                    return@withTransaction ToggleResult.Rejected("不能打卡未来日期")
                }
                val habit = database.habitDao().getById(habitId)?.toDomain()
                    ?: return@withTransaction ToggleResult.Rejected("习惯不存在")
                if (!DatePolicy.isEligible(habit, date, today)) {
                    return@withTransaction ToggleResult.Rejected("该日期不在习惯有效范围内")
                }
                val epochDay = date.toEpochDay()
                if (database.checkInDao().delete(habitId, epochDay) > 0) {
                    ToggleResult.Unchecked
                } else {
                    try {
                        val now = clock.millis()
                        database.checkInDao().insert(CheckInEntity(0, habitId, epochDay, now, now))
                        ToggleResult.Checked
                    } catch (_: SQLiteConstraintException) {
                        database.checkInDao().delete(habitId, epochDay)
                        ToggleResult.Unchecked
                    }
                }
            }
        }
    }
}
