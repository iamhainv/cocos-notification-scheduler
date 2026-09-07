# Cocos NotificationScheduler（中文）

为 Cocos Creator 游戏提供本地通知调度能力 —— 支持一次性和重复通知的调度、取消，
以及对点击/滑动消除事件的响应，同时支持 Android（`AlarmManager` +
`NotificationCompat`）和 iOS（`UNUserNotificationCenter`）。

本插件仅调度设备本地生成的**本地通知**，不实现推送通知（FCM/APNs）——没有服务器
或网络组件。

- 平台相关要求：[doc/zh/android/index.md](./android/index.md)、[doc/zh/ios/index.md](./ios/index.md)
- English docs: [doc/en/index.md](../en/index.md)

## 兼容性

| Cocos Creator | Android minSdk | Android target/compile SDK | iOS 部署目标 |
| --- | --- | --- | --- |
| >= 3.8.0（已在 3.8.8 上测试） | 使用项目默认值（Cocos 3.8.x 默认是 21）——所有权限/闹钟 API 均通过运行时 `Build.VERSION.SDK_INT` 判断做了兼容处理，无需提高 minSdk | 使用项目默认值（`PROP_COMPILE_SDK_VERSION` / `PROP_TARGET_SDK_VERSION`） | iOS 13.0（由 `notification.cmake` 设置） |

不同操作系统版本上的通知行为有所不同，具体权限/闹钟行为请参阅各平台文档。

## 安装

1. 将 `extensions/NotificationScheduler` 复制到项目的 `extensions/` 目录下。
2. 重启 Cocos Creator，或通过 **扩展 → 扩展管理器** 启用该插件。
3. 通过插件的 asset-db 挂载路径导入运行时 API：

   ```ts
   import { notificationScheduler, NotificationImportance } from 'db://notification-scheduler/index';
   ```

4. 构建项目（**项目 → 构建**）。Android/iOS 构建面板会显示下方描述的选项，构建后
   钩子会自动将原生库/CMake 集成拷贝进已构建的工程中。

## 构建面板选项

Android 和 iOS 构建面板都提供相同的两个选项（两个平台行为一致）：

| 选项 | 默认值 | 说明 |
| --- | --- | --- |
| **启用 Notification Scheduler**（`enableNotification`） | `true` | 启用/禁用该插件参与本次构建。为 `true` 时，原生库（Android 上为 `libnotification`，iOS 上为 `notification` CMake 目标）会被拷贝到已构建工程中并接入 Gradle/CMake。为 `false` 时，会将之前注入的 Gradle 片段和 CMake 引用彻底移除——可以放心在两个状态间来回切换。 |
| **强制覆盖 Notification Scheduler 库**（`overwriteLibrary`） | `true` | 为 `true` 时，每次构建都会用插件的 `template/` 内容覆盖已构建工程中的原生库目录。仅当你有意手动修改已构建工程内 `proj/libnotification`（Android）或 `proj/notification`（iOS）目录下的文件、并希望在重新构建后保留这些修改时，才关闭此选项——正常使用不建议关闭。 |

两个选项分别独立作用于 Android 和 iOS 构建面板，没有跨平台的统一开关。

## API 参考

完整的 JSDoc 位于 [`assets/NotificationClient.ts`](../../assets/NotificationClient.ts)
和 [`assets/Types.ts`](../../assets/Types.ts)——以下每个方法和字段在源码中都有更
详细的文档注释。从统一入口导入：

```ts
import {
    notificationScheduler,
    NotificationClient,
    NotificationImportance,
} from 'db://notification-scheduler/index';
import type {
    NotificationChannelOptions,
    NotificationData,
    NotificationError,
} from 'db://notification-scheduler/index';
```

### 生命周期

| 方法 | 说明 |
| --- | --- |
| `initialize(): Promise<void>` | 在调度或检查权限之前，启动时调用一次。在 iOS 上还会把冷启动（通过点击通知启动）的场景以一次同步的 `notificationOpened` 事件形式回放出来。 |
| `createNotificationChannel(options: NotificationChannelOptions): Promise<void>` | Android 8+：创建通知渠道（若已存在则空操作）。**Android 上在 `schedule()` 之前必须调用。** iOS 上为空操作。 |

### 调度

| 方法 | 说明 |
| --- | --- |
| `schedule(data: NotificationData): Promise<void>` | 调度一条在 `delaySeconds` 秒后触发的通知。传入 `intervalSeconds` 可实现重复通知（iOS 会将小于 60 的值钳制为 60）。使用相同的 `notificationId` 会替换掉之前待触发的通知。 |
| `cancel(notificationId: number): Promise<void>` | 按 id 取消一条待触发通知。若该通知已经触发过，则为空操作。 |
| `cancelAll(): Promise<void>` | 取消本应用所有待触发/已发出的通知。 |

### 角标 / 启动上下文

| 方法 | 说明 |
| --- | --- |
| `setBadgeCount(count: number): Promise<void>` | 设置应用图标角标。Android：由于没有第三方 OEM 库就不存在标准的全局角标 API，此调用会成功返回但实际为空操作。iOS：通过 `UNUserNotificationCenter`（iOS 16+）或 `UIApplication.applicationIconBadgeNumber` 回退方案真正设置角标数。 |
| `getNotificationId(defaultValue?: number): Promise<number>` | 返回最近一次启动/恢复应用所对应的通知 id，若无则返回 `defaultValue`（未传时为 `-1`）。 |

### 权限

| 方法 | 平台 | 说明 |
| --- | --- | --- |
| `hasPostNotificationsPermission(): Promise<{granted}>` | 双平台 | Android 13+：检查 `POST_NOTIFICATIONS`。低于 API 33：始终为 `true`。iOS：检查 `UNAuthorizationStatus`（已授权或临时授权都视为已授予）。 |
| `requestPostNotificationsPermission(): Promise<{granted}>` | 双平台 | Android 13+：弹出系统权限对话框。iOS：弹出 `UNUserNotificationCenter` 授权提示。具体流程见 [doc/zh/android/index.md](./android/index.md) 和 [doc/zh/ios/index.md](./ios/index.md)。 |
| `hasBatteryOptimizationsPermission(): Promise<{granted}>` | 仅 Android | 在 iOS 上会以 `E_NOT_SUPPORTED` 拒绝。检查应用是否已被排除在 Doze/待机耗电优化之外。 |
| `requestBatteryOptimizationsPermission(): Promise<{granted}>` | 仅 Android | 打开“忽略电池优化”系统页面。 |
| `hasScheduleExactAlarmPermission(): Promise<{granted}>` | 仅 Android | Android 12+：检查 `AlarmManager.canScheduleExactAlarms()`。低于 API 31：始终为 `true`。 |
| `requestScheduleExactAlarmPermission(): Promise<{granted}>` | 仅 Android | 打开“闹钟和提醒”系统设置页面。 |
| `openAppInfoSettings(): Promise<void>` | 仅 Android | 打开本应用的系统应用信息页面（适用于权限被永久拒绝后引导用户手动开启）。 |

带有系统对话框或设置页面的权限请求类调用在内部使用无限超时（桥接层默认的 30
秒超时对这些调用专门禁用），因为用户做出响应所需的时间是不确定的。

### 事件

| 方法 | 说明 |
| --- | --- |
| `onNotificationOpened(handler)` / `offNotificationOpened(handler)` | 当用户点击通知时触发（前台点击、后台恢复、或冷启动——冷启动场景也会在 `initialize()` 中回放一次）。 |
| `onNotificationDismissed(handler)` / `offNotificationDismissed(handler)` | **仅 Android。** 当用户滑动清除通知（未点击）时触发。iOS 无此类回调，该事件在 iOS 上永远不会触发。 |

## 示例

完整可运行的示例见
[`assets/script/test/NotificationSchedulerTest.ts`](../../../../assets/script/test/NotificationSchedulerTest.ts)
（场景：[`assets/scene/main.scene`](../../../../assets/scene/main.scene)）：初始化
→ 创建渠道 → 请求权限 → 调度/取消/取消全部，并包含事件订阅与 Web 预览下的兜底提示。

最简用法：

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

notificationScheduler.onNotificationOpened((data) => {
    console.log('opened via notification', data.notificationId, data.deeplink);
});
```

## 故障排查

- **每个调用都返回 `E_NOT_SUPPORTED`** —— 你正运行在 Web 预览或 Creator 编辑器
  的模拟器中。本插件仅支持原生平台；请构建到 Android 或 iOS 后再测试。
- **通知不出现（Android）** —— 依次检查：(1) `POST_NOTIFICATIONS` 是否已授予
  （`hasPostNotificationsPermission()`）；(2) 是否已在 `schedule()` 之前通过
  `createNotificationChannel()` 创建了对应 `channelId` 的渠道；(3) 该渠道是否
  被用户在系统通知设置中禁用；(4) 若调度后设备发生过重启，请确认
  `RECEIVE_BOOT_COMPLETED` 未在清单合并冲突中被移除（详见
  [doc/zh/android/index.md](./android/index.md)）。
- **通知不出现（iOS）** —— 确认用户已授权
  （`hasPostNotificationsPermission()`）；对于被拒绝/未决定授权状态的应用，iOS
  会静默丢弃已调度的通知，且不会返回任何错误回调。
- **Gradle 同步报错：重复的类/重复的模块 `libnotification`** —— 说明另一个插件
  或你自己的原生代码已声明了同名 Gradle 模块。检查
  `native/engine/android/settings.gradle` 中是否有重复的
  `include ':libnotification'` 行（该行应仅由本插件的构建钩子幂等地插入一次）。
- **CMake 报错：目标 `notification` 已定义** —— iOS 上的同类幂等性问题；检查
  `native/engine/ios/Pre-notification.cmake` / `Post-notification.cmake` 是否
  被手动重复添加。
- **构建面板显示原始字符串，如
  `i18n:notification-scheduler.enableNotification.title`，而不是正常标签** ——
  说明 `i18n/en.js` / `i18n/zh.js` 缺失或与 `source/builder.ts` 不同步。发布版
  本不应出现此问题；若出现，请确认两个 i18n 文件都导出了
  `configs.android.options` / `configs.ios.options` 中用到的所有 key。

## 已知限制

- **没有“列出所有待触发通知”的查询接口。** Android 和 iOS 原生层均未提供枚举
  当前所有已调度/已发出通知的接口。如果你的游戏需要此功能，请自行在 TypeScript
  层记录已调度的 id（参见示例中的“List Pending”按钮，它仅报告本会话内的记录，
  而非操作系统级别的查询结果）。
- **`setBadgeCount()` 在 Android 上是空操作** —— 若不借助第三方 OEM 专用库，
  Android 没有标准的启动器角标 API。
- **`onNotificationDismissed` 在 iOS 上永远不会触发** —— 在 iOS 的
  `UNUserNotificationCenter` 中无法观察到滑动清除操作。
- **iOS 重复通知的最小间隔是 60 秒** —— 小于 60 的 `intervalSeconds` 值会被 iOS
  系统本身静默钳制，与本插件无关。
- **Android 精确闹钟权限可能随时被用户在系统设置中撤销**（Android 12+）。撤销
  后，本插件会自动降级为不精确的 `setAndAllowWhileIdle` 闹钟，而不会报错——详见
  [doc/zh/android/index.md](./android/index.md)。
- 本插件的开发/验证仅在一个 Creator 版本（3.8.8）和一套 Cocos Creator 原生构建
  流程下进行。其他 3.8.x 小版本理论上应可正常工作（`editor` 字段声明为
  `">=3.8.0"`），但尚未逐一验证。
