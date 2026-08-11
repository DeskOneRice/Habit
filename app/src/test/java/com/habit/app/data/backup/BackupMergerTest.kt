package com.habit.app.data.backup

import com.habit.app.data.ai.AiSecretStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergerTest {
    @Test
    fun newerImportedHabitWinsWithoutChangingItsLocalId() {
        val current = backup(habit(id = 7, name = "旧名称", createdAt = 10, updatedAt = 20))
        val imported = backup(habit(id = 7, name = "新名称", createdAt = 10, updatedAt = 30))

        val result = BackupMerger.merge(current, imported).backup

        assertEquals(listOf("新名称"), result.habits.map(BackupHabit::name))
        assertEquals(7, result.habits.single().id)
    }

    @Test
    fun olderImportedHabitDoesNotOverwriteCurrentData() {
        val current = backup(habit(id = 7, name = "当前", createdAt = 10, updatedAt = 30))
        val imported = backup(habit(id = 7, name = "较旧", createdAt = 10, updatedAt = 20))

        assertEquals("当前", BackupMerger.merge(current, imported).backup.habits.single().name)
    }

    @Test
    fun collidingIdWithDifferentCreatedAtKeepsBothAndRemapsCheckIn() {
        val current = backup(habit(id = 7, name = "当前", createdAt = 10, updatedAt = 20))
        val imported = backup(
            habit = habit(id = 7, name = "导入", createdAt = 99, updatedAt = 30),
            checkIns = listOf(BackupCheckIn(3, 7, 20_302, 31, 31)),
        )

        val result = BackupMerger.merge(current, imported).backup

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

        val result = BackupMerger.merge(current, imported).backup

        assertEquals("咖啡", result.dietTemplates.single().name)
        assertEquals(result.dietTemplates.single().id, result.dietPhotos.single().templateId)
    }

    @Test
    fun newerImportedDietCategoryNameWins() {
        val current = backup(habit(7, "当前", 10, 20)).copy(
            dietCategories = listOf(
                BackupDietCategory(5, "BEVERAGE", "咖啡", true, false, 0, 1, 10),
            ),
        )
        val imported = backup(habit(8, "导入", 11, 21)).copy(
            dietCategories = listOf(
                BackupDietCategory(5, "BEVERAGE", "手冲咖啡", true, false, 0, 1, 20),
            ),
        )

        val result = BackupMerger.merge(current, imported).backup

        assertEquals("手冲咖啡", result.dietCategories.single { it.id == 5L }.name)
    }

    @Test
    fun sameExternalModelUsesCurrentIdAndNewerImportedMetadata() {
        val current = backup(habit(7, "当前", 10, 20)).copy(
            aiModelConfigs = listOf(aiModel(7, "shared", "旧配置", 20)),
        )
        val imported = backup(habit(8, "导入", 11, 21)).copy(
            aiModelConfigs = listOf(aiModel(99, "shared", "新配置", 30)),
        )

        val result = BackupMerger.merge(current, imported)
        val model = result.backup.aiModelConfigs.single()

        assertEquals(7L, model.id)
        assertEquals("新配置", model.name)
        assertEquals("NEEDS_KEY", model.lastTestStatus)
        assertEquals("", model.lastTestMessage)
        assertEquals(setOf("shared"), result.secretIdsToClear)
    }

    @Test
    fun collidingLongModelIdsWithDifferentExternalIdsAreRemapped() {
        val current = backup(habit(7, "当前", 10, 20)).copy(
            aiModelConfigs = listOf(aiModel(7, "current", "当前模型", 20)),
        )
        val imported = backup(habit(8, "导入", 11, 21)).copy(
            aiModelConfigs = listOf(aiModel(7, "incoming", "导入模型", 30)),
            aiFeatureBindings = listOf(BackupAiFeatureBinding("WEEKLY_REPORT", 7, 30)),
        )

        val result = BackupMerger.merge(current, imported)
        val importedModel = result.backup.aiModelConfigs.single { it.externalId == "incoming" }

        assertTrue(importedModel.id != 7L)
        assertEquals(importedModel.id, result.backup.aiFeatureBindings.single().modelConfigId)
        assertEquals(setOf("incoming"), result.secretIdsToClear)
    }

    @Test
    fun weeklyReportConflictUsesNewerReportForSameWeek() {
        val current = backup(habit(7, "当前", 10, 20)).copy(
            aiWeeklyReports = listOf(aiReport(5, 20_300, "旧周报", 20)),
        )
        val imported = backup(habit(8, "导入", 11, 21)).copy(
            aiWeeklyReports = listOf(aiReport(99, 20_300, "新周报", 30)),
        )

        val result = BackupMerger.merge(current, imported).backup.aiWeeklyReports.single()

        assertEquals(5L, result.id)
        assertEquals("新周报", result.title)
    }

    @Test
    fun importedEstimateFollowsRemappedMealId() {
        val currentMeal = meal(id = 7, description = "当前餐", createdAt = 10, updatedAt = 20)
        val importedMeal = meal(id = 7, description = "导入餐", createdAt = 99, updatedAt = 30)
        val current = backup(habit(7, "当前", 10, 20)).copy(mealRecords = listOf(currentMeal))
        val imported = backup(habit(8, "导入", 11, 21)).copy(
            mealRecords = listOf(importedMeal),
            aiCalorieEstimates = listOf(aiEstimate(7, generatedAt = 40)),
        )

        val result = BackupMerger.merge(current, imported).backup
        val importedTarget = result.mealRecords.single { it.description == "导入餐" }

        assertTrue(importedTarget.id != 7L)
        assertEquals(importedTarget.id, result.aiCalorieEstimates.single().mealRecordId)
    }

    @Test
    fun newerEstimateFromOlderImportedMealCannotReplaceWinningCurrentEvidence() {
        val currentMeal = meal(id = 7, description = "当前餐", createdAt = 10, updatedAt = 30)
        val importedMeal = meal(id = 7, description = "较旧导入餐", createdAt = 10, updatedAt = 20)
        val current = backup(habit(7, "当前", 10, 20)).copy(
            mealRecords = listOf(currentMeal),
            aiCalorieEstimates = listOf(aiEstimate(7, generatedAt = 40)),
        )
        val imported = backup(habit(8, "导入", 11, 21)).copy(
            mealRecords = listOf(importedMeal),
            aiCalorieEstimates = listOf(aiEstimate(7, generatedAt = 99)),
        )

        val result = BackupMerger.merge(current, imported).backup

        assertEquals("当前餐", result.mealRecords.single().description)
        assertEquals(40L, result.aiCalorieEstimates.single().generatedAt)
    }

    @Test
    fun newerImportedMealWithoutEvidenceClearsCurrentEvidence() {
        val currentMeal = meal(id = 7, description = "当前餐", createdAt = 10, updatedAt = 20)
        val importedMeal = meal(id = 7, description = "新版导入餐", createdAt = 10, updatedAt = 30)
        val current = backup(habit(7, "当前", 10, 20)).copy(
            mealRecords = listOf(currentMeal),
            aiCalorieEstimates = listOf(aiEstimate(7, generatedAt = 40)),
        )
        val imported = backup(habit(8, "导入", 11, 21)).copy(
            mealRecords = listOf(importedMeal),
        )

        val result = BackupMerger.merge(current, imported).backup

        assertEquals("新版导入餐", result.mealRecords.single().description)
        assertTrue(result.aiCalorieEstimates.isEmpty())
    }

    @Test
    fun equalMealVersionsDeterministicallyKeepCurrentEvidence() {
        val sameMeal = meal(id = 7, description = "同一餐", createdAt = 10, updatedAt = 30)
        val current = backup(habit(7, "当前", 10, 20)).copy(
            mealRecords = listOf(sameMeal),
            aiCalorieEstimates = listOf(aiEstimate(7, generatedAt = 40)),
        )
        val imported = backup(habit(8, "导入", 11, 21)).copy(
            mealRecords = listOf(sameMeal),
            aiCalorieEstimates = listOf(aiEstimate(7, generatedAt = 99)),
        )

        val result = BackupMerger.merge(current, imported).backup

        assertEquals(40L, result.aiCalorieEstimates.single().generatedAt)
    }

    @Test
    fun invalidImportedBindingIsNulledAndAllTouchedSecretsAreReturned() {
        val current = backup(habit(7, "当前", 10, 20)).copy(
            aiModelConfigs = listOf(aiModel(7, "shared", "当前模型", 40)),
        )
        val imported = backup(habit(8, "导入", 11, 21)).copy(
            aiModelConfigs = listOf(
                aiModel(71, "shared", "较旧模型", 30),
                aiModel(72, "new-model", "新模型", 30),
            ),
            aiFeatureBindings = listOf(BackupAiFeatureBinding("MEAL_CALORIE_ESTIMATE", 999, 50)),
        )

        val result = BackupMerger.merge(current, imported)

        assertNull(result.backup.aiFeatureBindings.single().modelConfigId)
        assertEquals(setOf("shared", "new-model"), result.secretIdsToClear)
        assertEquals("当前模型", result.backup.aiModelConfigs.single { it.externalId == "shared" }.name)
        assertEquals("NEEDS_KEY", result.backup.aiModelConfigs.single { it.externalId == "shared" }.lastTestStatus)
    }

    @Test
    fun failedRoomTransactionDoesNotClearAnySecret() = runTest {
        val store = RecordingSecretStore()

        val failure = runCatching {
            executeSecretSafeImport(ImportMode.MERGE, setOf("one"), store) {
                throw IllegalStateException("Room import failed")
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(store.removed.isEmpty())
        assertEquals(0, store.clearAllCalls)
    }

    @Test
    fun successfulMergeClearsOnlyTouchedSecretsAfterRoomCommit() = runTest {
        val events = mutableListOf<String>()
        val store = RecordingSecretStore(events)

        executeSecretSafeImport(ImportMode.MERGE, setOf("two", "one"), store) {
            events += "room-commit"
        }

        assertEquals(listOf("room-commit", "remove:one", "remove:two"), events)
    }

    @Test
    fun successfulReplaceClearsAllSecretsAfterRoomCommit() = runTest {
        val events = mutableListOf<String>()
        val store = RecordingSecretStore(events)

        executeSecretSafeImport(ImportMode.REPLACE, setOf("ignored"), store) {
            events += "room-commit"
        }

        assertEquals(listOf("room-commit", "clear-all"), events)
    }

    @Test
    fun cancellationAfterMergeCommitCannotInterruptTouchedSecretCleanup() = runTest {
        val cleanupStarted = CompletableDeferred<Unit>()
        val allowCleanup = CompletableDeferred<Unit>()
        val store = GatedSecretStore(cleanupStarted, allowCleanup)
        val job = launch {
            executeSecretSafeImport(ImportMode.MERGE, setOf("two", "one"), store) { "committed" }
        }

        cleanupStarted.await()
        job.cancel()
        allowCleanup.complete(Unit)
        job.join()

        assertEquals(listOf("one", "two"), store.removed)
    }

    @Test
    fun cancellationAfterReplaceCommitCannotInterruptClearAll() = runTest {
        val cleanupStarted = CompletableDeferred<Unit>()
        val allowCleanup = CompletableDeferred<Unit>()
        val store = GatedSecretStore(cleanupStarted, allowCleanup)
        val job = launch {
            executeSecretSafeImport(ImportMode.REPLACE, emptySet(), store) { "committed" }
        }

        cleanupStarted.await()
        job.cancel()
        allowCleanup.complete(Unit)
        job.join()

        assertEquals(1, store.clearAllCalls)
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

    private fun aiModel(id: Long, externalId: String, name: String, updatedAt: Long) = BackupAiModelConfig(
        id = id,
        externalId = externalId,
        name = name,
        baseUrl = "https://api.example.com/v1",
        modelId = "model-$id",
        supportsText = true,
        supportsVision = true,
        allowInsecureHttp = false,
        enabled = true,
        lastTestedAt = updatedAt,
        lastTestStatus = "PASSED",
        lastTestMessage = "ok",
        createdAt = 1,
        updatedAt = updatedAt,
    )

    private fun aiReport(id: Long, startEpochDay: Long, title: String, updatedAt: Long) = BackupAiWeeklyReport(
        id, startEpochDay, startEpochDay + 6, updatedAt, "模型", "model", title, "概览", "习惯", "饮食",
        "关联", "[]", "[]", "{}", 1, updatedAt,
    )

    private fun meal(id: Long, description: String, createdAt: Long, updatedAt: Long) = BackupMealRecord(
        id, "MEAL", "LUNCH", updatedAt, 20_300, description, null, 500, "MANUAL", "", createdAt, updatedAt,
    )

    private fun aiEstimate(mealRecordId: Long, generatedAt: Long) = BackupAiCalorieEstimate(
        mealRecordId, generatedAt, "模型", "model", "[]", 400, 600, 500, 500, false, "仅供参考",
    )
}

private class RecordingSecretStore(
    private val events: MutableList<String> = mutableListOf(),
) : AiSecretStore {
    val removed = mutableListOf<String>()
    var clearAllCalls = 0

    override suspend fun put(externalId: String, apiKey: String) = Unit
    override suspend fun get(externalId: String): String? = null
    override suspend fun maskedSuffix(externalId: String): String? = null
    override suspend fun remove(externalId: String) {
        removed += externalId
        events += "remove:$externalId"
    }
    override suspend fun clearAll() {
        clearAllCalls += 1
        events += "clear-all"
    }
}

private class GatedSecretStore(
    private val cleanupStarted: CompletableDeferred<Unit>,
    private val allowCleanup: CompletableDeferred<Unit>,
) : AiSecretStore {
    val removed = mutableListOf<String>()
    var clearAllCalls = 0

    override suspend fun put(externalId: String, apiKey: String) = Unit
    override suspend fun get(externalId: String): String? = null
    override suspend fun maskedSuffix(externalId: String): String? = null
    override suspend fun remove(externalId: String) {
        cleanupStarted.complete(Unit)
        allowCleanup.await()
        removed += externalId
    }
    override suspend fun clearAll() {
        cleanupStarted.complete(Unit)
        allowCleanup.await()
        clearAllCalls += 1
    }
}
