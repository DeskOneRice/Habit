# Habit 0.6.1 安装与快速开始

## 安装包信息

- APK 文件名：`Habit-0.6.1-release.apk`
- Android 包名：`com.habit.app`
- 版本：`versionCode 13` / `versionName 0.6.1`
- 构建类型：稳定签名的 release 版（签名身份与 0.5.1 相同）
- 最低 Android 版本：Android 6.0（API 23）

## 从 0.5.1 覆盖升级

覆盖升级会触发 Room `MIGRATION_4_5`，保留已有习惯、打卡、饮食、照片、模板、分类和设置。升级前可先在“主题与设置”中导出一份备份；**不要先卸载 0.5.1，也不要清除应用数据**。

1. 在手机的开发者选项中开启“USB 调试”，用 USB 连接电脑，并在手机上确认调试授权。
2. 查看设备并覆盖安装 0.6.1：

```powershell
& 'D:\MySoftware\Android\AndroidSdk\platform-tools\adb.exe' devices -l
& 'D:\MySoftware\Android\AndroidSdk\platform-tools\adb.exe' install -r 'artifacts\Habit-0.6.1-release.apk'
```

3. ADB 返回 `Success` 后打开 Habit，确认旧习惯、打卡、饮食记录及照片仍在，并检查 AI 页面显示未配置状态。

ADB 必须显示目标设备状态为 `device`；`offline` 或 `unauthorized` 时请先重新连接并确认设备上的授权提示。若提示签名不一致，请停止安装并核对 APK 来源，不能用卸载应用来规避签名检查。

## 全新安装

```powershell
& 'D:\MySoftware\Android\AndroidSdk\platform-tools\adb.exe' install 'artifacts\Habit-0.6.1-release.apk'
```

## 直接在手机上安装

1. 将 `Habit-0.6.1-release.apk` 复制到手机。
2. 在文件管理器中点按 APK。
3. 如果系统拦截安装，请按提示仅为当前文件管理器或浏览器开启“允许安装未知来源应用”，安装后可再次关闭该权限。
4. 已安装 0.5.1 时选择更新现有应用；确认安装并打开 Habit。

## 首次使用

1. 在欢迎页点“创建我的第一个习惯”。
2. 填写名称，选择 Emoji 图标、识别颜色、分类和开始日期，然后保存。
3. 默认进入“今日工作台”，可直接完成打卡；从屏幕左边缘右滑或点左上角菜单进入其他页面。
4. 在“习惯日历”中点日期，可完成或取消打卡；过去的有效日期也可补签。
5. 在“我的习惯”中进入详情，可查看累计完成、当前连续、最长连续和单习惯月历。
6. 在“主题与设置”中可切换内置主题，并可导出、导入或迁移本地备份。

## 可选 AI 设置

AI 功能默认关闭，不影响本地记录。需要启用时：

1. 打开侧栏“AI 洞察”→“模型配置”，点右下角 `+`。
2. 填写名称、OpenAI 兼容 API 地址、模型 ID 和 API Key，勾选模型实际支持的“文本”或“图片”能力。优先使用 HTTPS；HTTP 仅适合受信任的本地测试环境，并必须阅读风险提示后明确允许。
3. 保存后分别点“测试文本”或“测试图片”。只有已启用、能力匹配且测试通过的模型才能绑定。
4. 切到“功能绑定”，为“综合周报”和“饮食热量估算”分别选择模型；不需要时选择“暂不绑定”。
5. 综合周报会在发送结构化摘要前再次确认；饮食热量估算要求餐食或饮品已有照片，并在选择照片后再次确认发送当前记录信息与照片。

API Key 只保存在当前安装的加密存储中。恢复备份后需要重新输入 API Key，重新执行能力测试并检查功能绑定；备份文件不会提供或恢复 Key。

## 假端点测试流程

测试人员可使用本机 OpenAI Chat Completions 兼容假端点验证成功、鉴权失败、超时和错误响应，不必发送真实数据。假端点应监听 `/v1/chat/completions`，返回 `choices[0].message.content`；模拟器或 USB 设备可先映射端口：

```powershell
& 'D:\MySoftware\Android\AndroidSdk\platform-tools\adb.exe' reverse tcp:18080 tcp:18080
```

在模型配置中填写 `http://127.0.0.1:18080/v1`、测试模型 ID 和假 Key，明确确认 HTTP 风险，再运行“测试文本/测试图片”。测试结束后删除该模型或改回 HTTPS；不要把真实 API Key 输入假端点。

## 本地数据说明

Habit 0.6.1 不需要账号，业务数据默认保存在本机，不会自动同步。只有用户主动确认生成周报或估算饮食热量时，应用才会向已绑定模型发起网络请求。应用内默认将备份写入 `内部存储 / Download / Habit / 数据备份`，也可在设置中选择其他文件夹并自动迁移已有备份。导入时可选择“合并导入”或“完全替换”；合并冲突按更新时间和既定规则处理。schema 5 备份可包含 AI 模型的非秘密元数据、功能绑定、周报及热量依据，但不包含 API Key。
