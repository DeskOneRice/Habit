package com.habit.app.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.domain.model.BeverageCategory
import com.habit.app.domain.model.BeverageDetails
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.FoodItemDraft
import com.habit.app.domain.model.MealRecordDraft
import com.habit.app.domain.model.MealType
import com.habit.app.domain.repository.DietRepository
import com.habit.app.domain.stats.calculateCalories
import com.habit.app.domain.stats.suggestMealType
import com.habit.app.domain.time.DeviceDateProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DietEditorUiState(
    val recordType: DietRecordType = DietRecordType.MEAL,
    val mealType: MealType = MealType.SNACK,
    val date: LocalDate,
    val time: LocalTime,
    val description: String = "",
    val foodItems: List<FoodItemDraft> = emptyList(),
    val finalCaloriesText: String = "",
    val beverageCategory: BeverageCategory = BeverageCategory.COFFEE,
    val brandOrStore: String = "",
    val beverageName: String = "",
    val sizeOrVolume: String = "",
    val temperature: String = "",
    val iceLevel: String = "",
    val sweetness: String = "",
    val toppings: String = "",
    val cupCountText: String = "1",
    val note: String = "",
    val isSaving: Boolean = false,
    val message: String? = null,
)

internal fun DietEditorUiState.withDate(value: LocalDate) = copy(date = value)

internal fun DietEditorUiState.withTime(value: LocalTime) = copy(
    time = value.withSecond(0).withNano(0),
)

class DietEditorViewModel(
    private val recordId: Long?,
    private val repository: DietRepository,
    private val dateProvider: DeviceDateProvider,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val now = Instant.now(clock).atZone(dateProvider.zoneId)
    private val mutableState = MutableStateFlow(
        DietEditorUiState(
            date = dateProvider.today(),
            time = now.toLocalTime().withSecond(0).withNano(0),
            mealType = suggestMealType(now.toLocalTime()),
        ),
    )
    val state: StateFlow<DietEditorUiState> = mutableState.asStateFlow()

    init {
        if (recordId != null) viewModelScope.launch {
            repository.observeRecord(recordId).filterNotNull().first().let { record ->
                val occurred = Instant.ofEpochMilli(record.occurredAt).atZone(dateProvider.zoneId)
                val drink = record.beverage
                mutableState.value = DietEditorUiState(
                    recordType = record.recordType,
                    mealType = record.mealType ?: MealType.SNACK,
                    date = LocalDate.ofEpochDay(record.recordEpochDay),
                    time = occurred.toLocalTime().withSecond(0).withNano(0),
                    description = record.description,
                    foodItems = record.foodItems.map { FoodItemDraft(it.name, it.portionText, it.calories) },
                    finalCaloriesText = record.finalCalories?.toString().orEmpty(),
                    beverageCategory = drink?.category ?: BeverageCategory.COFFEE,
                    brandOrStore = drink?.brandOrStore.orEmpty(),
                    beverageName = drink?.beverageName.orEmpty(),
                    sizeOrVolume = drink?.sizeOrVolume.orEmpty(),
                    temperature = drink?.temperature.orEmpty(),
                    iceLevel = drink?.iceLevel.orEmpty(),
                    sweetness = drink?.sweetness.orEmpty(),
                    toppings = drink?.toppings?.joinToString("、").orEmpty(),
                    cupCountText = drink?.cupCount?.toString() ?: "1",
                    note = record.note,
                )
            }
        }
    }

    fun update(block: DietEditorUiState.() -> DietEditorUiState) {
        mutableState.value = mutableState.value.block().copy(message = null)
    }

    fun addFoodItem() = update { copy(foodItems = foodItems + FoodItemDraft("", null, null)) }
    fun updateFood(index: Int, name: String, portion: String, calories: String) = update {
        copy(foodItems = foodItems.mapIndexed { current, item ->
            if (current == index) item.copy(name = name, portionText = portion.ifBlank { null }, calories = calories.toIntOrNull()) else item
        })
    }
    fun removeFood(index: Int) = update { copy(foodItems = foodItems.filterIndexed { current, _ -> current != index }) }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, message = null)
            try {
                val current = mutableState.value
                val occurredAt = current.date.atTime(current.time).atZone(dateProvider.zoneId).toInstant().toEpochMilli()
                val manual = current.finalCaloriesText.toIntOrNull()
                calculateCalories(current.foodItems, manual)
                val beverage = if (current.recordType == DietRecordType.BEVERAGE) BeverageDetails(
                    current.beverageCategory,
                    current.brandOrStore,
                    current.beverageName,
                    current.sizeOrVolume,
                    current.temperature,
                    current.iceLevel,
                    current.sweetness,
                    current.toppings.split('、', ',', '，').map(String::trim).filter(String::isNotBlank),
                    current.cupCountText.toIntOrNull() ?: 0,
                ) else null
                repository.save(
                    recordId,
                    MealRecordDraft(
                        current.recordType,
                        if (current.recordType == DietRecordType.MEAL) current.mealType else null,
                        occurredAt,
                        current.date.toEpochDay(),
                        current.description,
                        current.foodItems,
                        manual,
                        beverage,
                        current.note,
                    ),
                )
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(message = error.message ?: "保存失败")
            } finally {
                mutableState.value = mutableState.value.copy(isSaving = false)
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = recordId ?: return
        viewModelScope.launch {
            repository.delete(id)
            onDeleted()
        }
    }
}
