# Android — NotificationScheduler（中文）

- [返回概览](../index.md) | [English](../../en/android/index.md)

## 本插件声明的权限

`template/android/libnotification/AndroidManifest.xml` 中声明了：

| 权限 | 用途 |
| --- | --- |
| `POST_NOTIFICATIONS` | 在 API 33+（Android 13）上，展示任何通知前都需要在运行时申请此权限。低于 API 33 时该权限不存在，通知会无条件展示。 |
| `SCHEDULE_EXACT_ALARM` | 使应用可以在 API 31+（Android 12）上调度精确闹钟，**无需**用户在系统设置中额外授权——全新安装时仅凭该清单权限，`AlarmManager.canScheduleExactAlarms()` 就会返回 `true`，除非用户之后手动撤销。（并未使用面向“闹钟类”应用、受 Play 商店限制的替代权限 `USE_EXACT_ALARM`。） |
| `RECEIVE_BOOT_COMPLETED` | 使 `BootReceiver` 能在设备重启后重新向 `AlarmManager` 注册闹钟（系统会在重启时清除所有 `AlarmManager` 条目）。 |
| `WAKE_LOCK` | 在通知闹钟触发的短暂过程中持有唤醒锁，避免设备在通知发出前重新进入休眠。 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 仅在你调用 `requestBatteryOptimizationsPermission()`、将用户引导至“忽略电池优化”系统页面时使用；插件不会自动发起该请求。 |

以上内容均无需你手动添加或修改——它们已随插件清单文件一起提供，构建钩子会自动
将其合并进你的应用。

## 运行时权限流程（POST_NOTIFICATIONS，API 33+）

```
hasPostNotificationsPermission()             // 立即返回 { granted: boolean }
        │
        ▼
requestPostNotificationsPermission()         // 低于 API 33 时为空操作/立即返回
        │
        ▼
启动一个透明的中转 Activity
（PermissionRequestActivity），
内部调用 ActivityCompat.requestPermissions(...)
        │
        ▼
向用户展示系统权限对话框
        │
   ┌────┴────┐
 已授予     已拒绝
   │          │
   ▼          ▼
{granted:true} {granted:false}
```

- `requestPostNotificationsPermission()` 内部使用**无限**桥接超时——它会真正
  等待用户点击，无论耗时多久。
- 若用户**连续两次**拒绝对话框（或勾选“不再询问”），系统将不再弹出该对话框；
  之后再调用 `requestPostNotificationsPermission()` 会立即返回
  `{granted:false}`，且没有任何界面出现。可以通过对比前后调用结果来判断这种
  情况，并在此时引导用户使用 `openAppInfoSettings()` 手动前往系统设置授权。
- 低于 API 33 时，两个调用始终返回 `{granted:true}`——因为根本没有需要申请的
  权限。

## 精确闹钟行为与降级（Android 12+）

`NotificationServiceHub.java` 中的 `scheduleOnce()` 会根据运行时系统版本，以及
在 API 31+ 上应用当前是否持有精确闹钟调度权限，选择不同的闹钟 API：

| 条件 | 使用的 API |
| --- | --- |
| API 31+（S）且 `AlarmManager.canScheduleExactAlarms()` 为 `true` | `setExactAndAllowWhileIdle(RTC_WAKEUP, ...)` —— 精确触发，即使在 Doze 模式下也会触发。 |
| API 31+（S）且用户已撤销精确闹钟权限 | `setAndAllowWhileIdle(RTC_WAKEUP, ...)` —— 不精确的降级方案；系统可能将投递延迟数分钟，但即便处于 Doze 模式，通知最终仍会触发。 |
| API 23–30（M–R） | `setExactAndAllowWhileIdle(RTC_WAKEUP, ...)` —— 精确触发；API 31 之前不存在此运行时权限门槛。 |
| API 19–22（KitKat–L） | `setExact(RTC_WAKEUP, ...)` |
| 低于 API 19 | `set(RTC_WAKEUP, ...)`（在非常老旧的 Android 上本身就不精确） |

重复通知（设置了 `intervalSeconds`）始终使用 `AlarmManager.setRepeating()`，
根据 Android 系统设计，该 API 在 API 19+ 上本身就是不精确的（系统会为省电而
合并批处理重复闹钟）——在任何 API 级别上都不要依赖重复通知的精确计时。

**重要提示：** 即使精确闹钟权限缺失或之后被撤销，应用也**不会**崩溃或报错——
它会静默降级为上表中的不精确 API。如果你的游戏需要精确到秒的闹钟，请先调用
`hasScheduleExactAlarmPermission()`，若为 `false`，则可以提示用户，或调用
`requestScheduleExactAlarmPermission()` 将其引导至“闹钟和提醒”系统设置页面
（这是一次完整的设置页面跳转，而非应用内弹窗——目前没有可以通过弹窗申请此权限
的系统 API）。

## 通知渠道（API 26+）

在某个 `channelId` 上首次调用 `schedule()` 之前，请先调用一次
`createNotificationChannel()`。在低于 API 26（Oreo 之前，无渠道概念）的系统上，
该调用是空操作，始终成功返回。渠道的重要性级别、声音、角标行为一旦创建后即由
系统按渠道单独控制——使用相同 id 重新创建渠道并不会更改其已有设置（这是标准
Android 行为，并非本插件的限制）；如需更改渠道的重要性级别，请使用新的
`channelId`。

## 构建配置

- 无需手动修改 Gradle 文件。构建钩子（`source/BuildTaskAndroid.ts`）会在勾选
  **启用 Notification Scheduler** 构建面板选项时，幂等地向
  `native/engine/android/settings.gradle` 插入 `include ':libnotification'`
  （以及对应的 `project(':libnotification').projectDir = ...`），并向主 App
  模块的 `build.gradle` 插入一行
  `implementation project(':libnotification')` 依赖。
- 原生库模块本身（`template/android/libnotification/build.gradle`）仅依赖
  `androidx.core:core:1.13.1`（构建时从 Google Maven 仓库解析下载——没有作为
  二进制文件被提交/内置在仓库中）。
- `minSdkVersion` / `compileSdkVersion` / `targetSdkVersion` 均沿用你项目现有
  的 Android 构建设置——本插件不会强制提高最低 SDK 版本；所有用到的 API 都通过
  `Build.VERSION.SDK_INT` 判断做了保护，并在旧版本上有安全的回退方案。

## 已知限制（Android）

- `setBadgeCount()` 是空操作 —— 若不借助第三方库（如 ShortcutBadger），
  Android 没有跨 OEM 厂商统一的启动器角标 API，本插件有意不内置此类第三方库。
- 当用户撤销 `SCHEDULE_EXACT_ALARM` 权限时，精确闹钟的投递时机会静默降级
  （见上表），而不会失败——如果你的功能确实需要秒级精确投递，请检查
  `hasScheduleExactAlarmPermission()`。
- 出于系统设计原因，重复闹钟（`setRepeating`）在所有受支持的 API 级别上都是
  不精确的；请预期会有数分钟级别的偏差，而非秒级。
