package com.habit.app.data.repository

import com.habit.app.data.local.CategoryEntity
import com.habit.app.data.local.HabitDatabase
import com.habit.app.data.local.toDomain
import com.habit.app.domain.model.Category
import com.habit.app.domain.repository.CategoryRepository
import androidx.room.withTransaction
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
        val normalized = validateName(name)
        val now = clock.millis()
        val existing = categoryDao.getAll()
        require(existing.none { it.name.equals(normalized, ignoreCase = true) }) { "分类名称已存在" }
        val sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        return categoryDao.insert(
            CategoryEntity(
                name = normalized,
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
        val normalized = validateName(name)
        require(categoryDao.getAll().none { it.id != id && it.name.equals(normalized, ignoreCase = true) }) {
            "分类名称已存在"
        }
        categoryDao.update(category.copy(name = normalized, updatedAt = clock.millis()))
    }

    override suspend fun setHidden(id: Long, hidden: Boolean) = database.withTransaction {
        val category = requireNotNull(categoryDao.getById(id)) { "分类不存在" }
        if (hidden && !category.isHidden) {
            require(categoryDao.getAll().count { !it.isHidden } > 1) { "至少保留一个可见分类" }
        }
        categoryDao.update(category.copy(isHidden = hidden, updatedAt = clock.millis()))
        Unit
    }

    override suspend fun migrateAndDelete(sourceId: Long, targetId: Long) = database.withTransaction {
        require(sourceId != targetId) { "请选择不同的目标分类" }
        val source = requireNotNull(categoryDao.getById(sourceId)) { "分类不存在" }
        val target = requireNotNull(categoryDao.getById(targetId)) { "分类不存在" }
        require(!target.isHidden) { "目标分类不可隐藏" }
        categoryDao.reassignHabitsAndDeleteCategory(source.id, target.id, clock.millis())
    }

    private fun validateName(name: String): String = name.trim().also {
        require(it.isNotEmpty()) { "分类名称不能为空" }
    }
}
