package com.habit.app.data.repository

import androidx.room.withTransaction
import com.habit.app.data.local.BeverageDetailEntity
import com.habit.app.data.local.BeverageToppingEntity
import com.habit.app.data.local.FoodItemEntity
import com.habit.app.data.local.HabitDatabase
import com.habit.app.data.local.MealRecordEntity
import com.habit.app.data.local.toDomain
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

    override fun observeDay(epochDay: Long): Flow<List<MealRecord>> =
        dao.observeDay(epochDay).map { rows -> rows.map { it.toDomain() } }

    override fun observeRecord(id: Long): Flow<MealRecord?> =
        dao.observeRecord(id).map { it?.toDomain() }

    override fun observeRange(startEpochDay: Long, endEpochDay: Long): Flow<List<MealRecord>> =
        dao.observeRange(startEpochDay, endEpochDay).map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(id: Long?, draft: MealRecordDraft): Long = database.withTransaction {
        val normalized = validate(draft)
        val now = clock.millis()
        val calories = calculateCalories(normalized.foodItems, normalized.manualFinalCalories)
        val existing = id?.let { requireNotNull(dao.getRecordEntity(it)) { "饮食记录不存在" } }
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
        )
        val recordId = if (existing == null) dao.insertRecord(entity) else {
            dao.updateRecord(entity)
            existing.id
        }
        dao.deleteFoodItems(recordId)
        dao.deleteBeverage(recordId)
        dao.deleteToppings(recordId)
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
        recordId
    }

    override suspend fun delete(id: Long) {
        dao.deleteRecord(id)
    }

    private fun validate(draft: MealRecordDraft): MealRecordDraft {
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
        )
    }
}
