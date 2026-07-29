package com.habit.app.data.repository

import com.habit.app.data.local.CategoryEntity
import com.habit.app.data.local.HabitDatabase
import com.habit.app.data.local.toDomain
import com.habit.app.domain.model.Category
import com.habit.app.domain.repository.CategoryRepository
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RoomCategoryRepository(
    private val database: HabitDatabase,
    private val clock: Clock,
) : CategoryRepository {
    private val categoryDao = database.categoryDao()

    override fun observeVisible(): Flow<List<Category>> = categoryDao.observeVisible().map { categories ->
        categories.map(CategoryEntity::toDomain)
    }

    override fun observeAll(): Flow<List<Category>> = categoryDao.observeAll().map { categories ->
        categories.map(CategoryEntity::toDomain)
    }

    override suspend fun create(name: String): Long {
        val now = clock.millis()
        val sortOrder = (categoryDao.observeAll().first().maxOfOrNull { it.sortOrder } ?: -1) + 1
        return categoryDao.insert(
            CategoryEntity(
                name = name.trim(),
                isPreset = false,
                isHidden = false,
                sortOrder = sortOrder,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun rename(id: Long, name: String) {
        val category = requireNotNull(categoryDao.getById(id)) { "分类不存在" }
        categoryDao.update(category.copy(name = name.trim(), updatedAt = clock.millis()))
    }

    override suspend fun setPresetHidden(id: Long, hidden: Boolean) {
        val category = requireNotNull(categoryDao.getById(id)) { "分类不存在" }
        require(category.isPreset) { "只能隐藏预设分类" }
        categoryDao.update(category.copy(isHidden = hidden, updatedAt = clock.millis()))
    }

    override suspend fun migrateAndDelete(sourceId: Long, targetId: Long) {
        require(sourceId != targetId) { "请选择不同的目标分类" }
        val source = requireNotNull(categoryDao.getById(sourceId)) { "分类不存在" }
        require(!source.isPreset) { "预设分类不能删除" }
        requireNotNull(categoryDao.getById(targetId)) { "分类不存在" }
        categoryDao.reassignHabitsAndDeleteCustomCategory(sourceId, targetId, clock.millis())
    }
}
