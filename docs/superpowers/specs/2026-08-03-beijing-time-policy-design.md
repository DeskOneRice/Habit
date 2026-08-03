# Habit 北京时间统一策略设计

## 目标

Habit 中所有面向用户的日期、时间、当天边界和备份文件名统一使用北京时间 `Asia/Shanghai`（UTC+8），不随设备时区变化，同时保持旧数据库和旧备份兼容。

## 时间语义

- `createdAt`、`updatedAt`、`occurredAt`、`exportedAt` 继续保存 Unix epoch milliseconds，表示绝对时刻，不人为增加 8 小时。
- `startEpochDay`、`checkInEpochDay`、`recordEpochDay` 按北京时间的本地日期计算。
- 所有可见时间在展示前使用 `Asia/Shanghai` 转换。
- “今天”、跨日刷新、习惯打卡日期和饮食记录日期均以北京时间零点为边界。

## 修改范围

1. 新增唯一的应用时区常量 `HabitTimeZone.zoneId = ZoneId.of("Asia/Shanghai")`，禁止业务代码直接使用 `ZoneId.systemDefault()`。
2. `SystemDeviceDateProvider` 始终通过应用时区计算今天，并向日历、习惯详情和饮食编辑器提供同一时区。
3. 备份文件名从 UTC 格式化改为北京时间格式化。
4. 设置页的备份预览时间显式使用北京时间格式化，不依赖系统默认时区。
5. DatePicker 内部仍按 UTC 处理纯日期毫秒值，因为 Material 3 的日期选择协议使用 UTC；选中的 `LocalDate` 进入业务层后仍按北京时间解释。

## 兼容性

- Room schema 不变，不进行数据库迁移。
- 备份 schema 继续为 v2；`exportedAt` 等数值字段含义不变。
- 旧版数据与备份可直接读取，并在新版界面按北京时间显示。
- 排序、冲突合并和“以较新数据为准”的比较继续使用 epoch milliseconds。

## 固定签名

- 下一版为 `0.3.2`，`versionCode = 7`。
- 使用当前 0.3.1 的证书作为今后的固定证书，保存于 `D:\MySoftware\Android\Signing\Habit`，私钥不提交到 Git。
- Gradle 从本地签名配置读取固定密钥；后续构建不得退回自动生成的临时 debug keystore。
- 由于 0.3.0 的临时私钥已丢失，0.3.0 无法直接覆盖升级到新签名版本；首次转换必须先导出数据，再卸载 0.3.0、安装 0.3.2 并导入。0.3.2 之后可直接覆盖升级。

## 测试与验收

- 在 JVM 默认时区分别设为 UTC、东京和洛杉矶时，同一时刻得到的业务日期始终是北京时间日期。
- 固定时刻 `2026-08-03T16:30:45Z` 的备份文件名包含北京时间 `20260804-003045`。
- 备份预览时间在任意系统时区下均显示北京时间。
- 完整单元测试与 APK 构建通过。
- APK 签名指纹与 0.3.1 的 `8674967e7901174339cd4fc85726ebce59b572a7ab58a799139e300a69382abb` 一致。
