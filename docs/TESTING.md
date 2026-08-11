# Habit 0.6.0 发布测试

结果日期：2026-08-11

## 门禁环境

- JDK：`D:\MySoftware\Java\jdk-17`
- Android SDK：`D:\MySoftware\Android\AndroidSdk`
- Gradle：`D:\MySoftware\Gradle\gradle-9.5.0`
- Gradle 缓存：`D:\MySoftware\Gradle\cache`
- 目标设备：`Small_Phone_API_35`，serial `emulator-5554`，Android 15 / API 35

## 最终自动化结果

- 版本/文档 smoke：5 项通过，0 失败、0 错误、0 跳过；测试先在 0.5.1/11 和旧文档上取得 2 项预期失败，再更新到 0.6.0/12。
- JVM：45 个测试套件、227 项测试全部通过，0 失败、0 错误、0 跳过。
- Android Lint：0 个 error、17 个非阻断 warning、2 个 hint。API 23 的 HTTP 响应长度兼容回归已覆盖，2 MiB 有界读取策略保持不变。
- Android 测试编译：`compileDebugAndroidTestKotlin` 成功。
- 设备：`Small_Phone_API_35` / `emulator-5554`，163 项测试全部通过，0 失败、0 错误、0 跳过，runner 用时 1,149.973 秒；Gradle 完整任务 `BUILD SUCCESSFUL`，用时 19 分 50 秒。
- 首次设备门禁的外层 10 分钟上限先于 163 项结束，135 项完成后 Gradle 被终止但 runner 继续推进；定向复现当时所在的饮食详情用例为 1/1 通过（13.189 秒）。清理孤儿 runner 后，以 30 分钟上限重新执行完整套件并取得上述 163/163 结果。

## 自动化门禁

先运行版本/文档 smoke，再连续执行完整 JVM、Lint、Android 测试编译和设备套件：

```powershell
& 'D:\MySoftware\Gradle\gradle-9.5.0\bin\gradle.bat' --gradle-user-home 'D:\MySoftware\Gradle\cache' testDebugUnitTest --tests 'com.habit.app.ProjectSmokeTest' --offline
& 'D:\MySoftware\Gradle\gradle-9.5.0\bin\gradle.bat' --gradle-user-home 'D:\MySoftware\Gradle\cache' testDebugUnitTest lintDebug compileDebugAndroidTestKotlin --offline
& 'D:\MySoftware\Gradle\gradle-9.5.0\bin\gradle.bat' --gradle-user-home 'D:\MySoftware\Gradle\cache' connectedDebugAndroidTest --offline
```

发布前必须同时满足：JVM 0 失败、Lint 0 错误、Android 测试编译成功、当前全部设备测试 0 失败。最终精确测试总数以 XML 结果和设备报告统计，不从源码文件数推算。

## 数据库与备份兼容

- `HabitDatabaseMigrationTest.migrateFourToFivePreservesExistingDataAndCreatesAiTables` 从真实 Room v4 schema 写入分类、习惯、打卡、饮食和照片，执行 `MIGRATION_4_5` 后逐项确认旧数据仍为 1 条，并确认新 AI 表为空且可查询。
- 0.5.1 → 0.6.0 必须做真实 APK 覆盖安装：先安装稳定签名的 0.5.1，写入习惯、打卡、饮食、照片和备份数据，再执行 `adb install -r` 安装 0.6.0；不得卸载或清除数据。升级后逐项核对旧数据与照片，并确认 AI 页面为未配置状态。
- 备份 schema 5 包含非秘密的模型配置、功能绑定、周报和图片热量估算依据；编码测试必须确认不含 `apiKey`、Authorization、密文或哨兵 Key。
- 备份 schema 1–4 兼容由 `HabitBackupCodecTest` 覆盖：schema 1 补空饮食集合、schema 2 保留饮品属性、schema 3 归一化饮食分类、schema 4 保留自定义分类并补空 AI 集合。还需从测试夹具或历史备份执行导入预览，确认不会要求旧备份包含 AI 字段。
- 恢复备份后需要重新输入 API Key；合并导入只清除受影响模型的旧 Key，完全替换会清除全部旧 Key。重新测试模型并核对功能绑定后才可调用 AI。

## AI 假端点与隐私回归

自动化测试使用注入的假客户端覆盖模型新增/编辑/删除、文本/图片能力测试、功能绑定、综合周报预览/保存/替换、图片选择/确认/取消和热量采用。端到端网络验证使用本机假端点：

1. 启动只记录经过脱敏请求的 OpenAI Chat Completions 兼容服务，监听 `127.0.0.1:18080`，路径为 `/v1/chat/completions`。
2. 执行 `adb reverse tcp:18080 tcp:18080`，在应用中配置 `http://127.0.0.1:18080/v1`、假模型 ID 和假 Key，并明确确认 HTTP 风险。
3. “测试文本”返回成功后绑定综合周报；确认请求只含上一完整周的结构化习惯/饮食摘要，不含照片、Key 字段或本地文件。
4. “测试图片”返回成功后绑定图片热量估算；未选择照片、取消确认或关闭弹窗时服务器必须收到 0 次请求，只有“确认并开始估算”才发送已选照片。
5. 分别返回 401、429、超时、5xx 和畸形 JSON，确认 UI 只显示安全中文错误，不泄露 Key、原始响应或内部模型标识。

## AI 关闭回归

在没有模型、没有 API Key、两项功能均“暂不绑定”的全新或升级安装上：

- 习惯创建、编辑、归档、删除、今日打卡、补签和月历统计正常；
- 饮食记录、照片、模板、分类、统计、详情页和“再记一次”正常；
- 导出、schema 1–4 导入、schema 5 导入与备份目录迁移正常；
- 打开综合周报或图片热量入口只显示未配置/不可用提示，不发起网络请求，不影响记录保存；
- 冷启动后 logcat 无 `FATAL EXCEPTION`，应用 Activity 正常恢复到前台。

## 发布 APK 验证

从干净已提交源码构建 release，将输出复制为 `artifacts\Habit-0.6.0-release.apk`。用 SDK `aapt2` 与 `apksigner` 验证：包名 `com.habit.app`、versionCode 12、versionName 0.6.0、minSdk 23、targetSdk 36、v1/v2 签名为 true，且证书 SHA-256 与 0.5.1 完全相同。复制前后的 SHA-256 必须一致，并记录最终 APK 字节数、完整 SHA-256、设备 serial、覆盖安装/数据保留及冷启动证据。
