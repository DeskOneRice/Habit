# Beijing Time, Skippable Onboarding, and Stable Signing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一 Habit 的北京时间语义、允许永久跳过首次习惯创建，并交付使用固定证书的 0.3.2 APK。

**Architecture:** 在 `domain/time` 建立单一时区常量，所有业务日期和显示转换依赖它，epoch milliseconds 保持不变。首次引导完成状态存入现有 DataStore，通过 ViewModel 合并习惯列表与本地标志决定启动页。签名私钥保存在 D 盘本地目录，Gradle 从被忽略的 `local.properties` 显式读取，禁止本次构建再次自动换钥。

**Tech Stack:** Kotlin, Jetpack Compose Material 3, DataStore Preferences, JUnit 4, Gradle Android Plugin

## Global Constraints

- 应用业务时区固定为 `Asia/Shanghai`。
- Room schema 和备份 schema v2 不变。
- epoch milliseconds 数值不增加 8 小时。
- `onboardingCompleted` 不进入备份。
- 版本为 `0.3.2`，`versionCode = 7`。
- APK 证书 SHA-256 必须为 `8674967e7901174339cd4fc85726ebce59b572a7ab58a799139e300a69382abb`。

---

### Task 1: 北京时间策略

**Files:**
- Create: `app/src/main/java/com/habit/app/domain/time/HabitTimePolicy.kt`
- Modify: `app/src/main/java/com/habit/app/domain/time/DeviceDateProvider.kt`
- Modify: `app/src/main/java/com/habit/app/data/backup/HabitBackupService.kt`
- Modify: `app/src/main/java/com/habit/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/test/java/com/habit/app/domain/time/DeviceDateProviderTest.kt`
- Create: `app/src/test/java/com/habit/app/data/backup/BackupTimePolicyTest.kt`
- Create: `app/src/test/java/com/habit/app/ui/settings/BackupTimeFormattingTest.kt`

**Interfaces:**
- Produces: `HabitTimePolicy.zoneId: ZoneId`、`backupFileName(epochMillis: Long): String`、`formatBackupTime(epochMillis: Long): String`。

- [ ] **Step 1: 写失败测试，证明系统默认时区会影响当前结果**

```kotlin
@Test fun businessDateAlwaysUsesBeijingTime() {
    val provider = SystemDeviceDateProvider(Clock.fixed(Instant.parse("2031-02-03T16:30:00Z"), ZoneOffset.UTC))
    assertEquals(LocalDate.of(2031, 2, 4), provider.today())
    assertEquals(ZoneId.of("Asia/Shanghai"), provider.zoneId)
}
```

- [ ] **Step 2: 运行目标测试并确认失败**

Run: `gradlew :app:testDebugUnitTest --tests "*DeviceDateProviderTest" --tests "*BackupTimePolicyTest" --tests "*BackupTimeFormattingTest"`
Expected: FAIL，因为固定时区和格式化接口尚不存在。

- [ ] **Step 3: 实现固定时区与边界格式化**

```kotlin
object HabitTimePolicy { val zoneId: ZoneId = ZoneId.of("Asia/Shanghai") }
internal fun backupFileName(epochMillis: Long) = "Habit-Backup-${FILE_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis))}.habitbackup.json"
```

- [ ] **Step 4: 目标测试通过并提交**

Run: `gradlew :app:testDebugUnitTest --tests "*DeviceDateProviderTest" --tests "*BackupTimePolicyTest" --tests "*BackupTimeFormattingTest"`
Expected: PASS。

```bash
git add app/src/main/java/com/habit/app/domain/time app/src/main/java/com/habit/app/data/backup/HabitBackupService.kt app/src/main/java/com/habit/app/ui/settings/SettingsScreen.kt app/src/test/java/com/habit/app
git commit -m "fix: standardize beijing time"
```

### Task 2: 可跳过首次引导

**Files:**
- Create: `app/src/main/java/com/habit/app/data/preferences/OnboardingPreferencesRepository.kt`
- Modify: `app/src/main/java/com/habit/app/data/preferences/HabitPreferenceKeys.kt`
- Modify: `app/src/main/java/com/habit/app/di/AppContainer.kt`
- Modify: `app/src/main/java/com/habit/app/ui/welcome/WelcomeViewModel.kt`
- Modify: `app/src/main/java/com/habit/app/ui/welcome/WelcomeScreen.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitApp.kt`
- Modify: `app/src/main/java/com/habit/app/ui/navigation/HabitNavHost.kt`
- Modify: `app/src/test/java/com/habit/app/ui/navigation/NavigationPolicyTest.kt`

**Interfaces:**
- Produces: `OnboardingPreferencesRepository.completed: Flow<Boolean>`、`suspend fun complete()`、`firstRunDestinationFor(habits, onboardingCompleted)`。

- [ ] **Step 1: 写失败测试覆盖未完成、跳过和已有习惯三种启动策略**

```kotlin
assertEquals(Welcome, firstRunDestinationFor(emptyList(), false))
assertEquals(Workbench, firstRunDestinationFor(emptyList(), true))
assertEquals(Workbench, firstRunDestinationFor(listOf(testHabit()), false))
```

- [ ] **Step 2: 运行导航策略测试并确认签名不匹配而失败**

Run: `gradlew :app:testDebugUnitTest --tests "*NavigationPolicyTest"`
Expected: FAIL，因为 `firstRunDestinationFor` 尚无完成状态参数。

- [ ] **Step 3: 实现 DataStore 仓库、启动策略和“暂时跳过”按钮**

```kotlin
class OnboardingPreferencesRepository(private val dataStore: DataStore<Preferences>) {
    val completed = dataStore.data.map { it[onboardingCompletedKey] ?: false }
    suspend fun complete() { dataStore.edit { it[onboardingCompletedKey] = true } }
}
```

- [ ] **Step 4: 运行导航策略测试与 Kotlin 编译并提交**

Run: `gradlew :app:testDebugUnitTest --tests "*NavigationPolicyTest" :app:compileDebugKotlin`
Expected: PASS / BUILD SUCCESSFUL。

```bash
git add app/src/main/java/com/habit/app app/src/test/java/com/habit/app/ui/navigation/NavigationPolicyTest.kt
git commit -m "feat: allow skipping onboarding"
```

### Task 3: 固定签名与 0.3.2 交付

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/habit/app/data/backup/HabitBackupService.kt`
- Modify: `app/src/main/java/com/habit/app/ui/settings/SettingsScreen.kt`
- Modify local ignored file: `local.properties`
- Create local secret: `D:\MySoftware\Android\Signing\Habit\habit-debug.keystore`
- Create: `outputs/Habit-0.3.2-debug.apk`

- [ ] **Step 1: 将当前固定证书复制到 D 盘并配置本地签名属性**

```properties
habit.signing.storeFile=D:/MySoftware/Android/Signing/Habit/habit-debug.keystore
habit.signing.storePassword=android
habit.signing.keyAlias=androiddebugkey
habit.signing.keyPassword=android
```

- [ ] **Step 2: Gradle 显式应用固定签名并更新版本**

```kotlin
versionCode = 7
versionName = "0.3.2"
```

- [ ] **Step 3: 运行完整单元测试和构建**

Run: `gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 核对 APK 版本、北京时间策略与签名指纹**

Run: `aapt dump badging outputs/Habit-0.3.2-debug.apk` and `apksigner verify --print-certs outputs/Habit-0.3.2-debug.apk`
Expected: `versionName='0.3.2'`、`versionCode='7'`、证书 SHA-256 为规格指定值。

- [ ] **Step 5: 提交可公开代码，保留私钥和密码配置为未跟踪本地文件**

```bash
git add app/build.gradle.kts app/src/main/java/com/habit/app/data/backup/HabitBackupService.kt app/src/main/java/com/habit/app/ui/settings/SettingsScreen.kt
git commit -m "build: prepare habit 0.3.2"
```
