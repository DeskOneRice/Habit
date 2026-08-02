# Habit 0.2.2 Calendar Alignment and System Bars Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align weekday labels with date cells and make status/navigation bar content readable on every built-in light theme.

**Architecture:** Reuse one centered calendar-cell composable for weekday headers, empty cells, and dates so column geometry cannot diverge. Derive a small pure system-bar appearance model from the active Material color scheme, then apply it from the Compose theme using a side effect so theme changes update the Android window.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose Material 3, AndroidX Core window insets, JUnit 4, Compose UI testing.

## Global Constraints

- Set `versionCode = 4` and `versionName = "0.2.2"` only after both fixes pass tests.
- Keep Room schema version, DataStore keys, and backup JSON schema unchanged.
- Never add `fallbackToDestructiveMigration`.
- Preserve application ID `com.habit.app` and sign the APK with the first-version certificate.
- Do not add food tracking tables, routes, or UI in this patch.

---

### Task 1: One centered geometry for weekday and date cells

**Files:**
- Modify: `app/src/main/java/com/habit/app/ui/components/HabitDatePicker.kt`
- Modify: `app/src/test/java/com/habit/app/ui/components/HabitDatePickerTest.kt`
- Create: `app/src/androidTest/java/com/habit/app/ui/components/HabitDatePickerAlignmentTest.kt`

**Interfaces:**
- Produces: semantic tags `habit_weekday_1` through `habit_weekday_7` and `habit_date_<day>` used only for UI verification.
- Produces: `internal val HABIT_CALENDAR_CELL_ALIGNMENT = Alignment.Center`, consumed by every calendar cell.
- Consumes: existing `HabitDatePickerDialog`, `HABIT_WEEKDAY_LABELS`, and `buildMonthCells`.

- [ ] **Step 1: Write the failing unit and Compose alignment tests**

First add a JVM assertion that the shared alignment policy is centered. It must fail to compile before production code defines the policy.

```kotlin
assertEquals(Alignment.Center, HABIT_CALENDAR_CELL_ALIGNMENT)
```

Render `HabitDatePickerDialog(LocalDate.of(2026, 8, 3), {}, {})`, fetch the unclipped bounds of `habit_weekday_1` and `habit_date_3`, and assert their horizontal centers differ by less than one pixel. Repeat for Sunday using `habit_weekday_7` and `habit_date_2`.

```kotlin
private fun assertSameCenter(firstTag: String, secondTag: String) {
    val first = composeRule.onNodeWithTag(firstTag).getUnclippedBoundsInRoot().center.x
    val second = composeRule.onNodeWithTag(secondTag).getUnclippedBoundsInRoot().center.x
    assertTrue(abs(first - second) < 1f)
}
```

- [ ] **Step 2: Verify the new JVM test fails for the expected reason**

Run `:app:testDebugUnitTest --tests com.habit.app.ui.components.HabitDatePickerTest`; expected result is compilation failure because `HABIT_CALENDAR_CELL_ALIGNMENT` does not exist. Compile the Compose test after implementing the policy; run it on the next available ADB device.

- [ ] **Step 3: Implement one shared centered cell**

Add a private `CalendarCell` whose modifier is `weight(1f).aspectRatio(1f)` and whose `Box` uses `contentAlignment = HABIT_CALENDAR_CELL_ALIGNMENT`. Use it for weekday labels and empty cells. For dates, keep the `TextButton` but place it inside the same cell and make it `fillMaxSize()`. Apply the semantic tags to the full cells.

- [ ] **Step 4: Verify the alignment test compiles and the existing date tests pass**

Run focused JVM tests for `HabitDatePickerTest`, then compile all Android tests.

- [ ] **Step 5: Commit**

Commit message: `fix: align date picker columns`.

---

### Task 2: Theme-aware readable system bars

**Files:**
- Modify: `app/src/main/java/com/habit/app/ui/theme/HabitTheme.kt`
- Create: `app/src/test/java/com/habit/app/ui/theme/HabitSystemBarsTest.kt`

**Interfaces:**
- Produces: `data class HabitSystemBarAppearance(val background: Color, val darkIcons: Boolean)`.
- Produces: `fun systemBarAppearance(themeId: HabitThemeId): HabitSystemBarAppearance`.
- Consumes: existing `HabitThemeId.colorScheme()`.

- [ ] **Step 1: Write the failing pure unit test**

For every `HabitThemeId.entries`, assert `systemBarAppearance(theme).darkIcons` is true and its background equals `theme.colorScheme().background`.

```kotlin
HabitThemeId.entries.forEach { theme ->
    val appearance = systemBarAppearance(theme)
    assertTrue(appearance.darkIcons)
    assertEquals(theme.colorScheme().background, appearance.background)
}
```

- [ ] **Step 2: Run the focused test and observe unresolved symbols**

Run `:app:testDebugUnitTest --tests com.habit.app.ui.theme.HabitSystemBarsTest`; expected result is compilation failure because the appearance API does not exist.

- [ ] **Step 3: Add the pure appearance model and apply it to the window**

Inside `HabitTheme`, derive the active appearance, obtain the hosting `Activity` from `LocalView`, and in `SideEffect` set status/navigation bar colors to the theme background. Use `WindowCompat.getInsetsController(window, view)` to set both `isAppearanceLightStatusBars` and `isAppearanceLightNavigationBars` to `darkIcons`. Skip the side effect in inspection mode.

- [ ] **Step 4: Run focused and full tests**

Run `HabitSystemBarsTest`, then all debug JVM tests and Android-test compilation.

- [ ] **Step 5: Commit**

Commit message: `fix: keep light system bars readable`.

---

### Task 3: Publish and verify 0.2.2

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/habit/app/ui/settings/SettingsScreen.kt`
- Modify: `docs/INSTALL.md`
- Create: `outputs/Habit-0.2.2-debug.apk`
- Create: `outputs/Habit-0.2.2-debug.sha256`

**Interfaces:**
- Consumes: Tasks 1 and 2 passing implementation.
- Produces: installable APK compatible with the existing local-data installation.

- [ ] **Step 1: Update visible and manifest version values**

Set Gradle to version code 4/name 0.2.2, set Settings to `0.2.2 · 内测版`, and update install documentation filenames and behavior.

- [ ] **Step 2: Run release verification**

Run all JVM tests, compile Android tests, and confirm `rg "fallbackToDestructiveMigration" app` returns no matches.

- [ ] **Step 3: Build and sign**

Build the APK with the configured offline Android toolchain. If Gradle signing is blocked by the sandbox keystore lock, assemble unsigned release output, ZIP-align it, and sign it with `.android-upgrade/debug.keystore` using Android `apksigner`.

- [ ] **Step 4: Verify artifact metadata**

Use `aapt dump badging`, `zipalign -c`, and `apksigner verify --print-certs`. Require package `com.habit.app`, version code 4/name 0.2.2, and certificate SHA-256 `8674967e7901174339cd4fc85726ebce59b572a7ab58a799139e300a69382abb`.

- [ ] **Step 5: Commit and push**

Commit message: `chore: release Habit 0.2.2`. Push `codex/ui-workbench-drawer` after verification.
