package com.habit.app.domain.model

enum class AiFeature { WEEKLY_REPORT, MEAL_CALORIE_ESTIMATE }

enum class AiTestStatus { UNTESTED, PASSED, FAILED, NEEDS_KEY }

data class AiModelConfig(
    val id: Long = 0,
    val externalId: String,
    val name: String,
    val baseUrl: String,
    val modelId: String,
    val supportsText: Boolean,
    val supportsVision: Boolean,
    val allowInsecureHttp: Boolean,
    val enabled: Boolean,
    val lastTestedAt: Long?,
    val lastTestStatus: AiTestStatus,
    val lastTestMessage: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class AiModelConfigDraft(
    val name: String,
    val baseUrl: String,
    val modelId: String,
    val supportsText: Boolean,
    val supportsVision: Boolean,
    val allowInsecureHttp: Boolean,
    val enabled: Boolean,
)

data class AiFeatureBinding(
    val feature: AiFeature,
    val modelConfigId: Long?,
    val updatedAt: Long,
)

data class AiCalorieItemEstimate(
    val name: String,
    val portion: String,
    val minKcal: Int,
    val maxKcal: Int,
)

data class AiCalorieEstimate(
    val mealRecordId: Long = 0,
    val generatedAt: Long,
    val modelNameSnapshot: String,
    val modelIdSnapshot: String,
    val items: List<AiCalorieItemEstimate>,
    val totalMinKcal: Int,
    val totalMaxKcal: Int,
    val suggestedKcal: Int,
    val adoptedKcal: Int,
    val wasModified: Boolean,
    val accuracyNote: String,
)

data class AiCalorieEstimateDraft(
    val generatedAt: Long,
    val modelNameSnapshot: String,
    val modelIdSnapshot: String,
    val items: List<AiCalorieItemEstimate>,
    val totalMinKcal: Int,
    val totalMaxKcal: Int,
    val suggestedKcal: Int,
    val adoptedKcal: Int,
    val wasModified: Boolean,
    val accuracyNote: String,
)

data class AiWeeklyReport(
    val id: Long = 0,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val generatedAt: Long,
    val modelNameSnapshot: String,
    val modelIdSnapshot: String,
    val title: String,
    val overview: String,
    val habitAnalysis: String,
    val dietAnalysis: String,
    val correlationFinding: String,
    val suggestions: List<String>,
    val cautions: List<String>,
    val coverage: WeeklyReportCoverage,
    val createdAt: Long,
    val updatedAt: Long,
)

data class WeeklyReportCoverage(
    val scheduledHabitCount: Int,
    val completedHabitCount: Int,
    val dietRecordCount: Int,
    val dietRecordDays: Int,
    val knownCalorieRecords: Int,
    val missingCalorieRecords: Int,
)

data class AiWeeklyReportDraft(
    val startEpochDay: Long,
    val endEpochDay: Long,
    val generatedAt: Long,
    val modelNameSnapshot: String,
    val modelIdSnapshot: String,
    val title: String,
    val overview: String,
    val habitAnalysis: String,
    val dietAnalysis: String,
    val correlationFinding: String,
    val suggestions: List<String>,
    val cautions: List<String>,
    val coverage: WeeklyReportCoverage,
)
