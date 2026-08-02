# Habit 0.2 Workbench Drawer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver Habit 0.2 as an upgrade-compatible Android APK with a pure side drawer, a low-saturation workbench home, and an expanded searchable Emoji picker.

**Architecture:** Keep Room schema 1 and all existing repositories intact. Add a presentation-only workbench that combines existing observable calendar, habit, and category data; add a small DataStore-backed Emoji history repository; place all top-level routes inside one `ModalNavigationDrawer` shell while keeping editor/detail routes as secondary pages.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation Compose, Room, Preferences DataStore, coroutines/Flow, JUnit 4, Gradle Android plugin.

## Global Constraints

- Keep `applicationId = "com.habit.app"`, Room database name `habit.db`, Room schema version `1`, and all existing date/`createdAt`/`updatedAt` semantics unchanged.
- Set `versionCode = 2` and `versionName = "0.2.0"`.
- Do not use destructive migrations, clear app data, add networking, or add food/AI/report placeholders.
- Default theme remains low-saturation sky blue; cards remain neutral white/near-white.
- Top-level routes are Workbench, Calendar, Habits, Categories, and Settings; no bottom navigation.
- Calendar cells and the 7-day strip show up to three distinct habit Emoji and then `+N`.
- Keep the implementation focused: run unit tests, lint, and debug assembly; physical-device acceptance is performed by the user.

---

### Task 1: Emoji catalog, compatibility, validation, and recent history

**Files:**
- Create: `app/src/main/java/com/habit/app/ui/emoji/EmojiCatalog.kt`
- Create: `app/src/main/java/com/habit/app/data/preferences/EmojiPreferencesRepository.kt`
- Modify: `app/src/main/java/com/habit/app/di/AppContainer.kt`
- Modify: `app/src/main/java/com/habit/app/ui/components/HabitVisuals.kt`
- Test: `app/src/test/java/com/habit/app/ui/emoji/EmojiCatalogTest.kt`

**Interfaces:**
- Produces: `EmojiOption(key, emoji, label, category, keywords)`, `EmojiCategory`, `EmojiCatalog.search(query, category)`, `normalizeEmojiKey(raw)`, `isSingleEmoji(raw)`, and `EmojiPreferencesRepository.recentEmojiKeys` / `record(key)`.
- Preserves: the six legacy keys and maps new entries to `emoji:<grapheme>`.

- [ ] **Step 1: Write failing catalog tests**

```kotlin
@Test fun legacyKeysAndUnicodeValuesRenderWithoutRewriting() {
    assertEquals("📚", habitEmoji("book"))
    assertEquals("🧪", habitEmoji("emoji:🧪"))
    assertEquals("🌱", habitEmoji("unknown-old-value"))
}

@Test fun chineseSearchAndCategoryFilteringAreStable() {
    assertTrue(EmojiCatalog.search("实验", EmojiCategory.STUDY).any { it.emoji == "🧪" })
    assertTrue(EmojiCatalog.options.size in 60..80)
}

@Test fun customInputAcceptsOneEmojiAndRejectsTextOrMultipleEmoji() {
    assertTrue(isSingleEmoji("🏸"))
    assertFalse(isSingleEmoji("习惯"))
    assertFalse(isSingleEmoji("🏸📚"))
}
```

- [ ] **Step 2: Run the focused test and observe the expected missing-symbol failure**

Run: `./gradlew.bat testDebugUnitTest --tests "com.habit.app.ui.emoji.EmojiCatalogTest"`
Expected: FAIL because `EmojiCatalog` and the normalization/validation functions do not exist.

- [ ] **Step 3: Implement the catalog and pure helpers**

```kotlin
data class EmojiOption(
    val emoji: String,
    val label: String,
    val category: EmojiCategory,
    val keywords: Set<String>,
) {
    val key: String = "emoji:$emoji"
}

fun habitEmoji(iconKey: String): String = legacyEmoji[iconKey]
    ?: iconKey.removePrefix("emoji:").takeIf { iconKey.startsWith("emoji:") && it.isNotBlank() }
    ?: "🌱"
```

Use Android's grapheme iterator plus Unicode emoji-property checks in `isSingleEmoji`; keep a fixed 60–80 item list across the six content categories.

- [ ] **Step 4: Add DataStore history with a bounded, de-duplicated 12-item list**

```kotlin
class EmojiPreferencesRepository(private val dataStore: DataStore<Preferences>) {
    val recentEmojiKeys: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[RECENT_EMOJI_KEYS].orEmpty().split(SEPARATOR).filter(String::isNotBlank).take(12)
    }

    suspend fun record(key: String) = dataStore.edit { prefs ->
        val old = prefs[RECENT_EMOJI_KEYS].orEmpty().split(SEPARATOR)
        prefs[RECENT_EMOJI_KEYS] = (listOf(key) + old.filterNot { it == key }).take(12).joinToString(SEPARATOR)
    }
}
```

- [ ] **Step 5: Run the focused tests until green, then commit**

Run: `./gradlew.bat testDebugUnitTest --tests "com.habit.app.ui.emoji.EmojiCatalogTest"`
Expected: PASS.

Commit: `feat: expand compatible emoji catalog`

### Task 2: Workbench state aggregation and actions

**Files:**
- Create: `app/src/main/java/com/habit/app/ui/workbench/WorkbenchViewModel.kt`
- Test: `app/src/test/java/com/habit/app/ui/workbench/WorkbenchViewModelTest.kt`

**Interfaces:**
- Consumes: `HabitRepository.observeAll`, `CategoryRepository.observeAll`, `CalendarRepository.observeDay/observeMonth`, `CheckInRepository.toggle`, `DeviceDateProvider`.
- Produces: `WorkbenchUiState`, `WorkbenchHabitItem`, `RecentDay`, `toggle(habitId)`, and `refreshDeviceDate()`.

- [ ] **Step 1: Write failing aggregation tests**

```kotlin
@Test fun stateCombinesTodayProgressStreakWeekAndMonth() = runTest(dispatcher) {
    val vm = workbenchWith(today = LocalDate.of(2031, 2, 3), completedHabitIds = setOf(1L))
    advanceUntilIdle()
    assertEquals(1, vm.state.value.completedCount)
    assertEquals(2, vm.state.value.totalCount)
    assertEquals(0.5f, vm.state.value.progress)
    assertEquals(7, vm.state.value.recentDays.size)
}

@Test fun duplicateToggleIsIgnoredAndFailureClearsOnlyThatGuard() = runTest(dispatcher) {
    val vm = workbenchWithPendingToggle()
    vm.toggle(1); vm.toggle(1); advanceUntilIdle()
    assertEquals(setOf(1L), vm.state.value.togglingHabitIds)
    assertEquals(1, pending.calls)
}
```

- [ ] **Step 2: Run and observe the expected missing-class failure**

Run: `./gradlew.bat testDebugUnitTest --tests "com.habit.app.ui.workbench.WorkbenchViewModelTest"`
Expected: FAIL because `WorkbenchViewModel` does not exist.

- [ ] **Step 3: Implement state combination without changing repositories**

```kotlin
data class WorkbenchUiState(
    val today: LocalDate,
    val habits: List<WorkbenchHabitItem> = emptyList(),
    val recentDays: List<RecentDay> = emptyList(),
    val monthStats: MonthStats = MonthStats(0, 0, 0, 0f),
    val togglingHabitIds: Set<Long> = emptySet(),
    val message: String? = null,
) {
    val completedCount get() = habits.count { it.checked }
    val totalCount get() = habits.size
    val progress get() = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount
}
```

Observe seven daily snapshots and the current month with `flatMapLatest`, filter archived/ineligible habits through existing snapshot results, and calculate current streaks using `observeHabitHistory` only for visible active habits.

- [ ] **Step 4: Implement guarded toggle and date refresh**

Use the same per-habit guard, rejection message, cancellation handling, and `DeviceDateProvider.snapshot()` pattern as `CalendarViewModel`.

- [ ] **Step 5: Run focused tests until green, then commit**

Run: `./gradlew.bat testDebugUnitTest --tests "com.habit.app.ui.workbench.WorkbenchViewModelTest"`
Expected: PASS.

Commit: `feat: add workbench state aggregation`

### Task 3: Shared low-saturation visual components

**Files:**
- Create: `app/src/main/java/com/habit/app/ui/components/HabitSurface.kt`
- Create: `app/src/main/java/com/habit/app/ui/components/HabitTopAppBar.kt`
- Create: `app/src/main/java/com/habit/app/ui/navigation/HabitDrawerContent.kt`
- Modify: `app/src/main/java/com/habit/app/ui/theme/HabitColorSchemes.kt`

**Interfaces:**
- Produces: `HabitCard`, `HabitTopAppBar`, `HabitDrawerContent`, and drawer destinations with stable test tags.

- [ ] **Step 1: Add preview/testable component contracts before integration**

```kotlin
@Composable fun HabitCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)
@Composable fun HabitTopAppBar(title: String, navigationMode: NavigationMode, onNavigation: () -> Unit, actions: @Composable RowScope.() -> Unit = {})
@Composable fun HabitDrawerContent(selectedRoute: String?, progress: Float, onDestination: (HabitDestination) -> Unit)
```

- [ ] **Step 2: Implement neutral cards, 48dp actions, sky-blue accents, and selected drawer rows**

Use Material semantics, `RoundedCornerShape(18.dp)`, neutral `surface`, `surfaceContainerLow`, and theme-derived primary containers; do not hard-code a full-screen theme tint.

- [ ] **Step 3: Compile the components, inspect Compose previews, then commit**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Commit: `feat: introduce Habit visual shell components`

### Task 4: Drawer navigation and upgrade-safe default route

**Files:**
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitDestination.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitApp.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitNavHost.kt`
- Modify: `app/src/main/java/com/habit/app/ui/welcome/WelcomeViewModel.kt`
- Test: `app/src/test/java/com/habit/app/ui/navigation/NavigationPolicyTest.kt`

**Interfaces:**
- Produces: `HabitDestination.Workbench`, `topLevelDestinations`, and a `ModalNavigationDrawer` app shell.

- [ ] **Step 1: Write failing route-policy tests**

```kotlin
@Test fun existingUsersStartAtWorkbench() {
    assertEquals(FirstRunDestination.Workbench, destinationFor(listOf(aHabit())))
}

@Test fun topLevelRoutesContainExactlyFiveDrawerItems() {
    assertEquals(listOf("workbench", "calendar", "habits", "categories", "settings"), topLevelDestinations.map { it.route })
}
```

- [ ] **Step 2: Run and observe expected failures for missing workbench policy**

Run: `./gradlew.bat testDebugUnitTest --tests "com.habit.app.ui.navigation.NavigationPolicyTest"`
Expected: FAIL.

- [ ] **Step 3: Replace bottom navigation with `ModalNavigationDrawer`**

```kotlin
ModalNavigationDrawer(
    drawerState = drawerState,
    gesturesEnabled = currentRoute in topLevelRoutes,
    drawerContent = { ModalDrawerSheet { HabitDrawerContent(...) } },
) {
    HabitNavHost(..., onOpenDrawer = { scope.launch { drawerState.open() } })
}
```

Navigate top-level items with `launchSingleTop`, restore state, and pop to Workbench. Secondary editor/detail routes keep back buttons and disable drawer gestures.

- [ ] **Step 4: Make onboarding and existing-user startup enter Workbench**

Change `FirstRunDestination.Calendar` to `Workbench`; after the first habit is saved, remove welcome/editor from the back stack and enter Workbench.

- [ ] **Step 5: Run policy tests and compile, then commit**

Run: `./gradlew.bat testDebugUnitTest --tests "com.habit.app.ui.navigation.NavigationPolicyTest" && ./gradlew.bat compileDebugKotlin`
Expected: both commands succeed.

Commit: `feat: replace bottom navigation with drawer`

### Task 5: Today Workbench UI

**Files:**
- Create: `app/src/main/java/com/habit/app/ui/workbench/WorkbenchScreen.kt`
- Create: `app/src/main/java/com/habit/app/ui/workbench/RecentWeekStrip.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitNavHost.kt`

**Interfaces:**
- Consumes: `WorkbenchUiState` and callbacks for drawer, create, calendar, detail, and toggle.
- Produces: the default page with progress, habit rows, seven-day markers, month summary, and empty/error states.

- [ ] **Step 1: Build the screen from state-only composables**

```kotlin
@Composable fun WorkbenchScreen(
    viewModel: WorkbenchViewModel,
    onOpenDrawer: () -> Unit,
    onCreateHabit: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenHabit: (Long) -> Unit,
)
```

Use `LazyColumn`, a circular/linear progress card, stable tags (`workbench_screen`, `workbench_toggle_<id>`, `recent_week_strip`), and a white neutral card background.

- [ ] **Step 2: Render distinct recent-day Emoji**

```kotlin
val visible = day.iconKeys.distinct().take(3)
visible.forEach { Text(habitEmoji(it)) }
if (day.iconKeys.distinct().size > 3) Text("+${day.iconKeys.distinct().size - 3}")
```

- [ ] **Step 3: Wire navigation and guarded toggles, compile, then commit**

Run: `./gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Commit: `feat: add Today workbench UI`

### Task 6: Searchable Emoji bottom sheet and editor integration

**Files:**
- Replace: `app/src/main/java/com/habit/app/ui/components/EmojiPicker.kt`
- Modify: `app/src/main/java/com/habit/app/ui/habits/HabitEditorScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/habits/HabitEditorViewModel.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitNavHost.kt`

**Interfaces:**
- Consumes: `EmojiCatalog`, recent keys Flow, and `EmojiPreferencesRepository.record`.
- Produces: `EmojiPickerSheet(selected, recentKeys, onSelected, onDismiss)`.

- [ ] **Step 1: Replace inline six-chip picker with a bottom-sheet trigger**

Show the selected Emoji inside a 56dp outlined button. The sheet contains a search field, horizontal category chips, six-column `LazyVerticalGrid`, and custom input.

- [ ] **Step 2: Implement custom input feedback before closing**

```kotlin
val normalized = normalizeEmojiKey(customInput)
if (normalized == null) customError = "请输入一个 Emoji"
else onSelected(normalized)
```

Record only successful selections, de-duplicate recent values, and keep old keys editable.

- [ ] **Step 3: Compile and run Emoji tests, then commit**

Run: `./gradlew.bat testDebugUnitTest --tests "com.habit.app.ui.emoji.EmojiCatalogTest" && ./gradlew.bat compileDebugKotlin`
Expected: both succeed.

Commit: `feat: add searchable emoji picker`

### Task 7: Polish existing pages and calendar markers

**Files:**
- Modify: `app/src/main/java/com/habit/app/ui/calendar/CalendarScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/calendar/MonthGrid.kt`
- Modify: `app/src/main/java/com/habit/app/ui/habits/HabitListScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/categories/CategoryScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/habits/HabitDetailScreen.kt`

**Interfaces:**
- Consumes: shared top bar/card components and drawer/back callbacks.
- Preserves: existing repository calls, test tags needed by existing tests, and all destructive-action confirmations.

- [ ] **Step 1: Add drawer callbacks to all five top-level pages and back callbacks to secondary pages**

Use `HabitTopAppBar`; do not remove existing accessibility labels or test tags.

- [ ] **Step 2: Apply neutral cards, consistent spacing, and distinct calendar markers**

Keep `MonthGrid` at a maximum of three Emoji per day plus `+N`; update list/detail/category/settings surfaces without changing business behavior.

- [ ] **Step 3: Compile and run existing unit tests, then commit**

Run: `./gradlew.bat testDebugUnitTest`
Expected: all unit tests pass.

Commit: `style: polish Habit screens for drawer UI`

### Task 8: Versioning, compatibility audit, and APK delivery

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `docs/INSTALL.md`
- Create: `outputs/Habit-0.2.0-debug.sha256`

**Interfaces:**
- Produces: `app/build/outputs/apk/debug/app-debug.apk` and a copied `outputs/Habit-0.2.0-debug.apk` signed with the existing debug key.

- [ ] **Step 1: Update only Android version metadata**

```kotlin
versionCode = 2
versionName = "0.2.0"
```

Confirm the schema JSON remains version 1, `applicationId` remains unchanged, `habit.db` remains unchanged, and `fallbackToDestructiveMigration` is absent.

- [ ] **Step 2: Run focused final verification**

Run: `./gradlew.bat testDebugUnitTest lintDebug assembleDebug`
Expected: BUILD SUCCESSFUL with zero test or lint failures.

- [ ] **Step 3: Copy and hash the APK**

Use `Copy-Item` to create `outputs/Habit-0.2.0-debug.apk`, then `Get-FileHash -Algorithm SHA256` and store the hash in `outputs/Habit-0.2.0-debug.sha256`.

- [ ] **Step 4: Run the upgrade-invariant audit**

Run searches proving `applicationId = "com.habit.app"`, `version = 1` in `HabitDatabase`, database name `habit.db`, no destructive migration, and unchanged entity fields. Do not claim real-device upgrade success; hand the APK to the user with `adb install -r` instructions.

- [ ] **Step 5: Commit delivery metadata**

Commit: `build: prepare Habit 0.2 APK`
