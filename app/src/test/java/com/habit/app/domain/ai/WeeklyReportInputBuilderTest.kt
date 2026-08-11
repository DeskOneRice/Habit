package com.habit.app.domain.ai

import com.habit.app.domain.model.BeverageCategory
import com.habit.app.domain.model.BeverageDetails
import com.habit.app.domain.model.CalorieSource
import com.habit.app.domain.model.Category
import com.habit.app.domain.model.DayHabit
import com.habit.app.domain.model.DaySnapshot
import com.habit.app.domain.model.DietPhoto
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.FoodItem
import com.habit.app.domain.model.Habit
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealRecordDraft
import com.habit.app.domain.model.MealType
import com.habit.app.domain.repository.CalendarRepository
import com.habit.app.domain.repository.CategoryRepository
import com.habit.app.domain.repository.DietRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReportInputBuilderTest {
    private val today = LocalDate.of(2026, 8, 11)
    private val monday = LocalDate.of(2026, 8, 3)
    private val habit = Habit(
        id = 91,
        name = "晨间阅读",
        iconKey = "private-icon",
        themeColor = 123,
        categoryId = 7,
        startEpochDay = monday.toEpochDay(),
        archivedEpochDay = null,
        sortOrder = 0,
        createdAt = 111,
        updatedAt = 222,
    )

    @Test
    fun buildsPreviousBeijingWeekAndSerializesOnlyAllowedStructuredFields() = runTest {
        val snapshots = (0L..6L).associate { offset ->
            val date = monday.plusDays(offset)
            date to DaySnapshot(date.toEpochDay(), listOf(DayHabit(habit, offset == 0L || offset == 3L, 999)))
        }
        val calendar = FakeCalendarRepository(snapshots)
        val diet = FakeDietRepository(
            listOf(
                meal(
                    id = 501,
                    epochDay = monday.toEpochDay(),
                    occurredAt = Instant.parse("2026-08-03T00:15:00Z").toEpochMilli(),
                    calories = 0,
                    source = CalorieSource.MANUAL,
                    note = "private note",
                    path = "photos/relativePath-secret.jpg",
                ),
                meal(
                    id = 502,
                    epochDay = monday.plusDays(1).toEpochDay(),
                    occurredAt = Instant.parse("2026-08-04T12:30:00Z").toEpochMilli(),
                    calories = null,
                    source = CalorieSource.NONE,
                    note = "private note two",
                    path = "photos/secret-2.jpg",
                ),
            ),
        )
        val builder = WeeklyReportInputBuilder(
            calendar,
            FakeCategoryRepository(listOf(category(7, "成长"))),
            diet,
        )

        val result = builder.build(today)
        val input = (result as WeeklyReportInputBuildResult.Ready).input

        assertEquals(monday.toEpochDay(), input.startEpochDay)
        assertEquals(monday.plusDays(6).toEpochDay(), input.endEpochDay)
        assertEquals((0L..6L).map(monday::plusDays), calendar.requestedDates)
        assertTrue(calendar.requestedToday.all { it == today })
        assertEquals(monday.toEpochDay() to monday.plusDays(6).toEpochDay(), diet.requestedRange)
        assertEquals(7, input.habits.single().applicableDays)
        assertEquals(2, input.habits.single().completedDays)
        assertEquals(listOf(true, false, false, true, false, false, false), input.habits.single().dailyCompleted)
        assertEquals(8, input.dietRecords[0].beijingHour)
        assertEquals(20, input.dietRecords[1].beijingHour)
        assertEquals(7, input.coverage.scheduledHabitCount)
        assertEquals(2, input.coverage.completedHabitCount)
        assertEquals(2, input.coverage.dietRecordDays)
        assertEquals(1, input.coverage.knownCalorieRecords)
        assertEquals(1, input.coverage.missingCalorieRecords)

        val json = input.json
        listOf(
            "relativePath", "secret.jpg", "private note", "createdAt", "updatedAt",
            "iconKey", "themeColor", "categoryId", "dietCategoryId", "occurredAt", "\"id\"",
        ).forEach { forbidden -> assertFalse("leaked $forbidden", json.contains(forbidden)) }
        assertTrue(json.contains("\"knownCalories\":0"))
        assertTrue(json.contains("\"calorieMissing\":false"))
        assertTrue(json.contains("\"calorieMissing\":true"))
        assertTrue(json.contains("\"completionRate\":"))
        assertTrue(json.contains("\"periodStartDate\":\"2026-08-03\""))
        assertTrue(json.contains("\"periodEndDate\":\"2026-08-09\""))
        assertTrue(json.contains("\"periodLabel\":\"2026年8月3日至8月9日\""))
        assertTrue(json.contains("\"date\":\"2026-08-03\""))
        assertFalse(json.contains("startEpochDay"))
        assertFalse(json.contains("endEpochDay"))
        assertFalse(json.contains("\"epochDay\""))
    }

    @Test
    fun distinguishesNoAnalyzableDataFromSingleModuleWeeksWithExplicitZeroCoverage() = runTest {
        val emptyCalendar = FakeCalendarRepository(emptyMap())
        val emptyDiet = FakeDietRepository(emptyList())
        val none = WeeklyReportInputBuilder(
            emptyCalendar,
            FakeCategoryRepository(emptyList()),
            emptyDiet,
        ).build(today)
        assertSame(WeeklyReportInputBuildResult.NoAnalyzableData, none)

        val dietOnly = WeeklyReportInputBuilder(
            emptyCalendar,
            FakeCategoryRepository(emptyList()),
            FakeDietRepository(listOf(meal(calories = null))),
        ).build(today) as WeeklyReportInputBuildResult.Ready
        assertEquals(0, dietOnly.input.coverage.scheduledHabitCount)
        assertEquals(0, dietOnly.input.coverage.completedHabitCount)
        assertEquals(1, dietOnly.input.coverage.dietRecordCount)
        assertTrue(dietOnly.input.json.contains("\"scheduledHabitCount\":0"))

        val habitOnlyCalendar = FakeCalendarRepository(
            mapOf(monday to DaySnapshot(monday.toEpochDay(), listOf(DayHabit(habit, false, null)))),
        )
        val habitOnly = WeeklyReportInputBuilder(
            habitOnlyCalendar,
            FakeCategoryRepository(listOf(category(7, "成长"))),
            emptyDiet,
        ).build(today) as WeeklyReportInputBuildResult.Ready
        assertEquals(0, habitOnly.input.coverage.dietRecordCount)
        assertEquals(0, habitOnly.input.coverage.dietRecordDays)
        assertTrue(habitOnly.input.json.contains("\"dietRecordCount\":0"))
    }
}

private class FakeCalendarRepository(
    private val snapshots: Map<LocalDate, DaySnapshot>,
) : CalendarRepository {
    val requestedDates = mutableListOf<LocalDate>()
    val requestedToday = mutableListOf<LocalDate>()

    override fun observeDay(date: LocalDate, today: LocalDate): Flow<DaySnapshot> {
        requestedDates += date
        requestedToday += today
        return flowOf(snapshots[date] ?: DaySnapshot(date.toEpochDay(), emptyList()))
    }

    override fun observeMonth(month: java.time.YearMonth, today: LocalDate) = error("unused")
    override fun observeHabitHistory(habitId: Long, today: LocalDate) = error("unused")
}

private class FakeCategoryRepository(private val categories: List<Category>) : CategoryRepository {
    override fun observeAll(): Flow<List<Category>> = flowOf(categories)
    override fun observeVisible(): Flow<List<Category>> = flowOf(categories.filterNot(Category::isHidden))
    override suspend fun create(name: String) = error("unused")
    override suspend fun rename(id: Long, name: String) = error("unused")
    override suspend fun setHidden(id: Long, hidden: Boolean) = error("unused")
    override suspend fun migrateAndDelete(sourceId: Long, targetId: Long) = error("unused")
}

private class FakeDietRepository(private val records: List<MealRecord>) : DietRepository {
    var requestedRange: Pair<Long, Long>? = null
    override fun observeRange(startEpochDay: Long, endEpochDay: Long): Flow<List<MealRecord>> {
        requestedRange = startEpochDay to endEpochDay
        return flowOf(records)
    }
    override fun observeAll(): Flow<List<MealRecord>> = flowOf(records)
    override fun observeDay(epochDay: Long): Flow<List<MealRecord>> = flowOf(records)
    override fun observeRecord(id: Long): Flow<MealRecord?> = flowOf(records.firstOrNull { it.id == id })
    override suspend fun save(id: Long?, draft: MealRecordDraft) = error("unused")
    override suspend fun delete(id: Long) = error("unused")
}

private fun category(id: Long, name: String) = Category(id, name, false, false, 0, 11, 22)

private fun meal(
    id: Long = 501,
    epochDay: Long = LocalDate.of(2026, 8, 3).toEpochDay(),
    occurredAt: Long = Instant.parse("2026-08-03T00:00:00Z").toEpochMilli(),
    calories: Int? = 100,
    source: CalorieSource = CalorieSource.ITEM_SUM,
    note: String = "private note",
    path: String = "photos/private.jpg",
) = MealRecord(
    id = id,
    recordType = DietRecordType.BEVERAGE,
    mealType = null,
    occurredAt = occurredAt,
    recordEpochDay = epochDay,
    description = "拿铁",
    foodItems = listOf(FoodItem(1, id, "秘密配料", null, calories, 0, 33, 44)),
    calculatedCalories = calories,
    finalCalories = calories,
    calorieSource = source,
    beverage = BeverageDetails(BeverageCategory.COFFEE, "店铺", "拿铁", "中杯", "热", "无冰", "无糖", emptyList(), 1),
    note = note,
    createdAt = 55,
    updatedAt = 66,
    photos = listOf(DietPhoto(8, path, 0)),
    dietCategoryId = 9,
)
