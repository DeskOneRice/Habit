package com.habit.app.data.backup

import kotlinx.serialization.json.*

object HabitBackupCodec {
    private val json = Json { isLenient = false }

    fun encode(backup: HabitBackup): String {
        validate(backup)
        return buildJsonObject {
            put("format", backup.format); put("schemaVersion", backup.schemaVersion)
            put("appVersion", backup.appVersion); put("exportedAt", backup.exportedAt)
            put("preferencesUpdatedAt", backup.preferencesUpdatedAt)
            put("categories", buildJsonArray { backup.categories.forEach { add(it.toJson()) } })
            put("dietCategories", buildJsonArray { backup.dietCategories.forEach { add(it.toJson()) } })
            put("habits", buildJsonArray { backup.habits.forEach { add(it.toJson()) } })
            put("checkIns", buildJsonArray { backup.checkIns.forEach { add(it.toJson()) } })
            put("mealRecords", buildJsonArray { backup.mealRecords.forEach { add(it.toJson()) } })
            put("foodItems", buildJsonArray { backup.foodItems.forEach { add(it.toJson()) } })
            put("beverageDetails", buildJsonArray { backup.beverageDetails.forEach { add(it.toJson()) } })
            put("beverageToppings", buildJsonArray { backup.beverageToppings.forEach { add(it.toJson()) } })
            put("dietPhotos", buildJsonArray { backup.dietPhotos.forEach { add(it.toJson()) } })
            put("dietTemplates", buildJsonArray { backup.dietTemplates.forEach { add(it.toJson()) } })
            put("dietTemplateFoodItems", buildJsonArray { backup.dietTemplateFoodItems.forEach { add(it.toJson()) } })
            put("dietTemplateToppings", buildJsonArray { backup.dietTemplateToppings.forEach { add(it.toJson()) } })
            put("aiModelConfigs", buildJsonArray { backup.aiModelConfigs.forEach { add(it.toJson()) } })
            put("aiFeatureBindings", buildJsonArray { backup.aiFeatureBindings.forEach { add(it.toJson()) } })
            put("aiWeeklyReports", buildJsonArray { backup.aiWeeklyReports.forEach { add(it.toJson()) } })
            put("aiCalorieEstimates", buildJsonArray { backup.aiCalorieEstimates.forEach { add(it.toJson()) } })
            put("preferences", backup.preferences.toJson())
        }.toString()
    }

    fun decode(text: String): HabitBackup {
        val root = try { json.parseToJsonElement(text).jsonObject }
        catch (error: Exception) { throw InvalidBackupException("备份文件不是有效 JSON", error) }
        val format = root.requiredString("format")
        val version = root.requiredInt("schemaVersion")
        if (version > HABIT_BACKUP_SCHEMA_VERSION) throw UnsupportedBackupVersionException(version)
        if (format != HABIT_BACKUP_FORMAT || version !in 1..HABIT_BACKUP_SCHEMA_VERSION) {
            throw InvalidBackupException("无法识别的 Habit 备份格式")
        }
        val backup = try {
            HabitBackup(
                format = format,
                schemaVersion = version,
                appVersion = root.requiredString("appVersion"),
                exportedAt = root.requiredLong("exportedAt"),
                preferencesUpdatedAt = root.requiredLong("preferencesUpdatedAt"),
                categories = root.requiredArray("categories").map { it.jsonObject.toCategory() },
                habits = root.requiredArray("habits").map { it.jsonObject.toHabit() },
                checkIns = root.requiredArray("checkIns").map { it.jsonObject.toCheckIn() },
                preferences = root.requiredObject("preferences").toPreferences(version),
                mealRecords = root.optionalArray("mealRecords").map { it.jsonObject.toMealRecord() },
                foodItems = root.optionalArray("foodItems").map { it.jsonObject.toFoodItem() },
                beverageDetails = root.optionalArray("beverageDetails").map { it.jsonObject.toBeverageDetail() },
                beverageToppings = root.optionalArray("beverageToppings").map { it.jsonObject.toBeverageTopping() },
                dietPhotos = root.optionalArray("dietPhotos").map { it.jsonObject.toDietPhoto() },
                dietTemplates = root.optionalArray("dietTemplates").map { it.jsonObject.toDietTemplate() },
                dietTemplateFoodItems = root.optionalArray("dietTemplateFoodItems").map { it.jsonObject.toDietTemplateFoodItem() },
                dietTemplateToppings = root.optionalArray("dietTemplateToppings").map { it.jsonObject.toDietTemplateTopping() },
                dietCategories = root.optionalArray("dietCategories").map { it.jsonObject.toDietCategory() },
                aiModelConfigs = root.optionalArray("aiModelConfigs").map { it.jsonObject.toAiModelConfig() },
                aiFeatureBindings = root.optionalArray("aiFeatureBindings").map { it.jsonObject.toAiFeatureBinding() },
                aiWeeklyReports = root.optionalArray("aiWeeklyReports").map { it.jsonObject.toAiWeeklyReport() },
                aiCalorieEstimates = root.optionalArray("aiCalorieEstimates").map { it.jsonObject.toAiCalorieEstimate() },
            )
        } catch (error: InvalidBackupException) { throw error }
        catch (error: Exception) { throw InvalidBackupException("备份字段无效", error) }
        validate(backup)
        return backup
    }

    fun validate(backup: HabitBackup) {
        if (backup.format != HABIT_BACKUP_FORMAT || backup.schemaVersion !in 1..HABIT_BACKUP_SCHEMA_VERSION) {
            throw InvalidBackupException("无法识别的 Habit 备份格式")
        }
        if (backup.exportedAt < 0 || backup.preferencesUpdatedAt < 0) throw InvalidBackupException("备份时间无效")
        requireUnique("分类", backup.categories.map { it.id })
        requireUnique("饮食分类", backup.dietCategories.map { it.id })
        requireUnique("习惯", backup.habits.map { it.id })
        requireUnique("打卡", backup.checkIns.map { it.id })
        requireUnique("饮食", backup.mealRecords.map { it.id })
        requireUnique("食物", backup.foodItems.map { it.id })
        requireUnique("加料", backup.beverageToppings.map { it.id })
        requireUnique("饮食照片", backup.dietPhotos.map { it.id })
        requireUnique("饮食模板", backup.dietTemplates.map { it.id })
        requireUnique("模板食物", backup.dietTemplateFoodItems.map { it.id })
        requireUnique("模板加料", backup.dietTemplateToppings.map { it.id })
        requireUnique("AI 模型", backup.aiModelConfigs.map { it.id })
        requireUnique("AI 模型外部标识", backup.aiModelConfigs.map { it.externalId })
        requireUnique("AI 功能绑定", backup.aiFeatureBindings.map { it.feature })
        requireUnique("AI 周报", backup.aiWeeklyReports.map { it.id })
        requireUnique("AI 周报周次", backup.aiWeeklyReports.map { it.startEpochDay })
        requireUnique("AI 热量依据", backup.aiCalorieEstimates.map { it.mealRecordId })
        val categoryIds = backup.categories.mapTo(mutableSetOf()) { it.id }
        val dietCategoriesById = backup.dietCategories.associateBy { it.id }
        val habitIds = backup.habits.mapTo(mutableSetOf()) { it.id }
        val mealIds = backup.mealRecords.mapTo(mutableSetOf()) { it.id }
        val templateIds = backup.dietTemplates.mapTo(mutableSetOf()) { it.id }
        if (backup.habits.any { it.categoryId !in categoryIds }) throw InvalidBackupException("习惯引用了不存在的分类")
        if (backup.checkIns.any { it.habitId !in habitIds }) throw InvalidBackupException("打卡引用了不存在的习惯")
        if (backup.foodItems.any { it.mealRecordId !in mealIds } ||
            backup.beverageDetails.any { it.mealRecordId !in mealIds } ||
            backup.beverageToppings.any { it.mealRecordId !in mealIds }
        ) throw InvalidBackupException("饮食子项引用了不存在的记录")
        if (backup.dietTemplateFoodItems.any { it.templateId !in templateIds } ||
            backup.dietTemplateToppings.any { it.templateId !in templateIds }
        ) throw InvalidBackupException("模板子项引用了不存在的模板")
        if (backup.dietPhotos.any {
                (it.mealRecordId != null) == (it.templateId != null) ||
                    (it.mealRecordId != null && it.mealRecordId !in mealIds) ||
                    (it.templateId != null && it.templateId !in templateIds) ||
                    it.relativePath.isBlank()
            }
        ) throw InvalidBackupException("饮食照片归属无效")
        if (backup.mealRecords.any { (it.calculatedCalories ?: 0) < 0 || (it.finalCalories ?: 0) < 0 }) {
            throw InvalidBackupException("饮食热量无效")
        }
        if (backup.mealRecords.any { record ->
                record.dietCategoryId?.let { id ->
                    val expectedScope = if (record.recordType == "BEVERAGE") "BEVERAGE" else "MEAL"
                    dietCategoriesById[id]?.scope != expectedScope
                } ?: false
            } || backup.dietTemplates.any { template ->
                template.dietCategoryId?.let { id ->
                    val expectedScope = if (template.recordType == "BEVERAGE") "BEVERAGE" else "MEAL"
                    dietCategoriesById[id]?.scope != expectedScope
                } ?: false
            }
        ) throw InvalidBackupException("饮食记录引用了不存在或类型不匹配的分类")
        if (backup.foodItems.any { (it.calories ?: 0) < 0 }) throw InvalidBackupException("食物热量无效")
        if (backup.beverageDetails.any { it.cupCount < 1 }) throw InvalidBackupException("饮品杯数无效")
        if (backup.aiModelConfigs.any { it.externalId.isBlank() }) {
            throw InvalidBackupException("AI 模型外部标识无效")
        }
        if (backup.aiCalorieEstimates.any { estimate ->
                estimate.mealRecordId !in mealIds ||
                    estimate.totalMinKcal < 0 || estimate.totalMaxKcal < estimate.totalMinKcal ||
                    estimate.suggestedKcal < 0 || estimate.adoptedKcal < 0
            }
        ) throw InvalidBackupException("AI 热量依据无效")
    }

    private fun <T> requireUnique(label: String, ids: List<T>) {
        if (ids.distinct().size != ids.size) throw InvalidBackupException("$label ID 重复")
    }
}

private fun BackupDietCategory.toJson() = buildJsonObject {
    put("id", id); put("scope", scope); put("name", name); put("isPreset", isPreset)
    put("isHidden", isHidden); put("sortOrder", sortOrder); put("createdAt", createdAt); put("updatedAt", updatedAt)
}
private fun BackupCategory.toJson() = buildJsonObject {
    put("id", id); put("name", name); put("isPreset", isPreset); put("isHidden", isHidden)
    put("sortOrder", sortOrder); put("createdAt", createdAt); put("updatedAt", updatedAt)
}
private fun BackupHabit.toJson() = buildJsonObject {
    put("id", id); put("name", name); put("iconKey", iconKey); put("themeColor", themeColor)
    put("categoryId", categoryId); put("startEpochDay", startEpochDay); putNullable("archivedEpochDay", archivedEpochDay)
    put("sortOrder", sortOrder); put("createdAt", createdAt); put("updatedAt", updatedAt)
}
private fun BackupCheckIn.toJson() = buildJsonObject {
    put("id", id); put("habitId", habitId); put("checkInEpochDay", checkInEpochDay)
    put("createdAt", createdAt); put("updatedAt", updatedAt)
}
private fun BackupMealRecord.toJson() = buildJsonObject {
    put("id", id); put("recordType", recordType); putNullable("mealType", mealType)
    put("occurredAt", occurredAt); put("recordEpochDay", recordEpochDay); put("description", description)
    putNullable("calculatedCalories", calculatedCalories); putNullable("finalCalories", finalCalories)
    put("calorieSource", calorieSource); put("note", note); put("createdAt", createdAt); put("updatedAt", updatedAt)
    putNullable("dietCategoryId", dietCategoryId)
}
private fun BackupFoodItem.toJson() = buildJsonObject {
    put("id", id); put("mealRecordId", mealRecordId); put("name", name); putNullable("portionText", portionText)
    putNullable("calories", calories); put("sortOrder", sortOrder); put("createdAt", createdAt); put("updatedAt", updatedAt)
}
private fun BackupBeverageDetail.toJson() = buildJsonObject {
    put("mealRecordId", mealRecordId); put("category", category); put("brandOrStore", brandOrStore)
    put("beverageName", beverageName); put("sizeOrVolume", sizeOrVolume); put("temperature", temperature)
    put("iceLevel", iceLevel); put("sweetness", sweetness); put("cupCount", cupCount)
}
private fun BackupBeverageTopping.toJson() = buildJsonObject {
    put("id", id); put("mealRecordId", mealRecordId); put("name", name); put("sortOrder", sortOrder)
    put("createdAt", createdAt); put("updatedAt", updatedAt)
}
private fun BackupDietPhoto.toJson() = buildJsonObject {
    put("id", id); putNullable("mealRecordId", mealRecordId); putNullable("templateId", templateId)
    put("relativePath", relativePath); put("sortOrder", sortOrder); put("createdAt", createdAt)
}
private fun BackupDietTemplate.toJson() = buildJsonObject {
    put("id", id); put("name", name); put("recordType", recordType); putNullable("mealType", mealType)
    put("description", description); putNullable("manualFinalCalories", manualFinalCalories)
    putNullable("beverageCategory", beverageCategory); putNullable("brandOrStore", brandOrStore)
    putNullable("beverageName", beverageName); putNullable("sizeOrVolume", sizeOrVolume)
    putNullable("temperature", temperature); putNullable("iceLevel", iceLevel); putNullable("sweetness", sweetness)
    putNullable("cupCount", cupCount); put("note", note); put("sortOrder", sortOrder)
    put("createdAt", createdAt); put("updatedAt", updatedAt); putNullable("dietCategoryId", dietCategoryId)
}
private fun BackupDietTemplateFoodItem.toJson() = buildJsonObject {
    put("id", id); put("templateId", templateId); put("name", name); putNullable("portionText", portionText)
    putNullable("calories", calories); put("sortOrder", sortOrder); put("createdAt", createdAt); put("updatedAt", updatedAt)
}
private fun BackupDietTemplateTopping.toJson() = buildJsonObject {
    put("id", id); put("templateId", templateId); put("name", name); put("sortOrder", sortOrder)
    put("createdAt", createdAt); put("updatedAt", updatedAt)
}
private fun BackupAiModelConfig.toJson() = buildJsonObject {
    put("id", id); put("externalId", externalId); put("name", name); put("baseUrl", baseUrl); put("modelId", modelId)
    put("supportsText", supportsText); put("supportsVision", supportsVision); put("allowInsecureHttp", allowInsecureHttp)
    put("enabled", enabled); putNullable("lastTestedAt", lastTestedAt); put("lastTestStatus", lastTestStatus)
    put("lastTestMessage", lastTestMessage); put("createdAt", createdAt); put("updatedAt", updatedAt)
}
private fun BackupAiFeatureBinding.toJson() = buildJsonObject {
    put("feature", feature); putNullable("modelConfigId", modelConfigId); put("updatedAt", updatedAt)
}
private fun BackupAiWeeklyReport.toJson() = buildJsonObject {
    put("id", id); put("startEpochDay", startEpochDay); put("endEpochDay", endEpochDay); put("generatedAt", generatedAt)
    put("modelNameSnapshot", modelNameSnapshot); put("modelIdSnapshot", modelIdSnapshot); put("title", title)
    put("overview", overview); put("habitAnalysis", habitAnalysis); put("dietAnalysis", dietAnalysis)
    put("correlationFinding", correlationFinding); put("suggestionsJson", suggestionsJson); put("cautionsJson", cautionsJson)
    put("coverageJson", coverageJson); put("createdAt", createdAt); put("updatedAt", updatedAt)
}
private fun BackupAiCalorieEstimate.toJson() = buildJsonObject {
    put("mealRecordId", mealRecordId); put("generatedAt", generatedAt); put("modelNameSnapshot", modelNameSnapshot)
    put("modelIdSnapshot", modelIdSnapshot); put("itemsJson", itemsJson); put("totalMinKcal", totalMinKcal)
    put("totalMaxKcal", totalMaxKcal); put("suggestedKcal", suggestedKcal); put("adoptedKcal", adoptedKcal)
    put("wasModified", wasModified); put("accuracyNote", accuracyNote)
}
private fun BackupPreferences.toJson() = buildJsonObject {
    put("themeId", themeId)
    put("recentEmojiKeys", buildJsonArray { recentEmojiKeys.forEach { add(JsonPrimitive(it)) } })
    put("dailyCalorieGoalEnabled", dailyCalorieGoalEnabled); putNullable("dailyCalorieGoalKcal", dailyCalorieGoalKcal)
}

private fun JsonObject.toDietCategory() = BackupDietCategory(requiredLong("id"), requiredString("scope"), requiredString("name"), requiredBoolean("isPreset"), requiredBoolean("isHidden"), requiredInt("sortOrder"), requiredLong("createdAt"), requiredLong("updatedAt"))
private fun JsonObject.toCategory() = BackupCategory(requiredLong("id"), requiredString("name"), requiredBoolean("isPreset"), requiredBoolean("isHidden"), requiredInt("sortOrder"), requiredLong("createdAt"), requiredLong("updatedAt"))
private fun JsonObject.toHabit() = BackupHabit(requiredLong("id"), requiredString("name"), requiredString("iconKey"), requiredLong("themeColor"), requiredLong("categoryId"), requiredLong("startEpochDay"), nullableLong("archivedEpochDay"), requiredInt("sortOrder"), requiredLong("createdAt"), requiredLong("updatedAt"))
private fun JsonObject.toCheckIn() = BackupCheckIn(requiredLong("id"), requiredLong("habitId"), requiredLong("checkInEpochDay"), requiredLong("createdAt"), requiredLong("updatedAt"))
private fun JsonObject.toMealRecord() = BackupMealRecord(requiredLong("id"), requiredString("recordType"), nullableString("mealType"), requiredLong("occurredAt"), requiredLong("recordEpochDay"), requiredString("description"), nullableInt("calculatedCalories"), nullableInt("finalCalories"), requiredString("calorieSource"), requiredString("note"), requiredLong("createdAt"), requiredLong("updatedAt"), nullableLong("dietCategoryId"))
private fun JsonObject.toFoodItem() = BackupFoodItem(requiredLong("id"), requiredLong("mealRecordId"), requiredString("name"), nullableString("portionText"), nullableInt("calories"), requiredInt("sortOrder"), requiredLong("createdAt"), requiredLong("updatedAt"))
private fun JsonObject.toBeverageDetail() = BackupBeverageDetail(requiredLong("mealRecordId"), requiredString("category"), requiredString("brandOrStore"), requiredString("beverageName"), requiredString("sizeOrVolume"), requiredString("temperature"), requiredString("iceLevel"), requiredString("sweetness"), requiredInt("cupCount"))
private fun JsonObject.toBeverageTopping() = BackupBeverageTopping(requiredLong("id"), requiredLong("mealRecordId"), requiredString("name"), requiredInt("sortOrder"), requiredLong("createdAt"), requiredLong("updatedAt"))
private fun JsonObject.toDietPhoto() = BackupDietPhoto(requiredLong("id"), nullableLong("mealRecordId"), nullableLong("templateId"), requiredString("relativePath"), requiredInt("sortOrder"), requiredLong("createdAt"))
private fun JsonObject.toDietTemplate() = BackupDietTemplate(
    requiredLong("id"), requiredString("name"), requiredString("recordType"), nullableString("mealType"),
    requiredString("description"), nullableInt("manualFinalCalories"), nullableString("beverageCategory"),
    nullableString("brandOrStore"), nullableString("beverageName"), nullableString("sizeOrVolume"),
    nullableString("temperature"), nullableString("iceLevel"), nullableString("sweetness"), nullableInt("cupCount"),
    requiredString("note"), requiredInt("sortOrder"), requiredLong("createdAt"), requiredLong("updatedAt"),
    nullableLong("dietCategoryId"),
)
private fun JsonObject.toDietTemplateFoodItem() = BackupDietTemplateFoodItem(requiredLong("id"), requiredLong("templateId"), requiredString("name"), nullableString("portionText"), nullableInt("calories"), requiredInt("sortOrder"), requiredLong("createdAt"), requiredLong("updatedAt"))
private fun JsonObject.toDietTemplateTopping() = BackupDietTemplateTopping(requiredLong("id"), requiredLong("templateId"), requiredString("name"), requiredInt("sortOrder"), requiredLong("createdAt"), requiredLong("updatedAt"))
private fun JsonObject.toAiModelConfig() = BackupAiModelConfig(
    requiredLong("id"), requiredString("externalId"), requiredString("name"), requiredString("baseUrl"), requiredString("modelId"),
    requiredBoolean("supportsText"), requiredBoolean("supportsVision"), requiredBoolean("allowInsecureHttp"), requiredBoolean("enabled"),
    nullableLong("lastTestedAt"), requiredString("lastTestStatus"), requiredString("lastTestMessage"), requiredLong("createdAt"),
    requiredLong("updatedAt"),
)
private fun JsonObject.toAiFeatureBinding() = BackupAiFeatureBinding(
    requiredString("feature"), nullableLong("modelConfigId"), requiredLong("updatedAt"),
)
private fun JsonObject.toAiWeeklyReport() = BackupAiWeeklyReport(
    requiredLong("id"), requiredLong("startEpochDay"), requiredLong("endEpochDay"), requiredLong("generatedAt"),
    requiredString("modelNameSnapshot"), requiredString("modelIdSnapshot"), requiredString("title"), requiredString("overview"),
    requiredString("habitAnalysis"), requiredString("dietAnalysis"), requiredString("correlationFinding"),
    requiredString("suggestionsJson"), requiredString("cautionsJson"), requiredString("coverageJson"),
    requiredLong("createdAt"), requiredLong("updatedAt"),
)
private fun JsonObject.toAiCalorieEstimate() = BackupAiCalorieEstimate(
    requiredLong("mealRecordId"), requiredLong("generatedAt"), requiredString("modelNameSnapshot"),
    requiredString("modelIdSnapshot"), requiredString("itemsJson"), requiredInt("totalMinKcal"), requiredInt("totalMaxKcal"),
    requiredInt("suggestedKcal"), requiredInt("adoptedKcal"), requiredBoolean("wasModified"), requiredString("accuracyNote"),
)
private fun JsonObject.toPreferences(version: Int) = BackupPreferences(
    requiredString("themeId"), requiredArray("recentEmojiKeys").map { it.jsonPrimitive.content },
    if (version >= 2) optionalBoolean("dailyCalorieGoalEnabled") ?: false else false,
    if (version >= 2) nullableInt("dailyCalorieGoalKcal") else null,
)

private fun JsonObject.requiredElement(name: String): JsonElement = this[name] ?: throw InvalidBackupException("缺少字段：$name")
private fun JsonObject.requiredString(name: String) = requiredElement(name).jsonPrimitive.content
private fun JsonObject.requiredLong(name: String) = requiredElement(name).jsonPrimitive.long
private fun JsonObject.requiredInt(name: String) = requiredElement(name).jsonPrimitive.int
private fun JsonObject.requiredBoolean(name: String) = requiredElement(name).jsonPrimitive.boolean
private fun JsonObject.requiredArray(name: String) = requiredElement(name).jsonArray
private fun JsonObject.optionalArray(name: String) = this[name]?.jsonArray ?: JsonArray(emptyList())
private fun JsonObject.requiredObject(name: String) = requiredElement(name).jsonObject
private fun JsonObject.nullableLong(name: String) = this[name].nullable()?.jsonPrimitive?.long
private fun JsonObject.nullableInt(name: String) = this[name].nullable()?.jsonPrimitive?.int
private fun JsonObject.nullableString(name: String) = this[name].nullable()?.jsonPrimitive?.content
private fun JsonObject.optionalBoolean(name: String) = this[name]?.jsonPrimitive?.boolean
private fun JsonElement?.nullable(): JsonElement? = if (this == null || this === JsonNull) null else this
private fun JsonObjectBuilder.putNullable(name: String, value: Long?) = put(name, value?.let(::JsonPrimitive) ?: JsonNull)
private fun JsonObjectBuilder.putNullable(name: String, value: Int?) = put(name, value?.let(::JsonPrimitive) ?: JsonNull)
private fun JsonObjectBuilder.putNullable(name: String, value: String?) = put(name, value?.let(::JsonPrimitive) ?: JsonNull)
