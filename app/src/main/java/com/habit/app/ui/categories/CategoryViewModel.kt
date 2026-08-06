package com.habit.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habit.app.domain.model.DietCategoryScope
import com.habit.app.domain.repository.CategoryRepository
import com.habit.app.domain.repository.DietCategoryRepository
import com.habit.app.domain.repository.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CategorySection { HABIT, MEAL, BEVERAGE }

data class ManagedCategoryItem(
    val id: Long,
    val section: CategorySection,
    val name: String,
    val isPreset: Boolean,
    val isHidden: Boolean,
    val usageCount: Int,
)

data class CategoryUiState(
    val section: CategorySection = CategorySection.HABIT,
    val items: List<ManagedCategoryItem> = emptyList(),
    val saving: Boolean = false,
    val message: String? = null,
)

private data class CategoryCatalog(
    val habits: List<ManagedCategoryItem>,
    val meals: List<ManagedCategoryItem>,
    val beverages: List<ManagedCategoryItem>,
) {
    fun items(section: CategorySection): List<ManagedCategoryItem> = when (section) {
        CategorySection.HABIT -> habits
        CategorySection.MEAL -> meals
        CategorySection.BEVERAGE -> beverages
    }
}

class CategoryViewModel(
    private val categories: CategoryRepository,
    habits: HabitRepository,
    private val dietCategories: DietCategoryRepository,
    initialSection: CategorySection = CategorySection.HABIT,
) : ViewModel() {
    private val selectedSection = MutableStateFlow(initialSection)
    private val saving = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val habitItems = combine(categories.observeAll(), habits.observeAll()) { groups, allHabits ->
        val counts = allHabits.groupingBy { it.categoryId }.eachCount()
        groups.map { category ->
            ManagedCategoryItem(
                id = category.id,
                section = CategorySection.HABIT,
                name = category.name,
                isPreset = category.isPreset,
                isHidden = category.isHidden,
                usageCount = counts[category.id] ?: 0,
            )
        }
    }
    private val mealItems = combine(
        dietCategories.observeAll(DietCategoryScope.MEAL),
        dietCategories.observeUsageCounts(DietCategoryScope.MEAL),
    ) { groups, counts -> groups.map { it.toManaged(CategorySection.MEAL, counts[it.id] ?: 0) } }
    private val beverageItems = combine(
        dietCategories.observeAll(DietCategoryScope.BEVERAGE),
        dietCategories.observeUsageCounts(DietCategoryScope.BEVERAGE),
    ) { groups, counts -> groups.map { it.toManaged(CategorySection.BEVERAGE, counts[it.id] ?: 0) } }
    private val catalog = combine(habitItems, mealItems, beverageItems, ::CategoryCatalog)

    val state: StateFlow<CategoryUiState> = combine(
        selectedSection,
        catalog,
        saving,
        message,
    ) { section, all, isSaving, currentMessage ->
        CategoryUiState(
            section = section,
            items = all.items(section),
            saving = isSaving,
            message = currentMessage,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CategoryUiState(section = initialSection),
    )

    fun selectSection(section: CategorySection) {
        selectedSection.value = section
        message.value = null
    }

    fun clearMessage() {
        message.value = null
    }

    fun create(name: String, onDone: () -> Unit) {
        val section = selectedSection.value
        mutate(onDone) {
            when (section) {
                CategorySection.HABIT -> categories.create(name)
                CategorySection.MEAL -> dietCategories.create(DietCategoryScope.MEAL, name)
                CategorySection.BEVERAGE -> dietCategories.create(DietCategoryScope.BEVERAGE, name)
            }
        }
    }

    fun rename(id: Long, name: String, onDone: () -> Unit) =
        rename(selectedSection.value, id, name, onDone)

    fun rename(section: CategorySection, id: Long, name: String, onDone: () -> Unit) {
        mutate(onDone) {
            if (section == CategorySection.HABIT) categories.rename(id, name)
            else dietCategories.rename(id, name)
        }
    }

    fun setHidden(id: Long, hidden: Boolean) {
        val section = selectedSection.value
        mutate {
            if (section == CategorySection.HABIT) categories.setHidden(id, hidden)
            else dietCategories.setHidden(id, hidden)
        }
    }

    fun migrateAndDelete(source: Long, target: Long, onDone: () -> Unit) {
        val section = selectedSection.value
        mutate(onDone) {
            require(source != target) { "请选择不同的目标分类" }
            if (section == CategorySection.HABIT) categories.migrateAndDelete(source, target)
            else dietCategories.migrateAndDelete(source, target)
        }
    }

    private fun mutate(onDone: () -> Unit = {}, action: suspend () -> Any?) {
        if (saving.value) return
        saving.value = true
        message.value = null
        viewModelScope.launch {
            try {
                action()
                onDone()
            } catch (error: Exception) {
                message.value = error.message ?: "操作失败"
            } finally {
                saving.value = false
            }
        }
    }
}

private fun com.habit.app.domain.model.DietCategory.toManaged(
    section: CategorySection,
    count: Int,
) = ManagedCategoryItem(id, section, name, isPreset, isHidden, count)
