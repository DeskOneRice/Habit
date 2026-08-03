# Diet Quick Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver Habit 0.4.0 with repeat-meal capture, reusable diet templates, up to three private photos per record, and a photo-aware backup format that preserves all older local data.

**Architecture:** Extend the existing Room diet aggregate with photo and template tables through a v2-to-v3 migration. Keep file ownership in a focused private-photo store, expose repeat/template operations through domain repositories, and extend the current editor, workbench, drawer, and backup service without changing the habit module. Export schema v3 as a ZIP containing `backup.json` and copied photo entries while continuing to import schema v1/v2 JSON.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room 2.6, Navigation Compose, Android Storage Access Framework, FileProvider, kotlinx.serialization JSON, JUnit4, AndroidX instrumented tests, Gradle/AGP 8.3.

## Global Constraints

- App release is `versionName = "0.4.0"` and `versionCode = 8`.
- All user-facing date/time behavior uses `Asia/Shanghai`; database timestamps remain epoch milliseconds.
- Preserve every schema-v2 habit and diet row through `MIGRATION_2_3`.
- A meal record may contain at most three photos; each imported file is at most 12 MB.
- Stored photo names are UUID-based and paths must remain below private `filesDir/diet_photos`.
- A photo belongs to exactly one meal record or one template.
- “再记一次” copies structured fields only, uses current Beijing time, and never copies the original record's photos.
- Saving a template offers “包含照片”, default off; selected photos are independent copies.
- Backup v3 is `.habitbackup.zip` with root `backup.json` and `photos/<relativePath>` entries.
- Imports continue to accept schema-v1/v2 `.habitbackup.json`; invalid or missing photos do not discard valid text records.
- Replace import removes orphan files only after the database import succeeds.
- Keep the fixed signing SHA-256 fingerprint `8674967e7901174339cd4fc85726ebce59b572a7ab58a799139e300a69382abb`.

---

### Task 1: Domain models and Room schema v3

**Files:**
- Modify: `app/src/main/java/com/habit/app/domain/model/DietModels.kt`
- Modify: `app/src/main/java/com/habit/app/data/local/Entities.kt`
- Modify: `app/src/main/java/com/habit/app/data/local/DietDao.kt`
- Modify: `app/src/main/java/com/habit/app/data/local/HabitDatabase.kt`
- Modify: `app/src/main/java/com/habit/app/data/local/EntityMappers.kt`
- Modify: `app/src/main/java/com/habit/app/di/AppContainer.kt`
- Test: `app/src/test/java/com/habit/app/domain/model/DietQuickCaptureTest.kt`
- Test: `app/src/androidTest/java/com/habit/app/data/local/HabitDatabaseTest.kt`

**Interfaces:**
- Produces: `DietPhoto`, `DietTemplate`, `DietTemplateDraft`, `MealRecord.toRepeatDraft(nowMillis, epochDay)`.
- Produces: `DietPhotoEntity`, `DietTemplateEntity`, `DietTemplateFoodItemEntity`, `DietTemplateToppingEntity`, `DietTemplateWithDetails`.
- Produces: `MIGRATION_2_3` and `HabitDatabase.version = 3`.

- [ ] **Step 1: Write failing repeat-copy and migration tests**

```kotlin
@Test fun repeatDraftCopiesContentButNotIdentityOrPhotos() {
    val draft = populatedMealRecord(photos = listOf(DietPhoto(1, "record/a.jpg", 0)))
        .toRepeatDraft(nowMillis = 1_785_687_600_000, epochDay = 20_671)
    assertEquals(1_785_687_600_000, draft.occurredAt)
    assertEquals(20_671, draft.recordEpochDay)
    assertTrue(draft.photos.isEmpty())
    assertEquals("番茄鸡蛋面", draft.description)
}
```

Add a Room migration test that creates the v2 schema, inserts a `meal_records` row, migrates with `MIGRATION_2_3`, verifies the old row, and inserts one template plus record/template photo rows.

- [ ] **Step 2: Run tests and confirm RED**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest --tests '*DietQuickCaptureTest' --tests '*HabitDatabaseTest'`
Expected: compilation/test failure because the new types, tables, and migration do not exist.

- [ ] **Step 3: Add the domain types and v3 tables**

```kotlin
data class DietPhoto(val id: Long = 0, val relativePath: String, val sortOrder: Int)
data class DietTemplate(
    val id: Long, val name: String, val draft: MealRecordDraft,
    val photos: List<DietPhoto>, val sortOrder: Int, val createdAt: Long, val updatedAt: Long,
)
data class DietTemplateDraft(
    val id: Long? = null, val name: String, val meal: MealRecordDraft,
    val includePhotos: Boolean = false, val sortOrder: Int = 0,
)
fun MealRecord.toRepeatDraft(nowMillis: Long, epochDay: Long) = MealRecordDraft(
    recordType, mealType, nowMillis, epochDay, description, foodItems,
    finalCalories, beverage, note, photos = emptyList(),
)
```

Add the four Room entities and relations. `diet_photos` has nullable `mealRecordId`/`templateId`, foreign keys with cascade delete, `relativePath`, `sortOrder`, and `createdAt`; migration SQL includes an XOR `CHECK` constraint. Register every entity and DAO method, increment the database version, and register `MIGRATION_2_3` in `AppContainer`.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest --tests '*DietQuickCaptureTest' --tests '*HabitDatabaseTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```text
git add app/src app/schemas
git commit -m "feat: add diet templates and photo schema"
```

### Task 2: Private photo storage and record editing

**Files:**
- Create: `app/src/main/java/com/habit/app/data/photos/DietPhotoStore.kt`
- Create: `app/src/main/java/com/habit/app/data/photos/AndroidDietPhotoStore.kt`
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/habit/app/domain/repository/DietRepository.kt`
- Modify: `app/src/main/java/com/habit/app/data/repository/RoomDietRepository.kt`
- Modify: `app/src/main/java/com/habit/app/ui/diet/DietEditorViewModel.kt`
- Modify: `app/src/main/java/com/habit/app/ui/diet/DietEditorScreen.kt`
- Test: `app/src/test/java/com/habit/app/data/photos/DietPhotoPathPolicyTest.kt`
- Test: `app/src/test/java/com/habit/app/ui/diet/DietEditorViewModelTest.kt`
- Test: `app/src/androidTest/java/com/habit/app/data/repository/RoomDietRepositoryTest.kt`

**Interfaces:**
- Consumes: `MealRecordDraft.photos: List<DietPhoto>` and `DietPhotoEntity`.
- Produces: `DietPhotoStore.stage(uri)`, `createCameraTarget()`, `commit(staged)`, `copy(relativePath)`, `discard(staged)`, `delete(relativePath)`, `removeOrphans(referenced)`.
- Produces: editor events `AddGalleryPhotos`, `AddCameraPhoto`, `RemovePhoto`, and `Save` with a three-photo cap.

- [ ] **Step 1: Write failing path, limit, and persistence tests**

```kotlin
@Test fun resolvedPhotoCannotEscapePrivateRoot() {
    assertFailsWith<IllegalArgumentException> { policy.resolve("../outside.jpg") }
}
@Test fun fourthPhotoIsRejectedWithoutChangingDraft() {
    val before = viewModel.state.value.photos
    viewModel.addPhotos(listOf(photo4))
    assertEquals(before, viewModel.state.value.photos)
    assertEquals("每条记录最多添加 3 张照片", viewModel.state.value.message)
}
```

The repository instrumented test saves a record with two photo paths, reloads it, removes one photo, and verifies Room returns only the remaining relation.

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest --tests '*DietPhotoPathPolicyTest' --tests '*DietEditorViewModelTest' --tests '*RoomDietRepositoryTest'`
Expected: failure because photo storage and draft photo behavior are absent.

- [ ] **Step 3: Implement staged private photo storage**

Use `diet_photos/staging` for pending files and `diet_photos/library` for committed files. Stream at most `12 * 1024 * 1024 + 1` bytes, decode image bounds before accepting, generate UUID filenames, atomically rename staged files on commit, canonicalize every resolved path below the private root, and copy template images to new UUID files. Add a `FileProvider` limited to the staging folder for camera capture.

- [ ] **Step 4: Add editor camera/gallery UI and repository relations**

Use `GetMultipleContents("image/*")` and `TakePicture()` launchers. Render a horizontal photo strip with thumbnail, remove button, and one “添加照片” tile while fewer than three photos exist. On Save, commit staged photos before the Room transaction; if the transaction fails, remove newly committed files. On Cancel/back, discard all staged files. After successful edits/deletes, delete only paths no longer referenced by records or templates.

- [ ] **Step 5: Run focused tests and confirm GREEN**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest --tests '*DietPhotoPathPolicyTest' --tests '*DietEditorViewModelTest' --tests '*RoomDietRepositoryTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```text
git add app/src
git commit -m "feat: attach private photos to diet records"
```

### Task 3: Repeat action, templates, and quick entry UI

**Files:**
- Create: `app/src/main/java/com/habit/app/domain/repository/DietTemplateRepository.kt`
- Create: `app/src/main/java/com/habit/app/data/repository/RoomDietTemplateRepository.kt`
- Create: `app/src/main/java/com/habit/app/ui/diet/DietTemplateViewModel.kt`
- Create: `app/src/main/java/com/habit/app/ui/diet/DietTemplateScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/diet/DietDiaryScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/diet/DietDiaryViewModel.kt`
- Modify: `app/src/main/java/com/habit/app/ui/diet/DietEditorScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/diet/DietEditorViewModel.kt`
- Modify: `app/src/main/java/com/habit/app/ui/workbench/WorkbenchScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/workbench/WorkbenchViewModel.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitDestination.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitDrawerContent.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitNavHost.kt`
- Modify: `app/src/main/java/com/habit/app/di/AppContainer.kt`
- Test: `app/src/test/java/com/habit/app/ui/diet/DietTemplateViewModelTest.kt`
- Test: `app/src/test/java/com/habit/app/ui/diet/DietDiaryViewModelTest.kt`
- Test: `app/src/test/java/com/habit/app/ui/workbench/WorkbenchViewModelTest.kt`

**Interfaces:**
- Produces: `DietTemplateRepository.observeAll(): Flow<List<DietTemplate>>`, `save(draft)`, `delete(id)`, `rename(id,name)`, `reorder(ids)`.
- Produces: navigation routes `diet/templates`, `diet/editor?repeatId={id}`, `diet/editor?templateId={id}`.

- [ ] **Step 1: Write failing template and quick-copy tests**

```kotlin
@Test fun templateGroupsAreCollapsibleAndKeepOrder() { /* emit meal/drink templates; assert grouped stable order and toggle state */ }
@Test fun repeatUsesCurrentBeijingDateAndLeavesOriginalUntouched() { /* open repeat draft; assert new time/day and original id absent */ }
@Test fun workbenchShowsFirstQuickTemplates() { /* emit sorted templates; assert quick cards */ }
```

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests '*DietTemplateViewModelTest' --tests '*DietDiaryViewModelTest' --tests '*WorkbenchViewModelTest'`
Expected: failure because the repository, states, and actions are missing.

- [ ] **Step 3: Implement template repository and copy policy**

Persist template aggregate changes in Room transactions. Duplicate names remain valid. Saving with photos copies each source file to a new UUID path; saving without photos creates no photo rows. Template-to-editor uses a new current Beijing timestamp and an editable unsaved draft. Rename, reorder, and delete update only the selected template and clean unreferenced files after success.

- [ ] **Step 4: Implement template management and quick entry UI**

Add “再记一次” to each diary record. Add “存为模板” in the editor with a name field and a default-off “包含照片” switch. Build the template screen with separate collapsible “正餐/加餐” and “饮品” groups and rename, drag/order controls, and delete confirmation. Add emoji-only consistent drawer entry `📋 饮食模板`, and show sorted template quick cards on the workbench that open an editable draft.

- [ ] **Step 5: Run focused tests and confirm GREEN**

Run: `./gradlew testDebugUnitTest --tests '*DietTemplateViewModelTest' --tests '*DietDiaryViewModelTest' --tests '*WorkbenchViewModelTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```text
git add app/src
git commit -m "feat: add repeat meals and diet templates"
```

### Task 4: ZIP backup v3 with legacy import compatibility

**Files:**
- Modify: `app/src/main/java/com/habit/app/data/backup/BackupModels.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/HabitBackupCodec.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/BackupDocumentStore.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/AndroidBackupDocumentStore.kt`
- Create: `app/src/main/java/com/habit/app/data/backup/HabitBackupArchive.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/HabitBackupService.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/RoomBackupRepository.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/BackupMerger.kt`
- Modify: `app/src/main/java/com/habit/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/habit/app/ui/settings/SettingsScreen.kt`
- Test: `app/src/test/java/com/habit/app/data/backup/HabitBackupCodecTest.kt`
- Test: `app/src/test/java/com/habit/app/data/backup/HabitBackupArchiveTest.kt`
- Test: `app/src/test/java/com/habit/app/data/backup/BackupMergerTest.kt`

**Interfaces:**
- Produces: schema-v3 arrays `dietPhotos`, `dietTemplates`, `dietTemplateFoodItems`, `dietTemplateToppings`.
- Produces: `HabitBackupArchive.write(output, backup, photoResolver)` and `read(input, stagingRoot): ArchiveReadResult`.
- Produces: import report fields `skippedPhotoCount` and `importedPhotoCount`.

- [ ] **Step 1: Write failing codec/archive/security tests**

```kotlin
@Test fun v3ArchiveRoundTripsMetadataAndPhotoBytes() { /* write ZIP; read; assert backup and bytes */ }
@Test fun legacyV2JsonStillDecodesWithoutPhotosOrTemplates() { /* decode v2 fixture */ }
@Test fun archiveRejectsTraversalEntry() { /* photos/../../escape.jpg -> InvalidBackupException */ }
@Test fun missingPhotoKeepsTextAndReportsSkip() { /* metadata references absent file; assert text retained and skip=1 */ }
```

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests '*HabitBackupCodecTest' --tests '*HabitBackupArchiveTest' --tests '*BackupMergerTest'`
Expected: failure because schema v3 and archive handling are absent.

- [ ] **Step 3: Extend metadata codec and archive streaming**

Set `HABIT_BACKUP_SCHEMA_VERSION = 3`, serialize the four new arrays, and default them to empty for v1/v2. Stream ZIP output directly to the selected SAF document: first `backup.json`, then each referenced existing photo as `photos/<relativePath>`. During import reject absolute paths, `..`, duplicate ZIP names, files larger than 12 MB, and paths outside staging. Skip corrupt/missing image entries while retaining their parent record/template text and count each skip.

- [ ] **Step 4: Make imports transactional and clean files safely**

Detect ZIP magic before legacy JSON parsing. Extract valid files to an import staging directory, remap collisions to UUID names, then run replace/merge in a Room transaction. Move staged files into the library only after validation; if database import fails, delete moved imports and preserve existing files. For replace mode, run orphan cleanup only after commit. Show imported/skipped photo counts in Settings.

- [ ] **Step 5: Run focused tests and confirm GREEN**

Run: `./gradlew testDebugUnitTest --tests '*HabitBackupCodecTest' --tests '*HabitBackupArchiveTest' --tests '*BackupMergerTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```text
git add app/src
git commit -m "feat: back up diet photos and templates"
```

### Task 5: Release 0.4.0, compatibility verification, and APK delivery

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Create: `outputs/Habit-0.4.0-debug.apk`

**Interfaces:**
- Consumes: all preceding tasks.
- Produces: one signed, installable 0.4.0 APK that upgrades the stable 0.3.2 signing line.

- [ ] **Step 1: Add/adjust release assertions before changing version**

Update `ProjectSmokeTest` to assert version name `0.4.0`, version code `8`, Room schema `3`, v1/v2 decoder support, and the expected backup extensions.

- [ ] **Step 2: Run the release assertion and confirm RED**

Run: `./gradlew testDebugUnitTest --tests '*ProjectSmokeTest'`
Expected: FAIL on the old 0.3.2 version/schema values.

- [ ] **Step 3: Set release metadata and documentation**

Set `versionCode = 8`, `versionName = "0.4.0"`, document quick repeat/templates/photos and v3 ZIP backup in `README.md`, and keep the existing signing properties untouched.

- [ ] **Step 4: Run the proportionate verification suite**

Run: `./gradlew clean testDebugUnitTest lintDebug assembleDebug`
Expected: BUILD SUCCESSFUL with all unit tests and lint passing.

Run: `./gradlew connectedDebugAndroidTest`
Expected: BUILD SUCCESSFUL on the attached device/emulator; Room v2-to-v3 migration and repository tests pass.

- [ ] **Step 5: Verify APK identity and signing**

Run `apkanalyzer manifest version-name app/build/outputs/apk/debug/app-debug.apk`, `apkanalyzer manifest version-code ...`, and `apksigner verify --print-certs ...`. Expected version is `0.4.0`, code is `8`, and certificate SHA-256 is `8674967e7901174339cd4fc85726ebce59b572a7ab58a799139e300a69382abb`.

- [ ] **Step 6: Copy and commit the release artifact**

Copy the verified APK to `outputs/Habit-0.4.0-debug.apk`, check it has non-zero size and matching SHA-256, then commit tracked source/schema/documentation changes:

```text
git add app README.md docs
git commit -m "release: prepare Habit 0.4.0"
```

