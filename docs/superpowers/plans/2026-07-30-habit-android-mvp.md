# Habit Android MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an offline Android APK for Redmi K70 that supports categorized daily habits, current and historical check-ins, aggregate and per-habit calendars, statistics, and five persistent built-in accent themes.

**Architecture:** A single Android application module uses Jetpack Compose and feature-focused packages. ViewModels expose immutable `StateFlow` UI state, repositories own business rules, Room stores habits/categories/check-ins, and Preferences DataStore stores the selected application theme. Dependency injection remains manual through an application-scoped `AppContainer`.

**Tech Stack:** Kotlin 2.3.21, Android Gradle Plugin 9.3.0, Gradle 9.5.0, compile/target SDK 36, min SDK 23, Jetpack Compose BOM 2026.06.00, Room 2.8.4, DataStore 1.2.1, Navigation Compose 2.9.8, Lifecycle 2.10.0, JUnit 4, AndroidX Test, KSP 2.3.9.

## Global Constraints

- Application ID and namespace: `com.habit.app`.
- App display name: `Habit`; internal MVP version: `0.1.0`.
- Android only; `minSdk = 23`, `compileSdk = 36`, `targetSdk = 36`.
- Use Java 17 for Gradle and Kotlin compilation.
- Reuse Java 17 from `D:\MySoftware\Java\jdk-17`.
- Keep newly installed development tooling under `D:\MySoftware`: Android SDK components in
  `D:\MySoftware\Android\AndroidSdk` and Gradle distributions/caches in
  `D:\MySoftware\gradle`.
- The MVP has no network dependency and declares no network permission.
- Store business dates as `epochDay: Long`; store audit timestamps as UTC Unix milliseconds.
- A check-in is unique on `(habitId, checkInEpochDay)`.
- A check-in date must be on or after `startEpochDay`, before `archivedEpochDay` when archived, and no later than the device-local current date.
- Application theme and per-habit identification color are independent.
- Default application theme is `SKY_BLUE`; background and body text stay neutral across themes.
- User-visible copy is Simplified Chinese.
- Build only stable dependencies; do not introduce Hilt, a network stack, reminders, login, AI, food tracking, payment, or cloud sync.
- Every implementation task follows red-green-refactor and ends with a focused commit.
- Official dependency references: [AGP/Gradle compatibility](https://developer.android.com/build/releases/about-agp), [Compose BOM](https://developer.android.com/develop/ui/compose/bom), [Room](https://developer.android.com/jetpack/androidx/releases/room), [DataStore](https://developer.android.com/jetpack/androidx/releases/datastore).

---

## File Structure

```text
.
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew
├── gradlew.bat
├── app/
│   ├── build.gradle.kts
│   ├── schemas/
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/habit/app/
│       │   │   ├── HabitApplication.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── di/AppContainer.kt
│       │   │   ├── domain/model/
│       │   │   ├── domain/repository/
│       │   │   ├── domain/stats/
│       │   │   ├── data/local/
│       │   │   ├── data/repository/
│       │   │   ├── data/preferences/
│       │   │   └── ui/
│       │   │       ├── navigation/
│       │   │       ├── welcome/
│       │   │       ├── calendar/
│       │   │       ├── habits/
│       │   │       ├── categories/
│       │   │       ├── settings/
│       │   │       ├── components/
│       │   │       └── theme/
│       │   └── res/
│       ├── test/java/com/habit/app/
│       └── androidTest/java/com/habit/app/
└── docs/
    ├── INSTALL.md
    └── TESTING.md
```

`domain` contains platform-light business types and deterministic rules. `data` owns persistence and mapping. Each `ui/<feature>` package owns its screen, ViewModel, UI state, and feature-specific components. Shared visual primitives live in `ui/components`, while theme tokens live only in `ui/theme`.

---

### Task 1: Reproducible Android Project and Build Smoke Test

**Files:**
- Modify: `.gitignore`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/habit/app/MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/test/java/com/habit/app/ProjectSmokeTest.kt`

**Interfaces:**
- Consumes: JDK 17, Android SDK Platform 36, Build Tools 36.0.0.
- Produces: `:app` Gradle module, `com.habit.app.MainActivity`, working unit-test and debug-APK tasks.

- [ ] **Step 1: Install and verify the build prerequisites**

Run in an approved elevated PowerShell session:

```powershell
$jdkRoot = 'D:\MySoftware\Java\jdk-17'
$env:JAVA_HOME = $jdkRoot
$env:Path = "$jdkRoot\bin;$env:Path"
$env:GRADLE_USER_HOME = 'D:\MySoftware\gradle\cache'
$sdk = 'D:\MySoftware\Android\AndroidSdk'
$archive = 'work\commandlinetools-win-15859902_latest.zip'
$extract = 'work\android-command-line-tools'
Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' -OutFile $archive
$actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash.ToLowerInvariant()
if ($actualHash -ne '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a') { throw "Android tools checksum mismatch: $actualHash" }
Expand-Archive -LiteralPath $archive -DestinationPath $extract -Force
New-Item -ItemType Directory -Force -Path "$sdk\cmdline-tools\latest" | Out-Null
Copy-Item -Path "$extract\cmdline-tools\*" -Destination "$sdk\cmdline-tools\latest" -Recurse -Force
$licenseAnswers = 1..200 | ForEach-Object { 'y' }
$licenseAnswers | & "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
& "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'
java -version
```

Expected: the downloaded archive matches the official SHA-256, Java reports version 17, and `platforms\android-36` plus `build-tools\36.0.0` exist.

Create `local.properties` with:

```properties
sdk.dir=D\:\\MySoftware\\Android\\AndroidSdk
```

Add `local.properties`, `.gradle/`, and every `build/` directory to `.gitignore`.

- [ ] **Step 2: Write the project smoke test**

```kotlin
package com.habit.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSmokeTest {
    @Test
    fun applicationIdIsStable() {
        assertEquals("com.habit.app", BuildConfig.APPLICATION_ID)
    }
}
```

- [ ] **Step 3: Create the version catalog and Gradle configuration**

Use these pinned values in `gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.3.0"
kotlin = "2.3.21"
ksp = "2.3.9"
composeBom = "2026.06.00"
activityCompose = "1.13.0"
lifecycle = "2.10.0"
navigation = "2.9.8"
room = "2.8.4"
datastore = "1.2.1"
coroutines = "1.10.2"
junit = "4.13.2"
androidxJunit = "1.3.0"
espresso = "3.7.0"
desugar = "2.1.5"

[libraries]
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
androidx-junit = { module = "androidx.test.ext:junit", version.ref = "androidxJunit" }
espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }
desugar-jdk-libs = { module = "com.android.tools:desugar_jdk_libs", version.ref = "desugar" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

Configure `app/build.gradle.kts` with SDK 36, `minSdk = 23`, Java 17, core library desugaring, Compose, Room schema export to `app/schemas`, and the dependencies above. Set `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` and `buildFeatures.buildConfig = true` so the smoke test can read `BuildConfig.APPLICATION_ID`.

- [ ] **Step 4: Generate the Gradle wrapper**

Run:

```powershell
$gradleRoot = 'D:\MySoftware\gradle'
$gradleZip = "$gradleRoot\gradle-9.5.0-bin.zip"
$gradleSha = "$gradleRoot\gradle-9.5.0-bin.zip.sha256"
Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-9.5.0-bin.zip' -OutFile $gradleZip
Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-9.5.0-bin.zip.sha256' -OutFile $gradleSha
$expected = (Get-Content -LiteralPath $gradleSha -Raw).Trim().ToLowerInvariant()
$actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $gradleZip).Hash.ToLowerInvariant()
if ($actual -ne $expected) { throw "Gradle checksum mismatch: $actual" }
Expand-Archive -LiteralPath $gradleZip -DestinationPath $gradleRoot -Force
& "$gradleRoot\gradle-9.5.0\bin\gradle.bat" wrapper --gradle-version 9.5.0
```

Expected: wrapper scripts and `gradle-wrapper.jar` are created, and `distributionUrl` ends with `gradle-9.5.0-bin.zip`.

- [ ] **Step 5: Add the minimal activity**

```kotlin
package com.habit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Habit") }
    }
}
```

- [ ] **Step 6: Run the smoke test and build**

Run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Expected: both tasks succeed and `app\build\outputs\apk\debug\app-debug.apk` exists.

- [ ] **Step 7: Commit**

```powershell
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew gradlew.bat app
git commit -m "build: scaffold Habit Android app"
```

---

### Task 2: Domain Models, Date Eligibility, and Statistics

**Files:**
- Create: `app/src/main/java/com/habit/app/domain/model/HabitModels.kt`
- Create: `app/src/main/java/com/habit/app/domain/stats/DatePolicy.kt`
- Create: `app/src/main/java/com/habit/app/domain/stats/HabitStatistics.kt`
- Create: `app/src/test/java/com/habit/app/domain/stats/DatePolicyTest.kt`
- Create: `app/src/test/java/com/habit/app/domain/stats/HabitStatisticsTest.kt`
- Create: `app/src/test/java/com/habit/app/TestFixtures.kt`

**Interfaces:**
- Produces: `Habit`, `Category`, `CheckIn`, `HabitDraft`, `HabitStats`, `MonthStats`, `DatePolicy.isEligible`, `HabitStatistics.forHabit`, `HabitStatistics.forMonth`.

- [ ] **Step 1: Write failing date-policy tests**

```kotlin
class DatePolicyTest {
    private val habit = Habit(
        id = 1,
        name = "背单词",
        iconKey = "book",
        themeColor = 0xFF8DB9CC,
        categoryId = 1,
        startEpochDay = LocalDate.of(2026, 7, 10).toEpochDay(),
        archivedEpochDay = LocalDate.of(2026, 7, 20).toEpochDay(),
        sortOrder = 0,
        createdAt = 1,
        updatedAt = 1,
    )

    @Test fun rejectsBeforeStart() {
        assertFalse(DatePolicy.isEligible(habit, LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 30)))
    }

    @Test fun acceptsBeforeArchive() {
        assertTrue(DatePolicy.isEligible(habit, LocalDate.of(2026, 7, 19), LocalDate.of(2026, 7, 30)))
    }

    @Test fun rejectsArchiveDateAndFuture() {
        assertFalse(DatePolicy.isEligible(habit, LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 30)))
        assertFalse(DatePolicy.isEligible(habit.copy(archivedEpochDay = null), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 30)))
    }
}
```

- [ ] **Step 2: Run the date-policy tests to verify failure**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*DatePolicyTest"
```

Expected: compilation fails because the domain types and `DatePolicy` do not exist.

- [ ] **Step 3: Implement domain types and date eligibility**

```kotlin
data class Habit(
    val id: Long,
    val name: String,
    val iconKey: String,
    val themeColor: Long,
    val categoryId: Long,
    val startEpochDay: Long,
    val archivedEpochDay: Long?,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class Category(
    val id: Long,
    val name: String,
    val isPreset: Boolean,
    val isHidden: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class CheckIn(
    val id: Long,
    val habitId: Long,
    val checkInEpochDay: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

data class HabitDraft(
    val name: String,
    val iconKey: String,
    val themeColor: Long,
    val categoryId: Long,
    val startEpochDay: Long,
)

object DatePolicy {
    fun isEligible(habit: Habit, date: LocalDate, today: LocalDate): Boolean {
        val epochDay = date.toEpochDay()
        return epochDay >= habit.startEpochDay &&
            epochDay < (habit.archivedEpochDay ?: Long.MAX_VALUE) &&
            !date.isAfter(today)
    }
}
```

- [ ] **Step 4: Write failing statistics tests**

```kotlin
class HabitStatisticsTest {
    @Test fun currentStreakFallsBackToYesterdayWhenTodayIsMissing() {
        val today = LocalDate.of(2026, 7, 30)
        val dates = setOf(
            LocalDate.of(2026, 7, 27).toEpochDay(),
            LocalDate.of(2026, 7, 28).toEpochDay(),
            LocalDate.of(2026, 7, 29).toEpochDay(),
        )
        assertEquals(3, HabitStatistics.forHabit(dates, today).currentStreak)
    }

    @Test fun monthRateUsesDailyEligibility() {
        val month = YearMonth.of(2026, 7)
        val habits = listOf(testHabit(start = 1, archived = null))
        val completed = mapOf(habits.single().id to setOf(LocalDate.of(2026, 7, 1).toEpochDay()))
        val result = HabitStatistics.forMonth(month, habits, completed, LocalDate.of(2026, 7, 2))
        assertEquals(2, result.expectedCount)
        assertEquals(1, result.completedCount)
        assertEquals(0.5f, result.completionRate)
    }
}
```

- [ ] **Step 5: Implement deterministic statistics**

```kotlin
data class HabitStats(val total: Int, val currentStreak: Int, val longestStreak: Int)
data class MonthStats(
    val completedCount: Int,
    val expectedCount: Int,
    val activeDays: Int,
    val completionRate: Float,
)

object HabitStatistics {
    fun forHabit(completedEpochDays: Set<Long>, today: LocalDate): HabitStats {
        val sorted = completedEpochDays.sorted()
        var longest = 0
        var run = 0
        var previous: Long? = null
        for (day in sorted) {
            run = if (previous != null && day == previous + 1) run + 1 else 1
            longest = maxOf(longest, run)
            previous = day
        }
        var cursor = if (today.toEpochDay() in completedEpochDays) today else today.minusDays(1)
        var current = 0
        while (cursor.toEpochDay() in completedEpochDays) {
            current += 1
            cursor = cursor.minusDays(1)
        }
        return HabitStats(sorted.size, current, longest)
    }

    fun forMonth(
        month: YearMonth,
        habits: List<Habit>,
        completedByHabit: Map<Long, Set<Long>>,
        today: LocalDate,
    ): MonthStats {
        val last = minOf(month.atEndOfMonth(), today)
        if (last.isBefore(month.atDay(1))) return MonthStats(0, 0, 0, 0f)
        var expected = 0
        val completedDates = mutableSetOf<Long>()
        var completed = 0
        for (dayNumber in 1..last.dayOfMonth) {
            val date = month.atDay(dayNumber)
            for (habit in habits) {
                if (DatePolicy.isEligible(habit, date, today)) {
                    expected += 1
                    if (date.toEpochDay() in completedByHabit[habit.id].orEmpty()) {
                        completed += 1
                        completedDates += date.toEpochDay()
                    }
                }
            }
        }
        return MonthStats(completed, expected, completedDates.size, if (expected == 0) 0f else completed.toFloat() / expected)
    }
}
```

Add the shared unit-test fixture:

```kotlin
package com.habit.app

import com.habit.app.domain.model.Habit

fun testHabit(
    id: Long = 1,
    start: Long = 1,
    archived: Long? = null,
    sortOrder: Int = 0,
): Habit = Habit(
    id = id,
    name = "测试习惯$id",
    iconKey = "book",
    themeColor = 0xFF8DB9CC,
    categoryId = 1,
    startEpochDay = start,
    archivedEpochDay = archived,
    sortOrder = sortOrder,
    createdAt = 100,
    updatedAt = 100,
)
```

- [ ] **Step 6: Run domain tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*DatePolicyTest" --tests "*HabitStatisticsTest"
```

Expected: all domain tests pass.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/habit/app/domain app/src/test/java/com/habit/app/domain
git commit -m "feat: add habit date and statistics rules"
```

---

### Task 3: Room Schema, DAO Contracts, and Preset Categories

**Files:**
- Create: `app/src/main/java/com/habit/app/data/local/Entities.kt`
- Create: `app/src/main/java/com/habit/app/data/local/HabitDao.kt`
- Create: `app/src/main/java/com/habit/app/data/local/CategoryDao.kt`
- Create: `app/src/main/java/com/habit/app/data/local/CheckInDao.kt`
- Create: `app/src/main/java/com/habit/app/data/local/HabitDatabase.kt`
- Create: `app/src/main/java/com/habit/app/data/local/EntityMappers.kt`
- Create: `app/src/androidTest/java/com/habit/app/data/local/HabitDatabaseTest.kt`

**Interfaces:**
- Consumes: domain models from Task 2.
- Produces: Room entities/DAOs, `HabitDatabase`, `PRESET_CATEGORIES`, entity-domain mapping extensions.

- [ ] **Step 1: Write failing database constraint tests**

```kotlin
@RunWith(AndroidJUnit4::class)
class HabitDatabaseTest {
    private lateinit var db: HabitDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HabitDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private suspend fun insertHabit(db: HabitDatabase): Long {
        val categoryId = db.categoryDao().insert(
            CategoryEntity(
                name = "学习",
                isPreset = false,
                isHidden = false,
                sortOrder = 0,
                createdAt = 100,
                updatedAt = 100,
            ),
        )
        return db.habitDao().insert(
            HabitEntity(
                name = "背单词",
                iconKey = "book",
                themeColor = 0xFF8DB9CC,
                categoryId = categoryId,
                startEpochDay = 20650,
                archivedEpochDay = null,
                sortOrder = 0,
                createdAt = 100,
                updatedAt = 100,
            ),
        )
    }

    @Test fun duplicateCheckInForSameHabitAndDayIsRejected() = runTest {
        val habitId = insertHabit(db)
        val checkIn = CheckInEntity(0, habitId, 20664, 100, 100)
        db.checkInDao().insert(checkIn)
        assertFailsWith<SQLiteConstraintException> { db.checkInDao().insert(checkIn) }
    }

    @Test fun deletingHabitCascadesCheckIns() = runTest {
        val habitId = insertHabit(db)
        db.checkInDao().insert(CheckInEntity(0, habitId, 20664, 100, 100))
        db.habitDao().deleteById(habitId)
        assertTrue(db.checkInDao().getForHabit(habitId).isEmpty())
    }
}
```

- [ ] **Step 2: Run the Room tests to verify failure**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.local.HabitDatabaseTest
```

Expected: compilation fails because Room types are absent.

- [ ] **Step 3: Implement entities and relationships**

```kotlin
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isPreset: Boolean,
    val isHidden: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "habits",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("categoryId")],
)
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconKey: String,
    val themeColor: Long,
    val categoryId: Long,
    val startEpochDay: Long,
    val archivedEpochDay: Long?,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "check_ins",
    foreignKeys = [ForeignKey(
        entity = HabitEntity::class,
        parentColumns = ["id"],
        childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["habitId", "checkInEpochDay"], unique = true)],
)
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val checkInEpochDay: Long,
    val createdAt: Long,
    val updatedAt: Long,
)
```

- [ ] **Step 4: Implement DAO contracts and database**

```kotlin
@Dao
interface CheckInDao {
    @Insert suspend fun insert(entity: CheckInEntity): Long
    @Query("DELETE FROM check_ins WHERE habitId = :habitId AND checkInEpochDay = :epochDay")
    suspend fun delete(habitId: Long, epochDay: Long): Int
    @Query("SELECT * FROM check_ins WHERE habitId = :habitId ORDER BY checkInEpochDay")
    suspend fun getForHabit(habitId: Long): List<CheckInEntity>
    @Query("SELECT * FROM check_ins WHERE checkInEpochDay BETWEEN :start AND :end")
    fun observeRange(start: Long, end: Long): Flow<List<CheckInEntity>>
}

@Database(
    entities = [CategoryEntity::class, HabitEntity::class, CheckInEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class HabitDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun habitDao(): HabitDao
    abstract fun checkInDao(): CheckInDao
}
```

Implement `HabitDao` and `CategoryDao` with observable sorted lists, get-by-ID, insert, update, archive, hide, delete, and category reassignment queries. Add a `@Transaction` method that reassigns every habit from one category to another and then deletes the empty custom category.

- [ ] **Step 5: Seed preset categories**

```kotlin
val PRESET_CATEGORIES = listOf("学习", "运动", "生活", "健康", "其他")

class PresetCategoryCallback(
    private val clock: Clock,
) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val now = clock.millis()
        PRESET_CATEGORIES.forEachIndexed { index, name ->
            db.execSQL(
                "INSERT INTO categories(name,isPreset,isHidden,sortOrder,createdAt,updatedAt) VALUES(?,1,0,?,?,?)",
                arrayOf(name, index, now, now),
            )
        }
    }
}
```

- [ ] **Step 6: Run database tests and export schema**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat kspDebugKotlin
```

Expected: Room tests pass and `app/schemas/com.habit.app.data.local.HabitDatabase/1.json` exists.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/habit/app/data/local app/src/androidTest/java/com/habit/app/data/local app/schemas
git commit -m "feat: add Room persistence schema"
```

---

### Task 4: Habit, Category, and Check-In Repositories

**Files:**
- Create: `app/src/main/java/com/habit/app/domain/repository/HabitRepository.kt`
- Create: `app/src/main/java/com/habit/app/domain/repository/CategoryRepository.kt`
- Create: `app/src/main/java/com/habit/app/domain/repository/CheckInRepository.kt`
- Create: `app/src/main/java/com/habit/app/data/repository/RoomHabitRepository.kt`
- Create: `app/src/main/java/com/habit/app/data/repository/RoomCategoryRepository.kt`
- Create: `app/src/main/java/com/habit/app/data/repository/RoomCheckInRepository.kt`
- Create: `app/src/androidTest/java/com/habit/app/data/repository/RoomCheckInRepositoryTest.kt`

**Interfaces:**
- Consumes: Room DAOs, domain models, `DatePolicy`.
- Produces: CRUD repositories and `ToggleResult`.

```kotlin
interface HabitRepository {
    fun observeAll(): Flow<List<Habit>>
    fun observeById(id: Long): Flow<Habit?>
    suspend fun create(draft: HabitDraft): Long
    suspend fun update(id: Long, draft: HabitDraft)
    suspend fun archive(id: Long, archivedEpochDay: Long)
    suspend fun delete(id: Long)
}

interface CategoryRepository {
    fun observeVisible(): Flow<List<Category>>
    fun observeAll(): Flow<List<Category>>
    suspend fun create(name: String): Long
    suspend fun rename(id: Long, name: String)
    suspend fun setPresetHidden(id: Long, hidden: Boolean)
    suspend fun migrateAndDelete(sourceId: Long, targetId: Long)
}

interface CheckInRepository {
    suspend fun toggle(habitId: Long, date: LocalDate, today: LocalDate): ToggleResult
}

sealed interface ToggleResult {
    data object Checked : ToggleResult
    data object Unchecked : ToggleResult
    data class Rejected(val reason: String) : ToggleResult
}
```

- [ ] **Step 1: Write failing repository tests**

```kotlin
class RoomCheckInRepositoryTest {
    private suspend fun repositoryWithHabit(start: LocalDate): RoomCheckInRepository {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HabitDatabase::class.java,
        ).allowMainThreadQueries().build()
        val categoryId = db.categoryDao().insert(
            CategoryEntity(
                name = "Study",
                isPreset = false,
                isHidden = false,
                sortOrder = 0,
                createdAt = 100,
                updatedAt = 100,
            ),
        )
        db.habitDao().insert(
            HabitEntity(
                name = "Vocabulary",
                iconKey = "book",
                themeColor = 0xFF8DB9CC,
                categoryId = categoryId,
                startEpochDay = start.toEpochDay(),
                archivedEpochDay = null,
                sortOrder = 0,
                createdAt = 100,
                updatedAt = 100,
            ),
        )
        return RoomCheckInRepository(
            db,
            Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC),
        )
    }

    @Test fun rejectsFutureAndBeforeStartDates() = runTest {
        val repository = repositoryWithHabit(start = LocalDate.of(2026, 7, 10))
        assertEquals(
            ToggleResult.Rejected("该日期不在习惯有效范围内"),
            repository.toggle(1, LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 30)),
        )
        assertEquals(
            ToggleResult.Rejected("不能打卡未来日期"),
            repository.toggle(1, LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 30)),
        )
    }

    @Test fun secondToggleRemovesExistingCheckIn() = runTest {
        val repository = repositoryWithHabit(start = LocalDate.of(2026, 7, 1))
        assertEquals(ToggleResult.Checked, repository.toggle(1, LocalDate.of(2026, 7, 29), LocalDate.of(2026, 7, 30)))
        assertEquals(ToggleResult.Unchecked, repository.toggle(1, LocalDate.of(2026, 7, 29), LocalDate.of(2026, 7, 30)))
    }
}
```

- [ ] **Step 2: Run repository tests to verify failure**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.repository.RoomCheckInRepositoryTest
```

Expected: compilation fails because repository implementations are absent.

- [ ] **Step 3: Implement repository validation and atomic toggling**

```kotlin
class RoomCheckInRepository(
    private val database: HabitDatabase,
    private val clock: Clock,
) : CheckInRepository {
    override suspend fun toggle(habitId: Long, date: LocalDate, today: LocalDate): ToggleResult =
        database.withTransaction {
            if (date.isAfter(today)) return@withTransaction ToggleResult.Rejected("不能打卡未来日期")
            val habit = database.habitDao().getById(habitId)?.toDomain()
                ?: return@withTransaction ToggleResult.Rejected("习惯不存在")
            if (!DatePolicy.isEligible(habit, date, today)) {
                return@withTransaction ToggleResult.Rejected("该日期不在习惯有效范围内")
            }
            val epochDay = date.toEpochDay()
            val removed = database.checkInDao().delete(habitId, epochDay)
            if (removed > 0) {
                ToggleResult.Unchecked
            } else {
                val now = clock.millis()
                database.checkInDao().insert(CheckInEntity(0, habitId, epochDay, now, now))
                ToggleResult.Checked
            }
        }
}
```

Add a per-habit `Mutex` or ViewModel interaction guard so rapid UI taps cannot overlap transactions. Keep the unique Room index as the final consistency boundary.

- [ ] **Step 4: Implement habit and category repositories**

For habit creation, trim the name, reject blank or names longer than 30 characters, assign `sortOrder = maxSortOrder + 1`, and use `clock.millis()` for audit fields. For category deletion, reject preset categories and call the DAO migration transaction. Use exact user-facing validation messages from the UI state.

- [ ] **Step 5: Run repository and Room tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
```

Expected: repository unit tests and Room instrumented tests pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/habit/app/domain/repository app/src/main/java/com/habit/app/data/repository app/src/androidTest/java/com/habit/app/data/repository
git commit -m "feat: add offline habit repositories"
```

---

### Task 5: Calendar Aggregation and Observable Screen Models

**Files:**
- Create: `app/src/main/java/com/habit/app/domain/model/CalendarModels.kt`
- Create: `app/src/main/java/com/habit/app/domain/repository/CalendarRepository.kt`
- Create: `app/src/main/java/com/habit/app/data/repository/RoomCalendarRepository.kt`
- Create: `app/src/androidTest/java/com/habit/app/data/repository/RoomCalendarRepositoryTest.kt`

**Interfaces:**
- Consumes: habits/check-ins `Flow`, `HabitStatistics`.
- Produces: `MonthSnapshot`, `DaySnapshot`, `HabitHistorySnapshot`, observable calendar APIs.

```kotlin
data class DayHabit(
    val habit: Habit,
    val checked: Boolean,
    val checkedAt: Long?,
)

data class DaySnapshot(
    val epochDay: Long,
    val habits: List<DayHabit>,
)

data class CalendarMark(val iconKey: String, val habitId: Long, val sortOrder: Int)

data class MonthSnapshot(
    val month: YearMonth,
    val marksByEpochDay: Map<Long, List<CalendarMark>>,
    val stats: MonthStats,
)

data class HabitHistorySnapshot(
    val habit: Habit,
    val completedEpochDays: Set<Long>,
    val stats: HabitStats,
)

interface CalendarRepository {
    fun observeMonth(month: YearMonth, today: LocalDate): Flow<MonthSnapshot>
    fun observeDay(date: LocalDate, today: LocalDate): Flow<DaySnapshot>
    fun observeHabitHistory(habitId: Long, today: LocalDate): Flow<HabitHistorySnapshot>
}
```

- [ ] **Step 1: Write failing aggregation tests**

```kotlin
@RunWith(AndroidJUnit4::class)
class RoomCalendarRepositoryTest {
    private lateinit var db: HabitDatabase
    private lateinit var repository: RoomCalendarRepository

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HabitDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomCalendarRepository(db.habitDao(), db.checkInDao())
        val categoryId = db.categoryDao().insert(
            CategoryEntity(name = "Study", isPreset = false, isHidden = false, sortOrder = 0, createdAt = 100, updatedAt = 100),
        )
        repeat(5) { index ->
            val habitId = db.habitDao().insert(
                HabitEntity(
                    name = "Habit $index",
                    iconKey = "icon_$index",
                    themeColor = 0xFF8DB9CC,
                    categoryId = categoryId,
                    startEpochDay = LocalDate.of(2026, 7, 1).toEpochDay(),
                    archivedEpochDay = null,
                    sortOrder = index + 1,
                    createdAt = 100,
                    updatedAt = 100,
                ),
            )
            db.checkInDao().insert(
                CheckInEntity(0, habitId, LocalDate.of(2026, 7, 29).toEpochDay(), 100, 100),
            )
        }
    }

    @After fun tearDown() = db.close()

@Test fun marksAreSortedAndOverflowCountIsDerivable() = runTest {
    val snapshot = repository.observeMonth(YearMonth.of(2026, 7), LocalDate.of(2026, 7, 30)).first()
    val marks = snapshot.marksByEpochDay[LocalDate.of(2026, 7, 29).toEpochDay()].orEmpty()
    assertEquals(listOf(1, 2, 3, 4, 5), marks.map { it.sortOrder })
    assertEquals(4, marks.take(4).size)
    assertEquals(1, (marks.size - 4).coerceAtLeast(0))
}

@Test fun daySnapshotExcludesNotStartedAndArchivedHabits() = runTest {
    val snapshot = repository.observeDay(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 30)).first()
    assertTrue(snapshot.habits.all { it.habit.startEpochDay <= snapshot.epochDay })
    assertTrue(snapshot.habits.all { it.habit.archivedEpochDay == null || snapshot.epochDay < it.habit.archivedEpochDay })
}
}
```

- [ ] **Step 2: Run aggregation tests to verify failure**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.repository.RoomCalendarRepositoryTest
```

Expected: compilation fails because snapshot types and repository are absent.

- [ ] **Step 3: Implement aggregation with Flow**

Combine the sorted habit stream and range check-in stream. Month marks use `DatePolicy` so they never represent a future or inactive check-in. Day snapshots use display eligibility only: `startEpochDay <= selectedEpochDay < archivedEpochDay` (when archived), so future dates remain viewable. The screen derives its disabled check-in controls from `selectedDate.isAfter(today)`; `RoomCheckInRepository.toggle` remains the mutation boundary that rejects future dates. Map check-ins by `(habitId, epochDay)`, always sort marks by `Habit.sortOrder`, and return the full mark list so the UI alone renders the first four and computes `+N`.

History must observe only the requested habit's check-ins rather than a full-table range:

```kotlin
@Query("SELECT * FROM check_ins WHERE habitId = :habitId ORDER BY checkInEpochDay")
fun observeForHabit(habitId: Long): Flow<List<CheckInEntity>>
```

```kotlin
override fun observeDay(date: LocalDate, today: LocalDate): Flow<DaySnapshot> =
    combine(habitDao.observeAll(), checkInDao.observeRange(date.toEpochDay(), date.toEpochDay())) {
            habitEntities, checkIns ->
        val byHabit = checkIns.associateBy { it.habitId }
        val epochDay = date.toEpochDay()
        val habits = habitEntities
            .map { it.toDomain() }
            .filter { habit ->
                epochDay >= habit.startEpochDay &&
                    epochDay < (habit.archivedEpochDay ?: Long.MAX_VALUE)
            }
            .sortedBy { it.sortOrder }
            .map { habit ->
                val checkIn = byHabit[habit.id]
                DayHabit(habit, checkIn != null, checkIn?.createdAt)
            }
        DaySnapshot(date.toEpochDay(), habits)
    }
```

- [ ] **Step 4: Run aggregation and statistics tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*HabitStatisticsTest"
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.repository.RoomCalendarRepositoryTest
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/habit/app/domain/model/CalendarModels.kt app/src/main/java/com/habit/app/domain/repository/CalendarRepository.kt app/src/main/java/com/habit/app/data/repository/RoomCalendarRepository.kt app/src/androidTest/java/com/habit/app/data/repository/RoomCalendarRepositoryTest.kt
git commit -m "feat: aggregate calendar and habit history"
```

---

### Task 6: Persistent Built-In Accent Themes

**Files:**
- Create: `app/src/main/java/com/habit/app/data/preferences/ThemePreferencesRepository.kt`
- Create: `app/src/main/java/com/habit/app/ui/theme/HabitThemeId.kt`
- Create: `app/src/main/java/com/habit/app/ui/theme/HabitColorSchemes.kt`
- Create: `app/src/main/java/com/habit/app/ui/theme/HabitTheme.kt`
- Create: `app/src/test/java/com/habit/app/ui/theme/HabitColorSchemesTest.kt`
- Create: `app/src/androidTest/java/com/habit/app/data/preferences/ThemePreferencesRepositoryTest.kt`

**Interfaces:**
- Produces: `HabitThemeId`, `ThemePreferencesRepository.theme: Flow<HabitThemeId>`, `setTheme`, `HabitTheme(themeId, content)`.

- [ ] **Step 1: Write failing theme preference tests**

```kotlin
class ThemePreferencesRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun createStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
            produceFile = { File(temporaryFolder.root, "theme.preferences_pb") },
        )

    @Test fun missingAndUnknownValuesFallBackToSkyBlue() = runTest {
        val store = createStore()
        store.edit { it[stringPreferencesKey("theme_id")] = "UNKNOWN" }
        val repository = ThemePreferencesRepository(store)
        assertEquals(HabitThemeId.SKY_BLUE, repository.theme.first())
    }

    @Test fun selectedThemeSurvivesRepositoryRecreation() = runTest {
        val store = createStore()
        ThemePreferencesRepository(store).setTheme(HabitThemeId.SAGE_GREEN)
        assertEquals(HabitThemeId.SAGE_GREEN, ThemePreferencesRepository(store).theme.first())
    }
}
```

- [ ] **Step 2: Run the theme tests to verify failure**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.preferences.ThemePreferencesRepositoryTest
```

Expected: compilation fails because theme types are absent.

- [ ] **Step 3: Implement stable IDs and DataStore mapping**

```kotlin
enum class HabitThemeId {
    SKY_BLUE, SOFT_PINK, SAGE_GREEN, MIST_PURPLE, NEUTRAL_GRAY;

    companion object {
        fun fromStored(value: String?): HabitThemeId =
            entries.firstOrNull { it.name == value } ?: SKY_BLUE
    }
}

class ThemePreferencesRepository(private val dataStore: DataStore<Preferences>) {
    private val themeKey = stringPreferencesKey("theme_id")

    val theme: Flow<HabitThemeId> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { HabitThemeId.fromStored(it[themeKey]) }
        .distinctUntilChanged()

    suspend fun setTheme(theme: HabitThemeId) {
        dataStore.edit { it[themeKey] = theme.name }
    }
}
```

- [ ] **Step 4: Implement theme tokens**

Create five immutable accent palettes. Keep background `#FCFEFE`, primary text
`#30434D`, and emoji colors unchanged. Use `#263238` as `onPrimary` for every
palette so normal-size text on each low-saturation accent meets WCAG AA
contrast (`>= 4.5:1`). Use these sky-blue defaults:

```kotlin
val SkyBlueScheme = lightColorScheme(
    primary = Color(0xFF8DB9CC),
    onPrimary = Color(0xFF263238),
    primaryContainer = Color(0xFFDDEEF5),
    onPrimaryContainer = Color(0xFF30434D),
    background = Color(0xFFFCFEFE),
    onBackground = Color(0xFF30434D),
    surface = Color(0xFFFCFEFE),
    onSurface = Color(0xFF30434D),
)
```

Map `SOFT_PINK` to `#DF988F`, `SAGE_GREEN` to `#A8C39D`, `MIST_PURPLE` to `#AAA0C1`, and `NEUTRAL_GRAY` to `#9CA5A7`, each with a low-saturation container and contrast-safe foreground.

- [ ] **Step 5: Run theme tests**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.data.preferences.ThemePreferencesRepositoryTest
```

Expected: all theme tests pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/habit/app/data/preferences app/src/main/java/com/habit/app/ui/theme app/src/test/java/com/habit/app/ui/theme app/src/androidTest/java/com/habit/app/data/preferences
git commit -m "feat: add persistent built-in themes"
```

---

### Task 7: Application Container, Navigation, and First-Run Flow

**Files:**
- Create: `app/src/main/java/com/habit/app/HabitApplication.kt`
- Create: `app/src/main/java/com/habit/app/di/AppContainer.kt`
- Modify: `app/src/main/java/com/habit/app/MainActivity.kt`
- Create: `app/src/main/java/com/habit/app/ui/navigation/HabitDestination.kt`
- Create: `app/src/main/java/com/habit/app/ui/navigation/HabitNavHost.kt`
- Create: `app/src/main/java/com/habit/app/ui/navigation/HabitApp.kt`
- Create: `app/src/main/java/com/habit/app/ui/welcome/WelcomeScreen.kt`
- Create: `app/src/main/java/com/habit/app/ui/welcome/WelcomeViewModel.kt`
- Create: `app/src/androidTest/java/com/habit/app/HabitTestRobot.kt`
- Create: `app/src/androidTest/java/com/habit/app/ui/welcome/WelcomeFlowTest.kt`

**Interfaces:**
- Consumes: repositories and theme flow.
- Produces: application-scoped dependencies, destinations `Welcome`, `Calendar`, `Habits`, `HabitEditor`, `HabitDetail`, `Settings`, `Categories`.

- [ ] **Step 1: Write the failing first-run UI test**

```kotlin
@RunWith(AndroidJUnit4::class)
class WelcomeFlowTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private val robot by lazy { HabitTestRobot(composeRule) }

    @Before fun reset() = robot.resetDatabase()

    @Test fun emptyDatabaseShowsWelcomeAndCreateAction() {
        composeRule.onNodeWithText("把每一天，积攒成喜欢的样子").assertIsDisplayed()
        composeRule.onNodeWithText("创建我的第一个习惯").assertHasClickAction()
    }
}
```

- [ ] **Step 2: Run the UI test to verify failure**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.ui.welcome.WelcomeFlowTest
```

Expected: test fails because the welcome screen is absent.

- [ ] **Step 3: Implement the application container**

```kotlin
class AppContainer(context: Context) {
    private val clock: Clock = Clock.systemUTC()
    val database = Room.databaseBuilder(context, HabitDatabase::class.java, "habit.db").build()
    val habitRepository: HabitRepository = RoomHabitRepository(database.habitDao(), clock)
    val categoryRepository: CategoryRepository = RoomCategoryRepository(database)
    val checkInRepository: CheckInRepository = RoomCheckInRepository(database, clock)
    val calendarRepository: CalendarRepository = RoomCalendarRepository(database.habitDao(), database.checkInDao())
    val themeRepository = ThemePreferencesRepository(context.themeDataStore)
}

class HabitApplication : Application() {
    val container by lazy { AppContainer(this) }
}
```

Declare `android:name=".HabitApplication"` and `android:theme="@style/Theme.Habit"` in the manifest. Do not add `android.permission.INTERNET`.

`HabitTestRobot` receives a `ComposeContentTestRule`, obtains the production container from the instrumentation target application, and provides database reset, navigation, seeding, interaction, and assertion methods:

```kotlin
class HabitTestRobot(val rule: ComposeContentTestRule) {
    private val application: HabitApplication
        get() = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as HabitApplication

    val container: AppContainer
        get() = application.container

    fun resetDatabase() = runBlocking {
        container.database.clearAllTables()
        val now = 100L
        PRESET_CATEGORIES.forEachIndexed { index, name ->
            container.database.categoryDao().insert(
                CategoryEntity(
                    name = name,
                    isPreset = true,
                    isHidden = false,
                    sortOrder = index,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        container.themeRepository.setTheme(HabitThemeId.SKY_BLUE)
    }
}
```

Each UI test calls `robot.resetDatabase()` in `@Before` and seeds the category records required by that test. Later UI tasks extend this one robot instead of defining free helper functions.

- [ ] **Step 4: Implement navigation and first-run routing**

Use typed route objects or stable string routes. `HabitApp` observes whether any habit exists; an empty database starts at `Welcome`, otherwise at `Calendar`. Bottom navigation is visible only on `Calendar`, `Habits`, and `Settings`.

- [ ] **Step 5: Implement the welcome screen**

Match the approved copy and layout: sky-blue gradient hero, sprout emoji, headline, one paragraph, primary create action, and “数据仅保存在你的手机上”. Add test tags `welcome_screen` and `welcome_create`.

- [ ] **Step 6: Run first-run test and unit suite**

Run:

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
```

Expected: welcome UI test and all earlier tests pass.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/AndroidManifest.xml app/src/main/java/com/habit/app/HabitApplication.kt app/src/main/java/com/habit/app/MainActivity.kt app/src/main/java/com/habit/app/di app/src/main/java/com/habit/app/ui/navigation app/src/main/java/com/habit/app/ui/welcome app/src/androidTest/java/com/habit/app/ui/welcome
git commit -m "feat: add app shell and first-run flow"
```

---

### Task 8: Habit Creation, Editing, Categories, Archive, and Delete

**Files:**
- Create: `app/src/main/java/com/habit/app/ui/habits/HabitEditorViewModel.kt`
- Create: `app/src/main/java/com/habit/app/ui/habits/HabitEditorScreen.kt`
- Create: `app/src/main/java/com/habit/app/ui/habits/HabitListViewModel.kt`
- Create: `app/src/main/java/com/habit/app/ui/habits/HabitListScreen.kt`
- Create: `app/src/main/java/com/habit/app/ui/categories/CategoryViewModel.kt`
- Create: `app/src/main/java/com/habit/app/ui/categories/CategoryScreen.kt`
- Create: `app/src/main/java/com/habit/app/ui/components/EmojiPicker.kt`
- Create: `app/src/main/java/com/habit/app/ui/components/HabitColorPicker.kt`
- Modify: `app/src/androidTest/java/com/habit/app/HabitTestRobot.kt`
- Create: `app/src/androidTest/java/com/habit/app/ui/habits/HabitCrudFlowTest.kt`

**Interfaces:**
- Consumes: `HabitRepository`, `CategoryRepository`, `HabitDraft`.
- Produces: complete habit and category management UI.

- [ ] **Step 1: Write the failing CRUD flow test**

```kotlin
@Test fun createsHabitAndShowsItInCategoryList() {
    composeRule.onNodeWithTag("welcome_create").performClick()
    composeRule.onNodeWithTag("habit_name").performTextInput("背单词")
    composeRule.onNodeWithTag("emoji_book").performClick()
    composeRule.onNodeWithText("学习").performClick()
    composeRule.onNodeWithTag("save_habit").performClick()
    composeRule.onNodeWithText("背单词").assertIsDisplayed()
    composeRule.onNodeWithText("学习").assertIsDisplayed()
}

@Test fun invalidNameShowsInlineError() {
    composeRule.onNodeWithTag("save_habit").performClick()
    composeRule.onNodeWithText("请输入 1～30 个字符的习惯名称").assertIsDisplayed()
}
```

- [ ] **Step 2: Run CRUD UI tests to verify failure**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.ui.habits.HabitCrudFlowTest
```

Expected: tests fail because editor and list UI are absent.

- [ ] **Step 3: Implement immutable editor state and validation**

```kotlin
data class HabitEditorUiState(
    val name: String = "",
    val iconKey: String = "book",
    val themeColor: Long = 0xFF8DB9CC,
    val categoryId: Long? = null,
    val startEpochDay: Long = LocalDate.now().toEpochDay(),
    val nameError: String? = null,
    val saving: Boolean = false,
)

fun validateHabitName(raw: String): String? {
    val value = raw.trim()
    return if (value.length in 1..30) null else "请输入 1～30 个字符的习惯名称"
}
```

Expose event methods for name, emoji, per-habit color, category, start date, and save. Guard save while `saving` is true.

Add these robot methods with fixed semantics tags:

```kotlin
fun createHabit(name: String, emojiTag: String, category: String) {
    rule.onNodeWithTag("habit_name").performTextInput(name)
    rule.onNodeWithTag(emojiTag).performClick()
    rule.onNodeWithText(category).performClick()
    rule.onNodeWithTag("save_habit").performClick()
}

fun assertTextVisible(text: String) {
    rule.onNodeWithText(text).assertIsDisplayed()
}
```

- [ ] **Step 4: Implement the approved editor and list UI**

The editor includes a name field, built-in grouped emoji picker, per-habit identification color picker, category selector, start-date picker, “每天重复” explanation, and save action. The list groups active habits by category and shows archived habits in a collapsed section.

- [ ] **Step 5: Implement category management**

Support creating and renaming custom categories, hiding/restoring presets, and migration-before-delete. Show a target-category selector before deleting a non-empty custom category.

- [ ] **Step 6: Implement archive and delete confirmations**

Archive uses the device-local current `epochDay`. Delete dialog copy explicitly states that all historical check-ins will be removed. On success, navigate back and refresh through Room `Flow`.

- [ ] **Step 7: Run CRUD and repository tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
```

Expected: editor, list, validation, archive, delete, and category tests pass.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/java/com/habit/app/ui/habits app/src/main/java/com/habit/app/ui/categories app/src/main/java/com/habit/app/ui/components app/src/androidTest/java/com/habit/app/ui/habits
git commit -m "feat: add habit and category management"
```

---

### Task 9: Aggregate Calendar and Historical Check-In Sheet

**Files:**
- Create: `app/src/main/java/com/habit/app/ui/calendar/CalendarViewModel.kt`
- Create: `app/src/main/java/com/habit/app/ui/calendar/CalendarScreen.kt`
- Create: `app/src/main/java/com/habit/app/ui/calendar/MonthGrid.kt`
- Create: `app/src/main/java/com/habit/app/ui/calendar/DayCheckInSheet.kt`
- Create: `app/src/main/java/com/habit/app/ui/components/HabitEmoji.kt`
- Modify: `app/src/androidTest/java/com/habit/app/HabitTestRobot.kt`
- Create: `app/src/androidTest/java/com/habit/app/ui/calendar/CalendarFlowTest.kt`

**Interfaces:**
- Consumes: `CalendarRepository`, `CheckInRepository`.
- Produces: month-first home, up to four emoji marks plus `+N`, today/historical check-in flow.

- [ ] **Step 1: Write the failing calendar flow tests**

```kotlin
@Test fun dayCellShowsFourMarksAndOverflow() {
    robot.seedFiveCompletedHabits(LocalDate.of(2026, 7, 29))
    composeRule.onNodeWithTag("day_2026-07-29").assertIsDisplayed()
    composeRule.onNodeWithTag("day_2026-07-29_mark_0").assertIsDisplayed()
    composeRule.onNodeWithTag("day_2026-07-29_mark_3").assertIsDisplayed()
    composeRule.onNodeWithText("+1").assertIsDisplayed()
}

@Test fun pastDateCanToggleAndFutureDateIsDisabled() {
    robot.seedFiveCompletedHabits(LocalDate.of(2026, 7, 27))
    composeRule.onNodeWithTag("day_2026-07-28").performClick()
    composeRule.onNodeWithTag("checkin_habit_1").performClick()
    composeRule.onNodeWithTag("day_2026-07-28_mark_0").assertIsDisplayed()
    composeRule.onNodeWithTag("day_2026-07-31").performClick()
    composeRule.onNodeWithTag("checkin_habit_1").assertIsNotEnabled()
}
```

- [ ] **Step 2: Run calendar tests to verify failure**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.ui.calendar.CalendarFlowTest
```

Expected: tests fail because the calendar screen is absent.

- [ ] **Step 3: Implement calendar UI state and ViewModel**

```kotlin
data class CalendarUiState(
    val visibleMonth: YearMonth,
    val selectedDate: LocalDate,
    val month: MonthSnapshot? = null,
    val day: DaySnapshot? = null,
    val togglingHabitIds: Set<Long> = emptySet(),
    val message: String? = null,
)
```

The ViewModel derives `today` from an injected device-local date provider, not from UTC. Switching month updates the `observeMonth` collection; selecting a date updates `observeDay`. `toggle(habitId)` ignores a second click while that habit is in `togglingHabitIds`.

Extend the test robot with deterministic seeding:

```kotlin
fun seedFiveCompletedHabits(date: LocalDate) = runBlocking {
    val application = InstrumentationRegistry.getInstrumentation()
        .targetContext.applicationContext as HabitApplication
    val container = application.container
    val categoryId = container.categoryRepository.create("Test")
    repeat(5) { index ->
        val habitId = container.habitRepository.create(
            HabitDraft(
                name = "Habit $index",
                iconKey = "icon_$index",
                themeColor = 0xFF8DB9CC,
                categoryId = categoryId,
                startEpochDay = date.minusDays(1).toEpochDay(),
            ),
        )
        container.checkInRepository.toggle(habitId, date, date)
    }
}
```

- [ ] **Step 4: Implement the month-first UI**

Render month title, previous/next controls, active days, completion rate, and current streak summary. `MonthGrid` always shows seven columns. Each day cell renders the first four sorted `CalendarMark` emojis and computes overflow as `(marks.size - 4).coerceAtLeast(0)`.

- [ ] **Step 5: Implement the day check-in sheet**

Show the localized date, weekday, completed count, all eligible habits, completion timestamps in local time, and a round check button. Future dates remain viewable but disabled. The explanatory text states that补签 belongs to the selected business date.

- [ ] **Step 6: Run calendar, repository, and statistics tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
```

Expected: all tests pass, including rapid-toggle protection and `+N`.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/habit/app/ui/calendar app/src/main/java/com/habit/app/ui/components/HabitEmoji.kt app/src/androidTest/java/com/habit/app/ui/calendar
git commit -m "feat: add aggregate calendar check-ins"
```

---

### Task 10: Per-Habit Calendar, Statistics, Settings, and Theme Picker

**Files:**
- Create: `app/src/main/java/com/habit/app/ui/habits/HabitDetailViewModel.kt`
- Create: `app/src/main/java/com/habit/app/ui/habits/HabitDetailScreen.kt`
- Create: `app/src/main/java/com/habit/app/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/habit/app/ui/settings/SettingsScreen.kt`
- Create: `app/src/main/java/com/habit/app/ui/settings/ThemePicker.kt`
- Create: `app/src/androidTest/java/com/habit/app/ui/settings/ThemePersistenceTest.kt`
- Create: `app/src/androidTest/java/com/habit/app/ui/habits/HabitDetailTest.kt`

**Interfaces:**
- Consumes: `observeHabitHistory`, `ThemePreferencesRepository`.
- Produces: per-habit month calendar/stats and settings with immediate persistent theme switching.

- [ ] **Step 1: Write failing detail and theme tests**

```kotlin
@Test fun detailShowsThreeStatisticsAndCompletedDays() {
    composeRule.onNodeWithText("背单词").performClick()
    composeRule.onNodeWithText("累计完成").assertIsDisplayed()
    composeRule.onNodeWithText("当前连续").assertIsDisplayed()
    composeRule.onNodeWithText("最长连续").assertIsDisplayed()
    composeRule.onNodeWithTag("habit_day_2026-07-29").assertIsDisplayed()
}

@Test fun themeSelectionAppliesImmediatelyAndSurvivesRecreation() {
    composeRule.onNodeWithText("设置").performClick()
    composeRule.onNodeWithTag("theme_SAGE_GREEN").performClick()
    composeRule.onNodeWithTag("theme_preview").assertExists()
    composeRule.activityRule.scenario.recreate()
    composeRule.onNodeWithTag("theme_SAGE_GREEN_selected").assertExists()
}
```

- [ ] **Step 2: Run detail and theme UI tests to verify failure**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.habit.app.ui.habits.HabitDetailTest,com.habit.app.ui.settings.ThemePersistenceTest
```

Expected: tests fail because detail and settings screens are absent.

- [ ] **Step 3: Implement habit detail**

The detail ViewModel observes `HabitHistorySnapshot`. The screen renders the habit emoji, category, total/current/longest statistics, a single-habit month grid, monthly progress, edit menu, archive action, and delete action.

- [ ] **Step 4: Implement settings and theme picker**

Render five labeled theme circles: “天蓝”, “柔粉”, “鼠尾草绿”, “雾紫”, and “中性灰”. Selecting a circle updates the in-memory `StateFlow` immediately, calls `setTheme`, and marks the selected item. Keep the app background neutral and never rewrite per-habit `themeColor`.

- [ ] **Step 5: Implement local-data and version sections**

Show “数据仅保存在本机，卸载应用会同时删除记录”, version `0.1.0 · 内测版`, and the category-management entry. Do not add destructive data-management actions to the MVP.

- [ ] **Step 6: Run detail, theme, and full regression tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
```

Expected: all tests pass; the UI test verifies Activity recreation and the DataStore repository test verifies persisted restoration.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/habit/app/ui/habits/HabitDetailViewModel.kt app/src/main/java/com/habit/app/ui/habits/HabitDetailScreen.kt app/src/main/java/com/habit/app/ui/settings app/src/androidTest/java/com/habit/app/ui/settings app/src/androidTest/java/com/habit/app/ui/habits/HabitDetailTest.kt
git commit -m "feat: add habit detail and app settings"
```

---

### Task 11: Accessibility, Device Verification, APK, and Handoff

**Files:**
- Modify: Compose screen files created in Tasks 7–10.
- Create: `app/src/androidTest/java/com/habit/app/ui/EndToEndMvpTest.kt`
- Create: `docs/INSTALL.md`
- Create: `docs/TESTING.md`
- Create: `outputs/Habit-0.1.0-debug.apk`

**Interfaces:**
- Consumes: completed application.
- Produces: tested installable APK, installation guide, test evidence.

- [ ] **Step 1: Write the end-to-end acceptance test**

```kotlin
@RunWith(AndroidJUnit4::class)
class EndToEndMvpTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private val robot by lazy { HabitTestRobot(composeRule) }

    private fun createHabit(name: String, emojiTag: String, category: String) {
        composeRule.onNodeWithTag("welcome_create").performClick()
        robot.createHabit(name, emojiTag, category)
    }

    private fun openDate(isoDate: String) {
        composeRule.onNodeWithTag("day_$isoDate").performClick()
    }

    private fun toggleHabit(name: String) {
        composeRule.onNodeWithText(name).performClick()
    }

    private fun openHabit(name: String) {
        composeRule.onNodeWithText("习惯").performClick()
        composeRule.onNodeWithText(name).performClick()
    }

    private fun assertTextVisible(text: String) = robot.assertTextVisible(text)
    private fun openSettings() = composeRule.onNodeWithText("设置").performClick()
    private fun selectTheme(name: String) = composeRule.onNodeWithText(name).performClick()
    private fun assertThemeSelected(id: String) {
        composeRule.onNodeWithTag("theme_${id}_selected").assertExists()
    }

@Test fun mvpUserJourneyPersistsAcrossRecreation() {
    createHabit(name = "背单词", emojiTag = "emoji_book", category = "学习")
    openDate("2026-07-30")
    toggleHabit("背单词")
    openHabit("背单词")
    assertTextVisible("累计完成")
    openSettings()
    selectTheme("天蓝")
    composeRule.activityRule.scenario.recreate()
    assertTextVisible("背单词")
    assertThemeSelected("SKY_BLUE")
}
}
```

- [ ] **Step 2: Add accessibility and adaptive-layout checks**

Give every icon-only control a Chinese content description, enforce at least 48 dp touch targets, use Material typography that respects system font scaling, handle status/navigation bar insets, and keep calendar cells legible at 1.3× font scale. Test portrait widths 360 dp and 480 dp.

- [ ] **Step 3: Run all automated verification**

Run:

```powershell
.\gradlew.bat clean testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug
```

Expected: all unit/instrumented tests pass, lint has no errors, and the debug APK is produced.

- [ ] **Step 4: Install on Redmi K70 and run the manual checklist**

Enable USB debugging and run:

```powershell
$adb = 'C:\Users\15599\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb devices
& $adb install -r 'app\build\outputs\apk\debug\app-debug.apk'
```

Verify: first launch, create/edit/archive/delete habit, custom category migration, today check-in, past补签, future disabled state, aggregate marks and `+N`, per-habit statistics, all five themes, app restart persistence, and no visible overflow on Redmi K70.

- [ ] **Step 5: Record installation and test documentation**

`docs/INSTALL.md` must include USB/APK installation steps, the unknown-source permission note, package ID, APK location, and the uninstall-data-loss warning. `docs/TESTING.md` must record the exact Gradle command, device model, HyperOS version, result date, and any known non-blocking limitations.

- [ ] **Step 6: Copy the verified APK to outputs**

Run:

```powershell
Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination 'outputs\Habit-0.1.0-debug.apk'
Get-FileHash -Algorithm SHA256 -LiteralPath 'outputs\Habit-0.1.0-debug.apk'
```

Expected: the APK exists under `outputs` and a SHA-256 hash is printed for handoff.

- [ ] **Step 7: Commit**

```powershell
git add app/src docs/INSTALL.md docs/TESTING.md
git commit -m "test: verify Habit MVP and document installation"
```

Do not commit the APK; keep it as a user-facing output artifact.

---

## Final Verification Gate

Before claiming completion, run:

```powershell
git status --short
.\gradlew.bat clean testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug
Get-FileHash -Algorithm SHA256 -LiteralPath 'outputs\Habit-0.1.0-debug.apk'
```

Completion requires:

1. All automated tasks exit successfully.
2. The Redmi K70 manual checklist is recorded in `docs/TESTING.md`.
3. `outputs/Habit-0.1.0-debug.apk` matches the just-built APK.
4. The working tree contains no unintended changes.
5. No network permission or out-of-scope feature is present.
