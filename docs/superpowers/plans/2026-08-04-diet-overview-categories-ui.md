# Diet Overview, Categories, and UI Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver Habit 0.5.0 with backward-compatible diet categories, a read-only diet detail flow, overview/photo browsing, and the approved habit/calendar/workbench UI refinements.

**Architecture:** Keep the stable habit category table unchanged and add a separate scoped diet-category table. Expose both repositories through one category-management ViewModel, add a dedicated diet-detail route, and split reusable UI units for category chips, diet thumbnails, and the recent-week timeline. Upgrade Room and backup schemas explicitly so 0.4.1 local data and schema-v3 backups migrate without loss.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation Compose, Room 2.x, Kotlin Flow/coroutines, kotlinx.serialization, JUnit 4, AndroidX Room migration testing, Compose UI testing, Gradle/AGP 9.3.0, Java 17.

## Global Constraints

- Android `minSdk = 23`, `targetSdk = 36`, Java/Kotlin target 17.
- Use the existing stable signing configuration; do not create or replace a keystore.
- All user-visible date/time behavior uses `Asia/Shanghai`; persisted instants remain epoch milliseconds and dates remain epoch days.
- Room upgrades use explicit migrations; never use destructive migration.
- Preserve all existing habit, check-in, diet, photo, template, creation-time, and update-time data.
- Keep the pure side-drawer navigation model; do not add bottom navigation.
- Keep the low-saturation sky-blue, near-white visual system.
- Every new or visibly changed screen follows the approved browser preview.
- Backup merge conflicts continue to prefer the newer record by `updatedAt`.
- Implement tests before production code and commit after each independently testable task.
- Use `D:\MySoftware\gradle\cache` as `GRADLE_USER_HOME` for verification.

---

## File Structure

New focused files:

- `app/src/main/java/com/habit/app/domain/model/DietCategoryModels.kt` — diet-category domain values and fixed preset definitions.
- `app/src/main/java/com/habit/app/domain/repository/DietCategoryRepository.kt` — scoped category CRUD contract.
- `app/src/main/java/com/habit/app/data/local/DietCategoryDao.kt` — Room queries and usage counts.
- `app/src/main/java/com/habit/app/data/repository/RoomDietCategoryRepository.kt` — validation, transactions, migration-on-delete.
- `app/src/main/java/com/habit/app/ui/components/CategoryChipFlow.kt` — reusable horizontal wrapping category selector.
- `app/src/main/java/com/habit/app/ui/diet/DietRecordThumbnail.kt` — photo preview with Emoji fallback.
- `app/src/main/java/com/habit/app/ui/diet/DietRecordDetailScreen.kt` — read-only detail UI.
- `app/src/main/java/com/habit/app/ui/diet/DietRecordDetailViewModel.kt` — read-only record state.
- `app/src/main/java/com/habit/app/ui/workbench/RecentWeekTimeline.kt` — approved two-position timeline renderer.
- `app/src/test/java/com/habit/app/ui/workbench/RecentWeekDisplayTest.kt` — pure count-display policy tests.
- `app/src/test/java/com/habit/app/ui/diet/DietRecordDetailViewModelTest.kt` — detail loading tests.
- `app/src/androidTest/java/com/habit/app/data/repository/RoomDietCategoryRepositoryTest.kt` — category transaction tests.
- `app/src/androidTest/java/com/habit/app/ui/diet/DietBrowseFlowTest.kt` — overview/detail navigation tests.

Existing files retain their current responsibilities and are modified only where listed by a task.

---

### Task 1: Diet Category Domain and Room 3→4 Migration

**Files:**
- Create: `app/src/main/java/com/habit/app/domain/model/DietCategoryModels.kt`
- Create: `app/src/main/java/com/habit/app/data/local/DietCategoryDao.kt`
- Modify: `app/src/main/java/com/habit/app/data/local/Entities.kt`
- Modify: `app/src/main/java/com/habit/app/data/local/HabitDatabase.kt`
- Modify: `app/src/main/java/com/habit/app/data/local/EntityMappers.kt`
- Modify: `app/src/main/java/com/habit/app/di/AppContainer.kt`
- Modify: `app/src/main/java/com/habit/app/domain/model/DietModels.kt`
- Test: `app/src/androidTest/java/com/habit/app/data/local/HabitDatabaseMigrationTest.kt`
- Generate: `app/schemas/com.habit.app.data.local.HabitDatabase/4.json`

**Interfaces:**
- Produces: `DietCategoryScope`, `DietCategory`, `DIET_CATEGORY_PRESETS`, `DietCategoryEntity`, `DietCategoryDao`.
- Produces: `MealRecord.dietCategoryId: Long`, `MealRecordDraft.dietCategoryId: Long`, `DietTemplate` drafts carrying the same ID.
- Produces: `MIGRATION_3_4` and `HabitDatabase.dietCategoryDao()`.

- [ ] **Step 1: Write the failing Room migration test**

Extend `HabitDatabaseMigrationTest` with a v3 fixture containing one meal, one coffee beverage, two templates, photos, and timestamps, then assert v4 category mapping:

```kotlin
@Test
fun migrate3To4_preservesDietDataAndMapsCategories() {
    helper.createDatabase(DB_NAME, 3).apply {
        execSQL("INSERT INTO meal_records VALUES (1,'MEAL','DINNER',1000,1,'面',500,500,'MANUAL','',10,20)")
        execSQL("INSERT INTO meal_records VALUES (2,'BEVERAGE',NULL,2000,1,'咖啡',NULL,NULL,'NONE','',11,21)")
        execSQL("INSERT INTO beverage_details VALUES (2,'COFFEE','店','美式','大杯','热','','','1')")
        close()
    }
    helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_3_4).use { db ->
        db.query("SELECT COUNT(*) FROM meal_records").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
        db.query("SELECT name FROM diet_categories WHERE id=(SELECT dietCategoryId FROM meal_records WHERE id=1)").use {
            assertTrue(it.moveToFirst()); assertEquals("其他餐食", it.getString(0))
        }
        db.query("SELECT name FROM diet_categories WHERE id=(SELECT dietCategoryId FROM meal_records WHERE id=2)").use {
            assertTrue(it.moveToFirst()); assertEquals("咖啡", it.getString(0))
        }
    }
}
```

- [ ] **Step 2: Run the migration test and verify it fails**

Run:

```powershell
$env:GRADLE_USER_HOME='D:\MySoftware\gradle\cache'
.\gradlew.bat --no-daemon --console=plain connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.local.HabitDatabaseMigrationTest
```

Expected: compilation fails because `MIGRATION_3_4` and schema 4 do not exist.

- [ ] **Step 3: Add the domain types and entities**

Create the domain model exactly around stable IDs and scopes:

```kotlin
enum class DietCategoryScope { MEAL, BEVERAGE }

data class DietCategory(
    val id: Long,
    val scope: DietCategoryScope,
    val name: String,
    val isPreset: Boolean,
    val isHidden: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class DietCategoryPreset(val id: Long, val scope: DietCategoryScope, val name: String)

val DIET_CATEGORY_PRESETS = listOf(
    DietCategoryPreset(1, DietCategoryScope.MEAL, "家常菜"),
    DietCategoryPreset(2, DietCategoryScope.MEAL, "快餐"),
    DietCategoryPreset(3, DietCategoryScope.MEAL, "零食"),
    DietCategoryPreset(4, DietCategoryScope.MEAL, "其他餐食"),
    DietCategoryPreset(5, DietCategoryScope.BEVERAGE, "咖啡"),
    DietCategoryPreset(6, DietCategoryScope.BEVERAGE, "奶茶"),
    DietCategoryPreset(7, DietCategoryScope.BEVERAGE, "茶"),
    DietCategoryPreset(8, DietCategoryScope.BEVERAGE, "果饮"),
    DietCategoryPreset(9, DietCategoryScope.BEVERAGE, "乳饮"),
    DietCategoryPreset(10, DietCategoryScope.BEVERAGE, "其他饮品"),
)
```

Add `DietCategoryEntity` with the same fields and indices on `scope` and `(scope, sortOrder)`. Add non-null `dietCategoryId: Long` to `MealRecordEntity` and `DietTemplateEntity`; keep the existing beverage `category` columns as legacy storage for schema compatibility, but stop using them as the domain category source.

- [ ] **Step 4: Add DAO and migration implementation**

Create DAO operations used by later tasks:

```kotlin
@Dao
interface DietCategoryDao {
    @Query("SELECT * FROM diet_categories WHERE scope=:scope ORDER BY sortOrder,id")
    fun observeAll(scope: String): Flow<List<DietCategoryEntity>>

    @Query("SELECT * FROM diet_categories WHERE scope=:scope AND isHidden=0 ORDER BY sortOrder,id")
    fun observeVisible(scope: String): Flow<List<DietCategoryEntity>>

    @Query("SELECT * FROM diet_categories WHERE id=:id") suspend fun get(id: Long): DietCategoryEntity?
    @Insert suspend fun insert(entity: DietCategoryEntity): Long
    @Update suspend fun update(entity: DietCategoryEntity)
    @Query("DELETE FROM diet_categories WHERE id=:id") suspend fun delete(id: Long)
    @Query("UPDATE meal_records SET dietCategoryId=:target WHERE dietCategoryId=:source") suspend fun moveRecords(source: Long, target: Long)
    @Query("UPDATE diet_templates SET dietCategoryId=:target WHERE dietCategoryId=:source") suspend fun moveTemplates(source: Long, target: Long)
}
```

Implement `MIGRATION_3_4` to create `diet_categories`, insert IDs 1–10, add `dietCategoryId INTEGER NOT NULL DEFAULT 0` to both diet tables, map `MEAL` rows/templates to ID 4, map legacy beverage enum names to IDs 5–10, and ensure no row remains at ID 0. Increase Room version to 4, add the entity/DAO, register `MIGRATION_3_4`, and seed the same fixed presets for fresh installs.

- [ ] **Step 5: Run migration and compile tests**

Run:

```powershell
$env:GRADLE_USER_HOME='D:\MySoftware\gradle\cache'
.\gradlew.bat --no-daemon --console=plain compileDebugKotlin compileDebugAndroidTestKotlin
```

Expected: BUILD SUCCESSFUL and generated Room schema 4 contains `diet_categories` and both `dietCategoryId` columns.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main app/src/androidTest app/schemas
git commit -m "feat: add backward-compatible diet categories"
```

---

### Task 2: Diet Category Repository and Transaction Rules

**Files:**
- Create: `app/src/main/java/com/habit/app/domain/repository/DietCategoryRepository.kt`
- Create: `app/src/main/java/com/habit/app/data/repository/RoomDietCategoryRepository.kt`
- Modify: `app/src/main/java/com/habit/app/data/local/DietCategoryDao.kt`
- Modify: `app/src/main/java/com/habit/app/di/AppContainer.kt`
- Test: `app/src/androidTest/java/com/habit/app/data/repository/RoomDietCategoryRepositoryTest.kt`

**Interfaces:**
- Consumes: `DietCategoryDao`, `DietCategoryScope`, `DietCategory` from Task 1.
- Produces: `observeAll`, `observeVisible`, `observeUsageCounts`, `create`, `rename`, `setHidden`, `migrateAndDelete`.

- [ ] **Step 1: Write failing repository tests**

Cover preset rename, quick create, hide/restore, same-scope migration, cross-scope rejection, and last-visible rejection:

```kotlin
@Test fun migrateAndDelete_movesRecordsAndTemplatesAtomically() = runTest {
    val source = repository.create(DietCategoryScope.MEAL, "外卖")
    val target = repository.create(DietCategoryScope.MEAL, "快手餐")
    insertMealAndTemplate(categoryId = source)
    repository.migrateAndDelete(source, target)
    assertNull(dao.get(source))
    assertEquals(target, dietDao.getRecordEntity(1)!!.dietCategoryId)
    assertEquals(target, dietDao.getTemplateEntity(1)!!.dietCategoryId)
}
```

- [ ] **Step 2: Run and verify failure**

Run `\.\gradlew.bat --no-daemon --console=plain compileDebugAndroidTestKotlin` with the configured Gradle cache.

Expected: compilation fails because `DietCategoryRepository` is absent.

- [ ] **Step 3: Implement repository contract and Room transaction**

Use this public contract:

```kotlin
interface DietCategoryRepository {
    fun observeAll(scope: DietCategoryScope): Flow<List<DietCategory>>
    fun observeVisible(scope: DietCategoryScope): Flow<List<DietCategory>>
    fun observeUsageCounts(scope: DietCategoryScope): Flow<Map<Long, Int>>
    suspend fun create(scope: DietCategoryScope, name: String): Long
    suspend fun rename(id: Long, name: String)
    suspend fun setHidden(id: Long, hidden: Boolean)
    suspend fun migrateAndDelete(sourceId: Long, targetId: Long)
}
```

Normalize names with `trim()`, reject blank or case-insensitive duplicates within the same scope, allow preset edits, and perform move-records, move-templates, and delete inside `database.withTransaction`. Reject deletion of the last visible category and cross-scope targets with Chinese error messages suitable for UI display.

- [ ] **Step 4: Run repository tests**

Run the new instrumentation test class. Expected: all category CRUD and transaction cases pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main app/src/androidTest
git commit -m "feat: manage meal and beverage categories"
```

---

### Task 3: Backup Schema 4 and Legacy Import Normalization

**Files:**
- Modify: `app/src/main/java/com/habit/app/data/backup/BackupModels.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/HabitBackupCodec.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/BackupMerger.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/RoomBackupRepository.kt`
- Test: `app/src/test/java/com/habit/app/data/backup/HabitBackupCodecTest.kt`
- Test: `app/src/test/java/com/habit/app/data/backup/BackupMergerTest.kt`
- Test: `app/src/androidTest/java/com/habit/app/data/local/HabitDatabaseMigrationTest.kt`

**Interfaces:**
- Consumes: fixed preset IDs 1–10 and `dietCategoryId` from Task 1.
- Produces: backup schema 4 with `dietCategories`, record/template category IDs, and schema-3 normalization.

- [ ] **Step 1: Write failing codec and merger tests**

Add tests proving schema 3 JSON still decodes and normalizes, schema 4 round-trips custom categories, and merge selects newer category data:

```kotlin
@Test fun schema3CoffeeRecord_normalizesToCoffeeCategory() {
    val decoded = HabitBackupCodec.decode(schema3Json)
    val normalized = decoded.normalizeDietCategories()
    assertEquals(5L, normalized.mealRecords.single { it.recordType == "BEVERAGE" }.dietCategoryId)
}

@Test fun mergeDietCategory_newerNameWins() {
    val result = BackupMerger.merge(localWithCategory(updatedAt = 10), incomingWithCategory(updatedAt = 20))
    assertEquals("手冲咖啡", result.dietCategories.single { it.id == 5L }.name)
}
```

- [ ] **Step 2: Run unit tests and verify failure**

Run `\.\gradlew.bat --no-daemon --console=plain testDebugUnitTest --tests "*HabitBackupCodecTest" --tests "*BackupMergerTest"`.

Expected: compilation fails on the new schema members.

- [ ] **Step 3: Implement schema 4 models and normalization**

Set `HABIT_BACKUP_SCHEMA_VERSION = 4`, add:

```kotlin
data class BackupDietCategory(
    val id: Long, val scope: String, val name: String, val isPreset: Boolean,
    val isHidden: Boolean, val sortOrder: Int, val createdAt: Long, val updatedAt: Long,
)
```

Add `dietCategories: List<BackupDietCategory> = emptyList()` to `HabitBackup`, and `dietCategoryId: Long? = null` to `BackupMealRecord` and `BackupDietTemplate` so schema 1–3 JSON remains decodable. `normalizeDietCategories()` must insert fixed presets when missing, map meal records to 4, map legacy beverage strings to 5–10, map templates identically, and return non-null valid IDs before database insertion.

Update export/import and merger ordering so diet categories are written before records, referenced categories are retained, and incoming newer data wins. REPLACE deletes diet categories after dependent diet rows; MERGE normalizes both sides before merging.

- [ ] **Step 4: Run backup tests**

Run all backup unit tests. Expected: schema 1–4 compatibility and merge tests pass.

- [ ] **Step 5: Run migration/import compile check**

Run `compileDebugAndroidTestKotlin`. Expected: BUILD SUCCESSFUL with backup entity mappings updated for the added fields.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: preserve diet categories in local backups"
```

---

### Task 4: Meal Type, Category Selection, and Habit Editor Layout

**Files:**
- Create: `app/src/main/java/com/habit/app/ui/components/CategoryChipFlow.kt`
- Modify: `app/src/main/java/com/habit/app/domain/model/DietModels.kt`
- Modify: `app/src/main/java/com/habit/app/domain/stats/DietStatistics.kt`
- Modify: `app/src/main/java/com/habit/app/ui/diet/DietEditorViewModel.kt`
- Modify: `app/src/main/java/com/habit/app/ui/diet/DietEditorScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/habits/HabitEditorScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitNavHost.kt`
- Test: `app/src/test/java/com/habit/app/ui/diet/DietEditorViewModelTest.kt`
- Test: `app/src/androidTest/java/com/habit/app/ui/habits/HabitCrudFlowTest.kt`

**Interfaces:**
- Consumes: `DietCategoryRepository.observeVisible/create` from Task 2.
- Produces: `MealType.LATE_NIGHT`, required `dietCategoryId`, and reusable `CategoryChipFlow`.

- [ ] **Step 1: Write failing editor tests**

Add unit cases for the fixed order and category persistence:

```kotlin
@Test fun lateNight_isBetweenDinnerAndSnack() {
    assertEquals(
        listOf(BREAKFAST, LUNCH, DINNER, LATE_NIGHT, SNACK),
        mealTypeDisplayOrder,
    )
}

@Test fun savingMeal_preservesSelectedDietCategory() = runTest {
    viewModel.selectDietCategory(3L)
    viewModel.save {}
    assertEquals(3L, repository.lastSaved!!.dietCategoryId)
}
```

Add a Compose test asserting multiple habit category chips share a row on a phone-width device and `diet_save`/`save_habit` have bottom safe padding.

- [ ] **Step 2: Run focused tests and verify failure**

Run the editor unit test and compile the habit Android test. Expected: missing `LATE_NIGHT`, category state, and chip-flow APIs.

- [ ] **Step 3: Implement reusable flow and editor state**

Implement `CategoryChipFlow` with `FlowRow(horizontalArrangement = spacedBy(8.dp), verticalArrangement = spacedBy(8.dp))`. Expose `items`, `selectedId`, `onSelected`, `onCreate`, and `onManage` parameters so habit and diet editors share the same wrap behavior.

Add:

```kotlin
enum class MealType { BREAKFAST, LUNCH, DINNER, LATE_NIGHT, SNACK }
val mealTypeDisplayOrder = listOf(BREAKFAST, LUNCH, DINNER, LATE_NIGHT, SNACK)
```

Change visible copy from “正餐 / 加餐” to “餐食”. Load categories according to `recordType`, select the matching preset for new drafts, retain hidden current categories while editing, and make category required before save. The quick-create dialog calls `DietCategoryRepository.create`, refreshes, and selects the returned ID without resetting other fields.

Use `WindowInsets.navigationBars.asPaddingValues()` plus at least 16dp visual padding below both editor save buttons.

- [ ] **Step 4: Run focused tests**

Expected: late-night order, category persistence, flow layout, and bottom spacing tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: add categorized meal editing and late night"
```

---

### Task 5: Diet Overview Modes and Photo Thumbnails

**Files:**
- Create: `app/src/main/java/com/habit/app/ui/diet/DietRecordThumbnail.kt`
- Modify: `app/src/main/java/com/habit/app/data/local/DietDao.kt`
- Modify: `app/src/main/java/com/habit/app/domain/repository/DietRepository.kt`
- Modify: `app/src/main/java/com/habit/app/data/repository/RoomDietRepository.kt`
- Modify: `app/src/main/java/com/habit/app/ui/diet/DietDiaryViewModel.kt`
- Modify: `app/src/main/java/com/habit/app/ui/diet/DietDiaryScreen.kt`
- Test: `app/src/test/java/com/habit/app/ui/diet/DietDiaryViewModelTest.kt`
- Test: `app/src/androidTest/java/com/habit/app/ui/diet/DietBrowseFlowTest.kt`

**Interfaces:**
- Produces: `DietDiaryMode { RECENT, DAY }`, `DietDayGroup`, and `DietRepository.observeAll()`.
- Consumes: existing `DietPhotoStore.file(relativePath)` and record photos sorted by `sortOrder`.

- [ ] **Step 1: Write failing grouping and thumbnail tests**

```kotlin
@Test fun recentMode_groupsDatesDescendingAndTimesAscending() = runTest {
    repository.records.value = listOf(record(day = 2, time = 20), record(day = 3, time = 18), record(day = 3, time = 8))
    val groups = viewModel.state.first { !it.isLoading }.recentGroups
    assertEquals(listOf(3L, 2L), groups.map { it.epochDay })
    assertEquals(listOf(8L, 18L), groups.first().records.map { it.occurredAt })
}
```

Add Compose semantics assertions that a valid first photo reports “饮食照片”, while missing and failed files report the expected fallback Emoji description.

- [ ] **Step 2: Run tests and verify failure**

Expected: `DietDiaryMode`, `recentGroups`, and `observeAll()` are undefined.

- [ ] **Step 3: Implement all-record observation and grouped state**

Add DAO query:

```kotlin
@Transaction
@Query("SELECT * FROM meal_records ORDER BY recordEpochDay DESC, occurredAt ASC, id ASC")
fun observeAllRecords(): Flow<List<MealRecordWithDetails>>
```

Expose `DietRepository.observeAll()`. Keep `selectedDate` and a `MutableStateFlow(DietDiaryMode.RECENT)` in the ViewModel. Build groups with `groupBy(recordEpochDay)`, sort group keys descending, and sort each group by `occurredAt` then `id`.

- [ ] **Step 4: Implement approved diary UI and thumbnail fallback**

Use a two-segment selector labeled “最近记录” and “按日查看”. In recent mode render date headers and records; in day mode render the existing week selector and daily summary. `DietRecordThumbnail` attempts the first sorted photo and invokes `onDecodeError`/fallback without throwing:

```kotlin
val first = record.photos.minByOrNull(DietPhoto::sortOrder)
if (first != null && photoStore.file(first.relativePath).isFile) {
    DietPhotoThumbnail(path = photoStore.file(first.relativePath).path, onRemove = null)
} else {
    Text(defaultDietEmoji(record))
}
```

Make the whole record card open the detail route. Keep “再记一次” as a separate action. Center both empty states.

- [ ] **Step 5: Run focused tests**

Expected: grouping, mode switch, photo, fallback, and empty-state tests pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: add photo-first diet overview"
```

---

### Task 6: Read-Only Diet Detail and Navigation

**Files:**
- Create: `app/src/main/java/com/habit/app/ui/diet/DietRecordDetailViewModel.kt`
- Create: `app/src/main/java/com/habit/app/ui/diet/DietRecordDetailScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitDestination.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitNavHost.kt`
- Modify: `app/src/main/java/com/habit/app/ui/diet/DietDiaryScreen.kt`
- Test: `app/src/test/java/com/habit/app/ui/diet/DietRecordDetailViewModelTest.kt`
- Test: `app/src/test/java/com/habit/app/ui/navigation/NavigationPolicyTest.kt`
- Test: `app/src/androidTest/java/com/habit/app/ui/diet/DietBrowseFlowTest.kt`

**Interfaces:**
- Consumes: `DietRepository.observeRecord(id)` and `DietPhotoStore`.
- Produces: `HabitDestination.DietDetail.route(id)`, `DietRecordDetailUiState`, detail-to-editor navigation.

- [ ] **Step 1: Write failing detail state and navigation tests**

```kotlin
@Test fun missingRecord_exposesNotFoundState() = runTest {
    repository.record.value = null
    assertTrue(viewModel.state.first { !it.loading }.notFound)
}

@Test fun detailRoute_isDistinctFromEditorRoute() {
    assertEquals("diet/42", HabitDestination.DietDetail.route(42))
    assertNotEquals(HabitDestination.DietEditor.route(42), HabitDestination.DietDetail.route(42))
}
```

Compose flow: tap `diet_record_42`, assert `diet_detail_screen` exists and `diet_save` does not; tap `diet_detail_edit`, assert editor appears.

- [ ] **Step 2: Run tests and verify failure**

Expected: detail route and ViewModel are absent; current navigation opens editor directly.

- [ ] **Step 3: Implement detail state and screen**

Use:

```kotlin
data class DietRecordDetailUiState(
    val loading: Boolean = true,
    val record: MealRecord? = null,
    val categoryName: String = "",
    val notFound: Boolean = false,
)
```

Render only nonblank/non-null fields. Keep meal and beverage sections mutually exclusive. Render photos in sort order, a small top-right “编辑” action, and a content-area “再记一次” action. Do not include editable controls, delete-photo controls, type chips, or template actions.

- [ ] **Step 4: Wire navigation and post-save behavior**

Add `DietDetail` to destinations and NavHost. Diary opens detail; detail edit pushes `DietEditor.route(id)`; editor save pops to detail, which refreshes from Flow. A missing/deleted record shows a centered message and back action.

- [ ] **Step 5: Run tests**

Expected: state, route, view-only, edit transition, and missing-record tests pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: add read-only diet record details"
```

---

### Task 7: Unified Habit, Meal, and Beverage Category Management

**Files:**
- Modify: `app/src/main/java/com/habit/app/domain/repository/CategoryRepository.kt`
- Modify: `app/src/main/java/com/habit/app/data/repository/RoomCategoryRepository.kt`
- Modify: `app/src/main/java/com/habit/app/data/local/CategoryDao.kt`
- Modify: `app/src/main/java/com/habit/app/ui/categories/CategoryViewModel.kt`
- Modify: `app/src/main/java/com/habit/app/ui/categories/CategoryScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitNavHost.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitDestination.kt`
- Test: `app/src/test/java/com/habit/app/ui/categories/CategoryViewModelTest.kt`
- Test: `app/src/androidTest/java/com/habit/app/ui/categories/CategoryAdaptiveLayoutTest.kt`
- Test: `app/src/androidTest/java/com/habit/app/data/repository/RoomCategoryRepositoryTest.kt`

**Interfaces:**
- Consumes: `CategoryRepository` and `DietCategoryRepository` CRUD APIs.
- Produces: `CategorySection { HABIT, MEAL, BEVERAGE }` and one unified category screen.

- [ ] **Step 1: Write failing unified-state tests**

```kotlin
@Test fun selectingBeverage_loadsOnlyBeverageCategories() = runTest {
    viewModel.selectSection(CategorySection.BEVERAGE)
    val state = viewModel.state.first { it.section == CategorySection.BEVERAGE }
    assertTrue(state.items.all { it.scope == CategorySection.BEVERAGE })
}

@Test fun presetCategory_canBeRenamed() = runTest {
    viewModel.rename(CategorySection.MEAL, presetId, "简餐") {}
    assertEquals("简餐", dietCategories.get(presetId)!!.name)
}
```

Update the existing habit-category repository test so presets can rename/delete under the same migration safeguards instead of being hide-only, and custom categories can also hide/restore.

- [ ] **Step 2: Run tests and verify failure**

Expected: no category section and preset mutation remains blocked by current UI/repository logic.

- [ ] **Step 3: Generalize the habit-category repository**

Replace preset-only hiding with the common operation:

```kotlin
interface CategoryRepository {
    fun observeVisible(): Flow<List<Category>>
    fun observeAll(): Flow<List<Category>>
    suspend fun create(name: String): Long
    suspend fun rename(id: Long, name: String)
    suspend fun setHidden(id: Long, hidden: Boolean)
    suspend fun migrateAndDelete(sourceId: Long, targetId: Long)
}
```

Remove the `require(!source.isPreset)` deletion restriction. Before hiding or deleting, require that at least one other visible habit category remains; before deletion require a different visible target. Keep habit reassignment and deletion in `CategoryDao.reassignHabitsAndDeleteCustomCategory`, renaming the DAO transaction to `reassignHabitsAndDeleteCategory` because it now accepts presets as well.

- [ ] **Step 4: Implement unified ViewModel**

Model normalized rows without merging persistence types:

```kotlin
enum class CategorySection { HABIT, MEAL, BEVERAGE }
data class ManagedCategoryItem(
    val id: Long, val section: CategorySection, val name: String,
    val isPreset: Boolean, val isHidden: Boolean, val usageCount: Int,
)
```

Route create/rename/hide/delete to the correct repository. For used categories require a same-section target. Convert repository exceptions into a visible `message` and never close the dialog on failure.

- [ ] **Step 5: Implement the three-tab management UI**

Use top segments “习惯 / 餐食 / 饮品”, a common `＋` top action, “正在使用” and “已隐藏” sections, and edit dialogs that expose rename, hide/restore, and delete for presets and custom categories. The delete dialog lists only valid same-section visible targets.

Ensure inline editor “管理分类” navigation returns to the editor without clearing its ViewModel state.

- [ ] **Step 6: Run focused tests**

Expected: all three sections, preset CRUD, migrations, errors, and adaptive layout tests pass.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: unify habit and diet category management"
```

---

### Task 8: Top Actions, Habit Buttons, and Calendar Spacing

**Files:**
- Modify: `app/src/main/java/com/habit/app/ui/components/HabitTopAppBar.kt`
- Modify: `app/src/main/java/com/habit/app/ui/habits/HabitDetailScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/calendar/MonthGrid.kt`
- Modify: `app/src/main/java/com/habit/app/ui/components/HabitDatePicker.kt`
- Modify: all screens passing right-side top actions under `app/src/main/java/com/habit/app/ui/`
- Test: `app/src/androidTest/java/com/habit/app/ui/components/HabitTopAppBarTest.kt`
- Test: `app/src/androidTest/java/com/habit/app/ui/calendar/MonthGridLayoutTest.kt`
- Test: `app/src/androidTest/java/com/habit/app/ui/habits/HabitDetailTest.kt`

**Interfaces:**
- Produces: `HabitTopAction` for symbols and small text with a common 48dp minimum target.

- [ ] **Step 1: Write failing UI assertions**

Assert menu, back, plus, and edit actions all expose at least 48dp bounds; “编辑” uses the small-action style; delete is an outlined button; weekday centers align with date centers; weekday top/bottom gaps are equal within tolerance.

- [ ] **Step 2: Run Android test compilation and verify failure**

Expected: missing `HabitTopAction`, delete remains a TextButton, and spacing semantics/tags fail.

- [ ] **Step 3: Implement common top action**

Add:

```kotlin
@Composable
fun HabitTopAction(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { this.contentDescription = contentDescription },
        contentPadding = PaddingValues(0.dp),
    ) { Text(text, style = textStyle) }
}
```

Use it for `＋`, “编辑”, and other top-right text/symbol actions. Keep navigation icons light and audit all screens for direct oversized `Text` actions.

- [ ] **Step 4: Implement habit button and calendar spacing fixes**

Change delete to `OutlinedButton` with error-colored border/content. Put weekday labels in the same weighted seven-column grid as dates, center their text, and apply explicit symmetric vertical padding between month controls, weekday row, and date rows in `MonthGrid`, `SingleHabitMonthGrid`, and date picker.

- [ ] **Step 5: Run focused UI tests**

Expected: common target size, small edit text, outlined delete, and alignment/spacing tests pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main app/src/androidTest
git commit -m "fix: unify top actions and calendar spacing"
```

---

### Task 9: Recent Seven-Day Two-Position Timeline

**Files:**
- Create: `app/src/main/java/com/habit/app/ui/workbench/RecentWeekTimeline.kt`
- Modify: `app/src/main/java/com/habit/app/ui/workbench/WorkbenchScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/workbench/WorkbenchViewModel.kt`
- Create: `app/src/test/java/com/habit/app/ui/workbench/RecentWeekDisplayTest.kt`
- Modify: `app/src/test/java/com/habit/app/ui/workbench/WorkbenchViewModelTest.kt`
- Test: `app/src/androidTest/java/com/habit/app/ui/AdaptivePrimaryActionsTest.kt`

**Interfaces:**
- Produces: `RecentWeekDisplay(primaryEmoji: String?, secondaryEmoji: String?, overflowCount: Int?)` and `buildRecentWeekDisplay(iconKeys)`.

- [ ] **Step 1: Write failing pure display-policy tests**

```kotlin
@Test fun itemSlots_followApprovedCounts() {
    assertEquals(RecentWeekDisplay(null, null, null), buildRecentWeekDisplay(emptyList()))
    assertEquals(RecentWeekDisplay("a", null, null), buildRecentWeekDisplay(listOf("a")))
    assertEquals(RecentWeekDisplay("a", "b", null), buildRecentWeekDisplay(listOf("a", "b")))
    assertEquals(RecentWeekDisplay("a", null, 2), buildRecentWeekDisplay(listOf("a", "b", "c")))
    assertEquals(RecentWeekDisplay("a", null, 9), buildRecentWeekDisplay((1..10).map(Int::toString)))
}

@Test fun duplicateEmojiKeys_areStillSeparateCompletedItems() {
    assertEquals(RecentWeekDisplay("same", "same", null), buildRecentWeekDisplay(listOf("same", "same")))
}
```

- [ ] **Step 2: Run tests and verify failure**

Expected: display policy does not exist and current UI calls `distinct()`.

- [ ] **Step 3: Implement policy and approved horizontal UI**

Implement:

```kotlin
fun buildRecentWeekDisplay(keys: List<String>) = when (keys.size) {
    0 -> RecentWeekDisplay(null, null, null)
    1 -> RecentWeekDisplay(keys[0], null, null)
    2 -> RecentWeekDisplay(keys[0], keys[1], null)
    else -> RecentWeekDisplay(keys[0], null, keys.size - 1)
}
```

Render seven equal-width columns, weekday plus date number, no connecting line and no ordinary date background. Wrap only today's weekday/date pair in a low-saturation rounded outline. Always show weekday text rather than “今天”. Render the two item positions horizontally under the date box.

Remove `distinct()` and preserve the existing habit sort order from the calendar snapshot.

- [ ] **Step 4: Run unit and UI tests**

Expected: 0/1/2/3/10 and duplicate-Emoji cases pass; the seven-day strip remains horizontal at phone width.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main app/src/test app/src/androidTest
git commit -m "fix: make recent week counts accurate"
```

---

### Task 10: Version, Full Verification, APK, and Device Upgrade Check

**Files:**
- Modify: `app/build.gradle.kts`
- Verify: all files changed in Tasks 1–9
- Produce: `outputs/Habit-0.5.0-debug.apk`

**Interfaces:**
- Consumes: completed implementation and tests.
- Produces: installable 0.5.0 APK signed with the existing stable key.

- [ ] **Step 1: Set the release identity**

Change only:

```kotlin
versionCode = 10
versionName = "0.5.0"
```

- [ ] **Step 2: Run the complete proportional verification suite**

```powershell
$env:GRADLE_USER_HOME='D:\MySoftware\gradle\cache'
.\gradlew.bat --no-daemon --console=plain '-Pkotlin.compiler.execution.strategy=in-process' testDebugUnitTest lintDebug compileDebugAndroidTestKotlin assembleDebug
```

Expected: BUILD SUCCESSFUL; all unit tests pass, Lint reports no blocking errors, Android tests compile, and the Debug APK is generated.

- [ ] **Step 3: Verify Room schema and migration on the connected device**

Run the focused migration instrumentation test first, then install with replacement:

```powershell
.\gradlew.bat --no-daemon --console=plain connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.local.HabitDatabaseMigrationTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Expected: migration tests pass and `adb install -r` returns `Success` without a signature mismatch.

- [ ] **Step 4: Perform a short device smoke check**

On the retained 0.4.1 data set, verify:

1. Existing habits, check-ins, diet records, photos, and templates remain visible.
2. Diet recent/day modes switch correctly.
3. A diet record opens read-only detail; edit is a separate action.
4. Create, rename, hide, restore, and delete one temporary category.
5. Create a night-snack record with a custom meal category and photo.
6. Export schema-4 backup, import it in merge mode, and confirm newer data wins.
7. Workbench timeline shows 1, 2, and overflow displays according to the approved rule.

Expected: no crash, no lost old data, and UI matches the approved previews.

- [ ] **Step 5: Copy and hash the APK**

```powershell
Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination 'outputs\Habit-0.5.0-debug.apk' -Force
Get-FileHash -Algorithm SHA256 -LiteralPath 'outputs\Habit-0.5.0-debug.apk'
```

Expected: the output file exists and a SHA-256 hash is recorded in the handoff.

- [ ] **Step 6: Commit release metadata and final fixes**

```powershell
git add app/build.gradle.kts app/schemas app/src
git commit -m "release: prepare Habit 0.5.0"
```

Do not commit the generated APK unless the repository's existing output policy explicitly tracks APK artifacts.
