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
            put("habits", buildJsonArray { backup.habits.forEach { add(it.toJson()) } })
            put("checkIns", buildJsonArray { backup.checkIns.forEach { add(it.toJson()) } })
            put("mealRecords", buildJsonArray { backup.mealRecords.forEach { add(it.toJson()) } })
            put("foodItems", buildJsonArray { backup.foodItems.forEach { add(it.toJson()) } })
            put("beverageDetails", buildJsonArray { backup.beverageDetails.forEach { add(it.toJson()) } })
            put("beverageToppings", buildJsonArray { backup.beverageToppings.forEach { add(it.toJson()) } })
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
        requireUnique("习惯", backup.habits.map { it.id })
        requireUnique("打卡", backup.checkIns.map { it.id })
        requireUnique("饮食", backup.mealRecords.map { it.id })
        requireUnique("食物", backup.foodItems.map { it.id })
        requireUnique("加料", backup.beverageToppings.map { it.id })
        val categoryIds = backup.categories.mapTo(mutableSetOf()) { it.id }
        val habitIds = backup.habits.mapTo(mutableSetOf()) { it.id }
        val mealIds = backup.mealRecords.mapTo(mutableSetOf()) { it.id }
        if (backup.habits.any { it.categoryId !in categoryIds }) throw InvalidBackupException("习惯引用了不存在的分类")
        if (backup.checkIns.any { it.habitId !in habitIds }) throw InvalidBackupException("打卡引用了不存在的习惯")
        if (backup.foodItems.any { it.mealRecordId !in mealIds } ||
            backup.beverageDetails.any { it.mealRecordId !in mealIds } ||
            backup.beverageToppings.any { it.mealRecordId !in mealIds }
        ) throw InvalidBackupException("饮食子项引用了不存在的记录")
        if (backup.mealRecords.any { (it.calculatedCalories ?: 0) < 0 || (it.finalCalories ?: 0) < 0 }) {
            throw InvalidBackupException("饮食热量无效")
        }
        if (backup.foodItems.any { (it.calories ?: 0) < 0 }) throw InvalidBackupException("食物热量无效")
        if (backup.beverageDetails.any { it.cupCount < 1 }) throw InvalidBackupException("饮品杯数无效")
    }

    private fun requireUnique(label: String, ids: List<Long>) {
        if (ids.distinct().size != ids.size) throw InvalidBackupException("$label ID 重复")
    }
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
private fun BackupPreferences.toJson() = buildJsonObject {
    put("themeId", themeId)
    put("recentEmojiKeys", buildJsonArray { recentEmojiKeys.forEach { add(JsonPrimitive(it)) } })
    put("dailyCalorieGoalEnabled", dailyCalorieGoalEnabled); putNullable("dailyCalorieGoalKcal", dailyCalorieGoalKcal)
}

private fun JsonObject.toCategory() = BackupCategory(requiredLong("id"), requiredString("name"), requiredBoolean("isPreset"), requiredBoolean("isHidden"), requiredInt("sortOrder"), requiredLong("createdAt"), requiredLong("updatedAt"))
private fun JsonObject.toHabit() = BackupHabit(requiredLong("id"), requiredString("name"), requiredString("iconKey"), requiredLong("themeColor"), requiredLong("categoryId"), requiredLong("startEpochDay"), nullableLong("archivedEpochDay"), requiredInt("sortOrder"), requiredLong("createdAt"), requiredLong("updatedAt"))
private fun JsonObject.toCheckIn() = BackupCheckIn(requiredLong("id"), requiredLong("habitId"), requiredLong("checkInEpochDay"), requiredLong("createdAt"), requiredLong("updatedAt"))
private fun JsonObject.toMealRecord() = BackupMealRecord(requiredLong("id"), requiredString("recordType"), nullableString("mealType"), requiredLong("occurredAt"), requiredLong("recordEpochDay"), requiredString("description"), nullableInt("calculatedCalories"), nullableInt("finalCalories"), requiredString("calorieSource"), requiredString("note"), requiredLong("createdAt"), requiredLong("updatedAt"))
private fun JsonObject.toFoodItem() = BackupFoodItem(requiredLong("id"), requiredLong("mealRecordId"), requiredString("name"), nullableString("portionText"), nullableInt("calories"), requiredInt("sortOrder"), requiredLong("createdAt"), requiredLong("updatedAt"))
private fun JsonObject.toBeverageDetail() = BackupBeverageDetail(requiredLong("mealRecordId"), requiredString("category"), requiredString("brandOrStore"), requiredString("beverageName"), requiredString("sizeOrVolume"), requiredString("temperature"), requiredString("iceLevel"), requiredString("sweetness"), requiredInt("cupCount"))
private fun JsonObject.toBeverageTopping() = BackupBeverageTopping(requiredLong("id"), requiredLong("mealRecordId"), requiredString("name"), requiredInt("sortOrder"), requiredLong("createdAt"), requiredLong("updatedAt"))
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
