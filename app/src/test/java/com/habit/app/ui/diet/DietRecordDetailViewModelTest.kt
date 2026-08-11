package com.habit.app.ui.diet

import com.habit.app.domain.model.DietCategory
import com.habit.app.domain.model.DietCategoryScope
import com.habit.app.domain.model.AiCalorieEstimate
import com.habit.app.domain.model.AiCalorieItemEstimate
import com.habit.app.domain.model.CalorieSource
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealRecordDraft
import com.habit.app.domain.model.MealType
import com.habit.app.domain.repository.DietCategoryRepository
import com.habit.app.domain.repository.DietRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DietRecordDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun missingRecordExposesNotFoundState() = runTest(dispatcher) {
        val viewModel = DietRecordDetailViewModel(
            recordId = 42,
            repository = EmptyDetailDietRepository,
            categoryRepository = EmptyDetailCategoryRepository,
        )

        advanceUntilIdle()

        assertTrue(viewModel.state.value.notFound)
        assertTrue(!viewModel.state.value.loading)
    }

    @Test
    fun mealAndBeverageEvidenceAreExposedForDetail() = runTest(dispatcher) {
        val meal = detailRecord(DietRecordType.MEAL)
        val mealViewModel = DietRecordDetailViewModel(
            recordId = meal.id,
            repository = DetailDietRepository(meal),
            categoryRepository = DetailCategoryRepository,
        )
        advanceUntilIdle()

        assertNotNull(mealViewModel.state.value.aiEvidence)
        assertEquals(680, mealViewModel.state.value.aiEvidence?.adoptedKcal)
        assertEquals("家常菜", mealViewModel.state.value.categoryName)

        val beverage = detailRecord(DietRecordType.BEVERAGE)
        val beverageViewModel = DietRecordDetailViewModel(
            recordId = beverage.id,
            repository = DetailDietRepository(beverage),
            categoryRepository = DetailCategoryRepository,
        )
        advanceUntilIdle()

        assertNotNull(beverageViewModel.state.value.aiEvidence)
        assertEquals(680, beverageViewModel.state.value.aiEvidence?.adoptedKcal)
    }

    @Test
    fun deleteRemovesCurrentRecordAndReturnsToPreviousPage() = runTest(dispatcher) {
        val record = detailRecord(DietRecordType.MEAL)
        val repository = DetailDietRepository(record)
        val viewModel = DietRecordDetailViewModel(record.id, repository, DetailCategoryRepository)
        var returned = false

        viewModel.delete { returned = true }
        advanceUntilIdle()

        assertEquals(record.id, repository.deletedId)
        assertTrue(returned)
    }

    private fun detailRecord(type: DietRecordType) = MealRecord(
        id = if (type == DietRecordType.MEAL) 7 else 8,
        recordType = type,
        mealType = if (type == DietRecordType.MEAL) MealType.LUNCH else null,
        occurredAt = 1,
        recordEpochDay = 1,
        description = "牛肉饭",
        foodItems = emptyList(),
        calculatedCalories = 680,
        finalCalories = 680,
        calorieSource = CalorieSource.AI_ESTIMATE,
        beverage = null,
        note = "",
        createdAt = 1,
        updatedAt = 1,
        dietCategoryId = if (type == DietRecordType.MEAL) 4 else 5,
        aiCalorieEstimate = AiCalorieEstimate(
            mealRecordId = 7,
            generatedAt = 1,
            modelNameSnapshot = "Private Vision Name",
            modelIdSnapshot = "vision-1",
            items = listOf(AiCalorieItemEstimate("牛肉饭", "1 份", 600, 760)),
            totalMinKcal = 600,
            totalMaxKcal = 760,
            suggestedKcal = 680,
            adoptedKcal = 680,
            wasModified = false,
            accuracyNote = "仅用于估算",
        ),
    )
}

private class DetailDietRepository(private val record: MealRecord) : DietRepository {
    var deletedId: Long? = null
    override fun observeAll(): Flow<List<MealRecord>> = MutableStateFlow(listOf(record))
    override fun observeDay(epochDay: Long): Flow<List<MealRecord>> = MutableStateFlow(listOf(record))
    override fun observeRecord(id: Long): Flow<MealRecord?> = MutableStateFlow(record.takeIf { it.id == id })
    override fun observeRange(startEpochDay: Long, endEpochDay: Long): Flow<List<MealRecord>> = MutableStateFlow(listOf(record))
    override suspend fun save(id: Long?, draft: MealRecordDraft): Long = error("unused")
    override suspend fun delete(id: Long) { deletedId = id }
}

private object DetailCategoryRepository : DietCategoryRepository {
    private val categories = listOf(
        DietCategory(4, DietCategoryScope.MEAL, "家常菜", true, false, 0, 1, 1),
        DietCategory(5, DietCategoryScope.BEVERAGE, "咖啡", true, false, 0, 1, 1),
    )
    override fun observeAll(scope: DietCategoryScope): Flow<List<DietCategory>> =
        MutableStateFlow(categories.filter { it.scope == scope })
    override fun observeVisible(scope: DietCategoryScope): Flow<List<DietCategory>> = observeAll(scope)
    override fun observeUsageCounts(scope: DietCategoryScope): Flow<Map<Long, Int>> = MutableStateFlow(emptyMap())
    override suspend fun create(scope: DietCategoryScope, name: String): Long = error("unused")
    override suspend fun rename(id: Long, name: String) = Unit
    override suspend fun setHidden(id: Long, hidden: Boolean) = Unit
    override suspend fun migrateAndDelete(sourceId: Long, targetId: Long) = Unit
}

private object EmptyDetailDietRepository : DietRepository {
    override fun observeAll(): Flow<List<MealRecord>> = MutableStateFlow(emptyList())
    override fun observeDay(epochDay: Long): Flow<List<MealRecord>> = MutableStateFlow(emptyList())
    override fun observeRecord(id: Long): Flow<MealRecord?> = MutableStateFlow(null)
    override fun observeRange(startEpochDay: Long, endEpochDay: Long): Flow<List<MealRecord>> = MutableStateFlow(emptyList())
    override suspend fun save(id: Long?, draft: MealRecordDraft): Long = error("unused")
    override suspend fun delete(id: Long) = Unit
}

private object EmptyDetailCategoryRepository : DietCategoryRepository {
    override fun observeAll(scope: DietCategoryScope): Flow<List<DietCategory>> = MutableStateFlow(emptyList())
    override fun observeVisible(scope: DietCategoryScope): Flow<List<DietCategory>> = MutableStateFlow(emptyList())
    override fun observeUsageCounts(scope: DietCategoryScope): Flow<Map<Long, Int>> = MutableStateFlow(emptyMap())
    override suspend fun create(scope: DietCategoryScope, name: String): Long = error("unused")
    override suspend fun rename(id: Long, name: String) = Unit
    override suspend fun setHidden(id: Long, hidden: Boolean) = Unit
    override suspend fun migrateAndDelete(sourceId: Long, targetId: Long) = Unit
}
