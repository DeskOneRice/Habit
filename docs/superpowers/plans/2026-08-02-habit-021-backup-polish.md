# Habit 0.2.1 Backup and Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver Habit 0.2.1 with reliable JSON import/export, a configurable and migratable backup folder, a near-white sky-blue theme, a correct Chinese date picker, and a stable Emoji interest category.

**Architecture:** Keep Room and DataStore as the live private data sources. Add a pure backup codec and merge planner, a transactional Room backup repository, and an Android document-storage boundary for MediaStore/SAF. UI fixes remain isolated in focused Compose components and pure calendar/color helpers so JVM tests cover the risky behavior.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose Material 3, Room 2.8.4, Preferences DataStore 1.2.1, Android Storage Access Framework, MediaStore Downloads, kotlinx.serialization JSON tree API 1.8.1, JUnit 4.

## Global Constraints

- Package name remains `com.habit.app`.
- Version becomes `0.2.1`, versionCode `3`.
- Minimum SDK remains 23 and target SDK remains 36.
- Database changes must use explicit Room migrations; never enable `fallbackToDestructiveMigration`.
- Default backup location is `内部存储 / Download / Habit / 数据备份`.
- Backup format is unencrypted, versioned JSON with `format=habit-backup` and `schemaVersion=1`.
- Import offers replace and merge; merge conflicts use the larger `updatedAt`.
- Live Room data never moves to public storage.
- Existing habit colors remain user-controlled and are not replaced by the global theme.
- Every implementation task follows red-green-refactor and ends in a focused commit.

---

### Task 1: Stabilize Emoji categories

**Files:**
- Modify: `app/src/main/java/com/habit/app/ui/emoji/EmojiCatalog.kt`
- Modify: `app/src/main/java/com/habit/app/ui/components/EmojiPicker.kt`
- Modify: `app/src/test/java/com/habit/app/ui/emoji/EmojiCatalogTest.kt`

**Interfaces:**
- Consumes: `EmojiCatalog.options`, `EmojiCatalog.search(query, category)`.
- Produces: `EmojiCatalog.pickerOptions(query, category, recentKeys): List<EmojiOption>` with unique keys and `pickerItemKey(category, key): String`.

- [ ] **Step 1: Add failing uniqueness and interest-category tests**

```kotlin
@Test
fun everyPickerCategoryHasUniqueStableKeys() {
    EmojiCategory.entries.filterNot { it == EmojiCategory.RECENT }.forEach { category ->
        val keys = EmojiCatalog.search("", category).map(EmojiOption::key)
        assertEquals("duplicate keys in $category", keys.distinct(), keys)
    }
}

@Test
fun hobbyCategoryCanBuildEveryPickerItemKey() {
    val keys = EmojiCatalog.search("", EmojiCategory.HOBBY)
        .map { pickerItemKey(EmojiCategory.HOBBY, it.key) }
    assertEquals(12, keys.size)
    assertEquals(keys.size, keys.distinct().size)
}

@Test
fun recentKeysAreNormalizedAndDeduplicatedBeforeRendering() {
    val rows = EmojiCatalog.pickerOptions("", EmojiCategory.RECENT, listOf("emoji:⭐", "emoji:⭐"))
    assertEquals(listOf("emoji:⭐"), rows.map(EmojiOption::key))
}
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run:

```powershell
$env:JAVA_HOME='D:\MySoftware\Java\jdk-17'; $env:ANDROID_HOME='D:\MySoftware\Android\AndroidSdk'; & 'D:\MySoftware\gradle\gradle-9.5.0\bin\gradle.bat' --offline --gradle-user-home .gradle-user :app:testDebugUnitTest --tests 'com.habit.app.ui.emoji.EmojiCatalogTest'
```

Expected: FAIL because `pickerItemKey` and `pickerOptions` do not exist.

- [ ] **Step 3: Implement normalized picker rows and category-scoped keys**

```kotlin
fun pickerItemKey(category: EmojiCategory, key: String): String = "${category.name}:$key"

fun EmojiCatalog.pickerOptions(
    query: String,
    category: EmojiCategory,
    recentKeys: List<String>,
): List<EmojiOption> = if (category == EmojiCategory.RECENT) {
    recentKeys.distinct().map { key ->
        options.firstOrNull { it.key == key }
            ?: EmojiOption(key.removePrefix("emoji:"), "最近使用", EmojiCategory.RECENT)
    }.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
} else {
    search(query, category)
}
```

Use `key = { pickerItemKey(category, it.key) }` in `LazyVerticalGrid`. Remove the duplicated global catalog key by changing the health “防晒” icon from `🧴` to `🧢`, preserving the daily “护肤” entry.

- [ ] **Step 4: Run the focused test and the full Emoji suite**

Expected: all `EmojiCatalogTest` methods PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/habit/app/ui/emoji/EmojiCatalog.kt app/src/main/java/com/habit/app/ui/components/EmojiPicker.kt app/src/test/java/com/habit/app/ui/emoji/EmojiCatalogTest.kt
git commit -m "fix: stabilize emoji category switching"
```

### Task 2: Replace the broken Material date picker

**Files:**
- Create: `app/src/main/java/com/habit/app/ui/components/HabitDatePicker.kt`
- Create: `app/src/test/java/com/habit/app/ui/components/HabitDatePickerTest.kt`
- Modify: `app/src/main/java/com/habit/app/ui/habits/HabitEditorScreen.kt`

**Interfaces:**
- Produces: `buildMonthCells(month: YearMonth): List<LocalDate?>` and `HabitDatePickerDialog(selectedDate, onConfirm, onDismiss)`.
- Consumes: editor `startEpochDay` and `onStartDateChange(epochDay)`.

- [ ] **Step 1: Add failing calendar-grid tests**

```kotlin
@Test
fun august2026StartsWithFiveEmptyMondayFirstCells() {
    val cells = buildMonthCells(YearMonth.of(2026, 8))
    assertEquals(5, cells.takeWhile { it == null }.size)
    assertEquals(LocalDate.of(2026, 8, 1), cells[5])
}

@Test
fun leapFebruaryContainsTwentyNineDates() {
    assertEquals(29, buildMonthCells(YearMonth.of(2028, 2)).filterNotNull().size)
}

@Test
fun weekdayLabelsAreChineseMondayFirst() {
    assertEquals(listOf("一", "二", "三", "四", "五", "六", "日"), HABIT_WEEKDAY_LABELS)
}
```

- [ ] **Step 2: Run the focused tests and confirm missing symbols fail**

- [ ] **Step 3: Implement the pure grid builder and Compose dialog**

```kotlin
val HABIT_WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

fun buildMonthCells(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - DayOfWeek.MONDAY.value
    return List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
}
```

The dialog owns a `YearMonth` and selected `LocalDate`, renders seven fixed weekday columns, and only calls `onConfirm` when the user presses “确定”. Replace Material `DatePicker`, `rememberDatePickerState`, `Instant`, and `ZoneOffset` usage in `HabitEditorScreen`.

- [ ] **Step 4: Run date tests and `HabitEditorViewModelTest`**

Expected: PASS and no source reference to Material `DatePicker(` remains.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/habit/app/ui/components/HabitDatePicker.kt app/src/test/java/com/habit/app/ui/components/HabitDatePickerTest.kt app/src/main/java/com/habit/app/ui/habits/HabitEditorScreen.kt
git commit -m "fix: add reliable Chinese date picker"
```

### Task 3: Complete the near-white color scheme

**Files:**
- Modify: `app/src/main/java/com/habit/app/ui/theme/HabitColorSchemes.kt`
- Modify: `app/src/test/java/com/habit/app/ui/theme/HabitColorSchemesTest.kt`

**Interfaces:**
- Produces: complete `ColorScheme` objects with no unspecified Material purple roles.

- [ ] **Step 1: Add failing color-role assertions**

```kotlin
@Test
fun skyBlueUsesNearWhiteSurfacesAndNoMaterialPurpleFallbacks() {
    val scheme = SkyBlueScheme
    assertEquals(0xFFFAFCFD, scheme.background.value.toLong())
    assertEquals(0xFFFFFFFF, scheme.surface.value.toLong())
    assertEquals(0xFFEAF4F8, scheme.primaryContainer.value.toLong())
    val forbidden = setOf(0xFF625B71, 0xFF7D5260, 0xFFE8DEF8)
    assertTrue(listOf(scheme.secondary, scheme.tertiary, scheme.secondaryContainer, scheme.tertiaryContainer)
        .none { it.value.toLong() in forbidden })
}
```

- [ ] **Step 2: Run the focused test and confirm current background/default roles fail**

- [ ] **Step 3: Explicitly populate visible Material roles**

Use `#FAFCFD` background, `#FFFFFF` surface, `#F3F7F9` neutral containers, `#EAF4F8` blue container, `#8EB9CC` primary, `#263840` text, `#708087` secondary text, and neutral blue-gray outline/inverse/error roles. Derive pink, green, purple, and gray themes from the same near-white neutral surfaces while changing only their accent families.

- [ ] **Step 4: Run theme tests**

Expected: PASS for every theme and no default purple roles in Sky Blue.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/habit/app/ui/theme/HabitColorSchemes.kt app/src/test/java/com/habit/app/ui/theme/HabitColorSchemesTest.kt
git commit -m "fix: complete near-white theme colors"
```

### Task 4: Build the pure backup format and codec

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/habit/app/data/backup/BackupModels.kt`
- Create: `app/src/main/java/com/habit/app/data/backup/HabitBackupCodec.kt`
- Create: `app/src/test/java/com/habit/app/data/backup/HabitBackupCodecTest.kt`

**Interfaces:**
- Produces: `HabitBackup`, `BackupCategory`, `BackupHabit`, `BackupCheckIn`, `BackupPreferences`, `HabitBackupCodec.encode`, and `HabitBackupCodec.decode`.

- [ ] **Step 1: Add failing codec tests**

```kotlin
@Test
fun roundTripPreservesDatabaseTimesAndEpochDays() {
    val source = sampleBackup(schemaVersion = 1)
    assertEquals(source, HabitBackupCodec.decode(HabitBackupCodec.encode(source)))
}

@Test(expected = UnsupportedBackupVersionException::class)
fun newerSchemaIsRejected() {
    HabitBackupCodec.decode("""{"format":"habit-backup","schemaVersion":99}""")
}

@Test(expected = InvalidBackupException::class)
fun danglingHabitCategoryIsRejected() {
    HabitBackupCodec.validate(sampleBackup(habits = listOf(sampleHabit(categoryId = 999))))
}
```

- [ ] **Step 2: Run the test and confirm the backup package is missing**

- [ ] **Step 3: Add and use the cached JSON tree dependency**

Add `kotlinx-serialization-json-jvm` version `1.8.1` to the version catalog and `implementation(libs.kotlinx.serialization.json)`. Use `JsonObject`, `JsonArray`, `JsonPrimitive`, `buildJsonObject`, and `buildJsonArray`; do not add the Kotlin serialization compiler plugin because the codec uses the tree API rather than generated serializers.

- [ ] **Step 4: Implement immutable models and strict validation**

```kotlin
data class HabitBackup(
    val format: String = "habit-backup",
    val schemaVersion: Int = 1,
    val appVersion: String,
    val exportedAt: Long,
    val preferencesUpdatedAt: Long,
    val categories: List<BackupCategory>,
    val habits: List<BackupHabit>,
    val checkIns: List<BackupCheckIn>,
    val preferences: BackupPreferences,
)
```

Encode every Long as a JSON number, ignore unknown fields while decoding, and reject missing required fields, duplicate entity IDs, dangling category/habit references, negative exported timestamps, and unsupported format/version.

- [ ] **Step 5: Run codec tests**

Expected: round-trip and validation tests PASS.

- [ ] **Step 6: Commit**

```powershell
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/habit/app/data/backup app/src/test/java/com/habit/app/data/backup
git commit -m "feat: add versioned Habit backup format"
```

### Task 5: Add transactional replace and merge import

**Files:**
- Modify: `app/src/main/java/com/habit/app/data/local/CategoryDao.kt`
- Modify: `app/src/main/java/com/habit/app/data/local/HabitDao.kt`
- Modify: `app/src/main/java/com/habit/app/data/local/CheckInDao.kt`
- Create: `app/src/main/java/com/habit/app/data/backup/RoomBackupRepository.kt`
- Create: `app/src/androidTest/java/com/habit/app/data/backup/RoomBackupRepositoryTest.kt`

**Interfaces:**
- Produces: `BackupRepository.exportSnapshot(preferences): HabitBackup` and `BackupRepository.import(backup, mode): ImportSummary`.
- Import mode: `enum class ImportMode { REPLACE, MERGE }`.

- [ ] **Step 1: Add Room integration tests for replace, merge and rollback**

Test cases must assert: replace exactly restores three tables; a newer imported habit wins; an older imported habit does not overwrite current data; colliding IDs with different `createdAt` are remapped; check-ins dedupe by mapped habit/date; invalid references leave all original rows intact.

```kotlin
@Test
fun mergeRemapsCollidingHabitIdAndPreservesBothLogicalHabits() = runTest {
    repository.import(backupWithDifferentCreatedAtSameHabitId(), ImportMode.MERGE)
    assertEquals(2, database.habitDao().getAll().size)
}
```

- [ ] **Step 2: Run `connectedDebugAndroidTest` on an available device and confirm missing DAO APIs fail compilation**

- [ ] **Step 3: Add snapshot and mutation DAO methods**

Add `suspend fun getAll()` to each DAO, `insertAll`, `deleteAll`, and update/upsert methods needed by the engine. Keep foreign-key order explicit: check-ins, habits, categories for delete; categories, habits, check-ins for insert.

- [ ] **Step 4: Implement `RoomBackupRepository` with `database.withTransaction`**

Merge builds category/habit ID maps first, compares immutable identity `(sourceId, createdAt)`, maps preset categories by name, and compares `updatedAt` only after identity matches. Return `ImportSummary(categories, habits, checkIns, skippedOlder)`.

- [ ] **Step 5: Run Room integration tests**

Expected: all replace, merge, dedupe, remap and rollback tests PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/habit/app/data/local app/src/main/java/com/habit/app/data/backup/RoomBackupRepository.kt app/src/androidTest/java/com/habit/app/data/backup/RoomBackupRepositoryTest.kt
git commit -m "feat: add transactional backup import"
```

### Task 6: Persist backup preferences and preference timestamps

**Files:**
- Create: `app/src/main/java/com/habit/app/data/preferences/BackupPreferencesRepository.kt`
- Modify: `app/src/main/java/com/habit/app/data/preferences/ThemePreferencesRepository.kt`
- Modify: `app/src/main/java/com/habit/app/data/preferences/EmojiPreferencesRepository.kt`
- Create: `app/src/test/java/com/habit/app/data/preferences/BackupPreferencePolicyTest.kt`
- Modify: `app/src/main/java/com/habit/app/di/AppContainer.kt`

**Interfaces:**
- Produces: `BackupPreferenceSnapshot(themeId, recentEmojiKeys, updatedAt, folder)` and persistent `BackupFolder.Default` / `BackupFolder.Tree(uri, displayName)`.

- [ ] **Step 1: Add failing preference conflict-policy tests**

```kotlin
@Test
fun importedPreferencesWinOnlyWhenTheirTimestampIsNewer() {
    assertEquals(imported, choosePreferences(current, imported))
    assertEquals(current, choosePreferences(current, imported.copy(updatedAt = current.updatedAt - 1)))
}
```

- [ ] **Step 2: Run the test and confirm policy types are missing**

- [ ] **Step 3: Implement shared timestamp and folder keys**

Every theme or recent-Emoji write updates `habit_preferences_updated_at`. The folder repository stores tree URI and display name separately and does not change the user-content timestamp. Missing timestamps read as zero.

- [ ] **Step 4: Register repositories in `AppContainer` and run preference/theme/Emoji tests**

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/habit/app/data/preferences app/src/test/java/com/habit/app/data/preferences app/src/main/java/com/habit/app/di/AppContainer.kt
git commit -m "feat: persist backup settings and timestamps"
```

### Task 7: Implement default/custom backup storage and safe folder migration

**Files:**
- Create: `app/src/main/java/com/habit/app/data/backup/BackupDocumentStore.kt`
- Create: `app/src/main/java/com/habit/app/data/backup/AndroidBackupDocumentStore.kt`
- Create: `app/src/main/java/com/habit/app/data/backup/BackupFolderMigrator.kt`
- Create: `app/src/test/java/com/habit/app/data/backup/BackupFolderMigratorTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `writeBackup`, `readBackup`, `listManagedBackups`, `copy`, `sha256`, `delete`, and `migrate(old, new): FolderMigrationResult`.

- [ ] **Step 1: Add failing fake-store migration tests**

```kotlin
@Test
fun oldFilesAreDeletedOnlyAfterEveryCopyMatches() = runTest {
    val result = migrator.migrate(old, new)
    assertTrue(result is FolderMigrationResult.Success)
    assertTrue(store.listManagedBackups(old).isEmpty())
}

@Test
fun checksumFailureKeepsEveryOldFile() = runTest {
    store.corruptNextCopy = true
    assertTrue(migrator.migrate(old, new) is FolderMigrationResult.Failed)
    assertEquals(2, store.listManagedBackups(old).size)
}
```

- [ ] **Step 2: Run tests and confirm storage interfaces are missing**

- [ ] **Step 3: Implement Android storage boundary**

Use MediaStore Downloads with `RELATIVE_PATH="Download/Habit/数据备份"` on API 29+, SAF `DocumentFile` for custom trees, and `WRITE_EXTERNAL_STORAGE` only on API 23-28. Managed filenames must match `Habit-Backup-*.habitbackup.json`; never enumerate or delete unrelated files.

- [ ] **Step 4: Implement copy-verify-delete migration**

Copy every managed source, compare length and SHA-256, and delete sources only after all targets verify. On failure delete only targets created by the current attempt and retain the old folder preference.

- [ ] **Step 5: Run storage policy tests**

Expected: success, checksum failure, permission loss and unrelated-file tests PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/habit/app/data/backup app/src/test/java/com/habit/app/data/backup app/src/main/AndroidManifest.xml
git commit -m "feat: add configurable backup storage"
```

### Task 8: Connect backup operations to Settings UI

**Files:**
- Modify: `app/src/main/java/com/habit/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/habit/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitNavHost.kt`
- Modify: `app/src/test/java/com/habit/app/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `BackupRepository`, `BackupDocumentStore`, `BackupFolderMigrator`, `BackupPreferencesRepository`.
- Produces: settings events for export URI, import preview, replace/merge confirmation, folder tree selection and operation messages.

- [ ] **Step 1: Add failing ViewModel state-machine tests**

Tests cover: default path text; export busy state; valid import preview counts; invalid file rejection; replace/merge mode forwarding; folder migration success; migration failure retaining old display path.

```kotlin
@Test
fun failedFolderMigrationKeepsOldLocation() = runTest {
    viewModel.onFolderSelected(newTree)
    advanceUntilIdle()
    assertEquals(DEFAULT_BACKUP_LABEL, viewModel.state.value.backupLocation)
    assertEquals("迁移失败，旧备份未删除", viewModel.state.value.message)
}
```

- [ ] **Step 2: Run focused settings tests and confirm new state/actions are missing**

- [ ] **Step 3: Implement the ViewModel operation state**

Add `backupLocation`, `busy`, `importPreview`, `pendingImport`, and `message`. Keep `ContentResolver` and activity-result contracts out of the ViewModel; screen callbacks pass selected `Uri` values to it.

- [ ] **Step 4: Implement the near-white Local Data card**

Show current location, “更改保存位置”, “导出数据”, and “导入数据”. Use `OpenDocumentTree` for folders and `OpenDocument` for backup selection. Before import, show record counts and a modal with “完全替换” and “合并导入”; replace uses a second destructive confirmation.

- [ ] **Step 5: Run settings tests and compile Debug sources**

Expected: tests PASS and Compose compilation succeeds.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/habit/app/ui/settings app/src/main/java/com/habit/app/ui/navigation/HabitNavHost.kt app/src/test/java/com/habit/app/ui/settings/SettingsViewModelTest.kt
git commit -m "feat: add local data controls"
```

### Task 9: Version, documentation and release verification

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `docs/INSTALL.md`
- Modify: `docs/TESTING.md`
- Update: `outputs/Habit-0.2.1-debug.apk`
- Create: `outputs/Habit-0.2.1-debug.sha256`

**Interfaces:**
- Produces: installable 0.2.1 APK and checksum.

- [ ] **Step 1: Set versionCode 3 and versionName 0.2.1**

```kotlin
versionCode = 3
versionName = "0.2.1"
```

- [ ] **Step 2: Run all JVM tests**

Run:

```powershell
$env:JAVA_HOME='D:\MySoftware\Java\jdk-17'; $env:ANDROID_HOME='D:\MySoftware\Android\AndroidSdk'; & 'D:\MySoftware\gradle\gradle-9.5.0\bin\gradle.bat' --offline --gradle-user-home .gradle-user :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL with zero failed tests.

- [ ] **Step 3: Run available connected tests**

Run `adb devices`. If an authorized device is present, execute `:app:connectedDebugAndroidTest`; otherwise record that true-device verification remains pending and do not claim it passed.

- [ ] **Step 4: Build with the established compatible debug key**

Build using the same certificate as the retained 0.1/0.2 delivery APK. Verify package, versionCode, versionName, min/target SDK and signer with `aapt dump badging` and `apksigner verify --print-certs`.

- [ ] **Step 5: Copy the verified APK and write SHA-256**

Delivery file: `outputs/Habit-0.2.1-debug.apk`. Generate the checksum from that exact copied file and verify it once by recomputation.

- [ ] **Step 6: Update installation/testing docs**

Document the default backup path, custom folder migration behavior, replace/merge semantics, cross-signature migration limitation, and a manual test matrix for Emoji interest, date weekdays, near-white colors, export, folder migration and import.

- [ ] **Step 7: Commit**

```powershell
git add app/build.gradle.kts docs/INSTALL.md docs/TESTING.md outputs/Habit-0.2.1-debug.apk outputs/Habit-0.2.1-debug.sha256
git commit -m "build: prepare Habit 0.2.1 APK"
```

## Final Review Checklist

- [ ] `git diff --check` reports no errors.
- [ ] Git worktree is clean after the release commit.
- [ ] Full JVM test suite passes from a fresh invocation.
- [ ] Debug APK builds successfully.
- [ ] APK signer matches the chosen compatibility certificate.
- [ ] No `fallbackToDestructiveMigration` exists.
- [ ] Default path is displayed exactly as `内部存储 / Download / Habit / 数据备份`.
- [ ] Import preview and both confirmation paths are visible.
- [ ] Old backup files survive every migration failure test.
- [ ] Date weekday row reads `一 二 三 四 五 六 日`.
- [ ] Sky Blue surfaces contain no Material fallback purple.
- [ ] Emoji interest category opens and every icon can be selected.
