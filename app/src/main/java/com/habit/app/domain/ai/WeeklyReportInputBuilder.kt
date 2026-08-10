package com.habit.app.domain.ai

import com.habit.app.domain.model.DayHabit
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.WeeklyReportCoverage
import com.habit.app.domain.repository.CalendarRepository
import com.habit.app.domain.repository.CategoryRepository
import com.habit.app.domain.repository.DietRepository
import com.habit.app.domain.time.HabitTimePolicy
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class WeeklyHabitInput(
    val name: String,
    val categoryName: String,
    val applicableDays: Int,
    val completedDays: Int,
    val dailyCompleted: List<Boolean>,
)

data class WeeklyDietInput(
    val name: String,
    val type: String,
    val subtype: String,
    val epochDay: Long,
    val beijingHour: Int,
    val finalCalories: Int?,
    val calorieSource: String,
)

data class WeeklyReportInput(
    val startEpochDay: Long,
    val endEpochDay: Long,
    val habits: List<WeeklyHabitInput>,
    val dietRecords: List<WeeklyDietInput>,
    val coverage: WeeklyReportCoverage,
    val json: String,
)

sealed interface WeeklyReportInputBuildResult {
    data class Ready(val input: WeeklyReportInput) : WeeklyReportInputBuildResult
    data object NoAnalyzableData : WeeklyReportInputBuildResult
}

fun interface WeeklyReportInputLoader {
    suspend fun build(today: LocalDate): WeeklyReportInputBuildResult
}

class WeeklyReportInputBuilder(
    private val calendarRepository: CalendarRepository,
    private val categoryRepository: CategoryRepository,
    private val dietRepository: DietRepository,
) : WeeklyReportInputLoader {
    override suspend fun build(today: LocalDate): WeeklyReportInputBuildResult {
        val week = previousCompleteWeek(today)
        val dates = generateSequence(week.start) { date -> date.plusDays(1).takeIf { it <= week.endInclusive } }
            .toList()
        val days = dates.map { date -> calendarRepository.observeDay(date, today).first() }
        val categories = categoryRepository.observeAll().first().associateBy { it.id }
        val records = dietRepository.observeRange(week.start.toEpochDay(), week.endInclusive.toEpochDay()).first()

        if (days.all { it.habits.isEmpty() } && records.isEmpty()) {
            return WeeklyReportInputBuildResult.NoAnalyzableData
        }

        val habits = linkedMapOf<Long, HabitAccumulator>()
        days.forEach { day ->
            day.habits.forEach { dayHabit ->
                val accumulator = habits.getOrPut(dayHabit.habit.id) {
                    HabitAccumulator(
                        name = dayHabit.habit.name,
                        categoryName = categories[dayHabit.habit.categoryId]?.name.orEmpty(),
                        sortOrder = dayHabit.habit.sortOrder,
                    )
                }
                accumulator.completed += dayHabit.checked
            }
        }
        val habitInputs = habits.values
            .sortedBy(HabitAccumulator::sortOrder)
            .map { accumulator ->
                WeeklyHabitInput(
                    name = accumulator.name,
                    categoryName = accumulator.categoryName,
                    applicableDays = accumulator.completed.size,
                    completedDays = accumulator.completed.count(Boolean::not).let { accumulator.completed.size - it },
                    dailyCompleted = accumulator.completed.toList(),
                )
            }
        val dietInputs = records.sortedWith(compareBy(MealRecord::recordEpochDay, MealRecord::occurredAt)).map { record ->
            WeeklyDietInput(
                name = record.beverage?.beverageName?.takeIf(String::isNotBlank)
                    ?: record.description.takeIf(String::isNotBlank)
                    ?: record.recordType.name,
                type = record.recordType.name,
                subtype = record.mealType?.name ?: record.beverage?.category?.name.orEmpty(),
                epochDay = record.recordEpochDay,
                beijingHour = Instant.ofEpochMilli(record.occurredAt).atZone(HabitTimePolicy.zoneId).hour,
                finalCalories = record.finalCalories,
                calorieSource = record.calorieSource.name,
            )
        }
        val coverage = WeeklyReportCoverage(
            scheduledHabitCount = habitInputs.sumOf(WeeklyHabitInput::applicableDays),
            completedHabitCount = habitInputs.sumOf(WeeklyHabitInput::completedDays),
            dietRecordCount = dietInputs.size,
            dietRecordDays = dietInputs.map(WeeklyDietInput::epochDay).distinct().size,
            knownCalorieRecords = dietInputs.count { it.finalCalories != null },
            missingCalorieRecords = dietInputs.count { it.finalCalories == null },
        )
        val inputWithoutJson = WeeklyReportInput(
            startEpochDay = week.start.toEpochDay(),
            endEpochDay = week.endInclusive.toEpochDay(),
            habits = habitInputs,
            dietRecords = dietInputs,
            coverage = coverage,
            json = "",
        )
        return WeeklyReportInputBuildResult.Ready(inputWithoutJson.copy(json = inputWithoutJson.toSafeJson()))
    }
}

private data class HabitAccumulator(
    val name: String,
    val categoryName: String,
    val sortOrder: Int,
    val completed: MutableList<Boolean> = mutableListOf(),
)

private fun WeeklyReportInput.toSafeJson(): String = buildJsonObject {
    put("startEpochDay", startEpochDay)
    put("endEpochDay", endEpochDay)
    put("habits", buildJsonArray {
        habits.forEach { habit ->
            add(buildJsonObject {
                put("name", habit.name)
                put("categoryName", habit.categoryName)
                put("applicableDays", habit.applicableDays)
                put("completedDays", habit.completedDays)
                put("dailyCompleted", JsonArray(habit.dailyCompleted.map(::JsonPrimitive)))
            })
        }
    })
    put("dietRecords", buildJsonArray {
        dietRecords.forEach { record ->
            add(buildJsonObject {
                put("name", record.name)
                put("type", record.type)
                put("subtype", record.subtype)
                put("epochDay", record.epochDay)
                put("beijingHour", record.beijingHour)
                put("knownCalories", record.finalCalories?.let(::JsonPrimitive) ?: JsonNull)
                put("calorieMissing", record.finalCalories == null)
                put("calorieSource", record.calorieSource)
            })
        }
    })
    put("coverage", buildJsonObject {
        put("scheduledHabitCount", coverage.scheduledHabitCount)
        put("completedHabitCount", coverage.completedHabitCount)
        put(
            "completionRate",
            if (coverage.scheduledHabitCount == 0) 0.0
            else coverage.completedHabitCount.toDouble() / coverage.scheduledHabitCount,
        )
        put("dietRecordCount", coverage.dietRecordCount)
        put("dietRecordDays", coverage.dietRecordDays)
        put("knownCalorieRecords", coverage.knownCalorieRecords)
        put("missingCalorieRecords", coverage.missingCalorieRecords)
    })
}.toString()
