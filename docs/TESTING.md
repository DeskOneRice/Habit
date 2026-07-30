# Habit 0.1.0 测试记录

结果日期：2026-07-30

## 自动化与构建环境

- JDK：`D:\MySoftware\Java\jdk-17`
- Android SDK：`D:\MySoftware\Android\AndroidSdk`
- 自动化设备：Android Emulator `sdk_gphone64_x86_64`（`emulator-5554`）
- Android API：35
- Gradle 缓存：仓库本地忽略目录 `work\.gcache`

最终 Gradle 命令：

```powershell
.\gradlew.bat clean testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug --offline --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" "-Pkotlin.incremental=false"
```

最终命令完整执行生产 Kotlin/Java、Android 测试、JVM 测试、Lint 和 APK 构建。首次 Lint 准备时曾联网下载官方 `lint-gradle:32.3.0` 到仓库本地缓存；最终整套命令在 `--offline` 模式下重新从干净构建成功。

## 最终结果

- JVM 测试：32 个通过，0 失败，0 错误，0 跳过
- 设备测试：60 个通过，0 失败，0 错误，0 跳过
- Android Lint：`lintDebug` 成功，0 错误（5 个非阻断警告、1 个提示）；`DataExtractionRules` 警告已消失
- `assembleDebug`：成功
- 完整命令：`BUILD SUCCESSFUL`，耗时 9 分 26 秒
- APK：`Habit-0.1.0-debug.apk`
- APK 大小：14,596,287 字节（13.92 MiB）
- APK SHA-256：`1388e21ee3b7b251999c0a63487f9749a7a8e4ce93498447dd15de898fe6d0fe`
- 模拟器安装与启动冒烟：`adb install -r` 返回 `Success`；冷启动返回 `Status: ok`，`com.habit.app/.MainActivity` 成为 `topResumedActivity`

自动化覆盖包括完整 MVP 用户旅程、动态设备本地日期、欢迎与返回栈、今日打卡、整体与单习惯月历、三项统计、编辑入口、主题即时切换与重建持久化，以及 360 dp / 480 dp、1.3 倍字体、中文无障碍描述和 48 dp 触控目标。

## 自适应、无障碍与安全策略复核

- 360 dp / 1.3 倍字体：真实 360 dp 测试视口中，六周月历首末日期和左右边界均在视口内；同一天的 5 个不同标记显示为 4 个 Emoji 与 `+1`。日期单元高 56 dp，Emoji 与 `+1` 均为 9 sp，不以不可读的小字号换取通过。
- 480 dp / 1.3 倍字体：设备测试通过 `wm size 960x1280` 与 320 dpi 创建真实 480 × 640 dp 配置，验证完整应用中的月份前后切换、月历左右边界、新建习惯、详情编辑、编辑器保存和设置分类入口。测试在 `finally` 中精确恢复原始显示覆盖值和字体比例；门禁前后设备均为 360 × 640 dp、1.0 倍字体。
- 主要图标按钮：月份切换、日期面板关闭、打卡/取消打卡、详情返回与月份切换，以及 Emoji/颜色选择均验证中文说明和至少 48 × 48 dp 触控目标。
- 分类短屏：主分类列表与删除非空分类后的迁移目标列表均可滚动到最后一项。
- 备份策略：源清单与打包合并清单的 `android:allowBackup` 均为 `false`；同时引用 legacy `fullBackupContent` 与 Android 12+ `dataExtractionRules`。两个规则文件对 `root`、`file`、`database`、`sharedpref`、`external` 及四个设备加密存储域全部使用 `path="."` 排除，Android 12+ 规则分别覆盖 `cloud-backup` 与 `device-transfer`。
- 打包权限：合并清单没有 `android.permission.INTERNET`、相机、位置、通讯录、存储等产品范围外权限，也没有 `<uses-feature>`。唯一 `<uses-permission>` 是 AndroidX 自动生成、仅供应用自身使用的签名级 `com.habit.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`。
- 变更卫生：最终提交前执行 `git diff --check` 与 `git diff --cached --check`，均要求 0 个空白错误。

第一次纳入全部整改的 clean 门禁在 60 个设备测试中通过 58 个，两个首次创建后的返回栈用例分别过早读取 Activity 生命周期、或只等待 5 秒。隔离重现确认系统返回最终会结束 Activity、Launcher 会恢复前台，生产导航没有回归。测试改为条件轮询 Activity 确实退出（最多 10 秒），仍保留“不能返回欢迎页或编辑器”的原断言；相关 focused 集合 5/5 通过，随后上述完整 clean 门禁 60/60 通过。

Android 12+ 设备迁移策略的 focused `ManifestPolicyTest` 最终通过 1/1。此前尝试在原工作区执行时，测试前的 Gradle 缓存 JAR 访问被 Windows 沙箱拒绝；最终使用指向同一工作树的短路径映射执行，避免路径/缓存访问问题。上述 9 分 26 秒的完整 clean 门禁也使用该映射，构建内容与本工作树一致。

## Redmi K70 / HyperOS 3.0.303.0 真机检查清单

状态：**待真机连接验证**

模拟器结果不能替代以下真机证据。只有 ADB 实际检测到 Redmi K70 后才能更新状态。

- [ ] `adb devices -l` 显示真实 Redmi K70，状态为 `device`
- [ ] 在 HyperOS 3.0.303.0 上安装并首次启动 APK
- [ ] 创建、编辑、归档和双重确认删除习惯
- [ ] 新建、重命名分类，并迁移后删除非空自定义分类
- [ ] 完成今天打卡、过去日期补签/取消，并确认未来日期不可修改
- [ ] 整体月历显示 Emoji 标记与四个以上标记的 `+N`
- [ ] 单习惯月历及累计完成、当前连续、最长连续正确
- [ ] 五套主题均可即时切换，并在重启后保持
- [ ] 重启后习惯、分类和打卡仍存在
- [ ] 主要页面无溢出、遮挡或不可点击区域

## 已知非阻断限制

- 数据仅保存在本机；没有账号、云同步、跨设备同步或备份恢复。
- 当前交付是可侧载的 debug 构建，并非应用商店签名的 release 版本。
- Redmi K70 / HyperOS 3.0.303.0 真机验证仍待真实设备连接。
- 将已验证提交安全同步到 `D:\MyProjects\VibeCodingProjects\Habit` 不在本工作区内执行，仍待后续处理。
