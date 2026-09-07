# Cocos NotificationScheduler

[![CI](https://github.com/iamhainv/cocos-notification-scheduler/actions/workflows/ci.yml/badge.svg)](https://github.com/iamhainv/cocos-notification-scheduler/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[English](#english) | [中文](#中文)

---

<a id="english"></a>
## English

A Cocos Creator 3.8.x extension for scheduling **local** notifications —
one-shot and repeating — with cancellation, tap/dismiss events, and runtime
permission handling, on Android (`AlarmManager` + `NotificationCompat`) and
iOS (`UNUserNotificationCenter`). No push (FCM/APNs), no server, no network
component.

### Features

- `notificationScheduler.schedule(data)` — schedule a one-shot or repeating
  local notification.
- `notificationScheduler.cancel(id)` / `cancelAll()` — cancel one or all
  pending notifications.
- `notificationScheduler.createNotificationChannel(options)` — Android 8+
  notification channels (no-op on iOS).
- Runtime permission helpers: `hasPostNotificationsPermission()` /
  `requestPostNotificationsPermission()` (Android 13+ `POST_NOTIFICATIONS`,
  iOS `UNUserNotificationCenter` authorization), plus Android-only
  `hasScheduleExactAlarmPermission()` / `requestScheduleExactAlarmPermission()`
  and `hasBatteryOptimizationsPermission()` / `requestBatteryOptimizationsPermission()`.
- `onNotificationOpened(handler)` / `onNotificationDismissed(handler)` events
  (dismiss is Android-only — see docs).
- `setBadgeCount(count)` and `getNotificationId(defaultValue?)` for badge and
  launch-context handling.
- Automatic exact-alarm fallback on Android: degrades from
  `setExactAndAllowWhileIdle` to `setAndAllowWhileIdle` if the user revokes
  exact-alarm rights, instead of failing.
- Promise-based API throughout; every call rejects with
  `{ code: 'E_NOT_SUPPORTED' }` on web/editor preview instead of throwing.

### Demo

A runnable demo lives at the project root:

- Scene: [`assets/scene/main.scene`](./assets/scene/main.scene)
- Script: [`assets/script/test/NotificationSchedulerTest.ts`](./assets/script/test/NotificationSchedulerTest.ts)

Open this repo as a Cocos Creator 3.8.x project, open `main.scene`, and run
it on an Android or iOS device/simulator build. It builds its whole UI in
code (five buttons: Schedule in 5s / Schedule Repeating / Cancel One /
Cancel All / List Pending) so the example is readable in one file. On web
preview it shows a status message instead of attempting to schedule, since
native notifications aren't available there.

> **Known first-open caveat**: `main.scene` was hand-authored (not saved by
> the Creator GUI), and the `NotificationSchedulerTest` component is
> referenced by the script asset's full UUID rather than Creator's internal
> compressed script id. If Creator shows a "script asset lost" /
> missing-script warning on the `Content` node the first time you open the
> project, just re-drag `assets/script/test/NotificationSchedulerTest.ts`
> onto that node in the Inspector and re-save the scene — a one-time fix;
> only the `Content` node is affected, nothing else is at risk.

### Compatibility

| Cocos Creator | Android | iOS | Web |
| --- | --- | --- | --- |
| >= 3.8.0 | Yes (API 21+, permission/alarm APIs guarded per SDK level) | Yes (13.0+) | Preview only (native features degrade gracefully) |

### Installation

1. Copy `extensions/NotificationScheduler` into your project's `extensions/` folder.
2. Restart Cocos Creator, or use **Extension → Extension Manager** to enable it.
3. Import from the extension's asset-db mount:
   ```ts
   import { notificationScheduler, NotificationImportance } from 'db://notification-scheduler/index';

   await notificationScheduler.initialize();
   await notificationScheduler.createNotificationChannel({
       channelId: 'daily',
       channelName: 'Daily Reminders',
       importance: NotificationImportance.DEFAULT,
   });
   await notificationScheduler.requestPostNotificationsPermission();
   await notificationScheduler.schedule({
       notificationId: 1,
       channelId: 'daily',
       title: 'Come back!',
       content: 'Your puzzle awaits.',
       delaySeconds: 86400,
   });
   ```
4. See the full documentation: [doc/en](./extensions/NotificationScheduler/doc/en/index.md)
   — it covers Android runtime `POST_NOTIFICATIONS` (API 33+), the Android
   12+ exact-alarm restrictions and fallback, and the iOS authorization
   flow in detail.

### Documentation

- English: [extensions/NotificationScheduler/doc/en/index.md](./extensions/NotificationScheduler/doc/en/index.md)
  ([Android](./extensions/NotificationScheduler/doc/en/android/index.md), [iOS](./extensions/NotificationScheduler/doc/en/ios/index.md))
- 中文: [extensions/NotificationScheduler/doc/zh/index.md](./extensions/NotificationScheduler/doc/zh/index.md)
  ([Android](./extensions/NotificationScheduler/doc/zh/android/index.md)、[iOS](./extensions/NotificationScheduler/doc/zh/ios/index.md))

### License

MIT — see [LICENSE](./LICENSE).

### Trademarks

This project is an independent, community-maintained wrapper and is **not
affiliated with, endorsed by, or sponsored by** Cocos, Google, or Apple.
"Cocos Creator", "AdMob", "Firebase", "Google Play" and "App Store" are
trademarks of their respective owners.

---

<a id="中文"></a>
## 中文

一个 Cocos Creator 3.8.x 扩展，用于调度**本地**通知 —— 支持一次性和重复通知、
取消调度、点击/滑动消除事件，以及运行时权限处理，同时支持 Android
（`AlarmManager` + `NotificationCompat`）和 iOS（`UNUserNotificationCenter`）。
不涉及推送（FCM/APNs），没有服务器或网络组件。

### 功能特性

- `notificationScheduler.schedule(data)` —— 调度一次性或重复的本地通知。
- `notificationScheduler.cancel(id)` / `cancelAll()` —— 取消一条或全部待触发
  通知。
- `notificationScheduler.createNotificationChannel(options)` —— Android 8+
  通知渠道（iOS 上为空操作）。
- 运行时权限辅助方法：`hasPostNotificationsPermission()` /
  `requestPostNotificationsPermission()`（Android 13+ 的
  `POST_NOTIFICATIONS`，iOS 的 `UNUserNotificationCenter` 授权），以及仅
  Android 的 `hasScheduleExactAlarmPermission()` /
  `requestScheduleExactAlarmPermission()` 和
  `hasBatteryOptimizationsPermission()` / `requestBatteryOptimizationsPermission()`。
- `onNotificationOpened(handler)` / `onNotificationDismissed(handler)` 事件
  （消除事件仅限 Android —— 详见文档）。
- `setBadgeCount(count)` 与 `getNotificationId(defaultValue?)`，用于角标和
  启动上下文处理。
- Android 上自动降级精确闹钟：当用户撤销精确闹钟权限时，从
  `setExactAndAllowWhileIdle` 自动降级为 `setAndAllowWhileIdle`，而不会失败。
- 全程基于 Promise 的 API；在 Web/编辑器预览下所有调用都会以
  `{ code: 'E_NOT_SUPPORTED' }` 拒绝，而不是抛出异常。

### 演示

项目根目录内置了一个可直接运行的演示：

- 场景：[`assets/scene/main.scene`](./assets/scene/main.scene)
- 脚本：[`assets/script/test/NotificationSchedulerTest.ts`](./assets/script/test/NotificationSchedulerTest.ts)

用 Cocos Creator 3.8.x 打开本仓库作为项目，打开 `main.scene`，然后构建到
Android 或 iOS 设备/模拟器运行。界面完全由代码构建（五个按钮：Schedule in 5s /
Schedule Repeating / Cancel One / Cancel All / List Pending），方便在单个文件
中读懂整个示例。Web 预览下会显示状态提示而不会尝试真正调度，因为原生通知在
Web 上不可用。

> **首次打开的已知注意事项**：`main.scene` 是手工编写的（并非通过 Creator
> 编辑器保存），其中 `NotificationSchedulerTest` 组件是通过脚本资源的完整
> UUID 引用的，而不是 Creator 内部的压缩脚本 ID。如果首次打开项目时 Creator
> 在 `Content` 节点上提示"脚本丢失"之类的警告，只需将
> `assets/script/test/NotificationSchedulerTest.ts` 重新拖拽到该节点的
> Inspector 面板上并重新保存场景即可 —— 这是一次性修复，只影响 `Content`
> 节点，不会影响项目其他部分。

### 兼容性

| Cocos Creator | Android | iOS | Web |
| --- | --- | --- | --- |
| >= 3.8.0 | 支持（API 21+，权限/闹钟 API 均按 SDK 版本做了保护） | 支持（13.0+） | 仅预览（原生功能会优雅降级） |

### 安装

1. 将 `extensions/NotificationScheduler` 复制到项目的 `extensions/` 目录下。
2. 重启 Cocos Creator，或通过 **扩展 → 扩展管理器** 启用该扩展。
3. 从扩展的 asset-db 挂载路径导入：
   ```ts
   import { notificationScheduler, NotificationImportance } from 'db://notification-scheduler/index';

   await notificationScheduler.initialize();
   await notificationScheduler.createNotificationChannel({
       channelId: 'daily',
       channelName: 'Daily Reminders',
       importance: NotificationImportance.DEFAULT,
   });
   await notificationScheduler.requestPostNotificationsPermission();
   await notificationScheduler.schedule({
       notificationId: 1,
       channelId: 'daily',
       title: 'Come back!',
       content: 'Your puzzle awaits.',
       delaySeconds: 86400,
   });
   ```
4. 查看完整文档：[doc/zh](./extensions/NotificationScheduler/doc/zh/index.md)
   —— 其中详细说明了 Android 运行时 `POST_NOTIFICATIONS`（API 33+）、
   Android 12+ 精确闹钟限制与降级方案，以及 iOS 授权流程。

### 文档

- English: [extensions/NotificationScheduler/doc/en/index.md](./extensions/NotificationScheduler/doc/en/index.md)
  ([Android](./extensions/NotificationScheduler/doc/en/android/index.md)、[iOS](./extensions/NotificationScheduler/doc/en/ios/index.md))
- 中文: [extensions/NotificationScheduler/doc/zh/index.md](./extensions/NotificationScheduler/doc/zh/index.md)
  ([Android](./extensions/NotificationScheduler/doc/zh/android/index.md)、[iOS](./extensions/NotificationScheduler/doc/zh/ios/index.md))

### 许可证

MIT — 详见 [LICENSE](./LICENSE)。

### 商标声明

本项目为独立的社区维护封装库，**与 Cocos、Google、Apple 均无关联，未获得其
认可或赞助**。"Cocos Creator"、"AdMob"、"Firebase"、"Google Play" 与
"App Store" 均为其各自所有者的商标。
