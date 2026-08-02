# Habit 0.2.1 安装与快速开始

## 安装包信息

- APK 文件名：`Habit-0.2.1-debug.apk`
- Android 包名：`com.habit.app`
- 构建类型：调试版（debug）
- 最低 Android 版本：Android 6.0（API 23）

## 通过 USB / ADB 安装

1. 在手机的开发者选项中开启“USB 调试”，用 USB 连接电脑，并在手机上确认调试授权。
2. 在 PowerShell 中进入 APK 所在目录。
3. 查看设备并安装：

```powershell
& 'D:\MySoftware\Android\AndroidSdk\platform-tools\adb.exe' devices -l
& 'D:\MySoftware\Android\AndroidSdk\platform-tools\adb.exe' install 'Habit-0.2.1-debug.apk'
```

如果手机上已经安装同一包名的旧版，可保留本地数据覆盖升级：

```powershell
& 'D:\MySoftware\Android\AndroidSdk\platform-tools\adb.exe' install -r 'Habit-0.2.1-debug.apk'
```

ADB 必须显示目标手机状态为 `device`；`offline` 或 `unauthorized` 时请先重新连接并确认手机上的授权提示。

## 直接在手机上安装

1. 将 `Habit-0.2.1-debug.apk` 复制到手机。
2. 在文件管理器中点按 APK。
3. 如果系统拦截安装，请按提示仅为当前文件管理器或浏览器开启“允许安装未知来源应用”，安装后可再次关闭该权限。
4. 确认安装并打开 Habit。

## 首次使用

1. 在欢迎页点“创建我的第一个习惯”。
2. 填写名称，选择 Emoji 图标、识别颜色、分类和开始日期，然后保存。
3. 默认进入“今日工作台”，可直接完成打卡；从屏幕左边缘右滑或点左上角菜单进入其他页面。
4. 在“习惯日历”中点日期，可完成或取消打卡；过去的有效日期也可补签。
5. 在“我的习惯”中进入详情，可查看累计完成、当前连续、最长连续和单习惯月历。
6. 在“主题与设置”中可切换五套内置主题，并可导出、导入或迁移本地备份。

## 本地数据说明

Habit 0.2.1 不需要账号，习惯、分类、打卡和主题均只保存在本机，不会上传或同步到云端。从旧版升级时必须直接覆盖安装；不要先卸载旧版，也不要清除应用数据。应用内默认将备份写入 `内部存储 / Download / Habit / 数据备份`，也可在设置中选择其他文件夹并自动迁移已有备份。导入时可选择“合并导入”或“完全替换”；合并冲突以 `updatedAt` 较新的数据为准。
