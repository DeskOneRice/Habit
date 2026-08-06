package com.habit.app.data.repository

import androidx.room.withTransaction
import com.habit.app.data.local.DietCategoryEntity
import com.habit.app.data.local.HabitDatabase
import com.habit.app.data.local.toDomain
import com.habit.app.domain.model.DietCategory
import com.habit.app.domain.model.DietCategoryScope
import com.habit.app.domain.repository.DietCategoryRepository
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RoomDietCategoryRepository(
    private val database: HabitDatabase,
    private val clock: Clock,
) : DietCategoryRepository {
    private val dao = database.dietCategoryDao()

    override fun observeAll(scope: DietCategoryScope): Flow<List<DietCategory>> =
        dao.observeAll(scope.name).map { rows -> rows.map(DietCategoryEntity::toDomain) }

    override fun observeVisible(scope: DietCategoryScope): Flow<List<DietCategory>> =
        dao.observeVisible(scope.name).map { rows -> rows.map(DietCategoryEntity::toDomain) }

    override fun observeUsageCounts(scope: DietCategoryScope): Flow<Map<Long, Int>> = combine(
        dao.observeRecordUsage(),
        dao.observeTemplateUsage(),
    ) { recordRows, templateRows ->
        val allowedIds = dao.observeAll(scope.name).first().map(DietCategoryEntity::id).toSet()
        (recordRows + templateRows)
            .filter { it.categoryId in allowedIds }
            .groupingBy { it.categoryId }
            .fold(0) { total, row -> total + row.usageCount }
    }

    override suspend fun create(scope: DietCategoryScope, name: String): Long {
        val normalized = validateName(name)
        val existing = dao.observeAll(scope.name).first()
        require(existing.none { it.name.equals(normalized, ignoreCase = true) }) { "分类名称已存在" }
        val now = clock.millis()
        return dao.insert(
            DietCategoryEntity(
                scope = scope.name,
                name = normalized,
                isPreset = false,
                isHidden = false,
                sortOrder = (existing.maxOfOrNull(DietCategoryEntity::sortOrder) ?: -1) + 1,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun rename(id: Long, name: String) {
        val source = requireNotNull(dao.get(id)) { "分类不存在" }
        val normalized = validateName(name)
        require(
            dao.observeAll(source.scope).first().none {
                it.id != id && it.name.equals(normalized, ignoreCase = true)
            },
        ) { "分类名称已存在" }
        dao.update(source.copy(name = normalized, updatedAt = clock.millis()))
    }

    override suspend fun setHidden(id: Long, hidden: Boolean) {
        val source = requireNotNull(dao.get(id)) { "分类不存在" }
        if (hidden && !source.isHidden) {
            require(dao.observeVisible(source.scope).first().size > 1) { "请至少保留一个可用分类" }
        }
        dao.update(source.copy(isHidden = hidden, updatedAt = clock.millis()))
    }

    override suspend fun migrateAndDelete(sourceId: Long, targetId: Long) {
        require(sourceId != targetId) { "请选择不同的目标分类" }
        database.withTransaction {
            val source = requireNotNull(dao.get(sourceId)) { "分类不存在" }
            val target = requireNotNull(dao.get(targetId)) { "目标分类不存在" }
            require(source.scope == target.scope) { "只能迁移到同类型分类" }
            require(!target.isHidden) { "不能迁移到已隐藏分类" }
            dao.moveRecords(sourceId, targetId)
            dao.moveTemplates(sourceId, targetId)
            dao.delete(sourceId)
        }
    }

    private fun validateName(name: String): String = name.trim().also {
        require(it.isNotEmpty()) { "分类名称不能为空" }
    }
}

