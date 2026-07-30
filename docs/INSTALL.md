# Habit 0.1.0 安装与快速开始

## 安装包信息

- APK 文件名：`Habit-0.1.0-debug.apk`
- Android 包名：`com.habit.app`
- 构建类型：调试版（debug）
- 最低 Android 版本：Android 6.0（API 23）

## 通过 USB / ADB 安装

1. 在手机的开发者选项中开启“USB 调试”，用 USB 连接电脑，并在手机上确认调试授权。
2. 在 PowerShell 中进入 APK 所在目录。
3. 查看设备并安装：

```powershell
& 'D:\MySoftware\Android\AndroidSdk\platform-tools\adb.exe' devices -l
& 'D:\MySoftware\Android\AndroidSdk\platform-tools\adb.exe' install 'Habit-0.1.0-debug.apk'
```

如果手机上已经安装同一包名的旧版，可保留本地数据覆盖升级：

```powershell
& 'D:\MySoftware\Android\AndroidSdk\platform-tools\adb.exe' install -r 'Habit-0.1.0-debug.apk'
```

ADB 必须显示目标手机状态为 `device`；`offline` 或 `unauthorized` 时请先重新连接并确认手机上的授权提示。

## 直接在手机上安装

1. 将 `Habit-0.1.0-debug.apk` 复制到手机。
2. 在文件管理器中点按 APK。
3. 如果系统拦截安装，请按提示仅为当前文件管理器或浏览器开启“允许安装未知来源应用”，安装后可再次关闭该权限。
4. 确认安装并打开 Habit。

## 首次使用

1. 在欢迎页点“创建我的第一个习惯”。
2. 填写名称，选择 Emoji 图标、识别颜色、分类和开始日期，然后保存。
3. 在“日历”中点当天日期，可完成或取消打卡；过去的有效日期也可补签。
4. 在“习惯”中进入详情，可查看累计完成、当前连续、最长连续和单习惯月历。
5. 在“设置”中可切换五套内置主题并管理分类。

## 本地数据说明

Habit 0.1.0 不需要账号，习惯、分类、打卡和主题均只保存在本机，不会上传或同步到云端。应用已禁用 Android 系统云备份，并通过提取规则排除 Android 12 及以上的设备间数据迁移；卸载应用、清除应用数据或刷机会永久删除这些记录，当前版本不提供备份恢复功能。
