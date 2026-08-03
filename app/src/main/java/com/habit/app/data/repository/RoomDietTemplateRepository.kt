package com.habit.app.data.repository

import androidx.room.withTransaction
import com.habit.app.data.local.DietPhotoEntity
import com.habit.app.data.local.DietTemplateEntity
import com.habit.app.data.local.DietTemplateFoodItemEntity
import com.habit.app.data.local.DietTemplateToppingEntity
import com.habit.app.data.local.HabitDatabase
import com.habit.app.data.local.toDomain
import com.habit.app.data.photos.DietPhotoStore
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.DietTemplateDraft
import com.habit.app.domain.model.MealRecordDraft
import com.habit.app.domain.repository.DietTemplateRepository
import java.time.Clock
import kotlinx.coroutines.flow.map

class RoomDietTemplateRepository(
    private val database: HabitDatabase,
    private val photoStore: DietPhotoStore,
    private val clock: Clock,
) : DietTemplateRepository {
    private val dao = database.dietDao()

    override fun observeAll() = dao.observeTemplates().map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(draft: DietTemplateDraft): Long {
        require(draft.name.trim().isNotEmpty()) { "请填写模板名称" }
        val copied = if (draft.includePhotos) {
            draft.meal.photos.take(3).map { photoStore.copy(it.relativePath) }.let { photoStore.commit(it) }
        } else emptyList()
        return try {
            val id = database.withTransaction {
                val now = clock.millis()
                val existing = draft.id?.let { dao.getTemplateEntity(it) }
                val drink = draft.meal.beverage
                val entity = DietTemplateEntity(
                    id = existing?.id ?: 0,
                    name = draft.name.trim(),
                    recordType = draft.meal.recordType.name,
                    mealType = draft.meal.mealType?.name,
                    description = draft.meal.description.trim(),
                    manualFinalCalories = draft.meal.manualFinalCalories,
                    beverageCategory = drink?.category?.name,
                    brandOrStore = drink?.brandOrStore,
                    beverageName = drink?.beverageName,
                    sizeOrVolume = drink?.sizeOrVolume,
                    temperature = drink?.temperature,
                    iceLevel = drink?.iceLevel,
                    sweetness = drink?.sweetness,
                    cupCount = drink?.cupCount,
                    note = draft.meal.note.trim(),
                    sortOrder = existing?.sortOrder
                        ?: dao.nextTemplateSortOrder(draft.meal.recordType.name),
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
                val templateId = if (existing == null) dao.insertTemplate(entity) else {
                    dao.updateTemplate(entity)
                    existing.id
                }
                dao.deleteTemplateFoodItems(templateId)
                dao.deleteTemplateToppings(templateId)
                dao.deleteTemplatePhotos(templateId)
                dao.insertTemplateFoodItems(draft.meal.foodItems.mapIndexed { index, food ->
                    DietTemplateFoodItemEntity(0, templateId, food.name, food.portionText, food.calories, index, now, now)
                })
                dao.insertTemplateToppings(drink?.toppings.orEmpty().mapIndexed { index, topping ->
                    DietTemplateToppingEntity(0, templateId, topping, index, now, now)
                })
                dao.insertPhotos(copied.mapIndexed { index, photo ->
                    DietPhotoEntity(0, null, templateId, photo.relativePath, index, now)
                })
                templateId
            }
            photoStore.removeOrphans(dao.getAllPhotoPaths().toSet())
            id
        } catch (error: Exception) {
            copied.forEach { photoStore.delete(it.relativePath) }
            throw error
        }
    }

    override suspend fun delete(id: Long) {
        database.withTransaction { dao.deleteTemplate(id) }
        photoStore.removeOrphans(dao.getAllPhotoPaths().toSet())
    }

    override suspend fun rename(id: Long, name: String) {
        require(name.trim().isNotEmpty()) { "模板名称不能为空" }
        require(dao.renameTemplate(id, name.trim(), clock.millis()) == 1) { "模板不存在" }
    }

    override suspend fun reorder(ids: List<Long>) {
        database.withTransaction {
            val now = clock.millis()
            ids.forEachIndexed { index, id -> dao.updateTemplateSortOrder(id, index, now) }
        }
    }

    override suspend fun createMealDraft(id: Long, nowMillis: Long, epochDay: Long): MealRecordDraft {
        val template = requireNotNull(dao.getTemplate(id)) { "模板不存在" }.toDomain()
        val stagedPhotos = template.photos.map { photoStore.copy(it.relativePath) }
        return template.draft.copy(
            occurredAt = nowMillis,
            recordEpochDay = epochDay,
            photos = stagedPhotos,
        )
    }
}
