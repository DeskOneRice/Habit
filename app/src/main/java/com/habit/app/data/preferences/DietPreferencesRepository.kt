package com.habit.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

enum class DrawerModuleGroup { HABIT, DIET, AI }

data class DietPreferences(
    val dailyGoalEnabled: Boolean = false,
    val dailyGoalKcal: Int? = null,
    val habitGroupExpanded: Boolean = true,
    val dietGroupExpanded: Boolean = true,
    val aiGroupExpanded: Boolean = true,
)

fun validateDietGoal(enabled: Boolean, kcal: Int?): Int? {
    if (!enabled) return null
    requireNotNull(kcal) { "请输入每日热量目标" }
    require(kcal > 0) { "每日热量目标必须大于 0" }
    return kcal
}

class DietPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock = Clock.systemUTC(),
) {
    val preferences: Flow<DietPreferences> = dataStore.data.map { values ->
        DietPreferences(
            dailyGoalEnabled = values[dailyCalorieGoalEnabledKey] ?: false,
            dailyGoalKcal = values[dailyCalorieGoalKcalKey],
            habitGroupExpanded = values[habitGroupExpandedKey] ?: true,
            dietGroupExpanded = values[dietGroupExpandedKey] ?: true,
            aiGroupExpanded = values[aiGroupExpandedKey] ?: true,
        )
    }

    suspend fun setGoal(enabled: Boolean, kcal: Int?) {
        val validated = validateDietGoal(enabled, kcal)
        dataStore.edit { values ->
            values[dailyCalorieGoalEnabledKey] = enabled
            if (validated == null) values.remove(dailyCalorieGoalKcalKey)
            else values[dailyCalorieGoalKcalKey] = validated
            values[preferencesUpdatedAtKey] = clock.millis()
        }
    }

    suspend fun setGroupExpanded(group: DrawerModuleGroup, expanded: Boolean) {
        dataStore.edit { values ->
            values[when (group) {
                DrawerModuleGroup.HABIT -> habitGroupExpandedKey
                DrawerModuleGroup.DIET -> dietGroupExpandedKey
                DrawerModuleGroup.AI -> aiGroupExpandedKey
            }] = expanded
        }
    }
}
