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
- 设备测试：56 个通过，0 失败，0 错误，0 跳过
- Android Lint：`lintDebug` 成功，0 错误（5 个非阻断警告、1 个提示）
- `assembleDebug`：成功
- APK：`Habit-0.1.0-debug.apk`
- APK 大小：14,594,704 字节（13.92 MiB）
- APK SHA-256：`ce2c9480864abb8229c3d9afb415a99a2ec9a7cbba7d8299e32e566726eef113`
- 模拟器安装与启动冒烟：`adb install -r` 返回 `Success`；冷启动返回 `Status: ok`，`com.habit.app/.MainActivity` 成为 `topResumedActivity`

自动化覆盖包括完整 MVP 用户旅程、动态设备本地日期、欢迎与返回栈、今日打卡、整体与单习惯月历、三项统计、编辑入口、主题即时切换与重建持久化，以及 360 dp / 480 dp、1.3 倍字体、中文无障碍描述和 48 dp 触控目标。

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
