# Changelog

All notable changes to this extension will be documented in this file.

## 1.0.0 — Initial release

- Initial open-source release of Cocos NotificationScheduler.
- Schedule one-shot and repeating local notifications
  (`notificationScheduler.schedule()`), with cancellation of one or all
  pending notifications (`cancel()` / `cancelAll()`).
- Android 8+ notification channel creation (`createNotificationChannel()`),
  no-op on iOS.
- Runtime permission API: `POST_NOTIFICATIONS` (Android 13+) and
  `UNUserNotificationCenter` authorization (iOS) via
  `hasPostNotificationsPermission()` / `requestPostNotificationsPermission()`.
- Android-only permission helpers for exact alarms
  (`hasScheduleExactAlarmPermission()` / `requestScheduleExactAlarmPermission()`)
  and battery-optimization exemption
  (`hasBatteryOptimizationsPermission()` / `requestBatteryOptimizationsPermission()`),
  plus `openAppInfoSettings()`.
- Automatic Android exact-alarm fallback: degrades from
  `setExactAndAllowWhileIdle` to `setAndAllowWhileIdle` when exact-alarm
  rights are unavailable or revoked, instead of failing.
- Notification tap/dismiss events (`onNotificationOpened()` /
  `onNotificationDismissed()`, dismiss is Android-only) and launch-context
  lookup (`getNotificationId()`).
- Badge count support (`setBadgeCount()` — functional on iOS, a documented
  no-op on Android).
- Boot-persistence on Android via `BootReceiver` (re-registers alarms after
  device reboot, since `AlarmManager` entries are cleared by the OS on
  reboot).
- Graceful degradation on web/editor preview — every call rejects with
  `{ code: 'E_NOT_SUPPORTED' }` instead of throwing.
- Demo scene and script (`assets/scene/main.scene`,
  `assets/script/test/NotificationSchedulerTest.ts`) covering all five
  demo actions: schedule in 5s, schedule repeating, cancel one, cancel all,
  list pending (session-local tracking — see docs' Known Limitations).
