package com.habit.app.domain.model

enum class DietCategoryScope { MEAL, BEVERAGE }

data class DietCategory(
    val id: Long,
    val scope: DietCategoryScope,
    val name: String,
    val isPreset: Boolean,
    val isHidden: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class DietCategoryPreset(
    val id: Long,
    val scope: DietCategoryScope,
    val name: String,
)

val DIET_CATEGORY_PRESETS = listOf(
    DietCategoryPreset(1, DietCategoryScope.MEAL, "家常菜"),
    DietCategoryPreset(2, DietCategoryScope.MEAL, "快餐"),
    DietCategoryPreset(3, DietCategoryScope.MEAL, "零食"),
    DietCategoryPreset(4, DietCategoryScope.MEAL, "其他餐食"),
    DietCategoryPreset(5, DietCategoryScope.BEVERAGE, "咖啡"),
    DietCategoryPreset(6, DietCategoryScope.BEVERAGE, "奶茶"),
    DietCategoryPreset(7, DietCategoryScope.BEVERAGE, "茶"),
    DietCategoryPreset(8, DietCategoryScope.BEVERAGE, "果饮"),
    DietCategoryPreset(9, DietCategoryScope.BEVERAGE, "乳饮"),
    DietCategoryPreset(10, DietCategoryScope.BEVERAGE, "其他饮品"),
)

