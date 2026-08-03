# Diet UI Bugfix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 Habit 导航、饮食日期轴、日期时间编辑、记录类型锁定与空状态对齐问题，并交付更新后的 0.3.1 APK。

**Architecture:** 保持现有 Compose/Room 架构，仅在公共顶部栏、侧栏目的地和饮食 UI/ViewModel 中做局部修改。可测试的日期时间与类型行为下沉为纯状态转换函数，布局变化由 Compose 编译和模拟器验证。

**Tech Stack:** Kotlin, Jetpack Compose Material 3, JUnit 4, Gradle

## Global Constraints

- 不修改 Room schema 或备份格式。
- 不新增依赖。
- 侧边栏入口全部使用 Emoji。
- 版本更新为 `0.3.1`，`versionCode` 更新为 `6`。

---

### Task 1: 饮食编辑状态行为

**Files:**
- Modify: `app/src/main/java/com/habit/app/ui/diet/DietEditorViewModel.kt`
- Test: `app/src/test/java/com/habit/app/ui/diet/DietEditorViewModelTest.kt`

**Interfaces:**
- Produces: `DietEditorUiState.withDate(LocalDate)`、`DietEditorUiState.withTime(LocalTime)`，更新时保留另一部分状态。

- [ ] **Step 1: 写失败测试**

```kotlin
@Test fun selectingTimeKeepsDate() {
    val initial = editorState(date = LocalDate.of(2026, 8, 3), time = LocalTime.of(8, 0))
    assertEquals(LocalDate.of(2026, 8, 3), initial.withTime(LocalTime.of(21, 45)).date)
    assertEquals(LocalTime.of(21, 45), initial.withTime(LocalTime.of(21, 45)).time)
}
```

- [ ] **Step 2: 验证测试因函数缺失而失败**

Run: `gradlew :app:testDebugUnitTest --tests "*DietEditorViewModelTest"`
Expected: FAIL，提示 `withTime` 或 `withDate` 未定义。

- [ ] **Step 3: 实现最小状态转换函数**

```kotlin
internal fun DietEditorUiState.withDate(value: LocalDate) = copy(date = value)
internal fun DietEditorUiState.withTime(value: LocalTime) = copy(time = value.withSecond(0).withNano(0))
```

- [ ] **Step 4: 运行目标测试并确认通过**

Run: `gradlew :app:testDebugUnitTest --tests "*DietEditorViewModelTest"`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/habit/app/ui/diet/DietEditorViewModel.kt app/src/test/java/com/habit/app/ui/diet/DietEditorViewModelTest.kt
git commit -m "fix: support precise diet date and time"
```

### Task 2: 导航和饮食布局修复

**Files:**
- Modify: `app/src/main/java/com/habit/app/ui/components/HabitTopAppBar.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitDestination.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitDrawerContent.kt`
- Modify: `app/src/main/java/com/habit/app/ui/diet/DietDiaryScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/diet/DietEditorScreen.kt`

**Interfaces:**
- Consumes: `DietEditorUiState.withDate`、`DietEditorUiState.withTime`。
- Produces: 七等分日期轴、日期/时间选择弹窗、编辑类型锁定、居中空状态。

- [ ] **Step 1: 将侧栏符号统一替换为 Emoji，并调整公共返回按钮左边距**

```kotlin
DrawerDestination(HabitDestination.Workbench, "今日工作台", "🏠")
DrawerDestination(HabitDestination.DietDiary, "饮食日记", "🍽️")
```

- [ ] **Step 2: 将周视图改为七个等宽 Column**

```kotlin
Row(Modifier.fillMaxWidth()) {
    repeat(7) { Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { /* weekday + day */ } }
}
```

- [ ] **Step 3: 新建页显示类型切换，编辑页只显示当前类型**

```kotlin
if (isEditing) AssistChip(onClick = {}, enabled = false, label = { Text(currentTypeLabel) })
else RecordTypeSelector(state, viewModel)
```

- [ ] **Step 4: 接入 Material 3 DatePickerDialog 与 TimePickerDialog**

```kotlin
TextButton(onClick = { showDatePicker = true }) { Text("选择日期") }
TextButton(onClick = { showTimePicker = true }) { Text("选择时间") }
```

- [ ] **Step 5: 为多行空状态设置整行宽度和 `TextAlign.Center`**

```kotlin
Text(message, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
```

- [ ] **Step 6: 编译并提交**

Run: `gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

```bash
git add app/src/main/java/com/habit/app/ui
git commit -m "fix: polish navigation and diet editor ui"
```

### Task 3: 版本、验证与 APK 交付

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/habit/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/HabitBackupService.kt`
- Create: `outputs/Habit-0.3.1-debug.apk`

- [ ] **Step 1: 更新版本号为 0.3.1 / 6**

```kotlin
versionCode = 6
versionName = "0.3.1"
```

- [ ] **Step 2: 运行单元测试和构建**

Run: `gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 复制并核对 APK 元数据**

```powershell
Copy-Item app\build\outputs\apk\debug\app-debug.apk outputs\Habit-0.3.1-debug.apk
```

Expected: `versionName='0.3.1' versionCode='6'`。

- [ ] **Step 4: 安装到可用设备；遇到签名冲突时不删除旧数据**

Run: `adb install -r outputs/Habit-0.3.1-debug.apk`
Expected: Success；若为 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，保留旧应用并明确报告。

- [ ] **Step 5: 提交版本变更并交付 APK**

```bash
git add app/build.gradle.kts app/src/main/java/com/habit/app/ui/settings/SettingsScreen.kt app/src/main/java/com/habit/app/data/backup/HabitBackupService.kt
git commit -m "build: prepare habit 0.3.1"
```
