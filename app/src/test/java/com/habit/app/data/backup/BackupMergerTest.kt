package com.habit.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergerTest {
    @Test
    fun newerImportedHabitWinsWithoutChangingItsLocalId() {
        val current = backup(habit(id = 7, name = "旧名称", createdAt = 10, updatedAt = 20))
        val imported = backup(habit(id = 7, name = "新名称", createdAt = 10, updatedAt = 30))

        val result = BackupMerger.merge(current, imported)

        assertEquals(listOf("新名称"), result.habits.map(BackupHabit::name))
        assertEquals(7, result.habits.single().id)
    }

    @Test
    fun olderImportedHabitDoesNotOverwriteCurrentData() {
        val current = backup(habit(id = 7, name = "当前", createdAt = 10, updatedAt = 30))
        val imported = backup(habit(id = 7, name = "较旧", createdAt = 10, updatedAt = 20))

        assertEquals("当前", BackupMerger.merge(current, imported).habits.single().name)
    }

    @Test
    fun collidingIdWithDifferentCreatedAtKeepsBothAndRemapsCheckIn() {
        val current = backup(habit(id = 7, name = "当前", createdAt = 10, updatedAt = 20))
        val imported = backup(
            habit = habit(id = 7, name = "导入", createdAt = 99, updatedAt = 30),
            checkIns = listOf(BackupCheckIn(3, 7, 20_302, 31, 31)),
        )

        val result = BackupMerger.merge(current, imported)

        assertEquals(2, result.habits.size)
        val importedHabit = result.habits.single { it.name == "导入" }
        assertTrue(importedHabit.id != 7L)
        assertEquals(importedHabit.id, result.checkIns.single().habitId)
    }

    @Test
    fun importedTemplateAndItsPhotoAreKeptDuringMerge() {
        val current = backup(habit(7, "当前", 10, 20))
        val template = BackupDietTemplate(
            5, "咖啡", "BEVERAGE", null, "", 120, "COFFEE", "店", "拿铁",
            "中杯", "热", "", "无糖", 1, "", 0, 30, 30,
        )
        val imported = backup(habit(8, "导入", 11, 21)).copy(
            dietTemplates = listOf(template),
            dietPhotos = listOf(BackupDietPhoto(9, null, 5, "library/coffee.jpg", 0, 30)),
        )

        val result = BackupMerger.merge(current, imported)

        assertEquals("咖啡", result.dietTemplates.single().name)
        assertEquals(result.dietTemplates.single().id, result.dietPhotos.single().templateId)
    }

    private fun backup(
        habit: BackupHabit,
        checkIns: List<BackupCheckIn> = emptyList(),
    ) = HabitBackup(
        appVersion = "0.2.1",
        exportedAt = 100,
        preferencesUpdatedAt = 100,
        categories = listOf(BackupCategory(1, "学习", true, false, 0, 1, 1)),
        habits = listOf(habit),
        checkIns = checkIns,
        preferences = BackupPreferences("sky_blue", emptyList()),
    )

    private fun habit(
        id: Long,
        name: String,
        createdAt: Long,
        updatedAt: Long,
    ) = BackupHabit(id, name, "emoji:📚", 0xFF8DB9CC, 1, 20_300, null, 0, createdAt, updatedAt)
}
