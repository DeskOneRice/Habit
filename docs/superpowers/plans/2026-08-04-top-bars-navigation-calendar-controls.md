# Habit Top Bars, Navigation, and Calendar Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every page respect the status bar, distinguish drawer and back navigation, reset drawer destinations to their roots, and use large lightweight arrows without circular fills.

**Architecture:** Keep `HabitTopAppBar` as the single top-bar component. Represent drawer category management with a dedicated root route while the existing category route remains a child route, and centralize drawer stack-reset options in a small navigation policy. Reuse one lightweight icon-button component for top-bar and month arrows.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation Compose, JUnit 4, Android Gradle Plugin.

## Global Constraints

- Preserve existing Room schema and backup formats.
- Keep every icon touch target at least 48dp.
- Use low-saturation sky-blue styling with mostly white surfaces.
- Do not restore prior detail or editor pages when navigating from the drawer.

---

### Task 1: Navigation source and stack-reset policy

**Files:**
- Modify: `app/src/test/java/com/habit/app/ui/navigation/NavigationPolicyTest.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitDestination.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitApp.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitNavHost.kt`

**Interfaces:**
- Produces: `HabitDestination.DrawerCategories`, `DrawerNavigationPolicy`, and separate menu/back category routes.

- [ ] **Step 1: Write failing tests** asserting the drawer uses `categories_drawer` and the drawer navigation policy disables saved-state restoration.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests com.habit.app.ui.navigation.NavigationPolicyTest` and confirm the new assertions fail because the route and policy do not exist.
- [ ] **Step 3: Implement** `DrawerCategories`, render it with `NavigationMode.MENU`, keep `Categories` with `NavigationMode.BACK`, and navigate with `saveState = false` plus `restoreState = false`.
- [ ] **Step 4: Re-run** the targeted test and confirm it passes.

### Task 2: Shared safe top bars and lightweight controls

**Files:**
- Modify: `app/src/main/java/com/habit/app/ui/components/HabitTopAppBar.kt`
- Create: `app/src/main/java/com/habit/app/ui/components/LightweightArrowButton.kt`
- Modify: `app/src/main/java/com/habit/app/ui/habits/HabitDetailScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/categories/CategoryScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/calendar/CalendarScreen.kt`
- Test: `app/src/androidTest/java/com/habit/app/ui/components/HabitTopAppBarTest.kt`

**Interfaces:**
- Consumes: `NavigationMode.MENU` and `NavigationMode.BACK` from the shared top-bar component.
- Produces: `LightweightArrowButton(onClick, direction, contentDescription, modifier)` and Scaffold-based detail/category screens.

- [ ] **Step 1: Write failing Compose tests** that expect a large lightweight navigation symbol and stable `open_drawer`/`navigate_back` semantics.
- [ ] **Step 2: Compile/run the targeted UI test** and confirm it fails against the current filled/custom implementations.
- [ ] **Step 3: Implement** the shared transparent 48dp arrow button, replace filled month arrows, and move detail/category titles into `Scaffold(topBar = HabitTopAppBar(...))`.
- [ ] **Step 4: Re-run** targeted UI tests and unit tests; confirm they pass.

### Task 3: Version, build, and release artifact

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `outputs/Habit-0.4.1-debug.apk`

**Interfaces:**
- Produces: upgrade-compatible APK version `0.4.1`, version code `9`.

- [ ] **Step 1: Update** `versionName` to `0.4.1` and `versionCode` to `9` without touching database versioning.
- [ ] **Step 2: Run** unit tests, lint, and `assembleDebug`; expect all tasks to complete successfully.
- [ ] **Step 3: Verify** APK metadata and signing fingerprint, then copy the built APK to `outputs/Habit-0.4.1-debug.apk`.
- [ ] **Step 4: Commit** implementation and release artifact metadata with a focused bug-fix commit.
