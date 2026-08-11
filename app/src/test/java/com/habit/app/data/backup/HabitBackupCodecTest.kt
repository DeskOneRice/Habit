package com.habit.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitBackupCodecTest {
    @Test
    fun roundTripPreservesDatabaseTimesAndEpochDays() {
        val source = sampleBackup()

        val decoded = HabitBackupCodec.decode(HabitBackupCodec.encode(source))

        assertEquals(source, decoded)
    }

    @Test
    fun newerSchemaIsRejected() {
        val json = """{"format":"habit-backup","schemaVersion":99}"""

        assertThrows(UnsupportedBackupVersionException::class.java) {
            HabitBackupCodec.decode(json)
        }
    }

    @Test
    fun danglingHabitCategoryIsRejected() {
        val invalid = sampleBackup().copy(
            habits = listOf(sampleBackup().habits.single().copy(categoryId = 999)),
        )

        assertThrows(InvalidBackupException::class.java) {
            HabitBackupCodec.validate(invalid)
        }
    }

    @Test
    fun schemaOneBackupDecodesWithEmptyDietData() {
        val legacy = HabitBackupCodec.encode(sampleBackup()).replace(Regex("\"schemaVersion\":\\d+"), "\"schemaVersion\":1")
            .replace(Regex(",\"mealRecords\":\\[.*?],\"foodItems\":\\[.*?],\"beverageDetails\":\\[.*?],\"beverageToppings\":\\[.*?]"), "")

        val decoded = HabitBackupCodec.decode(legacy)

        assertEquals(1, decoded.schemaVersion)
        assertTrue(decoded.mealRecords.isEmpty())
    }

    @Test
    fun schemaTwoRoundTripPreservesDrinkDetails() {
        val source = sampleBackup().copy(
            schemaVersion = 2,
            mealRecords = listOf(
                BackupMealRecord(1, "BEVERAGE", null, 200, 20, "", null, 260, "MANUAL", "", 100, 100),
            ),
            beverageDetails = listOf(
                BackupBeverageDetail(1, "MILK_TEA", "茶铺", "奶茶", "中杯", "冷", "少冰", "三分糖", 1),
            ),
            beverageToppings = listOf(BackupBeverageTopping(1, 1, "珍珠", 0, 100, 100)),
        )

        val decoded = HabitBackupCodec.decode(HabitBackupCodec.encode(source))

        assertEquals("少冰", decoded.beverageDetails.single().iceLevel)
        assertEquals("珍珠", decoded.beverageToppings.single().name)
    }

    @Test
    fun schemaThreeCoffeeRecordNormalizesToCoffeeCategory() {
        val legacy = sampleBackup().copy(
            schemaVersion = 3,
            mealRecords = listOf(
                BackupMealRecord(
                    id = 1,
                    recordType = "BEVERAGE",
                    mealType = null,
                    occurredAt = 200,
                    recordEpochDay = 20,
                    description = "",
                    calculatedCalories = null,
                    finalCalories = 20,
                    calorieSource = "MANUAL",
                    note = "",
                    createdAt = 100,
                    updatedAt = 100,
                ),
            ),
            beverageDetails = listOf(
                BackupBeverageDetail(1, "COFFEE", "", "美式", "大杯", "冰", "", "", 1),
            ),
        )

        val normalized = HabitBackupCodec.decode(HabitBackupCodec.encode(legacy)).normalizeDietCategories()

        assertEquals(5L, normalized.mealRecords.single().dietCategoryId)
        assertEquals("咖啡", normalized.dietCategories.single { it.id == 5L }.name)
    }

    @Test
    fun schemaFourRoundTripPreservesCustomDietCategory() {
        val source = sampleBackup().copy(
            schemaVersion = 4,
            dietCategories = listOf(
                BackupDietCategory(31, "BEVERAGE", "手冲咖啡", false, false, 30, 300, 400),
            ),
            mealRecords = listOf(
                BackupMealRecord(
                    id = 1,
                    recordType = "BEVERAGE",
                    mealType = null,
                    occurredAt = 200,
                    recordEpochDay = 20,
                    description = "",
                    calculatedCalories = null,
                    finalCalories = 20,
                    calorieSource = "MANUAL",
                    note = "",
                    createdAt = 100,
                    updatedAt = 100,
                    dietCategoryId = 31,
                ),
            ),
        )

        val decoded = HabitBackupCodec.decode(HabitBackupCodec.encode(source))

        assertEquals(source, decoded)
    }

    @Test
    fun schemaFourBackupDecodesWithEmptyAiCollections() {
        val legacy = HabitBackupCodec.encode(sampleBackup())
            .replace(Regex("\"schemaVersion\":\\d+"), "\"schemaVersion\":4")
            .replace(Regex(",\"aiModelConfigs\":\\[.*?]"), "")
            .replace(Regex(",\"aiFeatureBindings\":\\[.*?]"), "")
            .replace(Regex(",\"aiWeeklyReports\":\\[.*?]"), "")
            .replace(Regex(",\"aiCalorieEstimates\":\\[.*?]"), "")

        val decoded = HabitBackupCodec.decode(legacy)

        assertEquals(4, decoded.schemaVersion)
        assertTrue(decoded.aiModelConfigs.isEmpty())
        assertTrue(decoded.aiFeatureBindings.isEmpty())
        assertTrue(decoded.aiWeeklyReports.isEmpty())
        assertTrue(decoded.aiCalorieEstimates.isEmpty())
    }

    @Test
    fun schemaFiveRoundTripPreservesAiMetadata() {
        val source = sampleBackup().copy(
            aiModelConfigs = listOf(sampleAiModel()),
            aiFeatureBindings = listOf(BackupAiFeatureBinding("WEEKLY_REPORT", 41, 420)),
            aiWeeklyReports = listOf(sampleAiReport()),
            aiCalorieEstimates = listOf(sampleAiEstimate()),
            mealRecords = listOf(
                BackupMealRecord(8, "MEAL", "LUNCH", 200, 20, "午餐", null, 520, "AI_ESTIMATE", "", 100, 120),
            ),
        )

        val decoded = HabitBackupCodec.decode(HabitBackupCodec.encode(source))

        assertEquals(source, decoded)
    }

    @Test
    fun schemaFiveEncodingNeverContainsSecretMaterial() {
        val sentinelKey = "sk-HABIT-SENTINEL-NEVER-EXPORT"
        val encoded = HabitBackupCodec.encode(
            sampleBackup().copy(
                aiModelConfigs = listOf(sampleAiModel()),
                aiFeatureBindings = listOf(BackupAiFeatureBinding("WEEKLY_REPORT", 41, 420)),
            ),
        )
        val lowercase = encoded.lowercase()

        assertFalse(encoded.contains(sentinelKey))
        assertFalse(lowercase.contains("apikey"))
        assertFalse(lowercase.contains("authorization"))
        assertFalse(lowercase.contains("ciphertext"))
    }

    private fun sampleAiModel() = BackupAiModelConfig(
        id = 41,
        externalId = "model-external-41",
        name = "学习助手",
        baseUrl = "https://api.example.com/v1",
        modelId = "vision-model",
        supportsText = true,
        supportsVision = true,
        allowInsecureHttp = false,
        enabled = true,
        lastTestedAt = 410,
        lastTestStatus = "PASSED",
        lastTestMessage = "连接正常",
        createdAt = 400,
        updatedAt = 420,
    )

    private fun sampleAiReport() = BackupAiWeeklyReport(
        id = 51,
        startEpochDay = 20_300,
        endEpochDay = 20_306,
        generatedAt = 510,
        modelNameSnapshot = "学习助手",
        modelIdSnapshot = "vision-model",
        title = "本周状态",
        overview = "保持稳定",
        habitAnalysis = "完成率提升",
        dietAnalysis = "饮食规律",
        correlationFinding = "早睡后更稳定",
        suggestionsJson = "[\"继续保持\"]",
        cautionsJson = "[]",
        coverageJson = "{\"scheduledHabitCount\":7}",
        createdAt = 500,
        updatedAt = 520,
    )

    private fun sampleAiEstimate() = BackupAiCalorieEstimate(
        mealRecordId = 8,
        generatedAt = 610,
        modelNameSnapshot = "学习助手",
        modelIdSnapshot = "vision-model",
        itemsJson = "[{\"name\":\"米饭\",\"minKcal\":180,\"maxKcal\":230}]",
        totalMinKcal = 450,
        totalMaxKcal = 590,
        suggestedKcal = 520,
        adoptedKcal = 520,
        wasModified = false,
        accuracyNote = "图片估算，仅供参考",
    )

    private fun sampleBackup() = HabitBackup(
        appVersion = "0.2.1",
        exportedAt = 1_785_686_400_000,
        preferencesUpdatedAt = 1_785_686_300_000,
        categories = listOf(
            BackupCategory(1, "学习", true, false, 0, 100, 101),
        ),
        habits = listOf(
            BackupHabit(4, "背单词", "emoji:📚", 0xFF8DB9CC, 1, 20_300, null, 0, 110, 120),
        ),
        checkIns = listOf(
            BackupCheckIn(9, 4, 20_301, 130, 140),
        ),
        preferences = BackupPreferences("sky_blue", listOf("emoji:📚")),
    )
}
