# Habit 0.6.0 AI Insights Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a signed Habit 0.6.0 APK with reusable OpenAI-compatible model profiles, per-feature model bindings, manually generated habit-plus-diet weekly reports, user-confirmed meal-photo calorie estimates, refined diet details, and lossless 0.5.1/backup compatibility.

**Architecture:** Add a neutral AI domain/data boundary beside the existing habit and diet modules. Room stores non-secret configuration and AI artifacts; an Android Keystore-backed secret store owns API keys; a small transport/client boundary owns OpenAI-compatible requests. Weekly reporting and calorie estimation consume those shared interfaces but keep prompts, parsers, repositories, and UI state independent.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose Material 3, Room 2.8.4, coroutines/Flow, kotlinx.serialization JSON, Android Keystore AES/GCM, `HttpURLConnection`, JUnit 4, AndroidX Room/Compose instrumentation tests.

## Global Constraints

- Target `versionName = "0.6.0"`, `versionCode = 12`, `minSdk = 23`, `targetSdk = 36`.
- Upgrade Room exactly from version 4 to 5 through `MIGRATION_4_5`; never use destructive migration.
- Upgrade the app-owned backup format exactly from schema 4 to 5 while continuing to accept schemas 1–4.
- Keep the existing stable signing configuration and certificate fingerprint.
- All displayed and computed business dates use `Asia/Shanghai`; persisted instants remain epoch millis and business dates remain epoch days.
- API keys never enter Room, app-owned backups, logs, exceptions, reports, screenshots, or test fixtures.
- Weekly reports send structured data only; they never send diet photos or notes.
- Meal calorie analysis sends only photos explicitly selected for the current meal plus the current meal name.
- No silent AI calls, retries, model fallback, background generation, login, cloud sync, chat, payment, or backend.
- AI failures must not block or mutate non-AI habit, diet, statistics, category, or backup flows.
- Reuse global top bars, lightweight arrows, `HabitAddFab`, white/low-saturation sky-blue cards, 48 dp controls, system-bar insets, and the current drawer reset policy.

---

### Task 1: AI Domain Contracts and Deterministic Policies

**Files:**
- Create: `app/src/main/java/com/habit/app/domain/model/AiModels.kt`
- Create: `app/src/main/java/com/habit/app/domain/repository/AiRepository.kt`
- Create: `app/src/main/java/com/habit/app/domain/ai/AiPolicies.kt`
- Test: `app/src/test/java/com/habit/app/domain/ai/AiPoliciesTest.kt`

**Interfaces:**
- Produces: `AiModelConfig`, `AiFeature`, `AiTestStatus`, `AiFeatureBinding`, `AiWeeklyReport`, `AiCalorieEstimate`, `AiCalorieItemEstimate`, `AiModelRepository`, `AiWeeklyReportRepository`, `previousCompleteWeek(LocalDate)`, and `normalizedChatCompletionsUrl(String, Boolean)`.
- Consumes: `HabitTimePolicy.zoneId` and ordinary Kotlin/Java date types.

- [ ] **Step 1: Write failing date, URL, and capability tests**

```kotlin
class AiPoliciesTest {
    @Test fun previousCompleteWeekUsesBeijingMondayThroughSunday() {
        val range = previousCompleteWeek(LocalDate.of(2026, 8, 10))
        assertEquals(LocalDate.of(2026, 8, 3), range.start)
        assertEquals(LocalDate.of(2026, 8, 9), range.endInclusive)
    }

    @Test fun baseV1UrlAppendsChatCompletionsExactlyOnce() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            normalizedChatCompletionsUrl("https://api.example.com/v1/", false),
        )
    }

    @Test fun httpRequiresExplicitPerModelConsent() {
        assertFailsWith<IllegalArgumentException> {
            normalizedChatCompletionsUrl("http://192.168.1.2:11434/v1", false)
        }
        assertEquals(
            "http://192.168.1.2:11434/v1/chat/completions",
            normalizedChatCompletionsUrl("http://192.168.1.2:11434/v1", true),
        )
    }
}
```

- [ ] **Step 2: Run the tests and verify the unresolved symbols fail**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.habit.app.domain.ai.AiPoliciesTest"
```

Expected: compilation fails because the AI domain types and policies do not exist.

- [ ] **Step 3: Add focused domain types and repository contracts**

```kotlin
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
```

`AiRepository.kt` must expose exact Flow/suspend operations:

```kotlin
interface AiModelRepository {
    fun observeModels(): Flow<List<AiModelConfig>>
    fun observeBindings(): Flow<List<AiFeatureBinding>>
    fun observeModel(id: Long): Flow<AiModelConfig?>
    suspend fun saveModel(id: Long?, draft: AiModelConfigDraft): Long
    suspend fun recordTest(id: Long, status: AiTestStatus, message: String, testedAt: Long)
    suspend fun bind(feature: AiFeature, modelId: Long?)
    suspend fun deleteModel(id: Long)
}

interface AiWeeklyReportRepository {
    fun observeAll(): Flow<List<AiWeeklyReport>>
    fun observeWeek(startEpochDay: Long): Flow<AiWeeklyReport?>
    suspend fun save(report: AiWeeklyReport): Long
    suspend fun delete(id: Long)
}
```

- [ ] **Step 4: Implement strict date and URL policies**

```kotlin
fun previousCompleteWeek(today: LocalDate): ClosedRange<LocalDate> {
    val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val end = currentMonday.minusDays(1)
    return end.minusDays(6)..end
}

fun normalizedChatCompletionsUrl(baseUrl: String, allowInsecureHttp: Boolean): String {
    val uri = URI(baseUrl.trim())
    require(uri.scheme == "https" || uri.scheme == "http") { "仅支持 HTTP 或 HTTPS 地址" }
    require(uri.host != null && uri.userInfo == null && uri.fragment == null) { "API 地址格式不正确" }
    require(uri.scheme != "http" || allowInsecureHttp) { "HTTP 地址需要明确授权" }
    val normalized = baseUrl.trim().trimEnd('/')
    return if (normalized.endsWith("/chat/completions")) normalized else "$normalized/chat/completions"
}
```

- [ ] **Step 5: Run the policy tests**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 6: Commit the domain boundary**

```powershell
git add app/src/main/java/com/habit/app/domain app/src/test/java/com/habit/app/domain/ai
git commit -m "feat: define AI domain contracts"
```

---

### Task 2: Room 5 Schema, DAOs, Mappers, and Repositories

**Files:**
- Modify: `app/src/main/java/com/habit/app/data/local/Entities.kt`
- Create: `app/src/main/java/com/habit/app/data/local/AiDao.kt`
- Modify: `app/src/main/java/com/habit/app/data/local/HabitDatabase.kt`
- Create: `app/src/main/java/com/habit/app/data/repository/RoomAiModelRepository.kt`
- Create: `app/src/main/java/com/habit/app/data/repository/RoomAiWeeklyReportRepository.kt`
- Modify: `app/src/main/java/com/habit/app/data/local/EntityMappers.kt`
- Modify: `app/src/main/java/com/habit/app/di/AppContainer.kt`
- Modify: `app/schemas/com.habit.app.data.local.HabitDatabase/5.json`
- Test: `app/src/androidTest/java/com/habit/app/data/local/HabitDatabaseMigrationTest.kt`
- Create: `app/src/androidTest/java/com/habit/app/data/repository/RoomAiRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 1 domain models and repository contracts.
- Produces: `AiDao`, `MIGRATION_4_5`, `RoomAiModelRepository`, `RoomAiWeeklyReportRepository`, and Room access to one calorie estimate per meal.

- [ ] **Step 1: Extend migration tests with 4→5 data preservation**

Create a version-4 database containing one category, habit, check-in, meal, and photo; migrate with `MIGRATION_4_5`; assert all old counts remain 1 and the new tables exist:

```kotlin
val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)
assertEquals(1, db.query("SELECT COUNT(*) FROM habits").singleInt())
assertEquals(1, db.query("SELECT COUNT(*) FROM meal_records").singleInt())
assertEquals(0, db.query("SELECT COUNT(*) FROM ai_model_configs").singleInt())
assertEquals(0, db.query("SELECT COUNT(*) FROM ai_weekly_reports").singleInt())
```

- [ ] **Step 2: Run the migration test and verify it fails at missing migration/version 5 schema**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.local.HabitDatabaseMigrationTest
```

Expected: FAIL because `MIGRATION_4_5` and schema 5 do not exist.

- [ ] **Step 3: Add the four entities with exact constraints**

```kotlin
@Entity(tableName = "ai_model_configs", indices = [Index(value = ["externalId"], unique = true)])
data class AiModelConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val externalId: String,
    val name: String,
    val baseUrl: String,
    val modelId: String,
    val supportsText: Boolean,
    val supportsVision: Boolean,
    val allowInsecureHttp: Boolean,
    val enabled: Boolean,
    val lastTestedAt: Long?,
    val lastTestStatus: String,
    val lastTestMessage: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "ai_feature_bindings",
    foreignKeys = [ForeignKey(
        entity = AiModelConfigEntity::class,
        parentColumns = ["id"], childColumns = ["modelConfigId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("modelConfigId")],
)
data class AiFeatureBindingEntity(
    @PrimaryKey val feature: String,
    val modelConfigId: Long?,
    val updatedAt: Long,
)
```

Add the report and estimate entities with these exact columns:

```kotlin
@Entity(tableName = "ai_weekly_reports", indices = [Index(value = ["startEpochDay"], unique = true)])
data class AiWeeklyReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val suggestionsJson: String,
    val cautionsJson: String,
    val coverageJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "ai_calorie_estimates",
    foreignKeys = [ForeignKey(
        entity = MealRecordEntity::class,
        parentColumns = ["id"], childColumns = ["mealRecordId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class AiCalorieEstimateEntity(
    @PrimaryKey val mealRecordId: Long,
    val generatedAt: Long,
    val modelNameSnapshot: String,
    val modelIdSnapshot: String,
    val itemsJson: String,
    val totalMinKcal: Int,
    val totalMaxKcal: Int,
    val suggestedKcal: Int,
    val adoptedKcal: Int,
    val wasModified: Boolean,
    val accuracyNote: String,
)
```

- [ ] **Step 4: Add DAO queries and transactional repository behavior**

The DAO must include:

```kotlin
@Query("SELECT * FROM ai_model_configs ORDER BY name COLLATE NOCASE, id")
fun observeModels(): Flow<List<AiModelConfigEntity>>

@Query("SELECT * FROM ai_feature_bindings ORDER BY feature")
fun observeBindings(): Flow<List<AiFeatureBindingEntity>>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertBinding(binding: AiFeatureBindingEntity)

@Query("SELECT * FROM ai_weekly_reports ORDER BY startEpochDay DESC")
fun observeReports(): Flow<List<AiWeeklyReportEntity>>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertCalorieEstimate(estimate: AiCalorieEstimateEntity)

@Query("SELECT * FROM ai_calorie_estimates WHERE mealRecordId=:mealRecordId")
fun observeCalorieEstimate(mealRecordId: Long): Flow<AiCalorieEstimateEntity?>
```

`RoomAiModelRepository.deleteModel(id)` must execute inside `database.withTransaction`, delete the model, and rely on `SET_NULL` to preserve binding rows.

- [ ] **Step 5: Implement `MIGRATION_4_5` with explicit SQL**

Create all four tables and required indexes. The migration must not issue `DROP`, `DELETE`, `UPDATE`, or `ALTER` against pre-5 tables. Register it in `AppContainer`:

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
```

- [ ] **Step 6: Add repository behavior tests**

Test that deleting a model leaves both feature rows with `modelConfigId = null`, saving the same week replaces it, and deleting a meal cascades its estimate:

```kotlin
repository.bind(AiFeature.WEEKLY_REPORT, modelId)
repository.deleteModel(modelId)
assertNull(repository.observeBindings().first().single().modelConfigId)
```

- [ ] **Step 7: Run Room and migration tests**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.local.HabitDatabaseMigrationTest,com.habit.app.data.repository.RoomAiRepositoryTest
```

Expected: PASS and Room exports schema 5.

- [ ] **Step 8: Commit Room 5**

```powershell
git add app/src/main/java/com/habit/app/data app/src/main/java/com/habit/app/di app/src/androidTest app/schemas
git commit -m "feat: add Room 5 AI storage"
```

---

### Task 3: Android Keystore Secret Store

**Files:**
- Create: `app/src/main/java/com/habit/app/data/ai/AiSecretStore.kt`
- Create: `app/src/main/java/com/habit/app/data/ai/AndroidKeystoreAiSecretStore.kt`
- Modify: `app/src/main/java/com/habit/app/di/AppContainer.kt`
- Modify: `app/src/main/res/xml/backup_rules.xml`
- Modify: `app/src/main/res/xml/data_extraction_rules.xml`
- Create: `app/src/androidTest/java/com/habit/app/data/ai/AndroidKeystoreAiSecretStoreTest.kt`
- Modify: `app/src/androidTest/java/com/habit/app/ManifestPolicyTest.kt`

**Interfaces:**
- Produces: `AiSecretStore.put(externalId, apiKey)`, `get(externalId)`, `maskedSuffix(externalId)`, `remove(externalId)`, and `clearAll()`.
- Consumes: model `externalId`; no Room dependency.

- [ ] **Step 1: Write secret round-trip, replacement, deletion, and file-content tests**

```kotlin
@Test fun storesCiphertextAndNeverPlaintext() = runTest {
    store.put("model-uuid", "sk-secret-value")
    assertEquals("sk-secret-value", store.get("model-uuid"))
    assertEquals("alue", store.maskedSuffix("model-uuid"))
    val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).all.toString()
    assertFalse(raw.contains("sk-secret-value"))
}
```

- [ ] **Step 2: Run the instrumentation test and verify missing implementation failure**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.ai.AndroidKeystoreAiSecretStoreTest
```

Expected: compile failure because the store does not exist.

- [ ] **Step 3: Implement the secret interface and AES/GCM store**

Use alias `habit_ai_api_key_v1`, AndroidKeyStore AES/GCM/NoPadding, a fresh 12-byte IV for every write, and Base64 of `iv + ciphertext`. Reject blank Key values. Convert `AEADBadTagException`, invalidated key, and malformed ciphertext to `null`, never include ciphertext or Key in the exception message.

```kotlin
interface AiSecretStore {
    suspend fun put(externalId: String, apiKey: String)
    suspend fun get(externalId: String): String?
    suspend fun maskedSuffix(externalId: String): String?
    suspend fun remove(externalId: String)
    suspend fun clearAll()
}
```

- [ ] **Step 4: Exclude the secret preference filename in both Android backup rule formats**

Even though app-owned backup is the supported path, add explicit exclusions for `habit_ai_secrets.xml` and keep the existing all-data exclusion policy green.

- [ ] **Step 5: Run secret and manifest tests**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.ai.AndroidKeystoreAiSecretStoreTest,com.habit.app.ManifestPolicyTest
```

Expected: PASS.

- [ ] **Step 6: Commit secure storage**

```powershell
git add app/src/main/java/com/habit/app/data/ai app/src/main/java/com/habit/app/di app/src/main/res/xml app/src/androidTest
git commit -m "feat: secure AI API keys with Keystore"
```

---

### Task 4: OpenAI-Compatible Transport, Redaction, and Image Preparation

**Files:**
- Create: `app/src/main/java/com/habit/app/data/ai/AiCompletionClient.kt`
- Create: `app/src/main/java/com/habit/app/data/ai/OpenAiCompatibleClient.kt`
- Create: `app/src/main/java/com/habit/app/data/ai/AiHttpTransport.kt`
- Create: `app/src/main/java/com/habit/app/data/ai/UrlConnectionAiHttpTransport.kt`
- Create: `app/src/main/java/com/habit/app/data/ai/AiErrors.kt`
- Create: `app/src/main/java/com/habit/app/data/ai/CalorieEstimateImagePreparer.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/test/java/com/habit/app/data/ai/OpenAiCompatibleClientTest.kt`
- Create: `app/src/test/java/com/habit/app/data/ai/AiErrorRedactionTest.kt`
- Create: `app/src/androidTest/java/com/habit/app/data/ai/CalorieEstimateImagePreparerTest.kt`

**Interfaces:**
- Consumes: normalized URL, model config, API Key, selected photo files.
- Produces: exact client/transport signatures below, typed `AiServiceFailure`, and temporary JPEG `PreparedAiImage` files that are always closeable/deletable.

```kotlin
data class AiPreparedImage(val mimeType: String, val bytes: ByteArray)

interface AiCompletionClient {
    suspend fun completeText(
        model: AiModelConfig,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
    ): String

    suspend fun completeVision(
        model: AiModelConfig,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        images: List<AiPreparedImage>,
    ): String
}

data class AiHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: ByteArray,
    val connectTimeoutMillis: Int = 15_000,
    val readTimeoutMillis: Int = 60_000,
)

data class AiHttpResponse(val statusCode: Int, val body: ByteArray)

interface AiHttpTransport {
    suspend fun execute(request: AiHttpRequest): AiHttpResponse
}

enum class AiFailureKind { AUTH, QUOTA, NOT_FOUND, UNSUPPORTED, OFFLINE, TIMEOUT, SERVER, INVALID_RESPONSE }

class AiServiceFailure(
    val kind: AiFailureKind,
    val userMessage: String,
    val safeDiagnostic: String = "",
) : Exception(userMessage)
```

- [ ] **Step 1: Write fake-transport request and redaction tests**

```kotlin
@Test fun authorizationOnlyLivesInTransportRequest() = runTest {
    val transport = RecordingTransport(response(200, validChatBody("{\"ok\":true}")))
    val client = OpenAiCompatibleClient(transport)
    client.completeText(config, "sk-private", "system", "user")
    assertEquals("Bearer sk-private", transport.request.headers["Authorization"])
    assertFalse(transport.request.body.contains("sk-private"))
}

@Test fun rateLimitMessageDoesNotLeakBodyOrKey() {
    val failure = mapAiHttpFailure(429, "provider diagnostic sk-private", "sk-private")
    assertEquals("请求过于频繁或额度不足，请稍后重试", failure.userMessage)
    assertFalse(failure.toString().contains("sk-private"))
}
```

- [ ] **Step 2: Run the JVM tests and verify unresolved symbols fail**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.habit.app.data.ai.*"
```

Expected: compile failure.

- [ ] **Step 3: Implement cancellable `HttpURLConnection` transport**

The request DTO must contain URL, headers, JSON bytes, connect timeout 15 seconds, read timeout 60 seconds, and maximum response size 2 MiB. Execute on `Dispatchers.IO`; install cancellation that calls `disconnect()`; reject redirects to a different scheme/host; return only status, response headers, and bounded body.

- [ ] **Step 4: Implement OpenAI Chat Completions JSON**

Text request:

```json
{
  "model": "configured-model-id",
  "temperature": 0.2,
  "messages": [
    {"role":"system","content":"只返回一个符合约定结构的中文 JSON 对象。"},
    {"role":"user","content":"分析输入 JSON，并严格按系统要求返回。"}
  ]
}
```

Vision content must use one text part followed by 1–3 `image_url` parts whose URL is `data:image/jpeg;base64,` plus the encoded JPEG bytes. Parse only `choices[0].message.content`; reject empty or oversized content.

- [ ] **Step 5: Implement status/error mapping without raw body propagation**

Map errors to these exact messages: 401/403 → `API Key 无效或没有模型权限`; 402/429 → `请求过于频繁或额度不足，请稍后重试`; 404 → `API 地址或模型 ID 不存在`; 415/vision rejection → `当前模型不支持图片分析`; offline → `当前网络不可用`; timeout → `AI 请求超时，请重试`; 5xx → `AI 服务暂时不可用`; malformed/empty JSON → `模型返回内容无法解析，可更换模型或重试`. Cancellation propagates without a user error. A redaction helper must replace the current Key, `Authorization`, and Bearer token shapes before any diagnostic is retained.

- [ ] **Step 6: Write and implement Android image-preparation tests**

Create an EXIF-bearing 4000×3000 fixture, prepare it, and assert longest edge ≤1536, JPEG decoding succeeds, no original path is modified, and `PreparedAiImage.close()` deletes the temporary file. Use JPEG quality 82 and continue downsampling until total prepared payload ≤6 MiB.

- [ ] **Step 7: Add network policy**

Add `android.permission.INTERNET` and permit platform cleartext transport; keep the actual HTTP denial in `normalizedChatCompletionsUrl` so only an explicitly opted-in profile can use it. Extend `ManifestPolicyTest` to assert INTERNET exists and no unrelated network/account permissions were added.

- [ ] **Step 8: Run JVM and image-preparation tests**

Run the Step 2 command and the image preparer instrumentation class. Expected: PASS.

- [ ] **Step 9: Commit the client boundary**

```powershell
git add app/src/main/java/com/habit/app/data/ai app/src/main/AndroidManifest.xml app/src/test app/src/androidTest
git commit -m "feat: add OpenAI-compatible AI client"
```

---

### Task 5: Model Configuration, Capability Testing, and Feature Binding UI

**Files:**
- Create: `app/src/main/java/com/habit/app/ui/ai/AiSettingsViewModel.kt`
- Create: `app/src/main/java/com/habit/app/ui/ai/AiSettingsScreen.kt`
- Create: `app/src/main/java/com/habit/app/ui/ai/AiModelEditorScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitDestination.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitDrawerContent.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitNavHost.kt`
- Modify: `app/src/main/java/com/habit/app/di/AppContainer.kt`
- Create: `app/src/test/java/com/habit/app/ui/ai/AiSettingsViewModelTest.kt`
- Create: `app/src/androidTest/java/com/habit/app/ui/ai/AiSettingsFlowTest.kt`

**Interfaces:**
- Consumes: `AiModelRepository`, `AiSecretStore`, `AiCompletionClient`.
- Produces: drawer root `AiSettings`, child route `AiModelEditor`, tested model cards, and bindings for `WEEKLY_REPORT`/`MEAL_CALORIE_ESTIMATE`.

- [ ] **Step 1: Write ViewModel tests for save/reset/test/binding/delete rules**

Test these exact transitions:

```kotlin
viewModel.saveModel(validDraft, apiKey = "sk-one")
assertEquals(AiTestStatus.UNTESTED, repository.models.single().lastTestStatus)
viewModel.testText(repository.models.single().id)
assertEquals(AiTestStatus.PASSED, viewModel.state.value.models.single().textStatus)
viewModel.bind(AiFeature.WEEKLY_REPORT, repository.models.single().id)
assertEquals(modelId, repository.bindings[AiFeature.WEEKLY_REPORT])
```

Also assert vision binding rejects a text-only model and changing URL or Key resets tests.

- [ ] **Step 2: Run the ViewModel tests and verify they fail**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.habit.app.ui.ai.AiSettingsViewModelTest"
```

Expected: compile failure.

- [ ] **Step 3: Implement ViewModel state and one-shot actions**

Expose immutable models, two bindings, active pager tab, busy model IDs, delete-impact dialog, HTTP consent dialog, and Chinese messages. Test requests use a minimal text prompt or an app-generated neutral image. Persist only sanitized status text.

- [ ] **Step 4: Add AI drawer group and routes**

Add `AiReports`, `AiSettings`, and `AiModelEditor` destinations. The drawer contains:

```kotlin
val aiDestinations = listOf(
    DrawerDestination(HabitDestination.AiReports, "综合周报", "📝"),
    DrawerDestination(HabitDestination.AiSettings, "模型配置", "🤖"),
)
```

Use the existing collapsible group component with title `AI 洞察` and emoji `✨`.

- [ ] **Step 5: Build the two-tab settings and editor UI**

Use centered `模型配置 / 功能绑定` pager tabs. Model cards show display name, model ID, capability chips, HTTPS/HTTP warning, Key state, and Beijing test time. Use global plus-only `HabitAddFab`. The editor has validated name, URL, Key, model ID, capability switches, enabled switch, and explicit HTTP consent. Controls are ≥48 dp.

- [ ] **Step 6: Add Compose flow tests**

Cover: add two models, masked Key suffix, text/vision test buttons, ability-filtered bindings, delete impact, and drawer root reset. Tests inject fake repositories/client and never use a real Key.

- [ ] **Step 7: Run ViewModel and Compose tests**

Run both classes. Expected: PASS.

- [ ] **Step 8: Commit model configuration UI**

```powershell
git add app/src/main/java/com/habit/app/ui/ai app/src/main/java/com/habit/app/ui/navigation app/src/main/java/com/habit/app/di app/src/test app/src/androidTest
git commit -m "feat: add AI model configuration"
```

---

### Task 6: Weekly Report Input, Prompt, Parser, and Persistence

**Files:**
- Create: `app/src/main/java/com/habit/app/domain/ai/WeeklyReportInputBuilder.kt`
- Create: `app/src/main/java/com/habit/app/domain/ai/WeeklyReportPrompt.kt`
- Create: `app/src/main/java/com/habit/app/domain/ai/WeeklyReportParser.kt`
- Create: `app/src/main/java/com/habit/app/ui/ai/AiWeeklyReportViewModel.kt`
- Modify: `app/src/main/java/com/habit/app/di/AppContainer.kt`
- Create: `app/src/test/java/com/habit/app/domain/ai/WeeklyReportInputBuilderTest.kt`
- Create: `app/src/test/java/com/habit/app/domain/ai/WeeklyReportParserTest.kt`
- Create: `app/src/test/java/com/habit/app/ui/ai/AiWeeklyReportViewModelTest.kt`

**Interfaces:**
- Consumes: calendar/category/diet repositories, text-bound tested model, secret store, completion client, weekly report repository.
- Produces: `WeeklyReportInput`, strict request JSON text, validated `AiWeeklyReportDraft`, and preview/save/replace state.

```kotlin
data class WeeklyHabitInput(
    val name: String,
    val categoryName: String,
    val applicableDays: Int,
    val completedDays: Int,
    val dailyCompleted: List<Boolean>,
)

data class WeeklyDietInput(
    val name: String,
    val type: String,
    val subtype: String,
    val epochDay: Long,
    val beijingHour: Int,
    val finalCalories: Int?,
    val calorieSource: String,
)

data class WeeklyReportInput(
    val startEpochDay: Long,
    val endEpochDay: Long,
    val habits: List<WeeklyHabitInput>,
    val dietRecords: List<WeeklyDietInput>,
    val coverage: WeeklyReportCoverage,
    val json: String,
)
```

- [ ] **Step 1: Write privacy and missing-vs-zero input tests**

```kotlin
val json = buildWeeklyReportInput(fixtures).json
assertFalse(json.contains("relativePath"))
assertFalse(json.contains("private note"))
assertFalse(json.contains("createdAt"))
assertTrue(json.contains("\"knownCalories\":0"))
assertTrue(json.contains("\"calorieMissing\":true"))
```

Test zero habit+diet data produces `NoAnalyzableData` and a one-module week remains valid with explicit zero coverage.

- [ ] **Step 2: Write strict parser tests**

Accept exactly one object with title, overview, habit analysis, diet analysis, correlation finding, exactly 3 suggestions, and cautions. Reject Markdown fences after extracting only when the fenced content is a single JSON object; reject missing fields, wrong types, 2/4 suggestions, and all-blank content.

- [ ] **Step 3: Run the new JVM tests and verify they fail**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.habit.app.domain.ai.WeeklyReport*" --tests "com.habit.app.ui.ai.AiWeeklyReportViewModelTest"
```

Expected: compile failure.

- [ ] **Step 4: Implement the local weekly input builder**

Resolve the previous complete week via Task 1. For each date call `calendarRepository.observeDay(date, today).first()`, then call `categoryRepository.observeAll().first()` and `dietRepository.observeRange(start.toEpochDay(), endInclusive.toEpochDay()).first()`. Serialize only the allowed fields. Compute completion rate and diet record days locally so the UI never trusts AI for metrics.

- [ ] **Step 5: Implement prompt and parser**

The system prompt must forbid invented facts, diagnose only observable patterns, mention missing coverage, and require Chinese JSON. Temperature is 0.2. The parser returns `AiWeeklyReportDraft`; it never passes raw response text to UI.

- [ ] **Step 6: Implement the generation state machine**

States: `LoadingLocalData`, `ReadyToGenerate`, `Generating`, `Preview`, `Saving`, `Saved`, and recoverable `Error`. Generation checks binding, matching text capability, passed test, and decryptable Key. Save upserts by `startEpochDay`; replacement requires a confirmation event.

- [ ] **Step 7: Run weekly report unit tests**

Run the Step 3 command. Expected: PASS.

- [ ] **Step 8: Commit report core**

```powershell
git add app/src/main/java/com/habit/app/domain/ai app/src/main/java/com/habit/app/ui/ai/AiWeeklyReportViewModel.kt app/src/main/java/com/habit/app/di app/src/test
git commit -m "feat: generate structured AI weekly reports"
```

---

### Task 7: Weekly Report Screens, History, and Workbench Card

**Files:**
- Create: `app/src/main/java/com/habit/app/ui/ai/AiWeeklyReportScreen.kt`
- Create: `app/src/main/java/com/habit/app/ui/ai/AiReportHistoryScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitNavHost.kt`
- Modify: `app/src/main/java/com/habit/app/ui/workbench/WorkbenchViewModel.kt`
- Modify: `app/src/main/java/com/habit/app/ui/workbench/WorkbenchScreen.kt`
- Create: `app/src/androidTest/java/com/habit/app/ui/ai/AiWeeklyReportFlowTest.kt`
- Modify: `app/src/test/java/com/habit/app/ui/workbench/WorkbenchViewModelTest.kt`

**Interfaces:**
- Consumes: Task 6 ViewModel/repository and current global components.
- Produces: report root/history/detail routes and workbench `WeeklyInsightSummary`.

- [ ] **Step 1: Write workbench state tests**

Assert the card states `NEEDS_MODEL`, `READY_TO_GENERATE`, and `SAVED`, and that the date range is previous Monday–Sunday in Beijing time.

- [ ] **Step 2: Write Compose report flow tests**

With fake AI results, verify generate confirmation, preview cards, exactly 3 numbered suggestions, save, history descending order, read-only reopen, and confirmed same-week replacement. Verify error keeps the old saved report.

- [ ] **Step 3: Run the tests and verify UI symbols/routes are missing**

Run the two affected unit/instrumentation classes. Expected: FAIL.

- [ ] **Step 4: Implement report/history screens**

Follow the approved preview order: period/title/overview; local metrics; habit; diet; correlation; 3 suggestions; cautions/coverage/disclaimer. Use white cards, sky-blue emphasis, 48 dp controls, standard top bars, loading overlay, and no chat bubbles.

- [ ] **Step 5: Integrate workbench card**

Add only a summary Flow dependency to `WorkbenchViewModel`. The card navigates to generation or saved detail and never builds prompts itself.

- [ ] **Step 6: Run weekly UI and adaptive layout tests**

Run `AiWeeklyReportFlowTest`, `WorkbenchViewModelTest`, and existing `AdaptivePrimaryActionsTest`. Expected: PASS.

- [ ] **Step 7: Commit report UI**

```powershell
git add app/src/main/java/com/habit/app/ui/ai app/src/main/java/com/habit/app/ui/workbench app/src/main/java/com/habit/app/ui/navigation app/src/test app/src/androidTest
git commit -m "feat: add AI weekly report experience"
```

---

### Task 8: Calorie Estimate Parser and Meal Editor State

**Files:**
- Create: `app/src/main/java/com/habit/app/domain/ai/CalorieEstimatePrompt.kt`
- Create: `app/src/main/java/com/habit/app/domain/ai/CalorieEstimateParser.kt`
- Modify: `app/src/main/java/com/habit/app/domain/model/DietModels.kt`
- Modify: `app/src/main/java/com/habit/app/domain/repository/DietRepository.kt`
- Modify: `app/src/main/java/com/habit/app/data/repository/RoomDietRepository.kt`
- Modify: `app/src/main/java/com/habit/app/data/local/DietDao.kt`
- Modify: `app/src/main/java/com/habit/app/data/local/EntityMappers.kt`
- Modify: `app/src/main/java/com/habit/app/ui/diet/DietEditorViewModel.kt`
- Create: `app/src/test/java/com/habit/app/domain/ai/CalorieEstimateParserTest.kt`
- Modify: `app/src/test/java/com/habit/app/ui/diet/DietEditorViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/habit/app/data/repository/RoomDietRepositoryTest.kt`

**Interfaces:**
- Consumes: vision-bound tested model, secret store, completion client, image preparer.
- Produces: draft-attached `AiCalorieEstimateDraft`, preview state, adoption source, and transactional record+estimate save.

- [ ] **Step 1: Write parser boundary tests**

Accept non-negative items, ordered ranges, non-empty food list, suggestion inside total range, and total maximum 10000. Reject negative values, min > max, suggestion outside range, blank items, and total >10000.

- [ ] **Step 2: Write editor adoption tests**

```kotlin
viewModel.adoptEstimate(validEstimate, adoptedKcal = validEstimate.suggestedKcal)
assertEquals(CalorieSource.AI_ESTIMATE, viewModel.state.value.calorieSource)
viewModel.adoptEstimate(validEstimate, adoptedKcal = validEstimate.suggestedKcal + 20)
assertEquals(CalorieSource.MANUAL, viewModel.state.value.calorieSource)
assertTrue(viewModel.state.value.aiEstimate!!.wasModified)
```

Also verify cancel does not mutate calories and saving a template does not copy AI evidence.

- [ ] **Step 3: Run parser/editor tests and verify they fail**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.habit.app.domain.ai.CalorieEstimateParserTest" --tests "com.habit.app.ui.diet.DietEditorViewModelTest"
```

Expected: FAIL.

- [ ] **Step 4: Extend meal domain without affecting beverages/templates**

Add optional `aiCalorieEstimate: AiCalorieEstimateDraft?` to `MealRecordDraft` and optional persisted `aiCalorieEstimate` to `MealRecord`. `toRepeatDraft` and template conversion must set it to null. Existing call sites compile via default null.

- [ ] **Step 5: Implement prompt/parser and generation action**

Generation is available only for `MEAL`, 1–3 readable selected photos, matching vision binding/test, and decryptable Key. The request includes the current description and prepared images only. Always close prepared images in `finally`.

- [ ] **Step 6: Save meal and evidence in the same transaction**

After inserting/updating a meal and its regular children, delete any old estimate and insert the new attached estimate inside the existing `database.withTransaction`. Manual calorie edits without a new estimate retain existing persisted evidence but set `wasModified`/`adoptedKcal` to the saved final value.

- [ ] **Step 7: Run unit and Room tests**

Run the Step 3 command plus `RoomDietRepositoryTest`. Expected: PASS.

- [ ] **Step 8: Commit calorie estimate core**

```powershell
git add app/src/main/java/com/habit/app/domain app/src/main/java/com/habit/app/data app/src/main/java/com/habit/app/ui/diet/DietEditorViewModel.kt app/src/test app/src/androidTest
git commit -m "feat: persist AI meal calorie estimates"
```

---

### Task 9: Calorie Estimate UI and Refined Diet Details

**Files:**
- Create: `app/src/main/java/com/habit/app/ui/diet/AiCalorieEstimateSheet.kt`
- Modify: `app/src/main/java/com/habit/app/ui/diet/DietEditorScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/diet/DietRecordDetailViewModel.kt`
- Rewrite: `app/src/main/java/com/habit/app/ui/diet/DietRecordDetailScreen.kt`
- Create: `app/src/androidTest/java/com/habit/app/ui/diet/AiCalorieEstimateFlowTest.kt`
- Modify: `app/src/androidTest/java/com/habit/app/ui/diet/DietBrowseFlowTest.kt`
- Modify: `app/src/test/java/com/habit/app/ui/diet/DietRecordDetailViewModelTest.kt`

**Interfaces:**
- Consumes: Task 8 preview/adopt state and persisted estimate.
- Produces: approved photo-first detail and AI estimate confirmation UI.

- [ ] **Step 1: Write Compose tests for visibility and non-destructive behavior**

Assert meal-with-photo shows `AI 估算热量`, beverage never shows it, no-photo action is disabled with guidance, cancelling preview preserves existing final calories, adopting fills only calories, and save/reopen shows evidence.

- [ ] **Step 2: Write detail hierarchy tests**

Assert tags for hero photo, title/type/time, fact grid, user food card, AI evidence card, adopted value label, note, and repeat. Verify no AI card for records without evidence and photo click opens `diet_photo_preview`.

- [ ] **Step 3: Run UI tests and verify they fail**

Run the two diet instrumentation classes. Expected: FAIL.

- [ ] **Step 4: Build the estimate confirmation sheet**

Show selected photo count, recognized foods/portions/item ranges, total range, editable adopted kcal, accuracy note, privacy line, cancel, and 48 dp `采用 X kcal`. Display busy/cancel state and mapped Chinese errors.

- [ ] **Step 5: Rebuild detail content with focused components**

Split into `DietDetailHero`, `DietDetailHeader`, `DietFactGrid`, `UserFoodCard`, `BeverageAttributeCard`, `AiEstimateEvidenceCard`, and `DietNoteCard` in the same file or focused sibling files. Hero uses sampled decoding and existing full-screen preview. Keep the small global “编辑” action.

- [ ] **Step 6: Run diet UI, photo preview, and adaptive tests**

Run `AiCalorieEstimateFlowTest`, `DietBrowseFlowTest`, `AdaptiveDeviceConfigurationTest`, and `AccessibilityControlsTest`. Expected: PASS.

- [ ] **Step 7: Commit the approved UI**

```powershell
git add app/src/main/java/com/habit/app/ui/diet app/src/test app/src/androidTest
git commit -m "feat: add AI calorie UI and refined diet details"
```

---

### Task 10: Backup Schema 5 and Secret-Safe Import

**Files:**
- Modify: `app/src/main/java/com/habit/app/data/backup/BackupModels.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/HabitBackupCodec.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/BackupMerger.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/RoomBackupRepository.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/HabitBackupService.kt`
- Modify: `app/src/main/java/com/habit/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/habit/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/test/java/com/habit/app/data/backup/HabitBackupCodecTest.kt`
- Modify: `app/src/test/java/com/habit/app/data/backup/BackupMergerTest.kt`
- Modify: `app/src/test/java/com/habit/app/data/backup/HabitBackupArchiveTest.kt`
- Modify: `app/src/test/java/com/habit/app/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: Room AI entities/repositories and `AiSecretStore`.
- Produces: schema 5 export/import/preview with no Key material.

- [ ] **Step 1: Write schema 4 normalization and schema 5 round-trip tests**

Schema 4 JSON must decode with empty AI lists. Schema 5 must round-trip model configs, bindings, reports, and estimates. Assert encoded JSON lacks `apiKey`, a sentinel Key string, `Authorization`, and ciphertext preferences.

- [ ] **Step 2: Write merge security tests**

Test model identity by `externalId`, Long ID remapping for different external IDs, `updatedAt` winner for the same external ID, weekly report winner by week, estimate remapping by meal, and invalid binding nulling. Verify every imported touched external ID is returned as `secretIdsToClear`.

- [ ] **Step 3: Run backup tests and verify schema 5 expectations fail**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.habit.app.data.backup.*" --tests "com.habit.app.ui.settings.SettingsViewModelTest"
```

Expected: FAIL.

- [ ] **Step 4: Add exact backup DTOs and codec fields**

Set `HABIT_BACKUP_SCHEMA_VERSION = 5`. Add `BackupAiModelConfig`, `BackupAiFeatureBinding`, `BackupAiWeeklyReport`, and `BackupAiCalorieEstimate`. No DTO has an API Key field. Decode missing arrays as empty for schemas 1–4.

- [ ] **Step 5: Implement safe merge/replace sequencing**

Before database import commits, compute affected `externalId` values. After successful Room transaction, clear those keys; on full replace call `clearAll()`. If Room import fails, do not clear keys. Imported model test status becomes `NEEDS_KEY` and test message becomes blank.

- [ ] **Step 6: Update backup preview and copy**

Preview text adds `N 个模型配置 · N 份 AI 周报 · N 条热量依据` followed by `API Key 不包含在备份中`.

- [ ] **Step 7: Run all backup tests**

Run the Step 3 command. Expected: PASS.

- [ ] **Step 8: Commit backup schema 5**

```powershell
git add app/src/main/java/com/habit/app/data/backup app/src/main/java/com/habit/app/ui/settings app/src/test
git commit -m "feat: back up AI data without secrets"
```

---

### Task 11: Versioning, Documentation, Full Regression, and Release APK

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `docs/INSTALL.md`
- Modify: `docs/TESTING.md`
- Modify: `app/src/test/java/com/habit/app/ProjectSmokeTest.kt`
- Create: `artifacts/Habit-0.6.0-release.apk` (ignored binary deliverable)

**Interfaces:**
- Consumes: all previous tasks.
- Produces: verified 0.6.0 signed APK and delivery evidence.

- [ ] **Step 1: Update the smoke test first**

Expect `versionCode = 12`, `versionName = "0.6.0"`, Room version 5, backup schema 5, `MIGRATION_4_5`, INTERNET permission, and stable signing config.

- [ ] **Step 2: Run smoke test and verify old metadata fails**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.habit.app.ProjectSmokeTest"
```

Expected: FAIL on 0.5.1/11/Room 4/schema 4.

- [ ] **Step 3: Set release metadata and document user-visible behavior**

Set 0.6.0/12. README explains optional AI, model profiles/bindings, weekly structured-data privacy, photo-only explicit calorie calls, Key non-backup, and refined detail. INSTALL/TESTING include 0.5.1 overwrite preservation, Key setup, fake/test endpoint flows, backup restore requiring Key re-entry, and AI-off regression.

- [ ] **Step 4: Run full JVM, Lint, and Android test compilation**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug compileDebugAndroidTestKotlin
```

Expected: PASS with zero unit failures and zero Lint errors.

- [ ] **Step 5: Run the uninterrupted device suite**

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected: every current instrumentation test passes on `Small_Phone_API_35`.

- [ ] **Step 6: Build release from a clean committed tree**

Commit all source/docs, verify `git status --short` is empty, then run:

```powershell
.\gradlew.bat assembleRelease
Copy-Item app\build\outputs\apk\release\app-release.apk artifacts\Habit-0.6.0-release.apk -Force
```

- [ ] **Step 7: Verify exact artifact identity and signature**

Verify source/artifact SHA-256 match, `aapt2 dump badging` reports package `com.habit.app`, code 12, name 0.6.0, min 23, target 36, and `apksigner verify --verbose --print-certs` reports v1/v2 true and the same certificate SHA-256 as 0.5.1.

- [ ] **Step 8: Perform overwrite migration and cold-start checks**

Install the existing 0.5.1 APK, seed habit/diet/photo/backup data, install 0.6.0 with `adb install -r`, verify data remains, AI pages show unconfigured state, configure a fake endpoint, and cold-launch with no `FATAL EXCEPTION` in logcat.

- [ ] **Step 9: Commit and push release source**

```powershell
git add app README.md docs gradle
git commit -m "release: deliver Habit 0.6.0 AI insights"
git push github HEAD:main
```

- [ ] **Step 10: Deliver the APK**

Report the absolute artifact path, byte size, full SHA-256, version metadata, signing fingerprint, unit/device test totals, overwrite result, cold-start result, and the release commit.
