package com.habit.app.ui.diet

import com.habit.app.domain.model.DietCategory
import com.habit.app.domain.model.DietCategoryScope
import com.habit.app.domain.model.MealRecord
import com.habit.app.domain.model.MealRecordDraft
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
