# Android — NotificationScheduler (English)

- [Back to overview](../index.md) | [中文](../../zh/android/index.md)

## Permissions declared by this extension

`template/android/libnotification/AndroidManifest.xml` declares:

| Permission | Why |
| --- | --- |
| `POST_NOTIFICATIONS` | Required at runtime on API 33+ (Android 13) to show any notification. Below API 33 this permission does not exist and notifications are shown unconditionally. |
| `SCHEDULE_EXACT_ALARM` | Lets the app schedule exact alarms on API 31+ (Android 12) **without** the user having to grant anything in Settings — this manifest permission alone is enough for `AlarmManager.canScheduleExactAlarms()` to return `true` on a fresh install, unless the user later revokes it. (`USE_EXACT_ALARM`, the alternative Play-Store-restricted permission for alarm-clock-class apps, is **not** used.) |
| `RECEIVE_BOOT_COMPLETED` | Lets `BootReceiver` re-register alarms with `AlarmManager` after a device reboot (the OS clears all `AlarmManager` entries on reboot). |
| `WAKE_LOCK` | Held briefly while a notification alarm fires, so the device does not go back to sleep before the notification is posted. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Used only if you call `requestBatteryOptimizationsPermission()` to send the user to the "ignore battery optimizations" system screen; the extension never requests this automatically. |

You should not need to add or edit any of these — they ship in the extension's
manifest and are merged into your app automatically by the build hook.

## Runtime permission flow (POST_NOTIFICATIONS, API 33+)

```
hasPostNotificationsPermission()             // instantly resolves { granted: boolean }
        │
        ▼
requestPostNotificationsPermission()         // no-op / resolves immediately below API 33
        │
        ▼
Launches a transparent trampoline Activity
(PermissionRequestActivity) which calls
ActivityCompat.requestPermissions(...)
        │
        ▼
System permission dialog shown to the user
        │
   ┌────┴────┐
 Granted   Denied
   │          │
   ▼          ▼
{granted:true} {granted:false}
```

- `requestPostNotificationsPermission()` uses an **unlimited** bridge timeout
  internally — it genuinely waits for the user's tap, however long that
  takes.
- If the user denies the dialog **twice** (or checks "Don't ask again"), the
  OS stops showing the dialog at all; subsequent
  `requestPostNotificationsPermission()` calls resolve immediately with
  `{granted:false}` without any UI. Detect this by comparing before/after
  results, and fall back to `openAppInfoSettings()` so the user can grant it
  manually from system settings.
- Below API 33, both calls always resolve `{granted:true}` — there is nothing
  to request.

## Exact-alarm behavior and fallback (Android 12+)

`scheduleOnce()` in `NotificationServiceHub.java` picks the alarm API based on
both the running OS version and, on API 31+, whether the app currently holds
exact-alarm scheduling rights:

| Condition | API used |
| --- | --- |
| API 31+ (S) **and** `AlarmManager.canScheduleExactAlarms()` is `true` | `setExactAndAllowWhileIdle(RTC_WAKEUP, ...)` — exact, fires even in Doze. |
| API 31+ (S) **and** exact-alarm permission was revoked by the user | `setAndAllowWhileIdle(RTC_WAKEUP, ...)` — inexact fallback; the OS may batch/delay delivery by several minutes, but the notification still fires eventually, even in Doze. |
| API 23–30 (M–R) | `setExactAndAllowWhileIdle(RTC_WAKEUP, ...)` — exact; no runtime permission gate existed before API 31. |
| API 19–22 (KitKat–L) | `setExact(RTC_WAKEUP, ...)` |
| Below API 19 | `set(RTC_WAKEUP, ...)` (inherently inexact on very old Android) |

Repeating notifications (`intervalSeconds` set) always use
`AlarmManager.setRepeating()`, which is inexact on API 19+ by OS design
(Android batches repeating alarms to save battery) — do not rely on precise
timing for repeating notifications on any API level.

**Important:** the app never crashes or errors out if exact-alarm permission
is missing or later revoked — it silently degrades to the inexact API above.
If your game needs alarms that are exact to the second, call
`hasScheduleExactAlarmPermission()` first and, if `false`, either warn the
user or call `requestScheduleExactAlarmPermission()` to send them to the
"Alarms & reminders" system settings screen (this is a full-screen Settings
navigation, not an in-app dialog — there is no OS API to request this
permission via a popup).

## Notification channels (API 26+)

Call `createNotificationChannel()` once per distinct `channelId` before your
first `schedule()` call on that channel. On API < 26 (pre-Oreo, no channel
concept) this is a no-op that always resolves successfully. Channel
importance, sound, and badge behavior are all controlled per-channel by the
system once created — recreating a channel with the same id does not change
its existing settings (this is standard Android behavior, not a limitation
of this extension); if you need to change a channel's importance, use a new
`channelId`.

## Build configuration

- No manual Gradle edits are required. The build hook (`source/BuildTaskAndroid.ts`)
  idempotently inserts `include ':libnotification'` (and the corresponding
  `project(':libnotification').projectDir = ...`) into
  `native/engine/android/settings.gradle`, and a
  `implementation project(':libnotification')` dependency line into the main
  app module's `build.gradle`, only when the **Enable Notification
  Scheduler** build panel option is checked.
- The native library module itself
  (`template/android/libnotification/build.gradle`) depends only on
  `androidx.core:core:1.13.1` (resolved from Google's Maven repo at build
  time — nothing is vendored/committed as a binary).
- `minSdkVersion` / `compileSdkVersion` / `targetSdkVersion` are taken from
  your project's existing Android build settings — this extension does not
  force a minimum SDK bump; every API used is guarded by a
  `Build.VERSION.SDK_INT` check with a safe fallback on older versions.

## Known Limitations (Android)

- `setBadgeCount()` is a no-op — Android has no standard cross-OEM launcher
  badge API without a third-party library (e.g. ShortcutBadger), which this
  extension intentionally does not vendor.
- Exact-alarm delivery timing degrades silently (see table above) rather than
  failing when the user revokes `SCHEDULE_EXACT_ALARM` rights — check
  `hasScheduleExactAlarmPermission()` if your feature genuinely requires
  second-accurate delivery.
- Repeating alarms (`setRepeating`) are inexact on all supported API levels
  by OS design; expect drift of minutes, not seconds.
