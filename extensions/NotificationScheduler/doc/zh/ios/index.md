# iOS — NotificationScheduler（中文）

- [返回概览](../index.md) | [English](../../en/ios/index.md)

本插件仅使用 `UNUserNotificationCenter` 实现**本地**（设备端）通知，不集成
APNs/推送功能，因此无需推送相关的 entitlement、描述文件（provisioning
profile）能力，也无需 `aps-environment` 键。

## 授权流程

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
 已授予     已拒绝
   │          │
   ▼          ▼
{granted:true} {granted:false}
```

- 系统授权对话框**每次安装最多只会展示一次**——这是 iOS 系统本身的限制，并非
  本插件的行为。若用户拒绝，之后每次调用
  `requestPostNotificationsPermission()` 都会立即返回 `{granted:false}`，且
  **不会再次弹出对话框**。用户之后唯一能改变主意的途径是通过“设置”App；iOS
  15.4 起提供了一个设置深链（`UIApplicationOpenNotificationSettingsURLString`），
  本插件的原生层在适当场景下会内部使用它，但目前没有对应的 JS 层方法可以直接
  打开它（不同于 Android 上的 `openAppInfoSettings()`）——请在自己的 UI 文案中
  手动引导用户前往 设置 → 通知 → [你的应用]，或通过系统分享面板/自定义 UI 打开
  应用的通用设置页面。
- 与 Android 流程类似，`requestPostNotificationsPermission()` 在 iOS 上同样使用
  无限内部超时，因为响应取决于用户何时点击系统对话框。
- 请求的权限选项始终固定为 `alert | badge | sound`——目前没有办法从 TypeScript
  层请求更窄或更宽的选项组合。

## 调度行为

- 通知通过 `UNTimeIntervalNotificationTrigger` 投递。
- `delaySeconds` 直接映射为触发器的 `timeInterval`。
- `intervalSeconds`（重复通知）映射为触发器的 `repeats: YES`，使用相同的
  `timeInterval`——**iOS 对重复触发器强制设有 60 秒的最小间隔**；小于 60 的值
  会被系统本身静默钳制为 60，与本插件无关。一次性的 `delaySeconds` 值没有类似
  的下限。
- 使用相同的 `notificationId`（作为 `UNNotificationRequest` 的 identifier）
  重新调度会替换掉之前待触发的请求，与 Android 上的行为一致。
- `badgeCount`（若包含在 `NotificationData` 中）会在投递时通过 `content.badge`
  应用；而 `setBadgeCount()` 会直接立即设置角标（iOS 16+ 通过
  `UNUserNotificationCenter`，更早版本回退到
  `UIApplication.applicationIconBadgeNumber`）——与 Android 不同，此调用在 iOS
  上是真正生效的。
- 前台展示：应用处于前台时到达的通知仍会调用完成回调，因此
  `notificationOpened`/横幅展示逻辑都继续可用；如需更改前台展示选项（当前配置
  为在前台时抑制横幅/声音，以匹配大多数游戏不希望原生横幅遮挡自身 UI 的常见
  需求），请查看 `NotificationServiceHub.mm`。

## 构建配置

- 完全通过 CMake 集成——无需手动编辑 Xcode 工程。当勾选
  **启用 Notification Scheduler** 构建面板选项时，构建钩子会向生成的
  Xcode/CMake 工程插入 `include(Pre-notification.cmake)` /
  `include(Post-notification.cmake)`；取消勾选时则会将其彻底移除。
- `notification.cmake` 链接了 `UserNotifications` 和 `UIKit` 两个系统框架——均
  为标准 iOS SDK 框架，无需下载或额外内置。
- 部署目标：**iOS 13.0**（在 `notification.cmake` 中显式设置）。若你项目自身
  的部署目标低于该值，需要将其提高到至少 13.0，否则本插件的构建会在
  CMake/Xcode 层面失败。
- 纯 Objective-C 实现——无需 Swift 工具链或桥接头文件，也没有内置任何
  `.xcframework` 二进制文件（所有源码直接从
  `template/ios/notification/*.mm` 编译而来）。
- 本地通知本身不需要任何 `Info.plist` 配置项。（推送通知会需要
  `aps-environment` entitlement，但本插件不实现推送。）

## 已知限制（iOS）

- 授权对话框只能由系统展示一次；iOS 上没有类似 Android
  `openAppInfoSettings()` 的 JS 层“打开通知设置”快捷方法——如果你需要“重新申请
  权限”的 UI/UX 流程，请自行处理并文档化。
- `onNotificationDismissed` 永远不会触发——`UNUserNotificationCenter` 没有对应
  滑动清除的委托回调，只有投递和“用户已响应（点击）”回调。在 iOS 上只有
  `onNotificationOpened` 是有意义的。
- 重复通知的触发频率不能高于每 60 秒一次
  （`intervalSeconds < 60` 会被 iOS 系统本身钳制为 60）。
- 本插件未在 TypeScript 层暴露“列出所有待触发通知”的查询接口（参见顶层文档中的
  [已知限制](../index.md#已知限制)）；原生层其实存在
  `UNUserNotificationCenter.getPendingNotificationRequestsWithCompletionHandler:`
  接口，但目前尚未桥接到 TypeScript。
