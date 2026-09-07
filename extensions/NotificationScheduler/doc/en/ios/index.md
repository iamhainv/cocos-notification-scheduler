# iOS — NotificationScheduler (English)

- [Back to overview](../index.md) | [中文](../../zh/ios/index.md)

This extension only uses `UNUserNotificationCenter` for **local** (on-device)
notifications. It does not integrate APNs/push, so no push entitlement,
provisioning-profile capability, or `aps-environment` key is needed.

## Authorization flow

```
hasPostNotificationsPermission()
        │
        ▼
UNUserNotificationCenter.getNotificationSettingsWithCompletionHandler:
        │
        ▼
{ granted: authorizationStatus == Authorized
          || authorizationStatus == Provisional }

requestPostNotificationsPermission()
        │
        ▼
UNUserNotificationCenter.requestAuthorizationWithOptions:
    (UNAuthorizationOptionAlert | UNAuthorizationOptionBadge | UNAuthorizationOptionSound)
        │
   ┌────┴────┐
 Granted   Denied
   │          │
   ▼          ▼
{granted:true} {granted:false}
```

- The system authorization dialog is shown **at most once per app install**
  — this is enforced by iOS itself, not by this extension. If the user
  denies it, every subsequent `requestPostNotificationsPermission()` call
  resolves immediately with `{granted:false}` and **no dialog is shown
  again**. The only way for the user to change their mind afterwards is
  through the Settings app; there is a Settings deep-link available since
  iOS 15.4 (`UIApplicationOpenNotificationSettingsURLString`) that this
  extension's native layer uses internally when appropriate, but there is no
  JS-level method to force-open it directly (unlike Android's
  `openAppInfoSettings()`) — guide users to Settings → Notifications →
  [Your App] manually, or open the app's general Settings page via the OS
  share sheet / your own UI copy.
- Like the Android flow, `requestPostNotificationsPermission()` uses an
  unlimited internal timeout on iOS as well, since the response depends on
  the user tapping the system dialog.
- Requested options are always `alert | badge | sound` — there is currently
  no way to request a narrower or wider option set from the TypeScript API.

## Scheduling behavior

- Notifications are delivered via `UNTimeIntervalNotificationTrigger`.
- `delaySeconds` maps directly to the trigger's `timeInterval`.
- `intervalSeconds` (repeating) maps to the trigger's `repeats: YES` with the
  same `timeInterval` — **iOS enforces a hard minimum interval of 60
  seconds** for repeating triggers; values below 60 are silently clamped by
  the OS, not by this extension. There is no equivalent floor for the
  one-shot `delaySeconds` value.
- Re-scheduling with the same `notificationId` (used as the
  `UNNotificationRequest` identifier) replaces the previous pending request,
  same as on Android.
- `badgeCount` (if included in `NotificationData`) is applied via
  `content.badge` at delivery time, and `setBadgeCount()` sets it directly
  and immediately (via `UNUserNotificationCenter` on iOS 16+, or
  `UIApplication.applicationIconBadgeNumber` as the pre-iOS-16 fallback) —
  unlike Android, this call actually does something on iOS.
- Foreground presentation: notifications that arrive while the app is in the
  foreground still call the completion handler, so `notificationOpened` /
  banner presentation both continue to work; check
  `NotificationServiceHub.mm` if you need to change the foreground
  presentation options (currently configured to suppress the banner/sound
  while foregrounded, matching common game UX where you don't want a native
  banner overlapping your own UI).

## Build configuration

- Integrated purely via CMake — no Xcode project hand-editing needed. The
  build hook inserts `include(Pre-notification.cmake)` /
  `include(Post-notification.cmake)` into the generated Xcode/CMake project
  when the **Enable Notification Scheduler** build panel option is checked,
  and cleanly removes them when unchecked.
- `notification.cmake` links the `UserNotifications` and `UIKit` system
  frameworks — both are stock iOS SDK frameworks, nothing to download or
  vendor.
- Deployment target: **iOS 13.0** (set explicitly in `notification.cmake`).
  If your project's own deployment target is lower, raise it to at least
  13.0 or this extension's build will fail at the CMake/Xcode level.
- Pure Objective-C — no Swift toolchain or bridging header is required, and
  no `.xcframework` binary is vendored (all source is compiled directly from
  `template/ios/notification/*.mm`).
- No `Info.plist` entries are required for local notifications specifically.
  (Push notifications would require the `aps-environment` entitlement, but
  this extension does not implement push.)

## Known Limitations (iOS)

- The authorization dialog can only be shown once by the OS; there is no
  JS-level "open notification settings" shortcut on iOS (present on Android
  as `openAppInfoSettings()`) — document this for your own UI/UX if you need
  a "grant permission" retry flow.
- `onNotificationDismissed` never fires — `UNUserNotificationCenter` has no
  delegate callback for swipe-to-dismiss, only for delivery and for
  user-tap ("did receive response"). Only `onNotificationOpened` is
  meaningful on iOS.
- Repeating notifications cannot fire more often than once every 60 seconds
  (`intervalSeconds < 60` is clamped to 60 by iOS itself).
- There is no native "list all pending notifications" query surfaced by this
  extension (see the top-level [Known Limitations](../index.md#known-limitations));
  `UNUserNotificationCenter.getPendingNotificationRequestsWithCompletionHandler:`
  exists at the native layer but is not currently bridged to TypeScript.
