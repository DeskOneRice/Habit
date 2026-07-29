package com.habit.app.data.repository

import com.habit.app.data.local.HabitDao
import com.habit.app.data.local.HabitEntity
import com.habit.app.data.local.toDomain
import com.habit.app.domain.model.Habit
import com.habit.app.domain.model.HabitDraft
import com.habit.app.domain.repository.HabitRepository
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RoomHabitRepository(
    private val habitDao: HabitDao,
    private val clock: Clock,
) : HabitRepository {
    override fun observeAll(): Flow<List<Habit>> = habitDao.observeAll().map { habits ->
        habits.map(HabitEntity::toDomain)
    }

    override fun observeById(id: Long): Flow<Habit?> = habitDao.observeById(id).map { it?.toDomain() }

    override suspend fun create(draft: HabitDraft): Long {
        val name = validateName(draft.name)
        val now = clock.millis()
        val sortOrder = (habitDao.observeAll().first().maxOfOrNull { it.sortOrder } ?: -1) + 1
        return habitDao.insert(
            HabitEntity(
                name = name,
                iconKey = draft.iconKey,
                themeColor = draft.themeColor,
                categoryId = draft.categoryId,
                startEpochDay = draft.startEpochDay,
                archivedEpochDay = null,
                sortOrder = sortOrder,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun update(id: Long, draft: HabitDraft) {
        val existing = requireNotNull(habitDao.getById(id)) { "习惯不存在" }
        habitDao.update(
            existing.copy(
                name = validateName(draft.name),
                iconKey = draft.iconKey,
                themeColor = draft.themeColor,
                categoryId = draft.categoryId,
                startEpochDay = draft.startEpochDay,
                updatedAt = clock.millis(),
            ),
        )
    }

    override suspend fun archive(id: Long, archivedEpochDay: Long) {
        habitDao.archive(id, archivedEpochDay, clock.millis())
    }

    override suspend fun delete(id: Long) {
        habitDao.deleteById(id)
    }

    private fun validateName(raw: String): String {
        val name = raw.trim()
        require(name.length in 1..30) { "请输入 1～30 个字符的习惯名称" }
        return name
    }
}
