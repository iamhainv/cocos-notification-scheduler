# Third-Party Notices

Cocos NotificationScheduler may bundle or depend on third-party software. Their licenses are
reproduced below (or referenced) as required by their respective terms.

This extension does not vendor or commit any third-party binaries. It links
one Gradle-resolved Android library (fetched from Google's Maven repository
at build time, not committed to this repository) and two stock Apple system
frameworks on iOS (no third-party code at all).

| Component | Version | License | Notes |
| --- | --- | --- | --- |
| AndroidX Core (`androidx.core:core`) | 1.13.1 | Apache License 2.0 | Declared as a Gradle dependency in `extensions/NotificationScheduler/template/android/libnotification/build.gradle`; resolved from Google's Maven repository at build time. Provides `NotificationCompat` / `NotificationManagerCompat`. Not vendored in this repository. License: https://www.apache.org/licenses/LICENSE-2.0 |
| Apple `UserNotifications.framework` | iOS SDK (system) | Apple SDK license (part of Xcode/iOS SDK) | Linked in `extensions/NotificationScheduler/template/ios/notification/notification.cmake`. Ships with Xcode; not distributed by this repository. |
| Apple `UIKit.framework` | iOS SDK (system) | Apple SDK license (part of Xcode/iOS SDK) | Linked alongside `UserNotifications.framework` for badge/settings-URL support. Ships with Xcode; not distributed by this repository. |

The Android native scheduling implementation
(`extensions/NotificationScheduler/template/android/libnotification/src/com/cocos/notification/NotificationServiceHub.java`)
was originally ported from a Godot Engine notification plugin; see the
"porting notes" comment at the top of that file for background. No Godot
code or assets are included in this repository — only the original author's
own MIT-licensed rewrite for the Cocos native bridge.
