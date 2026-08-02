# Habit Diet & Life Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a backward-compatible diet diary, structured beverage tracking, diet statistics, collapsible life-module navigation, and combined habit/diet workbench to Habit 0.3.0.

**Architecture:** Keep diet as an independent domain/data/UI module. Room database version 2 adds diet-only tables through an explicit migration; a versioned backup codec imports both schema 1 and schema 2. Navigation and the workbench consume small module summaries instead of reaching into diet DAOs.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose Material 3, Room 2.8.4, DataStore Preferences 1.2.1, kotlinx.serialization JSON 1.8.1, JUnit 4, AndroidX instrumented tests, Gradle 9.5/AGP 9.3.0, Java 17.

## Global Constraints

- Android `minSdk = 23`, `targetSdk = 36`, `compileSdk = 36`.
- Keep existing package name `com.habit.app` and preserve every Room v1 habit/category/check-in row during migration.
- Never use destructive Room migration; register an explicit `Migration(1, 2)`.
- Distinguish `occurredAt`, `recordEpochDay`, `createdAt`, and `updatedAt` exactly as specified.
- Diet calories are optional non-negative integer kcal values; statistics distinguish missing calories from `0 kcal`.
- Daily calorie goal is optional and disabled by default; UI language stays neutral when the total exceeds the goal.
- Beverage v1 fields exclude caffeine and milk-base selection.
- Diet photos are not part of 0.3.0.
- Continue the existing off-white, low-saturation sky-blue UI and readable light system bars.
- Backup schema 2 must import schema 1; merge conflicts use the newer `updatedAt` value.
- Use TDD, run focused tests after every behavior change, and commit each completed task separately.
- Run builds through `gradlew.bat`; the currently installed Android Studio 2023.2.1 cannot sync AGP 9.3.0 and must not be used as build-success evidence.

## Planned File Structure

### Domain

- Create `app/src/main/java/com/habit/app/domain/model/DietModels.kt`: diet enums, records, drafts, summaries, and validation-friendly value objects.
- Create `app/src/main/java/com/habit/app/domain/repository/DietRepository.kt`: day observation, record CRUD, and range-summary interface.
- Create `app/src/main/java/com/habit/app/domain/stats/DietStatistics.kt`: pure calorie aggregation, beverage ranking, and meal suggestion rules.

### Data

- Modify `app/src/main/java/com/habit/app/data/local/Entities.kt`: add four diet entities and foreign keys.
- Create `app/src/main/java/com/habit/app/data/local/DietDao.kt`: transactional diet reads/writes and range queries.
- Modify `app/src/main/java/com/habit/app/data/local/HabitDatabase.kt`: database v2, `dietDao()`, and `MIGRATION_1_2`.
- Modify `app/src/main/java/com/habit/app/data/local/EntityMappers.kt`: diet entity/domain mappings.
- Create `app/src/main/java/com/habit/app/data/repository/RoomDietRepository.kt`: validation and transactional repository implementation.
- Create `app/src/main/java/com/habit/app/data/preferences/DietPreferencesRepository.kt`: calorie goal and drawer group state.
- Modify backup files under `app/src/main/java/com/habit/app/data/backup/`: schema-2 models, backward codec, merge, Room import/export, and preference handling.
- Modify `app/src/main/java/com/habit/app/di/AppContainer.kt`: register migration and expose diet repositories.

### UI

- Create `app/src/main/java/com/habit/app/ui/diet/DietDiaryViewModel.kt` and `DietDiaryScreen.kt`.
- Create `app/src/main/java/com/habit/app/ui/diet/DietEditorViewModel.kt`, `DietEditorScreen.kt`, `MealFields.kt`, and `BeverageFields.kt`.
- Create `app/src/main/java/com/habit/app/ui/diet/DietStatsViewModel.kt` and `DietStatsScreen.kt`.
- Create `app/src/main/java/com/habit/app/ui/diet/DietSettingsViewModel.kt` and `DietSettingsScreen.kt`.
- Modify navigation, drawer, and workbench files to expose independent life modules and summaries.

### Tests

- Add focused JVM tests for pure diet rules and ViewModels.
- Add Room instrumented tests for migration, transactions, cascades, and repository queries.
- Extend backup, navigation, workbench, and end-to-end tests.

---

### Task 1: Diet domain model and pure rules

**Files:**
- Create: `app/src/main/java/com/habit/app/domain/model/DietModels.kt`
- Create: `app/src/main/java/com/habit/app/domain/stats/DietStatistics.kt`
- Create: `app/src/test/java/com/habit/app/domain/stats/DietStatisticsTest.kt`

**Interfaces:**
- Produces: `MealRecord`, `MealRecordDraft`, `FoodItem`, `FoodItemDraft`, `BeverageDetails`, `BeverageDraft`, `DietDaySummary`, `DietRangeSummary`.
- Produces: `suggestMealType(LocalTime): MealType`, `calculateCalories(List<FoodItemDraft>, Int?): CalorieCalculation`, and `summarizeDiet(records, startEpochDay, endEpochDay): DietRangeSummary`.

- [ ] **Step 1: Write failing pure-rule tests**

```kotlin
class DietStatisticsTest {
    @Test fun mealSuggestionUsesExpectedBoundaries() {
        assertEquals(MealType.BREAKFAST, suggestMealType(LocalTime.of(7, 0)))
        assertEquals(MealType.LUNCH, suggestMealType(LocalTime.of(12, 0)))
        assertEquals(MealType.DINNER, suggestMealType(LocalTime.of(18, 0)))
        assertEquals(MealType.SNACK, suggestMealType(LocalTime.of(23, 0)))
    }

    @Test fun manualTotalOverridesItemSum() {
        val result = calculateCalories(
            listOf(FoodItemDraft("米饭", "1 碗", 230), FoodItemDraft("青菜", null, 80)),
            manualFinalCalories = 350,
        )
        assertEquals(310, result.calculatedCalories)
        assertEquals(350, result.finalCalories)
        assertEquals(CalorieSource.MANUAL, result.source)
    }

    @Test fun missingCaloriesRemainDifferentFromZero() {
        assertNull(calculateCalories(listOf(FoodItemDraft("水", null, null)), null).finalCalories)
        assertEquals(0, calculateCalories(listOf(FoodItemDraft("无糖茶", null, 0)), null).finalCalories)
    }
}
```

- [ ] **Step 2: Run the focused test and verify failure**

Run: `./gradlew.bat testDebugUnitTest --tests "com.habit.app.domain.stats.DietStatisticsTest"`  
Expected: compilation fails because diet types and functions do not exist.

- [ ] **Step 3: Implement the domain types**

```kotlin
enum class DietRecordType { MEAL, BEVERAGE }
enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }
enum class CalorieSource { NONE, ITEM_SUM, MANUAL, AI_ESTIMATE }
enum class BeverageCategory { COFFEE, MILK_TEA, TEA, FRUIT_DRINK, DAIRY, OTHER }
enum class DrinkTemperature { HOT, COLD, ROOM }

data class FoodItemDraft(val name: String, val portionText: String?, val calories: Int?)
data class BeverageDraft(
    val category: BeverageCategory,
    val brandOrStore: String,
    val beverageName: String,
    val sizeOrVolume: String,
    val temperature: DrinkTemperature?,
    val iceLevel: String,
    val sweetness: String,
    val toppings: List<String>,
    val cupCount: Int,
)
data class MealRecordDraft(
    val recordType: DietRecordType,
    val mealType: MealType?,
    val occurredAt: Long,
    val recordEpochDay: Long,
    val description: String,
    val foodItems: List<FoodItemDraft>,
    val manualFinalCalories: Int?,
    val beverage: BeverageDraft?,
    val note: String,
)
```

Add persisted record types with `id`, `createdAt`, `updatedAt`, plus day/range summary types containing nullable calorie totals and beverage rankings.

- [ ] **Step 4: Implement pure meal and calorie rules**

```kotlin
fun suggestMealType(time: LocalTime): MealType = when (time.hour) {
    in 5..9 -> MealType.BREAKFAST
    in 10..14 -> MealType.LUNCH
    in 17..21 -> MealType.DINNER
    else -> MealType.SNACK
}

fun calculateCalories(items: List<FoodItemDraft>, manualFinalCalories: Int?): CalorieCalculation {
    require(manualFinalCalories == null || manualFinalCalories >= 0)
    require(items.all { it.calories == null || it.calories >= 0 })
    val values = items.mapNotNull(FoodItemDraft::calories)
    val calculated = if (values.isEmpty()) null else values.sum()
    return when {
        manualFinalCalories != null -> CalorieCalculation(calculated, manualFinalCalories, CalorieSource.MANUAL)
        calculated != null -> CalorieCalculation(calculated, calculated, CalorieSource.ITEM_SUM)
        else -> CalorieCalculation(null, null, CalorieSource.NONE)
    }
}
```

- [ ] **Step 5: Run the tests and commit**

Run: `./gradlew.bat testDebugUnitTest --tests "com.habit.app.domain.stats.DietStatisticsTest"`  
Expected: `BUILD SUCCESSFUL` and all diet-statistics tests pass.

```bash
git add app/src/main/java/com/habit/app/domain app/src/test/java/com/habit/app/domain/stats
git commit -m "feat: add diet domain rules"
```

### Task 2: Room v2 schema and non-destructive migration

**Files:**
- Modify: `app/src/main/java/com/habit/app/data/local/Entities.kt`
- Create: `app/src/main/java/com/habit/app/data/local/DietDao.kt`
- Modify: `app/src/main/java/com/habit/app/data/local/HabitDatabase.kt`
- Modify: `app/src/main/java/com/habit/app/di/AppContainer.kt`
- Create: `app/src/androidTest/java/com/habit/app/data/local/HabitMigrationTest.kt`
- Extend: `app/src/androidTest/java/com/habit/app/data/local/HabitDatabaseTest.kt`
- Generate: `app/schemas/com.habit.app.data.local.HabitDatabase/2.json`

**Interfaces:**
- Produces: `MealRecordEntity`, `FoodItemEntity`, `BeverageDetailEntity`, `BeverageToppingEntity`, and relation `MealRecordWithDetails`.
- Produces: `HabitDatabase.dietDao(): DietDao` and public `MIGRATION_1_2`.

- [ ] **Step 1: Write migration and cascade tests**

```kotlin
@Test fun migrate1To2PreservesHabitRowsAndCreatesDietTables() {
    helper.createDatabase(TEST_DB, 1).apply {
        execSQL("INSERT INTO categories VALUES(1,'学习',1,0,0,100,100)")
        execSQL("INSERT INTO habits VALUES(1,'背单词','book',4287474124,1,20650,NULL,0,100,100)")
        execSQL("INSERT INTO check_ins VALUES(1,1,20664,100,100)")
        close()
    }
    helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { db ->
        assertEquals(1, db.query("SELECT * FROM habits").count)
        assertTrue(db.query("SELECT * FROM meal_records").columnCount > 0)
    }
}

@Test fun deletingMealCascadesFoodBeverageAndToppings() = runTest {
    val mealId = db.dietDao().insertMeal(testBeverageEntity())
    db.dietDao().insertFoodItems(listOf(testFoodEntity(mealId)))
    db.dietDao().upsertBeverage(testBeverageDetail(mealId))
    db.dietDao().insertToppings(listOf(testTopping(mealId)))
    db.dietDao().deleteMeal(mealId)
    assertNull(db.dietDao().getRecord(mealId))
}
```

Define the test fixtures in the same test file with explicit valid defaults:

```kotlin
private fun testBeverageEntity() = MealRecordEntity(
    recordType = DietRecordType.BEVERAGE.name,
    mealType = null,
    occurredAt = 100,
    recordEpochDay = 20_668,
    description = "",
    calculatedCalories = null,
    finalCalories = 260,
    calorieSource = CalorieSource.MANUAL.name,
    note = "",
    createdAt = 100,
    updatedAt = 100,
)
private fun testFoodEntity(mealId: Long) = FoodItemEntity(
    mealRecordId = mealId, name = "珍珠", portionText = null,
    calories = 60, sortOrder = 0, createdAt = 100, updatedAt = 100,
)
private fun testBeverageDetail(mealId: Long) = BeverageDetailEntity(
    mealRecordId = mealId, category = BeverageCategory.MILK_TEA.name,
    brandOrStore = "测试茶铺", beverageName = "奶茶", sizeOrVolume = "中杯",
    temperature = DrinkTemperature.COLD.name, iceLevel = "少冰",
    sweetness = "三分糖", cupCount = 1,
)
private fun testTopping(mealId: Long) = BeverageToppingEntity(
    mealRecordId = mealId, name = "珍珠", sortOrder = 0,
    createdAt = 100, updatedAt = 100,
)
```

- [ ] **Step 2: Run instrumented tests and verify failure**

Run: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.local.HabitMigrationTest,com.habit.app.data.local.HabitDatabaseTest`  
Expected: compilation fails because Room v2 diet entities and migration do not exist.

- [ ] **Step 3: Add four normalized entities**

Implement:

```kotlin
@Entity(tableName = "meal_records", indices = [Index("recordEpochDay"), Index("occurredAt")])
data class MealRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordType: String,
    val mealType: String?,
    val occurredAt: Long,
    val recordEpochDay: Long,
    val description: String,
    val calculatedCalories: Int?,
    val finalCalories: Int?,
    val calorieSource: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
)
```

Add child entities with `ForeignKey.CASCADE`; make `BeverageDetailEntity.mealRecordId` its primary key and add `(mealRecordId, sortOrder)` indices for food items and toppings.

- [ ] **Step 4: Implement DAO and migration SQL**

DAO must provide `observeDay(epochDay)`, `observeRecord(id)`, `getRecord(id)`, range reads, inserts/updates, child replacement helpers, `deleteMeal`, and `deleteAllDietData`.

Create `MIGRATION_1_2` with four explicit `CREATE TABLE IF NOT EXISTS` statements and all Room-required indices. Change `@Database(version = 2, entities = [...])`, expose `dietDao()`, and register `.addMigrations(MIGRATION_1_2)` in `AppContainer`.

- [ ] **Step 5: Run migration tests and inspect schema**

Run: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.local.HabitMigrationTest,com.habit.app.data.local.HabitDatabaseTest`  
Expected: migration and cascade tests pass. Confirm `app/schemas/.../2.json` contains all four diet tables and no changes to existing table definitions.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/habit/app/data/local app/src/main/java/com/habit/app/di/AppContainer.kt app/src/androidTest/java/com/habit/app/data/local app/schemas
git commit -m "feat: migrate database for diet records"
```

### Task 3: Transactional diet repository and statistics

**Files:**
- Create: `app/src/main/java/com/habit/app/domain/repository/DietRepository.kt`
- Modify: `app/src/main/java/com/habit/app/data/local/EntityMappers.kt`
- Create: `app/src/main/java/com/habit/app/data/repository/RoomDietRepository.kt`
- Modify: `app/src/main/java/com/habit/app/di/AppContainer.kt`
- Create: `app/src/androidTest/java/com/habit/app/data/repository/RoomDietRepositoryTest.kt`
- Create: `app/src/test/java/com/habit/app/data/repository/DietDraftValidationTest.kt`

**Interfaces:**
- Produces:

```kotlin
interface DietRepository {
    fun observeDay(epochDay: Long): Flow<List<MealRecord>>
    fun observeRecord(id: Long): Flow<MealRecord?>
    fun observeRange(startEpochDay: Long, endEpochDay: Long): Flow<List<MealRecord>>
    suspend fun save(id: Long?, draft: MealRecordDraft): Long
    suspend fun delete(id: Long)
}
```

- [ ] **Step 1: Write repository behavior tests**

Cover: blank record rejection; negative calorie rejection; beverage name requirement; cup count minimum; child replacement on edit; transaction rollback; sorted day results; and range statistics.

```kotlin
@Test fun editingRecordReplacesChildrenAtomically() = runTest {
    val id = repository.save(null, mealDraft(items = listOf(food("米饭", 230))))
    repository.save(id, mealDraft(items = listOf(food("面条", 410))))
    val saved = repository.observeRecord(id).first()!!
    assertEquals(listOf("面条"), saved.foodItems.map(FoodItem::name))
    assertEquals(410, saved.finalCalories)
}
```

- [ ] **Step 2: Verify tests fail**

Run: `./gradlew.bat testDebugUnitTest --tests "*DietDraftValidationTest"` and `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.repository.RoomDietRepositoryTest`  
Expected: compilation fails because `DietRepository` and `RoomDietRepository` do not exist.

- [ ] **Step 3: Implement mapping and validation**

Add strict enum mapping through `.name`/`valueOf`, trim optional strings, discard blank food/topping rows, and enforce:

```kotlin
require(draft.manualFinalCalories == null || draft.manualFinalCalories >= 0)
require(draft.foodItems.all { it.calories == null || it.calories >= 0 })
require(draft.beverage == null || draft.beverage.cupCount >= 1)
require(
    draft.description.isNotBlank() ||
        draft.foodItems.any { it.name.isNotBlank() } ||
        !draft.beverage?.beverageName.isNullOrBlank(),
) { "请至少填写一项饮食内容" }
```

- [ ] **Step 4: Implement transactional save/edit/delete**

Use `database.withTransaction`. On create set both audit timestamps from injected `Clock`; on edit preserve `createdAt`, update `updatedAt`, delete and replace children, and recalculate calories using Task 1 rules. Expose `dietRepository` from `AppContainer`.

- [ ] **Step 5: Run repository and domain tests**

Run: `./gradlew.bat testDebugUnitTest --tests "*Diet*"`  
Run: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.repository.RoomDietRepositoryTest`  
Expected: all focused tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/habit/app/domain/repository app/src/main/java/com/habit/app/data/local/EntityMappers.kt app/src/main/java/com/habit/app/data/repository/RoomDietRepository.kt app/src/main/java/com/habit/app/di/AppContainer.kt app/src/test app/src/androidTest
git commit -m "feat: persist diet records transactionally"
```

### Task 4: Diet preferences and collapsible module state

**Files:**
- Modify: `app/src/main/java/com/habit/app/data/preferences/HabitPreferenceKeys.kt`
- Create: `app/src/main/java/com/habit/app/data/preferences/DietPreferencesRepository.kt`
- Modify: `app/src/main/java/com/habit/app/di/AppContainer.kt`
- Create: `app/src/test/java/com/habit/app/data/preferences/DietPreferencePolicyTest.kt`
- Create: `app/src/androidTest/java/com/habit/app/data/preferences/DietPreferencesRepositoryTest.kt`

**Interfaces:**
- Produces: `DietPreferences(goalEnabled, dailyGoalKcal, habitGroupExpanded, dietGroupExpanded)`.
- Produces: `DietPreferencesRepository.preferences`, `setGoal(enabled, kcal)`, and `setGroupExpanded(group, expanded)`.

- [ ] **Step 1: Write preference-policy tests**

```kotlin
@Test fun goalIsDisabledByDefault() {
    assertEquals(DietPreferences(false, null, true, true), decodeDietPreferences(emptyPreferences()))
}

@Test fun enabledGoalMustBePositive() {
    assertFailsWith<IllegalArgumentException> { validateDietGoal(true, 0) }
    assertEquals(1800, validateDietGoal(true, 1800))
}
```

- [ ] **Step 2: Verify focused tests fail**

Run: `./gradlew.bat testDebugUnitTest --tests "*DietPreferencePolicyTest"`  
Expected: missing diet preference types/functions.

- [ ] **Step 3: Add keys and repository**

Use Boolean keys for goal enablement and both group states, and an Int key for kcal. Defaults: goal disabled, kcal null, both groups expanded. `setGoal(false, null)` removes stored kcal; `setGoal(true, kcal)` requires `kcal > 0`.

- [ ] **Step 4: Add DataStore instrumented persistence test**

Persist `setGoal(true, 1800)` and collapsed diet group, recreate repository over the same test DataStore, then assert values are restored.

- [ ] **Step 5: Run and commit**

Run: `./gradlew.bat testDebugUnitTest --tests "*DietPreference*"`  
Run: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.preferences.DietPreferencesRepositoryTest`  
Expected: all preference tests pass.

```bash
git add app/src/main/java/com/habit/app/data/preferences app/src/main/java/com/habit/app/di/AppContainer.kt app/src/test/java/com/habit/app/data/preferences app/src/androidTest/java/com/habit/app/data/preferences
git commit -m "feat: store diet and module preferences"
```

### Task 5: Backup schema 2 with schema-1 import compatibility

**Files:**
- Modify: `app/src/main/java/com/habit/app/data/backup/BackupModels.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/HabitBackupCodec.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/BackupMerger.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/RoomBackupRepository.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/HabitBackupService.kt`
- Modify: `app/src/main/java/com/habit/app/data/preferences/BackupPreferencesRepository.kt`
- Extend: `app/src/test/java/com/habit/app/data/backup/HabitBackupCodecTest.kt`
- Extend: `app/src/test/java/com/habit/app/data/backup/BackupMergerTest.kt`
- Create: `app/src/androidTest/java/com/habit/app/data/backup/DietBackupRoundTripTest.kt`

**Interfaces:**
- Changes `HABIT_BACKUP_SCHEMA_VERSION` from `1` to `2`.
- Adds backup lists for meal records, food items, beverage details, toppings and goal preferences.
- Schema-1 decode returns empty diet lists, disabled goal and null goal kcal.

- [ ] **Step 1: Add failing codec compatibility tests**

```kotlin
@Test fun schema1BackupDecodesWithEmptyDietData() {
    val decoded = HabitBackupCodec.decode(schema1Fixture)
    assertTrue(decoded.mealRecords.isEmpty())
    assertFalse(decoded.preferences.dailyCalorieGoalEnabled)
    assertNull(decoded.preferences.dailyCalorieGoalKcal)
}

@Test fun schema2RoundTripPreservesDrinkAttributes() {
    val decoded = HabitBackupCodec.decode(HabitBackupCodec.encode(schema2Backup()))
    assertEquals("少冰", decoded.beverageDetails.single().iceLevel)
    assertEquals(listOf("珍珠"), decoded.beverageToppings.map { it.name })
}
```

- [ ] **Step 2: Verify backup tests fail**

Run: `./gradlew.bat testDebugUnitTest --tests "*HabitBackupCodecTest" --tests "*BackupMergerTest"`  
Expected: schema 1 is rejected by the current exact-version check and diet fields do not exist.

- [ ] **Step 3: Implement version-aware codec**

Accept schema versions `1..HABIT_BACKUP_SCHEMA_VERSION`; use optional arrays/defaults only for schema 1, require all diet arrays for schema 2, and validate every parent/child reference, non-negative calorie, positive cup count, unique ID and enum name.

- [ ] **Step 4: Extend merge mapping**

Match meal records by `(id, createdAt)`, remap colliding IDs, choose the newer `updatedAt`, then remap food/beverage/topping children through the resulting meal ID map. Preferences continue to use `preferencesUpdatedAt`; the newer preference snapshot includes the diet goal.

- [ ] **Step 5: Extend transactional Room backup import/export**

Export and restore all four diet tables. During replace, delete child tables before parent tables. During merge, build a full current backup, invoke `BackupMerger`, then restore the merged snapshot in one Room transaction. Expand `ImportSummary` with `mealRecords` and `beverages`.

- [ ] **Step 6: Run codec, merge and Room round-trip tests**

Run: `./gradlew.bat testDebugUnitTest --tests "*Backup*"`  
Run: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.backup.DietBackupRoundTripTest`  
Expected: schema 1 and schema 2 fixtures import; diet round trip and newer-data conflict rules pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/habit/app/data/backup app/src/main/java/com/habit/app/data/preferences/BackupPreferencesRepository.kt app/src/test/java/com/habit/app/data/backup app/src/androidTest/java/com/habit/app/data/backup
git commit -m "feat: back up diet data compatibly"
```

### Task 6: Diet diary and editor ViewModels

**Files:**
- Create: `app/src/main/java/com/habit/app/ui/diet/DietDiaryViewModel.kt`
- Create: `app/src/main/java/com/habit/app/ui/diet/DietEditorViewModel.kt`
- Create: `app/src/test/java/com/habit/app/ui/diet/DietDiaryViewModelTest.kt`
- Create: `app/src/test/java/com/habit/app/ui/diet/DietEditorViewModelTest.kt`

**Interfaces:**
- Produces: `DietDiaryUiState(selectedDate, records, summary, goal, isLoading, message)`.
- Produces: `DietEditorUiState` with draft fields, validation errors, saving/deleting flags.
- Produces: `selectDate`, `previousWeek`, `nextWeek`, `update*`, `addFoodItem`, `removeFoodItem`, `addTopping`, `save`, and `delete` actions.

- [ ] **Step 1: Write ViewModel tests with a fake repository**

Test default date, date switching, time-based meal suggestion, description-only save, structured-item save, manual calorie override, beverage validation, child editing, and surfaced repository failures.

```kotlin
@Test fun newMealUsesDeviceDateAndSuggestedMealType() = runTest {
    val clock = Clock.fixed(Instant.parse("2026-08-03T12:10:00Z"), ZoneOffset.UTC)
    val vm = DietEditorViewModel(null, fakeRepository, fixedDateProvider, clock)
    advanceUntilIdle()
    assertEquals(MealType.LUNCH, vm.state.value.mealType)
    assertEquals(LocalDate.of(2026, 8, 3), vm.state.value.date)
}
```

- [ ] **Step 2: Verify tests fail**

Run: `./gradlew.bat testDebugUnitTest --tests "com.habit.app.ui.diet.*ViewModelTest"`  
Expected: diet ViewModels do not exist.

- [ ] **Step 3: Implement diary state flow**

Drive day records with `selectedDate.flatMapLatest { repository.observeDay(it.toEpochDay()) }`, combine with diet preferences, and compute the selected-day summary through Task 1 pure rules. Follow the existing cancellation/error pattern used by current ViewModels.

- [ ] **Step 4: Implement editor state and save mapping**

Load existing records when `recordId != null`; otherwise seed device date/time and `suggestMealType`. Keep free-text and list edits in immutable state. Map `LocalDate` + `LocalTime` to `occurredAt` with the device zone and always set `recordEpochDay` from the displayed date.

- [ ] **Step 5: Run and commit**

Run: `./gradlew.bat testDebugUnitTest --tests "com.habit.app.ui.diet.*ViewModelTest"`  
Expected: all editor and diary ViewModel tests pass.

```bash
git add app/src/main/java/com/habit/app/ui/diet/*ViewModel.kt app/src/test/java/com/habit/app/ui/diet
git commit -m "feat: add diet diary state management"
```

### Task 7: Time-axis diet diary and meal/beverage editor UI

**Files:**
- Create: `app/src/main/java/com/habit/app/ui/diet/DietDiaryScreen.kt`
- Create: `app/src/main/java/com/habit/app/ui/diet/DietEditorScreen.kt`
- Create: `app/src/main/java/com/habit/app/ui/diet/MealFields.kt`
- Create: `app/src/main/java/com/habit/app/ui/diet/BeverageFields.kt`
- Create: `app/src/androidTest/java/com/habit/app/ui/diet/DietDiaryFlowTest.kt`
- Create: `app/src/androidTest/java/com/habit/app/ui/diet/DietEditorValidationTest.kt`

**Interfaces:**
- Consumes Task 6 ViewModels.
- Produces composables `DietDiaryScreen`, `DietEditorScreen`, `MealFields`, and `BeverageFields` with navigation callbacks only.

- [ ] **Step 1: Write Compose flow tests**

Test tags and flows:

```kotlin
compose.onNodeWithTag("diet_add").performClick()
compose.onNodeWithTag("diet_description").performTextInput("米饭、番茄炒蛋")
compose.onNodeWithTag("diet_food_add").performClick()
compose.onNodeWithTag("diet_food_name_0").performTextInput("米饭")
compose.onNodeWithTag("diet_food_calories_0").performTextInput("230")
compose.onNodeWithTag("diet_save").performClick()
compose.onNodeWithText("米饭、番茄炒蛋").assertIsDisplayed()
```

Add a beverage flow that records brand, sweetness, ice, two toppings and cup count; add delete-confirmation coverage.

- [ ] **Step 2: Verify UI tests fail**

Run: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.habit.app.ui.diet`  
Expected: screen classes and tags do not exist.

- [ ] **Step 3: Implement diary visual hierarchy**

Use existing `HabitTopAppBar`/surface components where suitable. Build the confirmed UI: seven-day strip, off-white summary card, nullable-calorie copy, chronological cards, empty state, and sky-blue floating add button. Do not add a bottom bar.

- [ ] **Step 4: Implement editor components**

Use Material 3 fields with minimum 48 dp controls. Meal form includes type, date/time, description, dynamic food rows, calculated total, editable final total and note. Beverage form includes category, brand/store, name, size/volume, temperature, ice, sweetness, toppings, cups, total and note. Hide meal-only fields for beverages.

- [ ] **Step 5: Implement delete and unsaved-change dialogs**

Delete calls `viewModel.delete` only after confirmation. Back navigation with changed state shows a save/discard/stay dialog; unchanged state exits immediately.

- [ ] **Step 6: Run UI and accessibility tests**

Run: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.habit.app.ui.diet`  
Expected: diary, editor, validation and deletion flows pass with no clipped 48 dp controls.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/habit/app/ui/diet app/src/androidTest/java/com/habit/app/ui/diet
git commit -m "feat: add diet diary and editor UI"
```

### Task 8: Diet statistics and neutral goal settings

**Files:**
- Create: `app/src/main/java/com/habit/app/ui/diet/DietStatsViewModel.kt`
- Create: `app/src/main/java/com/habit/app/ui/diet/DietStatsScreen.kt`
- Create: `app/src/main/java/com/habit/app/ui/diet/DietSettingsViewModel.kt`
- Create: `app/src/main/java/com/habit/app/ui/diet/DietSettingsScreen.kt`
- Create: `app/src/test/java/com/habit/app/ui/diet/DietStatsViewModelTest.kt`
- Create: `app/src/test/java/com/habit/app/ui/diet/DietSettingsViewModelTest.kt`
- Create: `app/src/androidTest/java/com/habit/app/ui/diet/DietStatsAndSettingsTest.kt`

**Interfaces:**
- Consumes `DietRepository.observeRange` and `DietPreferencesRepository.preferences`.
- Produces 7-day trend, recorded-day count, meal totals, cup totals, and ranked category/brand/sweetness/ice rows.

- [ ] **Step 1: Write failing statistics and settings tests**

Assert: records without calories count as meals but not kcal; 0 kcal remains visible; cup totals multiply by `cupCount`; ties rank deterministically by label; disabled goal shows no progress; enabled 1800 kcal goal at 1900 kcal uses neutral copy and normal primary color.

- [ ] **Step 2: Verify focused tests fail**

Run: `./gradlew.bat testDebugUnitTest --tests "com.habit.app.ui.diet.DietStatsViewModelTest" --tests "com.habit.app.ui.diet.DietSettingsViewModelTest"`  
Expected: statistics/settings ViewModels do not exist.

- [ ] **Step 3: Implement ViewModels**

Observe exactly seven epoch days ending at the selected day. Use pure summary functions, expose `List<TrendPoint>` and `List<RankedValue>`, and store goal changes only after positive-integer validation.

- [ ] **Step 4: Implement screens**

Statistics screen uses a simple Compose bar trend without a chart dependency. Settings screen uses a switch plus numeric field. Copy reads “今日记录 1,900 / 1,800 kcal” rather than “超标 100 kcal”.

- [ ] **Step 5: Run and commit**

Run: `./gradlew.bat testDebugUnitTest --tests "com.habit.app.ui.diet.*"`  
Run: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.ui.diet.DietStatsAndSettingsTest`  
Expected: all diet statistics and goal tests pass.

```bash
git add app/src/main/java/com/habit/app/ui/diet app/src/test/java/com/habit/app/ui/diet app/src/androidTest/java/com/habit/app/ui/diet
git commit -m "feat: add diet statistics and settings"
```

### Task 9: Modular navigation, collapsible drawer, and combined workbench

**Files:**
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitDestination.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitDrawerContent.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitNavHost.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitApp.kt`
- Modify: `app/src/main/java/com/habit/app/ui/workbench/WorkbenchViewModel.kt`
- Modify: `app/src/main/java/com/habit/app/ui/workbench/WorkbenchScreen.kt`
- Extend: `app/src/test/java/com/habit/app/ui/navigation/NavigationPolicyTest.kt`
- Extend: `app/src/test/java/com/habit/app/ui/workbench/WorkbenchViewModelTest.kt`
- Create: `app/src/androidTest/java/com/habit/app/ui/navigation/CollapsibleModuleDrawerTest.kt`
- Create: `app/src/androidTest/java/com/habit/app/ui/workbench/LifeWorkbenchTest.kt`

**Interfaces:**
- Adds destinations: `DietDiary`, `DietEditor`, `DietStats`, `DietSettings`.
- Produces `DrawerModule(id, label, symbol, summary, expanded, children)` and calls `setGroupExpanded`.
- Extends `WorkbenchUiState` with `dietSummary` and callbacks `onQuickAddMeal`/`onOpenDiet`.

- [ ] **Step 1: Write navigation and workbench tests**

Assert all diet top-level routes use drawer gestures; editor route does not. Assert group toggle persists, collapse hides child tags, expansion restores them, and selecting a child closes the drawer. Assert workbench combines habit completion with same-day diet records and exposes the quick-add action.

- [ ] **Step 2: Verify tests fail**

Run: `./gradlew.bat testDebugUnitTest --tests "*NavigationPolicyTest" --tests "*WorkbenchViewModelTest"`  
Run: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.ui.navigation.CollapsibleModuleDrawerTest,com.habit.app.ui.workbench.LifeWorkbenchTest`  
Expected: diet routes, drawer groups and diet workbench summary are absent.

- [ ] **Step 3: Add routes and factories**

Wire all four diet screens into `HabitNavHost`, passing repository, preferences, date provider and record ID. Navigate quick-add to `DietEditor.route()` and record edit to `DietEditor.route(id)`.

- [ ] **Step 4: Replace flat drawer list with module groups**

Keep Today Workbench and global Settings as standalone items. Add collapsible Habit and Diet groups with persisted expansion state. Collapsed summaries read from `WorkbenchUiState`; avoid DAO access in drawer code.

- [ ] **Step 5: Extend workbench summary and UI**

Combine diet day observation with existing habit flows. Add a diet summary card showing record count and nullable kcal total. Add “记一餐” and “打卡” actions without introducing a bottom navigation bar.

- [ ] **Step 6: Run navigation/workbench tests and commit**

Run: `./gradlew.bat testDebugUnitTest --tests "*NavigationPolicyTest" --tests "*WorkbenchViewModelTest"`  
Run: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.ui.navigation.CollapsibleModuleDrawerTest,com.habit.app.ui.workbench.LifeWorkbenchTest`  
Expected: all focused tests pass.

```bash
git add app/src/main/java/com/habit/app/ui/navigation app/src/main/java/com/habit/app/ui/workbench app/src/test/java/com/habit/app/ui/navigation app/src/test/java/com/habit/app/ui/workbench app/src/androidTest/java/com/habit/app/ui/navigation app/src/androidTest/java/com/habit/app/ui/workbench
git commit -m "feat: expand Habit into a life workbench"
```

### Task 10: Release integration, regression verification, and APK

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `docs/INSTALL.md`
- Modify: `docs/TESTING.md`
- Extend: `app/src/androidTest/java/com/habit/app/ui/EndToEndMvpTest.kt`
- Extend: `app/src/test/java/com/habit/app/ProjectSmokeTest.kt`
- Create output: `outputs/Habit-0.3.0-debug.apk`

**Interfaces:**
- Changes version to `versionCode = 5`, `versionName = "0.3.0"`.
- User-visible Chinese copy includes diet backup counts and module names.

- [ ] **Step 1: Extend end-to-end regression test**

Create a habit and check it in, add a lunch with two food items, add a milk tea with brand/sweetness/ice/topping, verify workbench summaries, export backup, import it, and confirm both habit and diet data remain. Keep existing onboarding, calendar, theme and backup-location assertions.

- [ ] **Step 2: Run the complete JVM suite**

Run: `./gradlew.bat testDebugUnitTest`  
Expected: `BUILD SUCCESSFUL` with zero failed tests.

- [ ] **Step 3: Run the complete instrumented suite on the independent emulator**

Run: `./gradlew.bat connectedDebugAndroidTest`  
Expected: `BUILD SUCCESSFUL`; migration, backup, diet, navigation and existing MVP tests all pass.

- [ ] **Step 4: Update version and user documentation**

Set version code/name to 5/0.3.0. Document diet records in import/export, the non-destructive v1→v2 upgrade, independent emulator test steps, and the absence of AI/photo features in this release.

- [ ] **Step 5: Build, copy, install, and smoke-test APK**

Run: `./gradlew.bat assembleDebug`  
Expected: `app/build/outputs/apk/debug/app-debug.apk` exists.

Copy it mechanically to `outputs/Habit-0.3.0-debug.apk`, then run:

```powershell
& 'D:\MySoftware\Android\AndroidSdk\platform-tools\adb.exe' install -r 'outputs\Habit-0.3.0-debug.apk'
& 'D:\MySoftware\Android\AndroidSdk\platform-tools\adb.exe' shell am start -n com.habit.app/.MainActivity
```

Expected: install reports `Success`; existing emulator data survives and Habit opens on the life workbench.

- [ ] **Step 6: Commit release integration**

```bash
git add app/build.gradle.kts app/src/main/res/values/strings.xml docs/INSTALL.md docs/TESTING.md app/src/test app/src/androidTest
git commit -m "chore: release Habit 0.3.0"
```

## Final Verification Checklist

- [ ] `git status --short` is clean.
- [ ] Room schema 2 is exported and migration validation passes.
- [ ] Schema-1 and schema-2 backup fixtures both import.
- [ ] Existing local habits/check-ins survive in-place APK update.
- [ ] Diet record CRUD, drink attributes, 7-day statistics, calorie goal and merge behavior pass.
- [ ] Collapsible module groups restore state after process restart.
- [ ] Workbench shows both module summaries and both quick actions.
- [ ] System bars remain readable and no fixed purple surfaces appear under the sky-blue theme.
- [ ] `outputs/Habit-0.3.0-debug.apk` installs and opens on emulator and USB-connected device.
