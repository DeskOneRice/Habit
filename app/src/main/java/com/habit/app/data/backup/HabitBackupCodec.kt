package com.habit.app.data.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

object HabitBackupCodec {
    private val json = Json { isLenient = false }

    fun encode(backup: HabitBackup): String {
        validate(backup)
        return buildJsonObject {
            put("format", backup.format)
            put("schemaVersion", backup.schemaVersion)
            put("appVersion", backup.appVersion)
            put("exportedAt", backup.exportedAt)
            put("preferencesUpdatedAt", backup.preferencesUpdatedAt)
            put("categories", buildJsonArray { backup.categories.forEach { add(it.toJson()) } })
            put("habits", buildJsonArray { backup.habits.forEach { add(it.toJson()) } })
            put("checkIns", buildJsonArray { backup.checkIns.forEach { add(it.toJson()) } })
            put("preferences", backup.preferences.toJson())
        }.toString()
    }

    fun decode(text: String): HabitBackup {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (error: Exception) {
            throw InvalidBackupException("备份文件不是有效 JSON", error)
        }
        val format = root.requiredString("format")
        val schemaVersion = root.requiredInt("schemaVersion")
        if (schemaVersion > HABIT_BACKUP_SCHEMA_VERSION) {
            throw UnsupportedBackupVersionException(schemaVersion)
        }
        if (format != HABIT_BACKUP_FORMAT || schemaVersion != HABIT_BACKUP_SCHEMA_VERSION) {
            throw InvalidBackupException("无法识别的 Habit 备份格式")
        }
        val backup = try {
            HabitBackup(
                format = format,
                schemaVersion = schemaVersion,
                appVersion = root.requiredString("appVersion"),
                exportedAt = root.requiredLong("exportedAt"),
                preferencesUpdatedAt = root.requiredLong("preferencesUpdatedAt"),
                categories = root.requiredArray("categories").map { it.jsonObject.toCategory() },
                habits = root.requiredArray("habits").map { it.jsonObject.toHabit() },
                checkIns = root.requiredArray("checkIns").map { it.jsonObject.toCheckIn() },
                preferences = root.requiredObject("preferences").toPreferences(),
            )
        } catch (error: InvalidBackupException) {
            throw error
        } catch (error: Exception) {
            throw InvalidBackupException("备份字段无效", error)
        }
        validate(backup)
        return backup
    }

    fun validate(backup: HabitBackup) {
        if (backup.format != HABIT_BACKUP_FORMAT || backup.schemaVersion != HABIT_BACKUP_SCHEMA_VERSION) {
            throw InvalidBackupException("无法识别的 Habit 备份格式")
        }
        if (backup.exportedAt < 0 || backup.preferencesUpdatedAt < 0) {
            throw InvalidBackupException("备份时间无效")
        }
        requireUnique("分类", backup.categories.map(BackupCategory::id))
        requireUnique("习惯", backup.habits.map(BackupHabit::id))
        requireUnique("打卡", backup.checkIns.map(BackupCheckIn::id))
        val categoryIds = backup.categories.mapTo(mutableSetOf(), BackupCategory::id)
        val habitIds = backup.habits.mapTo(mutableSetOf(), BackupHabit::id)
        if (backup.habits.any { it.categoryId !in categoryIds }) {
            throw InvalidBackupException("习惯引用了不存在的分类")
        }
        if (backup.checkIns.any { it.habitId !in habitIds }) {
            throw InvalidBackupException("打卡引用了不存在的习惯")
        }
    }

    private fun requireUnique(label: String, ids: List<Long>) {
        if (ids.distinct().size != ids.size) throw InvalidBackupException("$label ID 重复")
    }
}

private fun BackupCategory.toJson() = buildJsonObject {
    put("id", id)
    put("name", name)
    put("isPreset", isPreset)
    put("isHidden", isHidden)
    put("sortOrder", sortOrder)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun BackupHabit.toJson() = buildJsonObject {
    put("id", id)
    put("name", name)
    put("iconKey", iconKey)
    put("themeColor", themeColor)
    put("categoryId", categoryId)
    put("startEpochDay", startEpochDay)
    put("archivedEpochDay", archivedEpochDay?.let(::JsonPrimitive) ?: JsonNull)
    put("sortOrder", sortOrder)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun BackupCheckIn.toJson() = buildJsonObject {
    put("id", id)
    put("habitId", habitId)
    put("checkInEpochDay", checkInEpochDay)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun BackupPreferences.toJson() = buildJsonObject {
    put("themeId", themeId)
    put("recentEmojiKeys", buildJsonArray { recentEmojiKeys.forEach { add(JsonPrimitive(it)) } })
}

private fun JsonObject.toCategory() = BackupCategory(
    id = requiredLong("id"),
    name = requiredString("name"),
    isPreset = requiredBoolean("isPreset"),
    isHidden = requiredBoolean("isHidden"),
    sortOrder = requiredInt("sortOrder"),
    createdAt = requiredLong("createdAt"),
    updatedAt = requiredLong("updatedAt"),
)

private fun JsonObject.toHabit() = BackupHabit(
    id = requiredLong("id"),
    name = requiredString("name"),
    iconKey = requiredString("iconKey"),
    themeColor = requiredLong("themeColor"),
    categoryId = requiredLong("categoryId"),
    startEpochDay = requiredLong("startEpochDay"),
    archivedEpochDay = nullableLong("archivedEpochDay"),
    sortOrder = requiredInt("sortOrder"),
    createdAt = requiredLong("createdAt"),
    updatedAt = requiredLong("updatedAt"),
)

private fun JsonObject.toCheckIn() = BackupCheckIn(
    id = requiredLong("id"),
    habitId = requiredLong("habitId"),
    checkInEpochDay = requiredLong("checkInEpochDay"),
    createdAt = requiredLong("createdAt"),
    updatedAt = requiredLong("updatedAt"),
)

private fun JsonObject.toPreferences() = BackupPreferences(
    themeId = requiredString("themeId"),
    recentEmojiKeys = requiredArray("recentEmojiKeys").map { it.jsonPrimitive.content },
)

private fun JsonObject.requiredElement(name: String): JsonElement =
    this[name] ?: throw InvalidBackupException("缺少字段：$name")

private fun JsonObject.requiredString(name: String) = requiredElement(name).jsonPrimitive.content
private fun JsonObject.requiredLong(name: String) = requiredElement(name).jsonPrimitive.long
private fun JsonObject.requiredInt(name: String) = requiredElement(name).jsonPrimitive.int
private fun JsonObject.requiredBoolean(name: String) = requiredElement(name).jsonPrimitive.boolean
private fun JsonObject.requiredArray(name: String): JsonArray = requiredElement(name).jsonArray
private fun JsonObject.requiredObject(name: String): JsonObject = requiredElement(name).jsonObject
private fun JsonObject.nullableLong(name: String): Long? = requiredElement(name).let { element ->
    if (element === JsonNull) null else element.jsonPrimitive.long
}
