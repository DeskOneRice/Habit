package com.habit.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.data.local.CategoryEntity
import com.habit.app.data.local.HabitDatabase
import com.habit.app.data.local.HabitEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCategoryRepositoryTest {
    private var database: HabitDatabase? = null

    @After
    fun close() {
        database?.close()
    }

    @Test
    fun migrationMovesHabitsThenDeletesCustomSourceAtomically() = runTest {
        val db = database()
        val repository = RoomCategoryRepository(db, Clock.fixed(Instant.ofEpochMilli(500), ZoneOffset.UTC))
        val source = repository.create("Source")
        val target = repository.create("Target")
        val habitId = db.habitDao().insert(
            HabitEntity(
                name = "Vocabulary",
                iconKey = "book",
                themeColor = 0xFF8DB9CC,
                categoryId = source,
                startEpochDay = 1,
                archivedEpochDay = null,
                sortOrder = 0,
                createdAt = 100,
                updatedAt = 100,
            ),
        )

        repository.migrateAndDelete(source, target)

        assertEquals(target, db.habitDao().getById(habitId)!!.categoryId)
        assertEquals(500, db.habitDao().getById(habitId)!!.updatedAt)
        assertFalse(repository.observeAll().first().any { it.id == source })
    }

    @Test
    fun presetCategoryCanBeRenamedAndDeletedWithMigration() = runTest {
        val db = database()
        val presetId = db.categoryDao().insert(
            CategoryEntity(
                name = "Study",
                isPreset = true,
                isHidden = false,
                sortOrder = 0,
                createdAt = 100,
                updatedAt = 100,
            ),
        )
        val repository = RoomCategoryRepository(db, Clock.fixed(Instant.ofEpochMilli(500), ZoneOffset.UTC))
        val targetId = repository.create("Target")
        val habitId = db.habitDao().insert(
            HabitEntity(
                name = "Preset habit", iconKey = "book", themeColor = 0xFF8DB9CC,
                categoryId = presetId, startEpochDay = 1, archivedEpochDay = null,
                sortOrder = 0, createdAt = 100, updatedAt = 100,
            ),
        )

        repository.rename(presetId, "Reading")
        assertEquals("Reading", repository.observeAll().first().single { it.id == presetId }.name)
        repository.migrateAndDelete(presetId, targetId)

        assertFalse(repository.observeAll().first().any { it.id == presetId })
        assertEquals(targetId, db.habitDao().getById(habitId)!!.categoryId)
    }

    @Test
    fun lastVisibleCategoryCannotBeHidden() = runTest {
        val db = database()
        val onlyId = db.categoryDao().insert(
            CategoryEntity(
                name = "Only", isPreset = true, isHidden = false,
                sortOrder = 0, createdAt = 100, updatedAt = 100,
            ),
        )
        val repository = RoomCategoryRepository(db, Clock.fixed(Instant.ofEpochMilli(500), ZoneOffset.UTC))

        val error = runCatching { repository.setHidden(onlyId, true) }.exceptionOrNull()

        assertEquals("至少保留一个可见分类", error?.message)
        assertTrue(repository.observeVisible().first().any { it.id == onlyId })
    }

    @Test
    fun sameIdMigrationRejectsBeforeDeletingAnEmptyCustomCategory() = runTest {
        val db = database()
        val repository = RoomCategoryRepository(db, Clock.fixed(Instant.ofEpochMilli(500), ZoneOffset.UTC))
        val source = repository.create("Empty source")

        val error = runCatching { repository.migrateAndDelete(source, source) }.exceptionOrNull()

        assertEquals("请选择不同的目标分类", error?.message)
        assertTrue(repository.observeAll().first().any { it.id == source })
    }

    @Test
    fun sameIdMigrationRejectsWithoutMovingAnExistingHabit() = runTest {
        val db = database()
        val repository = RoomCategoryRepository(db, Clock.fixed(Instant.ofEpochMilli(500), ZoneOffset.UTC))
        val source = repository.create("Source")
        val habitId = db.habitDao().insert(
            HabitEntity(
                name = "Vocabulary",
                iconKey = "book",
                themeColor = 0xFF8DB9CC,
                categoryId = source,
                startEpochDay = 1,
                archivedEpochDay = null,
                sortOrder = 0,
                createdAt = 100,
                updatedAt = 100,
            ),
        )

        val error = runCatching { repository.migrateAndDelete(source, source) }.exceptionOrNull()

        assertEquals("请选择不同的目标分类", error?.message)
        assertEquals(source, db.habitDao().getById(habitId)!!.categoryId)
        assertTrue(repository.observeAll().first().any { it.id == source })
    }

    private fun database(): HabitDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        HabitDatabase::class.java,
    ).allowMainThreadQueries().build().also { database = it }
}
