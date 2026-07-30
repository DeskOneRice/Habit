package com.habit.app.ui.categories

import com.habit.app.domain.model.Category
import com.habit.app.domain.model.Habit
import com.habit.app.domain.model.HabitDraft
import com.habit.app.domain.repository.CategoryRepository
import com.habit.app.domain.repository.HabitRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun createDisablesFurtherSavesUntilRepositoryCompletes() = runTest(dispatcher) {
        val repository = BlockingCategoryRepository()
        val viewModel = CategoryViewModel(repository, EmptyHabitRepository)
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        var completions = 0

        viewModel.create("阅读") { completions += 1 }
        viewModel.create("重复创建") { completions += 1 }
        advanceUntilIdle()

        assertTrue(viewModel.state.value.saving)
        assertEquals(1, repository.createCalls)

        repository.createGate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.saving)
        assertEquals(1, completions)
        collection.cancel()
    }

    @Test
    fun renameDisablesFurtherSavesUntilRepositoryCompletes() = runTest(dispatcher) {
        val repository = BlockingCategoryRepository()
        val viewModel = CategoryViewModel(repository, EmptyHabitRepository)
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        var completions = 0

        viewModel.rename(7, "阅读") { completions += 1 }
        viewModel.rename(7, "重复重命名") { completions += 1 }
        advanceUntilIdle()

        assertTrue(viewModel.state.value.saving)
        assertEquals(1, repository.renameCalls)

        repository.renameGate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.saving)
        assertEquals(1, completions)
        collection.cancel()
    }
}

private class BlockingCategoryRepository : CategoryRepository {
    val createGate = CompletableDeferred<Unit>()
    val renameGate = CompletableDeferred<Unit>()
    var createCalls = 0
    var renameCalls = 0

    override fun observeVisible() = flowOf(emptyList<Category>())
    override fun observeAll() = flowOf(emptyList<Category>())

    override suspend fun create(name: String): Long {
        createCalls += 1
        createGate.await()
        return 1
    }

    override suspend fun rename(id: Long, name: String) {
        renameCalls += 1
        renameGate.await()
    }

    override suspend fun setPresetHidden(id: Long, hidden: Boolean) = Unit
    override suspend fun migrateAndDelete(sourceId: Long, targetId: Long) = Unit
}

private data object EmptyHabitRepository : HabitRepository {
    override fun observeAll() = flowOf(emptyList<Habit>())
    override fun observeById(id: Long) = flowOf(null)
    override suspend fun create(draft: HabitDraft): Long = error("Not used")
    override suspend fun update(id: Long, draft: HabitDraft) = Unit
    override suspend fun archive(id: Long, archivedEpochDay: Long) = Unit
    override suspend fun delete(id: Long) = Unit
}
