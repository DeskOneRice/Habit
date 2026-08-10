package com.habit.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habit.app.data.local.AiCalorieEstimateEntity
import com.habit.app.data.local.AiWeeklyReportEntity
import com.habit.app.data.local.HabitDatabase
import com.habit.app.domain.model.AiFeature
import com.habit.app.domain.model.AiModelConfigDraft
import com.habit.app.domain.model.AiWeeklyReport
import com.habit.app.domain.model.DietRecordType
import com.habit.app.domain.model.FoodItemDraft
import com.habit.app.domain.model.MealRecordDraft
import com.habit.app.domain.model.MealType
import com.habit.app.domain.model.WeeklyReportCoverage
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomAiRepositoryTest {
    private lateinit var database: HabitDatabase
    private lateinit var models: RoomAiModelRepository
    private lateinit var reports: RoomAiWeeklyReportRepository
    private lateinit var diets: RoomDietRepository
    private lateinit var clock: MutableClock

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HabitDatabase::class.java,
        ).allowMainThreadQueries().build()
        clock = MutableClock(1_000)
        models = RoomAiModelRepository(database, clock)
        reports = RoomAiWeeklyReportRepository(database, clock)
        diets = RoomDietRepository(database, clock)
    }

    @After
    fun close() = database.close()

    @Test
    fun deletingModelRetainsFeatureBindingsWithNullModel() = runTest {
        val modelId = models.saveModel(null, modelDraft("Primary"))
        models.bind(AiFeature.WEEKLY_REPORT, modelId)
        models.bind(AiFeature.MEAL_CALORIE_ESTIMATE, modelId)

        models.deleteModel(modelId)

        assertEquals(2, models.observeBindings().first().size)
        models.observeBindings().first().forEach { assertNull(it.modelConfigId) }
    }

    @Test
    fun savingSameWeekReplacesExistingReport() = runTest {
        reports.save(report(startEpochDay = 10, title = "First"))
        val original = reports.observeWeek(10).first()!!

        clock.currentMillis = 2_000

        reports.save(report(startEpochDay = 10, title = "Replacement"))

        val saved = reports.observeAll().first()
        assertEquals(1, saved.size)
        assertEquals(original.id, saved.single().id)
        assertEquals("Replacement", saved.single().title)
        assertEquals(1_000, saved.single().createdAt)
        assertEquals(2_000, saved.single().updatedAt)
    }

    @Test
    fun failedRoomUpdateRollsBackAndKeepsOldWeeklyReportExactly() = runTest {
        reports.save(report(startEpochDay = 10, title = "First"))
        val original = reports.observeWeek(10).first()!!
        clock.currentMillis = 2_000
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_weekly_report_update BEFORE UPDATE ON ai_weekly_reports BEGIN SELECT RAISE(ABORT, 'forced update failure'); END",
        )

        try {
            reports.save(report(startEpochDay = 10, title = "Replacement"))
            fail("Expected the SQLite trigger to abort the update")
        } catch (_: Exception) {
            // The real SQLite failure is the behavior under test.
        }

        val retained = reports.observeWeek(10).first()!!
        assertEquals(original.id, retained.id)
        assertEquals(original.title, retained.title)
        assertEquals(original.createdAt, retained.createdAt)
        assertEquals(original.updatedAt, retained.updatedAt)
        assertTrue(retained.updatedAt < clock.currentMillis)
    }

    @Test
    fun malformedPersistedValuesAreIsolatedFromRepositoryFlows() = runTest {
        val modelId = models.saveModel(null, modelDraft("Primary"))
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO ai_model_configs (id,externalId,name,baseUrl,modelId,supportsText,supportsVision,allowInsecureHttp,enabled,lastTestedAt,lastTestStatus,lastTestMessage,createdAt,updatedAt) VALUES (2,'other','Future','https://example.test','future',1,0,0,1,NULL,'UNKNOWN_STATUS','',1,1)",
        )
        models.bind(AiFeature.WEEKLY_REPORT, modelId)
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO ai_feature_bindings (feature,modelConfigId,updatedAt) VALUES ('UNKNOWN_FEATURE',NULL,1)",
        )
        database.aiDao().insertReport(
            AiWeeklyReportEntity(
                startEpochDay = 20,
                endEpochDay = 26,
                generatedAt = 1,
                modelNameSnapshot = "Primary",
                modelIdSnapshot = "model",
                title = "Still readable",
                overview = "overview",
                habitAnalysis = "habits",
                dietAnalysis = "diet",
                correlationFinding = "correlation",
                suggestionsJson = "{}",
                cautionsJson = "[1]",
                coverageJson = "[]",
                createdAt = 1,
                updatedAt = 1,
            ),
        )

        val observedModels = models.observeModels().first()
        val observedBindings = models.observeBindings().first()
        val observedReport = reports.observeAll().first().single()

        assertEquals(2, observedModels.size)
        assertEquals("UNTESTED", observedModels.single { it.id == 2L }.lastTestStatus.name)
        assertEquals(listOf(AiFeature.WEEKLY_REPORT), observedBindings.map { it.feature })
        assertEquals("Still readable", observedReport.title)
        assertEquals(emptyList<String>(), observedReport.suggestions)
        assertEquals(emptyList<String>(), observedReport.cautions)
        assertEquals(WeeklyReportCoverage(0, 0, 0, 0, 0, 0), observedReport.coverage)
    }

    @Test
    fun deletingMealCascadesCalorieEstimate() = runTest {
        val mealId = diets.save(null, mealDraft())
        database.aiDao().upsertCalorieEstimate(
            AiCalorieEstimateEntity(
                mealRecordId = mealId,
                generatedAt = 1,
                modelNameSnapshot = "Primary",
                modelIdSnapshot = "vision",
                itemsJson = "[]",
                totalMinKcal = 100,
                totalMaxKcal = 200,
                suggestedKcal = 150,
                adoptedKcal = 150,
                wasModified = false,
                accuracyNote = "",
            ),
        )

        diets.delete(mealId)

        assertNull(database.aiDao().observeCalorieEstimate(mealId).first())
    }

    private fun modelDraft(name: String) = AiModelConfigDraft(
        name = name,
        baseUrl = "https://example.test/v1",
        modelId = "model",
        supportsText = true,
        supportsVision = true,
        allowInsecureHttp = false,
        enabled = true,
    )

    private fun report(startEpochDay: Long, title: String) = AiWeeklyReport(
        startEpochDay = startEpochDay,
        endEpochDay = startEpochDay + 6,
        generatedAt = 10,
        modelNameSnapshot = "Primary",
        modelIdSnapshot = "model",
        title = title,
        overview = "overview",
        habitAnalysis = "habits",
        dietAnalysis = "diet",
        correlationFinding = "correlation",
        suggestions = listOf("suggestion"),
        cautions = listOf("caution"),
        coverage = WeeklyReportCoverage(1, 1, 1, 1, 1, 0),
        createdAt = 0,
        updatedAt = 0,
    )

    private fun mealDraft() = MealRecordDraft(
        recordType = DietRecordType.MEAL,
        mealType = MealType.LUNCH,
        occurredAt = 1,
        recordEpochDay = 1,
        description = "Lunch",
        foodItems = listOf(FoodItemDraft("Rice", "1 bowl", 100)),
        manualFinalCalories = null,
        beverage = null,
        note = "",
    )

    private class MutableClock(
        var currentMillis: Long,
    ) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(currentMillis)
    }
}
