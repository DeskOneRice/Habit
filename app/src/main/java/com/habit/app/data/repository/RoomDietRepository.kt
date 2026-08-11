package com.habit.app.data.repository

import androidx.room.withTransaction
import com.habit.app.data.local.BeverageDetailEntity
import com.habit.app.data.local.BeverageToppingEntity
import com.habit.app.data.local.FoodItemEntity
import com.habit.app.data.local.DietPhotoEntity
import com.habit.app.data.local.HabitDatabase
import com.habit.app.data.local.MealRecordEntity
import com.habit.app.data.local.toDomain
import com.habit.app.data.local.toEntity
import com.habit.app.domain.model.CalorieSource
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealRecordDraft
import com.habit.app.domain.repository.DietRepository
import com.habit.app.domain.stats.calculateCalories
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDietRepository(
    private val database: HabitDatabase,
    private val clock: Clock,
) : DietRepository {
    private val dao = database.dietDao()

    override fun observeAll(): Flow<List<MealRecord>> =
        dao.observeAllRecords().map { rows -> rows.map { it.toDomain() } }

    override fun observeDay(epochDay: Long): Flow<List<MealRecord>> =
        dao.observeDay(epochDay).map { rows -> rows.map { it.toDomain() } }

    override fun observeRecord(id: Long): Flow<MealRecord?> =
        dao.observeRecord(id).map { it?.toDomain() }

    override fun observeRange(startEpochDay: Long, endEpochDay: Long): Flow<List<MealRecord>> =
        dao.observeRange(startEpochDay, endEpochDay).map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(id: Long?, draft: MealRecordDraft): Long = database.withTransaction {
        val normalized = validate(draft)
        val now = clock.millis()
        val calculated = calculateCalories(normalized.foodItems, normalized.manualFinalCalories)
        val existing = id?.let { requireNotNull(dao.getRecordEntity(it)) { "饮食记录不存在" } }
        val existingEstimate = existing?.let { dao.getCalorieEstimate(it.id) }
        val attachedEstimate = normalized.aiCalorieEstimate?.takeIf { normalized.recordType == DietRecordType.MEAL }
        val reconciledAttachedEstimate = attachedEstimate?.let { estimate ->
            val finalCalories = requireNotNull(calculated.finalCalories) { "AI evidence requires final calories" }
            estimate.copy(
                adoptedKcal = finalCalories,
                wasModified = finalCalories != estimate.suggestedKcal,
            )
        }
        val reconciledRetainedEstimate = existingEstimate
            ?.takeIf { reconciledAttachedEstimate == null && normalized.recordType == DietRecordType.MEAL }
            ?.let { estimate ->
                val finalCalories = requireNotNull(calculated.finalCalories) { "AI evidence requires final calories" }
                estimate.copy(
                    adoptedKcal = finalCalories,
                    wasModified = finalCalories != estimate.suggestedKcal,
                )
            }
        val evidenceWasModified = reconciledAttachedEstimate?.wasModified
            ?: reconciledRetainedEstimate?.wasModified
        val calories = calculated.copy(
            source = when (evidenceWasModified) {
                false -> CalorieSource.AI_ESTIMATE
                true -> CalorieSource.MANUAL
                null -> calculated.source
            },
        )
        val entity = MealRecordEntity(
            id = existing?.id ?: 0,
            recordType = normalized.recordType.name,
            mealType = normalized.mealType?.name,
            occurredAt = normalized.occurredAt,
            recordEpochDay = normalized.recordEpochDay,
            description = normalized.description,
            calculatedCalories = calories.calculatedCalories,
            finalCalories = calories.finalCalories,
            calorieSource = calories.source.name,
            note = normalized.note,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            dietCategoryId = normalized.dietCategoryId,
        )
        val recordId = if (existing == null) dao.insertRecord(entity) else {
            dao.updateRecord(entity)
            existing.id
        }
        dao.deleteFoodItems(recordId)
        dao.deleteBeverage(recordId)
        dao.deleteToppings(recordId)
        dao.deleteRecordPhotos(recordId)
        dao.insertFoodItems(normalized.foodItems.mapIndexed { index, item ->
            FoodItemEntity(
                mealRecordId = recordId,
                name = item.name,
                portionText = item.portionText,
                calories = item.calories,
                sortOrder = index,
                createdAt = now,
                updatedAt = now,
            )
        })
        normalized.beverage?.let { drink ->
            dao.insertBeverage(
                BeverageDetailEntity(
                    recordId,
                    drink.category.name,
                    drink.brandOrStore,
                    drink.beverageName,
                    drink.sizeOrVolume,
                    drink.temperature,
                    drink.iceLevel,
                    drink.sweetness,
                    drink.cupCount,
                ),
            )
            dao.insertToppings(drink.toppings.mapIndexed { index, topping ->
                BeverageToppingEntity(
                    mealRecordId = recordId,
                    name = topping,
                    sortOrder = index,
                    createdAt = now,
                    updatedAt = now,
                )
            })
        }
        dao.insertPhotos(normalized.photos.mapIndexed { index, photo ->
            DietPhotoEntity(
                mealRecordId = recordId,
                templateId = null,
                relativePath = photo.relativePath,
                sortOrder = index,
                createdAt = now,
            )
        })
        if (reconciledAttachedEstimate != null) {
            dao.deleteCalorieEstimate(recordId)
            dao.insertCalorieEstimate(reconciledAttachedEstimate.toEntity(recordId))
        } else if (reconciledRetainedEstimate != null) {
            dao.insertCalorieEstimate(reconciledRetainedEstimate)
        } else if (existingEstimate != null) {
            dao.deleteCalorieEstimate(recordId)
        }
        recordId
    }

    override suspend fun delete(id: Long) {
        dao.deleteRecord(id)
    }

    override suspend fun referencedPhotoPaths(): Set<String> = dao.getAllPhotoPaths().toSet()

    private fun validate(draft: MealRecordDraft): MealRecordDraft {
        require(draft.photos.size <= 3) { "每条记录最多添加 3 张照片" }
        require(draft.manualFinalCalories == null || draft.manualFinalCalories >= 0) { "热量不能小于 0" }
        val foods = draft.foodItems
            .map { it.copy(name = it.name.trim(), portionText = it.portionText?.trim()) }
            .filter { it.name.isNotBlank() }
        require(foods.all { it.calories == null || it.calories >= 0 }) { "食物热量不能小于 0" }
        val drink = draft.beverage?.copy(
            brandOrStore = draft.beverage.brandOrStore.trim(),
            beverageName = draft.beverage.beverageName.trim(),
            sizeOrVolume = draft.beverage.sizeOrVolume.trim(),
            temperature = draft.beverage.temperature.trim(),
            iceLevel = draft.beverage.iceLevel.trim(),
            sweetness = draft.beverage.sweetness.trim(),
            toppings = draft.beverage.toppings.map(String::trim).filter(String::isNotBlank).distinct(),
        )
        if (draft.recordType == DietRecordType.MEAL) require(draft.mealType != null) { "请选择餐次" }
        if (draft.recordType == DietRecordType.BEVERAGE) {
            requireNotNull(drink) { "请填写饮品信息" }
            require(drink.beverageName.isNotBlank()) { "请填写饮品名称" }
            require(drink.cupCount >= 1) { "杯数至少为 1" }
        }
        require(draft.description.isNotBlank() || foods.isNotEmpty() || !drink?.beverageName.isNullOrBlank()) {
            "请至少填写一项饮食内容"
        }
        return draft.copy(
            description = draft.description.trim(),
            foodItems = foods,
            beverage = drink,
            note = draft.note.trim(),
            dietCategoryId = draft.dietCategoryId.takeIf { it > 0 } ?: defaultDietCategoryId(draft),
            aiCalorieEstimate = draft.aiCalorieEstimate?.takeIf { draft.recordType == DietRecordType.MEAL },
        )
    }

    private fun defaultDietCategoryId(draft: MealRecordDraft): Long = when {
        draft.recordType == DietRecordType.MEAL -> 4L
        draft.beverage?.category?.name == "COFFEE" -> 5L
        draft.beverage?.category?.name == "MILK_TEA" -> 6L
        draft.beverage?.category?.name == "TEA" -> 7L
        draft.beverage?.category?.name == "FRUIT_DRINK" -> 8L
        draft.beverage?.category?.name == "DAIRY" -> 9L
        else -> 10L
    }
}
