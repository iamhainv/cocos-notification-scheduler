# Cocos NotificationScheduler (English)

Local notification scheduling for Cocos Creator games — schedule one-shot and
repeating notifications, cancel them, and react to taps/dismissals, on both
Android (`AlarmManager` + `NotificationCompat`) and iOS
(`UNUserNotificationCenter`).

This extension only schedules **local** notifications generated on-device.
It does not implement push notifications (FCM/APNs) — there is no server or
network component.

- Android and iOS platform requirements: [doc/en/android/index.md](./android/index.md), [doc/en/ios/index.md](./ios/index.md)
- 中文文档: [doc/zh/index.md](../zh/index.md)

## Compatibility

| Cocos Creator | Android minSdk | Android target/compile SDK | iOS deployment target |
| --- | --- | --- | --- |
| >= 3.8.0 (tested on 3.8.8) | Project default (Cocos 3.8.x default is 21) — permission/alarm APIs are all guarded by runtime `Build.VERSION.SDK_INT` checks, so no minSdk bump is required | Project default (`PROP_COMPILE_SDK_VERSION` / `PROP_TARGET_SDK_VERSION`) | iOS 13.0 (set by `notification.cmake`) |

Notifications behave differently across OS versions; see the platform docs for
the exact permission/alarm behavior at each API level.

## Installation

1. Copy `extensions/NotificationScheduler` into your project's `extensions/` folder.
2. Restart Cocos Creator, or use **Extension → Extension Manager** to enable it.
3. Import the runtime API from the extension's asset-db mount:

   ```ts
   import { notificationScheduler, NotificationImportance } from 'db://notification-scheduler/index';
   ```

4. Build your project (**Project → Build**). The Android/iOS build panels show
   the options described below, and the post-build hooks copy the native
   library / CMake integration into the built project automatically.

## Build Panel Options

Both the Android and iOS build panels expose the same two options (identical
behavior on each platform):

| Option | Default | Description |
| --- | --- | --- |
| **Enable Notification Scheduler** (`enableNotification`) | `true` | Enables/disables the extension for this build. When `true`, the native library (`libnotification` on Android, the `notification` CMake target on iOS) is copied into the built project and wired into Gradle/CMake. When `false`, any previously-injected Gradle snippets and CMake includes are cleanly removed instead — safe to toggle back and forth between builds. |
| **Force overwrite the Notification Scheduler library** (`overwriteLibrary`) | `true` | When `true`, the native library directory in the built project is overwritten from the extension's `template/` on every build. Turn this **off** only if you intentionally hand-edit files inside the built project's `proj/libnotification` (Android) or `proj/notification` (iOS) directory and want to preserve those edits across rebuilds — not recommended for normal use. |

Both options apply independently to the Android and iOS build panels; there
is no cross-platform toggle.

## API Reference

Full JSDoc lives in [`assets/NotificationClient.ts`](../../assets/NotificationClient.ts)
and [`assets/Types.ts`](../../assets/Types.ts) — every method and field below
has a longer doc comment there. Import everything from the barrel:

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

### Lifecycle

| Method | Description |
| --- | --- |
| `initialize(): Promise<void>` | Call once at startup, before scheduling or checking permissions. On iOS, also flushes a launch notification (cold start via tap) as a synchronous `notificationOpened` event. |
| `createNotificationChannel(options: NotificationChannelOptions): Promise<void>` | Android 8+: creates (or no-ops if it already exists) a notification channel. **Required before `schedule()` on Android.** No-op on iOS. |

### Scheduling

| Method | Description |
| --- | --- |
| `schedule(data: NotificationData): Promise<void>` | Schedules a notification to fire after `delaySeconds`. Pass `intervalSeconds` for a repeating notification (iOS clamps values below 60 to 60). Re-using a `notificationId` replaces the pending notification. |
| `cancel(notificationId: number): Promise<void>` | Cancels one pending notification by id. No-op if it already fired. |
| `cancelAll(): Promise<void>` | Cancels every pending/posted notification from this app. |

### Badge / launch context

| Method | Description |
| --- | --- |
| `setBadgeCount(count: number): Promise<void>` | Sets the app icon badge. Android: no native global badge API exists without a third-party OEM library — this resolves successfully but is a no-op. iOS: sets the real badge count via `UNUserNotificationCenter` (iOS 16+) or `UIApplication.applicationIconBadgeNumber` fallback. |
| `getNotificationId(defaultValue?: number): Promise<number>` | Returns the id of the notification that most recently launched/resumed the app, or `defaultValue` (`-1` if omitted). |

### Permissions

| Method | Platform | Description |
| --- | --- | --- |
| `hasPostNotificationsPermission(): Promise<{granted}>` | Both | Android 13+: checks `POST_NOTIFICATIONS`. Below API 33: always `true`. iOS: checks `UNAuthorizationStatus` (authorized or provisional counts as granted). |
| `requestPostNotificationsPermission(): Promise<{granted}>` | Both | Android 13+: shows the system permission dialog. iOS: shows the `UNUserNotificationCenter` authorization prompt. See [doc/en/android/index.md](./android/index.md) and [doc/en/ios/index.md](./ios/index.md) for the exact flow. |
| `hasBatteryOptimizationsPermission(): Promise<{granted}>` | Android only | Rejects with `E_NOT_SUPPORTED` on iOS. Checks whether the app is exempt from Doze/App Standby battery optimizations. |
| `requestBatteryOptimizationsPermission(): Promise<{granted}>` | Android only | Opens the "ignore battery optimizations" system screen. |
| `hasScheduleExactAlarmPermission(): Promise<{granted}>` | Android only | Android 12+: checks `AlarmManager.canScheduleExactAlarms()`. Below API 31: always `true`. |
| `requestScheduleExactAlarmPermission(): Promise<{granted}>` | Android only | Opens the "Alarms & reminders" system settings screen. |
| `openAppInfoSettings(): Promise<void>` | Android only | Opens this app's system App Info page (useful after a permission was permanently denied). |

Permission-request calls with an OS dialog or settings screen use an
unlimited internal timeout (the bridge's default 30s timeout is disabled for
these calls specifically), since the user may take an arbitrary amount of
time to respond.

### Events

| Method | Description |
| --- | --- |
| `onNotificationOpened(handler)` / `offNotificationOpened(handler)` | Fires when the user taps a notification (foreground tap, background resume, or cold launch — the latter also flushed once from `initialize()`). |
| `onNotificationDismissed(handler)` / `offNotificationDismissed(handler)` | **Android only.** Fires when the user swipes away a notification without tapping it. iOS delivers no such callback; this will never fire there. |

## Examples

See the runnable demo at [`assets/script/test/NotificationSchedulerTest.ts`](../../../../assets/script/test/NotificationSchedulerTest.ts)
(scene: [`assets/scene/main.scene`](../../../../assets/scene/main.scene)) for
a complete, minimal example: initialize → create channel → request
permission → schedule / cancel / cancel-all, plus event subscriptions and a
web-preview fallback.

Minimal usage:

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

## Troubleshooting

- **`E_NOT_SUPPORTED` on every call** — you are running on web preview or in
  the Creator editor's Simulator. This extension is native-only; build to
  Android or iOS to test it.
- **Notification never appears (Android)** — check, in order: (1)
  `POST_NOTIFICATIONS` was granted (`hasPostNotificationsPermission()`); (2)
  a channel with a matching `channelId` was created via
  `createNotificationChannel()` before `schedule()`; (3) the channel itself
  isn't disabled by the user in system notification settings; (4) if the
  device was rebooted between scheduling and the fire time, confirm
  `RECEIVE_BOOT_COMPLETED` wasn't stripped by a manifest merge conflict (see
  [doc/en/android/index.md](./android/index.md)).
- **Notification never appears (iOS)** — confirm the user granted
  authorization (`hasPostNotificationsPermission()`); iOS silently drops
  scheduled notifications for a denied/undetermined app without any error
  callback.
- **Gradle sync error: duplicate class / duplicate module `libnotification`** —
  another extension or your own native code already declares a Gradle module
  with the same name. Check `native/engine/android/settings.gradle` for a
  duplicate `include ':libnotification'` line (should only be inserted once,
  idempotently, by this extension's build hook).
- **CMake error: target `notification` already defined** — same idempotency
  issue on iOS; check `native/engine/ios/Pre-notification.cmake` /
  `Post-notification.cmake` were not manually duplicated.
- **Build panel shows a raw string like `i18n:notification-scheduler.enableNotification.title`
  instead of a label** — the `i18n/en.js` / `i18n/zh.js` files are missing or
  out of sync with `source/builder.ts`. This should not happen in the
  packaged release; if it does, verify both i18n files export every key used
  in `configs.android.options` / `configs.ios.options`.

## Known Limitations

- **No "list all pending notifications" query.** Neither the Android nor the
  iOS native layer exposes a call to enumerate everything currently
  scheduled/posted by the OS. Track scheduled ids yourself in TypeScript if
  your game needs this (see the demo's "List Pending" button, which reports
  session-local bookkeeping only, not an OS-level query).
- **`setBadgeCount()` is a no-op on Android** — there is no standard Android
  API for a launcher badge without a third-party OEM-specific library.
- **`onNotificationDismissed` never fires on iOS** — swipe-to-dismiss is not
  observable via `UNUserNotificationCenter` on iOS.
- **iOS repeat interval floor is 60 seconds** — `intervalSeconds` values below
  60 are silently clamped by iOS itself, not by this extension.
- **Android exact-alarm permission can be revoked by the user at any time**
  via system settings (Android 12+). When revoked, this extension
  automatically falls back to an inexact `setAndAllowWhileIdle` alarm rather
  than failing — see [doc/en/android/index.md](./android/index.md).
- Only one Creator version (3.8.8) and one Cocos Creator native pipeline have
  been exercised during development of this extension. Other 3.8.x point
  releases should work (declared `editor: ">=3.8.0"`) but have not been
  individually verified.
