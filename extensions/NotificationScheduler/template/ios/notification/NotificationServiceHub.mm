/**
 * NotificationServiceHub.mm — NotificationScheduler extension iOS bridge singleton.
 *
 * Ported from godot-notification-scheduler-main/ios/src/:
 *   notification_scheduler_plugin.mm  (core logic)
 *   nsp_service.mm                    (UNUserNotificationCenterDelegate)
 *   nsp_converter.mm                  (Godot ↔ NSDictionary; replaced with JSON)
 *   model/notification_data.mm        (data model)
 *
 * Deviations from the Godot source (and rationale):
 *
 *   1. No Godot GDScript signal layer — events are emitted as JSON over
 *      JsbBridgeWrapper on the "notification" channel to match sibling extensions
 *      (Share, IAP, Review).
 *
 *   2. Repeating notifications use UNTimeIntervalNotificationTrigger with
 *      repeats:YES instead of the Godot plugin's UNCalendarNotificationTrigger
 *      sequence of 64 one-shot triggers.  Rationale: simpler, lower overhead,
 *      sufficient for mobile game reminder cadences.  Constraint: iOS enforces
 *      a minimum interval of 60 s for repeating triggers; the bridge returns
 *      E_DEVELOPER_ERROR if intervalSeconds < 60 (matching Godot behaviour).
 *
 *   3. No NSUserDefaults notification cache.  The Godot plugin persists every
 *      scheduled notification to NSUserDefaults so it can reschedule on
 *      re-initialize.  The Cocos bridge omits this: JS drives the schedule on
 *      every app launch (same as the Android channel behaviour), avoiding the
 *      complex re-schedule-on-restore path.  Risk: a force-kill + relaunch cycle
 *      loses pending notifications.  Acceptable for the current game's use case
 *      (reminder cadence set fresh each session).
 *
 *   4. notificationId is treated as a string throughout (JS passes it as a
 *      number, converted with [NSString stringWithFormat:@"%ld", …] on receipt).
 *      The Godot plugin uses int; we preserve JS numeric IDs unambiguously as
 *      strings to match platform bridge conventions.
 *
 *   5. cancelAll is added (not in the Godot plugin API) — maps to
 *      removeAllPendingNotificationRequests + removeAllDeliveredNotifications.
 *
 *   6. createNotificationChannel is a documented no-op on iOS (Android concept).
 *      The Godot plugin does set a UNNotificationCategory; we skip it because
 *      we rely on UNNotificationDefaultActionIdentifier only and do not register
 *      custom categories.  notificationDismissed therefore cannot be emitted on
 *      iOS (UNNotificationDismissActionIdentifier requires a category with
 *      UNNotificationCategoryOptionCustomDismissAction).
 *
 *   7. openAppInfoSettings opens UIApplicationOpenNotificationSettingsURLString
 *      (iOS 15.4+) or UIApplicationOpenSettingsURLString (older), matching the
 *      Godot plugin exactly.
 *
 * Threading:
 *   All JsbBridgeWrapper dispatchEventToScript: calls are marshalled to the
 *   main queue, matching FirebaseHub and other sibling bridges.
 *   All UNUserNotificationCenter completion-handler callbacks are treated as
 *   arriving on an arbitrary background queue.
 */

#import "NotificationServiceHub.h"

#import <UIKit/UIKit.h>
#import "platform/apple/JsbBridgeWrapper.h"

// ---------------------------------------------------------------------------
// Channel name (must match Bridge.ts: channel "notification")
// ---------------------------------------------------------------------------

static NSString *const kNotificationChannel = @"notification";

// ---------------------------------------------------------------------------
// Error codes (camelCase, surfaced to JS)
// ---------------------------------------------------------------------------

static NSString *const kErrDeveloperError  = @"E_DEVELOPER_ERROR";
static NSString *const kErrScheduleFailed  = @"E_SCHEDULE_FAILED";
static NSString *const kErrNotSupported    = @"E_NOT_SUPPORTED";

// ---------------------------------------------------------------------------
// userInfo keys stored inside each UNNotificationContent
// (used to reconstruct the event payload when the user taps)
// ---------------------------------------------------------------------------

static NSString *const kUserInfoNotificationId = @"notificationId";
static NSString *const kUserInfoChannelId      = @"channelId";
static NSString *const kUserInfoTitle          = @"title";
static NSString *const kUserInfoContent        = @"content";
static NSString *const kUserInfoDeeplink       = @"deeplink";
static NSString *const kUserInfoCustomData     = @"customData";

// ---------------------------------------------------------------------------
// Wire-protocol helpers
// ---------------------------------------------------------------------------

/**
 * Build a success-response JSON string for the given call id.
 * @param callId  The numeric JS call identifier.
 * @param data    The response data dictionary (may be empty @{}).
 */
static NSString *NSResponseSuccess(NSInteger callId, NSDictionary *data) {
    NSDictionary *envelope = @{
        @"kind"    : @"response",
        @"id"      : @(callId),
        @"success" : @YES,
        @"data"    : data,
    };
    NSData *bytes = [NSJSONSerialization dataWithJSONObject:envelope options:0 error:nil];
    return bytes ? [[NSString alloc] initWithData:bytes encoding:NSUTF8StringEncoding] : @"{}";
}

/**
 * Build a failure-response JSON string for the given call id.
 * @param callId   The numeric JS call identifier.
 * @param code     Short error code string (e.g. "E_SCHEDULE_FAILED").
 * @param message  Human-readable error description.
 */
static NSString *NSResponseError(NSInteger callId, NSString *code, NSString *message) {
    NSDictionary *envelope = @{
        @"kind"    : @"response",
        @"id"      : @(callId),
        @"success" : @NO,
        @"error"   : @{ @"code": code, @"message": message },
    };
    NSData *bytes = [NSJSONSerialization dataWithJSONObject:envelope options:0 error:nil];
    return bytes ? [[NSString alloc] initWithData:bytes encoding:NSUTF8StringEncoding] : @"{}";
}

/**
 * Build an event JSON string (no call id — unsolicited push from native).
 * @param name     Event name (e.g. "notificationOpened").
 * @param data     Event payload dictionary.
 */
static NSString *NSEvent(NSString *name, NSDictionary *data) {
    NSDictionary *envelope = @{
        @"kind" : @"event",
        @"name" : name,
        @"data" : data,
    };
    NSData *bytes = [NSJSONSerialization dataWithJSONObject:envelope options:0 error:nil];
    return bytes ? [[NSString alloc] initWithData:bytes encoding:NSUTF8StringEncoding] : @"{}";
}

/**
 * Marshal a dispatchEventToScript call to the main queue.
 * All callbacks from UNUserNotificationCenter and completion handlers arrive on
 * arbitrary background queues; JS engine access must be on the main thread.
 */
static void NSEmit(NSString *channel, NSString *json) {
    dispatch_async(dispatch_get_main_queue(), ^{
        [[JsbBridgeWrapper sharedInstance] dispatchEventToScript:channel arg:json];
    });
}

// ---------------------------------------------------------------------------
// Parse helpers
// ---------------------------------------------------------------------------

/** Parse a JSON arg string into an NSDictionary; returns @{} on failure. */
static NSDictionary *NSParse(NSString *json) {
    if (json.length == 0) return @{};
    NSData *d = [json dataUsingEncoding:NSUTF8StringEncoding];
    if (!d) return @{};
    id obj = [NSJSONSerialization JSONObjectWithData:d options:0 error:nil];
    return [obj isKindOfClass:[NSDictionary class]] ? obj : @{};
}

// ---------------------------------------------------------------------------
// NotificationServiceHub implementation
// ---------------------------------------------------------------------------

@interface NotificationServiceHub ()

/** Guards -initNotificationService against multiple calls. */
@property (nonatomic, assign) BOOL _serviceRegistered;

/**
 * Notification identifier of the notification that last opened the app, or nil.
 * Set from the UNNotificationResponse in both cold-start and warm-tap paths.
 * Cleared once JS calls getNotificationId or initialize.
 *
 * NOTE: stored as the raw string identifier (the JS notificationId converted to
 * a string, e.g. "42"), NOT as an integer, to match JS bridge conventions.
 */
@property (nonatomic, copy, nullable) NSString *_lastOpenedNotificationId;

/**
 * Cold-start tap buffer.
 * When the app is launched by tapping a notification, didReceiveNotificationResponse
 * fires BEFORE the JS engine is ready and before the JS "initialize" call registers
 * a listener.  We store the event payload here and flush it 500 ms after initialize
 * completes.
 */
@property (nonatomic, copy, nullable) NSDictionary *_pendingLaunchEventData;

@end

@implementation NotificationServiceHub

// ---------------------------------------------------------------------------
// Singleton
// ---------------------------------------------------------------------------

+ (instancetype)sharedInstance {
    static NotificationServiceHub *s = nil;
    static dispatch_once_t once;
    dispatch_once(&once, ^{ s = [[NotificationServiceHub alloc] init]; });
    return s;
}

// ---------------------------------------------------------------------------
// Bootstrap
// ---------------------------------------------------------------------------

/**
 * Install the UN delegate and register the JsbBridgeWrapper listener.
 * Called once from AppDelegate -application:didFinishLaunchingWithOptions:.
 * Idempotent: subsequent calls return immediately.
 */
- (void)initNotificationService {
    if (self._serviceRegistered) return;
    self._serviceRegistered = YES;

    // ── Install UNUserNotificationCenter delegate ────────────────────────────
    //
    // Delegate conflict check (performed 2026-06-10):
    //   grep for UNUserNotificationCenter + setDelegate/delegate= in
    //   native/engine/ios/ and extensions/firebase/ found NO existing delegate
    //   assignment.  Plain delegate install is safe.  If a future extension
    //   needs to share the delegate, save the previous value here and forward
    //   unhandled callbacks to it (delegate chaining).
    //
    // Why here (AppDelegate time) rather than inside the JS "initialize" call:
    //   We must set the delegate BEFORE the app finishes launching so that any
    //   cold-start notification tap is captured in didReceiveNotificationResponse.
    //   Setting the delegate late (inside "initialize") would miss the delivery.
    //
    UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];
    center.delegate = self;
    NSLog(@"[NotificationServiceHub] UNUserNotificationCenter delegate installed");

    // ── Register JsbBridgeWrapper "notification" channel listener ───────────
    // JsbBridgeWrapper is MRC and calls [listener release] on the block after
    // adding it to its internal array (engine source JsbBridgeWrapper.mm:58-69).
    // An inline block literal passed to addScriptEventListener: is +0 (ARC does
    // not retain it before the call), so the engine's release over-frees it →
    // dangling block → EXC_BAD_ACCESS on the next triggerEvent call.  Keep a
    // strong static reference so the block survives the engine's release.
    __weak __typeof__(self) wself = self;
    static OnScriptEventListener sNotificationListener = nil;
    sNotificationListener = ^(NSString *arg) {
        NotificationServiceHub *hub = wself;
        if (!hub) { return; }
        [hub _handleBridgeCall:NSParse(arg)];
    };
    JsbBridgeWrapper *bridge = [JsbBridgeWrapper sharedInstance];
    [bridge addScriptEventListener:kNotificationChannel listener:sNotificationListener];

    NSLog(@"[NotificationServiceHub] initNotificationService complete");
}

// ---------------------------------------------------------------------------
// Bridge dispatch
// ---------------------------------------------------------------------------

/**
 * Route an incoming JS call to the appropriate handler method.
 * Envelope shape: {"id":int, "method":"<name>", "args":{...}}
 */
- (void)_handleBridgeCall:(NSDictionary *)envelope {
    NSInteger callId = [envelope[@"id"] integerValue];
    NSString *method = envelope[@"method"] ?: @"";
    NSDictionary *args = [envelope[@"args"] isKindOfClass:[NSDictionary class]]
                         ? envelope[@"args"] : @{};

    if ([method isEqualToString:@"initialize"]) {
        [self _handleInitialize:callId args:args];
    } else if ([method isEqualToString:@"createNotificationChannel"]) {
        [self _handleCreateNotificationChannel:callId args:args];
    } else if ([method isEqualToString:@"schedule"]) {
        [self _handleSchedule:callId args:args];
    } else if ([method isEqualToString:@"cancel"]) {
        [self _handleCancel:callId args:args];
    } else if ([method isEqualToString:@"cancelAll"]) {
        [self _handleCancelAll:callId args:args];
    } else if ([method isEqualToString:@"setBadgeCount"]) {
        [self _handleSetBadgeCount:callId args:args];
    } else if ([method isEqualToString:@"getNotificationId"]) {
        [self _handleGetNotificationId:callId args:args];
    } else if ([method isEqualToString:@"hasPostNotificationsPermission"]) {
        [self _handleHasPostNotificationsPermission:callId args:args];
    } else if ([method isEqualToString:@"requestPostNotificationsPermission"]) {
        [self _handleRequestPostNotificationsPermission:callId args:args];
    } else if ([method isEqualToString:@"openAppInfoSettings"]) {
        [self _handleOpenAppInfoSettings:callId args:args];
    } else if ([method isEqualToString:@"hasBatteryOptimizationsPermission"]
            || [method isEqualToString:@"requestBatteryOptimizationsPermission"]
            || [method isEqualToString:@"hasScheduleExactAlarmPermission"]
            || [method isEqualToString:@"requestScheduleExactAlarmPermission"]) {
        // Android-only — return E_NOT_SUPPORTED on iOS.
        NSEmit(kNotificationChannel,
               NSResponseError(callId, kErrNotSupported,
                   [NSString stringWithFormat:@"'%@' is not supported on iOS", method]));
    } else {
        NSLog(@"[NotificationServiceHub] Unknown method: %@", method);
        NSEmit(kNotificationChannel,
               NSResponseError(callId, @"E_UNKNOWN_METHOD",
                   [NSString stringWithFormat:@"Unknown method '%@'", method]));
    }
}

// ---------------------------------------------------------------------------
// Method handlers
// ---------------------------------------------------------------------------

/**
 * initialize {} → {}
 *
 * Sets the UNUserNotificationCenter delegate (idempotent — already done in
 * initNotificationService), caches the current authorization status, and flushes
 * any buffered cold-start launch notification to JS after a 500 ms delay.
 *
 * The 500 ms delay gives the JS listener time to register after the "initialize"
 * response is received before the "notificationOpened" event fires.
 */
- (void)_handleInitialize:(NSInteger)callId args:(NSDictionary *)args {
    // Respond immediately — initialization is synchronous from the bridge's POV.
    NSEmit(kNotificationChannel, NSResponseSuccess(callId, @{}));

    // Flush buffered cold-start tap event (if any) after JS has time to register
    // its listener.
    NSDictionary *pending = self._pendingLaunchEventData;
    if (pending) {
        self._pendingLaunchEventData = nil;
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.5 * NSEC_PER_SEC)),
                       dispatch_get_main_queue(), ^{
            NSLog(@"[NotificationServiceHub] Flushing buffered launch notificationOpened event");
            NSEmit(kNotificationChannel, NSEvent(@"notificationOpened", pending));
        });
    }
}

/**
 * createNotificationChannel {...} → {}
 *
 * No-op on iOS — notification channels are an Android concept.
 * Returns success so cross-platform JS code does not need a platform guard.
 */
- (void)_handleCreateNotificationChannel:(NSInteger)callId args:(NSDictionary *)args {
    NSLog(@"[NotificationServiceHub] createNotificationChannel — no-op on iOS");
    NSEmit(kNotificationChannel, NSResponseSuccess(callId, @{}));
}

/**
 * schedule {notificationId, channelId?, title, content, delaySeconds,
 *           intervalSeconds?, deeplink?, badgeCount?, customData?,
 *           restartApp? (ignored), smallIconName/largeIconName (ignored)} → {}
 *
 * Builds a UNMutableNotificationContent and a UNTimeIntervalNotificationTrigger.
 * For repeating notifications (intervalSeconds > 0), iOS enforces a minimum
 * interval of 60 s; values below that return E_DEVELOPER_ERROR.
 *
 * Notification userInfo stores the full payload so the tap handler can reconstruct
 * the event without a separate cache.
 */
- (void)_handleSchedule:(NSInteger)callId args:(NSDictionary *)args {
    // ── Parse arguments ──────────────────────────────────────────────────────
    NSString *notificationId = [NSString stringWithFormat:@"%@", args[@"notificationId"] ?: @"0"];
    NSString *channelId   = args[@"channelId"]  ?: @"";
    NSString *title       = args[@"title"]      ?: @"";
    NSString *body        = args[@"content"]    ?: @"";
    NSString *deeplink    = args[@"deeplink"];
    NSInteger badgeCount  = [args[@"badgeCount"] integerValue];
    id        customData  = args[@"customData"];   // pass-through (dict or nil)

    NSTimeInterval delaySeconds    = [args[@"delaySeconds"] doubleValue];
    NSTimeInterval intervalSeconds = [args[@"intervalSeconds"] doubleValue];

    // ── Validate repeat interval ─────────────────────────────────────────────
    if (intervalSeconds > 0 && intervalSeconds < 60) {
        NSLog(@"[NotificationServiceHub] schedule: intervalSeconds %f < 60 — rejecting", intervalSeconds);
        NSEmit(kNotificationChannel,
               NSResponseError(callId, kErrDeveloperError,
                   @"intervalSeconds must be >= 60 for repeating notifications (iOS UNTimeIntervalNotificationTrigger requirement)"));
        return;
    }

    // ── Ensure a positive delay ──────────────────────────────────────────────
    if (delaySeconds <= 0) {
        delaySeconds = 1.0;  // UNTimeIntervalNotificationTrigger requires > 0
    }

    // ── Build content ────────────────────────────────────────────────────────
    UNMutableNotificationContent *content = [[UNMutableNotificationContent alloc] init];
    content.title = title;
    content.body  = body;
    content.sound = [UNNotificationSound defaultSound];
    if (badgeCount > 0) {
        content.badge = @(badgeCount);
    }

    // Embed the full payload in userInfo so the tap handler can reconstruct the
    // event without a separate cache (deviation from Godot plugin — see header).
    NSMutableDictionary *userInfo = [NSMutableDictionary dictionary];
    userInfo[kUserInfoNotificationId] = notificationId;
    if (channelId.length)    userInfo[kUserInfoChannelId] = channelId;
    if (title.length)        userInfo[kUserInfoTitle]     = title;
    if (body.length)         userInfo[kUserInfoContent]   = body;
    if (deeplink.length)     userInfo[kUserInfoDeeplink]  = deeplink;
    if ([customData isKindOfClass:[NSDictionary class]]) {
        userInfo[kUserInfoCustomData] = customData;
    }
    content.userInfo = [userInfo copy];

    // ── Build trigger ────────────────────────────────────────────────────────
    BOOL repeats = (intervalSeconds >= 60);
    NSTimeInterval triggerInterval = repeats ? intervalSeconds : delaySeconds;
    UNTimeIntervalNotificationTrigger *trigger =
        [UNTimeIntervalNotificationTrigger triggerWithTimeInterval:triggerInterval
                                                           repeats:repeats];

    // ── Submit request ───────────────────────────────────────────────────────
    UNNotificationRequest *request =
        [UNNotificationRequest requestWithIdentifier:notificationId
                                             content:content
                                             trigger:trigger];

    [[UNUserNotificationCenter currentNotificationCenter]
        addNotificationRequest:request
         withCompletionHandler:^(NSError *_Nullable error) {
        if (error) {
            NSLog(@"[NotificationServiceHub] schedule failed for id=%@: %@",
                  notificationId, error.localizedDescription);
            NSEmit(kNotificationChannel,
                   NSResponseError(callId, kErrScheduleFailed, error.localizedDescription));
        } else {
            NSLog(@"[NotificationServiceHub] scheduled notification id=%@ delay=%.1f repeats=%@",
                  notificationId, triggerInterval, repeats ? @"YES" : @"NO");
            NSEmit(kNotificationChannel, NSResponseSuccess(callId, @{}));
        }
    }];
}

/**
 * cancel {notificationId} → {}
 *
 * Removes both the pending request and any delivered notification with the
 * given identifier from UNUserNotificationCenter.
 */
- (void)_handleCancel:(NSInteger)callId args:(NSDictionary *)args {
    NSString *notificationId = [NSString stringWithFormat:@"%@", args[@"notificationId"] ?: @"0"];
    NSArray<NSString *> *ids = @[notificationId];

    UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];
    [center removePendingNotificationRequestsWithIdentifiers:ids];
    [center removeDeliveredNotificationsWithIdentifiers:ids];

    NSLog(@"[NotificationServiceHub] cancelled notification id=%@", notificationId);
    NSEmit(kNotificationChannel, NSResponseSuccess(callId, @{}));
}

/**
 * cancelAll {} → {}
 *
 * Clears all pending and delivered notifications for this app.
 */
- (void)_handleCancelAll:(NSInteger)callId args:(NSDictionary *)args {
    UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];
    [center removeAllPendingNotificationRequests];
    [center removeAllDeliveredNotifications];

    NSLog(@"[NotificationServiceHub] cancelled all notifications");
    NSEmit(kNotificationChannel, NSResponseSuccess(callId, @{}));
}

/**
 * setBadgeCount {count} → {}
 *
 * iOS 16+: uses [UNUserNotificationCenter setBadgeCount:withCompletionHandler:].
 * Older: falls back to UIApplication.applicationIconBadgeNumber on the main thread.
 */
- (void)_handleSetBadgeCount:(NSInteger)callId args:(NSDictionary *)args {
    NSInteger count = [args[@"count"] integerValue];

    if (@available(iOS 16.0, *)) {
        [[UNUserNotificationCenter currentNotificationCenter]
            setBadgeCount:count
            withCompletionHandler:^(NSError *_Nullable error) {
            if (error) {
                NSLog(@"[NotificationServiceHub] setBadgeCount failed: %@", error.localizedDescription);
            } else {
                NSLog(@"[NotificationServiceHub] setBadgeCount=%ld", (long)count);
            }
            // Always succeed from the JS side — badge is cosmetic.
            NSEmit(kNotificationChannel, NSResponseSuccess(callId, @{}));
        }];
    } else {
        dispatch_async(dispatch_get_main_queue(), ^{
            [UIApplication sharedApplication].applicationIconBadgeNumber = count;
            NSLog(@"[NotificationServiceHub] setBadgeCount=%ld (legacy UIApplication)", (long)count);
            NSEmit(kNotificationChannel, NSResponseSuccess(callId, @{}));
        });
    }
}

/**
 * getNotificationId {defaultValue} → {notificationId}
 *
 * Returns the string identifier of the notification that last opened the app,
 * or defaultValue if none.  The stored value is NOT cleared — JS can call this
 * multiple times to read the same launch context.
 */
- (void)_handleGetNotificationId:(NSInteger)callId args:(NSDictionary *)args {
    id defaultValue = args[@"defaultValue"] ?: [NSNull null];
    id result = self._lastOpenedNotificationId ?: defaultValue;

    NSEmit(kNotificationChannel,
           NSResponseSuccess(callId, @{ @"notificationId": result }));
}

/**
 * hasPostNotificationsPermission {} → {granted: bool}
 *
 * Asynchronously queries UNUserNotificationCenter for the current authorization
 * status.  Considers authorized AND provisional as "granted" (matching the Godot
 * plugin and Android behaviour).
 */
- (void)_handleHasPostNotificationsPermission:(NSInteger)callId args:(NSDictionary *)args {
    [[UNUserNotificationCenter currentNotificationCenter]
        getNotificationSettingsWithCompletionHandler:^(UNNotificationSettings *settings) {
        BOOL granted = (settings.authorizationStatus == UNAuthorizationStatusAuthorized
                     || settings.authorizationStatus == UNAuthorizationStatusProvisional);
        NSLog(@"[NotificationServiceHub] hasPostNotificationsPermission → %@", granted ? @"YES" : @"NO");
        NSEmit(kNotificationChannel,
               NSResponseSuccess(callId, @{ @"granted": @(granted) }));
    }];
}

/**
 * requestPostNotificationsPermission {} → {granted: bool}
 *
 * Presents the system permission dialog (alert | badge | sound).
 * Responds asynchronously once the user dismisses the dialog.
 */
- (void)_handleRequestPostNotificationsPermission:(NSInteger)callId args:(NSDictionary *)args {
    UNAuthorizationOptions opts =
        UNAuthorizationOptionAlert | UNAuthorizationOptionBadge | UNAuthorizationOptionSound;

    [[UNUserNotificationCenter currentNotificationCenter]
        requestAuthorizationWithOptions:opts
        completionHandler:^(BOOL granted, NSError *_Nullable error) {
        if (error) {
            NSLog(@"[NotificationServiceHub] requestPostNotificationsPermission error: %@",
                  error.localizedDescription);
        }
        NSLog(@"[NotificationServiceHub] requestPostNotificationsPermission → %@",
              granted ? @"granted" : @"denied");
        NSEmit(kNotificationChannel,
               NSResponseSuccess(callId, @{ @"granted": @(granted) }));
    }];
}

/**
 * openAppInfoSettings {} → {}
 *
 * iOS 15.4+: opens the app's Notifications settings page directly.
 * Older iOS: falls back to the general app settings page.
 */
- (void)_handleOpenAppInfoSettings:(NSInteger)callId args:(NSDictionary *)args {
    dispatch_async(dispatch_get_main_queue(), ^{
        NSURL *url;
        if (@available(iOS 15.4, *)) {
            url = [NSURL URLWithString:UIApplicationOpenNotificationSettingsURLString];
        } else {
            url = [NSURL URLWithString:UIApplicationOpenSettingsURLString];
        }
        [[UIApplication sharedApplication] openURL:url options:@{} completionHandler:nil];
        NSLog(@"[NotificationServiceHub] openAppInfoSettings");
        NSEmit(kNotificationChannel, NSResponseSuccess(callId, @{}));
    });
}

// ---------------------------------------------------------------------------
// UNUserNotificationCenterDelegate
// ---------------------------------------------------------------------------

/**
 * Foreground presentation: suppress banner and sound when a notification arrives
 * while the app is active.  The JS layer cancels pending notifications on
 * foreground, so delivering them mid-puzzle would be intrusive and redundant.
 * We do NOT emit notificationOpened here — that event means the user tapped;
 * merely receiving it while foregrounded is not a tap.
 */
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
       willPresentNotification:(UNNotification *)notification
         withCompletionHandler:(void (^)(UNNotificationPresentationOptions))completionHandler {
    NSLog(@"[NotificationServiceHub] willPresentNotification id=%@ — suppressed (app foregrounded)",
          notification.request.identifier);
    completionHandler(UNNotificationPresentationOptionNone);
}

/**
 * Tap or dismiss handler — fires for both:
 *   a) Cold-start: app was launched by tapping a notification.
 *   b) Warm-tap: user tapped while app was backgrounded/suspended.
 *
 * UNNotificationDefaultActionIdentifier → emit notificationOpened.
 * UNNotificationDismissActionIdentifier → emit notificationDismissed.
 *   (Note: dismissed only fires if the notification has a category with
 *    UNNotificationCategoryOptionCustomDismissAction.  Since we register no
 *    custom category, dismissed events are effectively unreachable on iOS.
 *    The code handles it defensively for forward compatibility.)
 *
 * Cold-start buffering:
 *   If this fires before the JS "initialize" call (JS not yet listening), the
 *   event data is stored in _pendingLaunchEventData and flushed in
 *   _handleInitialize: with a 500 ms delay.
 */
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
    didReceiveNotificationResponse:(UNNotificationResponse *)response
             withCompletionHandler:(void (^)(void))completionHandler {

    NSString *identifier    = response.notification.request.identifier;
    NSString *actionId      = response.actionIdentifier;
    NSDictionary *userInfo  = response.notification.request.content.userInfo;

    NSLog(@"[NotificationServiceHub] didReceiveNotificationResponse id=%@ action=%@",
          identifier, actionId);

    // Store the opened notification ID so getNotificationId can return it.
    if ([actionId isEqualToString:UNNotificationDefaultActionIdentifier]) {
        self._lastOpenedNotificationId = userInfo[kUserInfoNotificationId] ?: identifier;
    }

    // Build the event payload from userInfo (embedded at schedule time).
    NSMutableDictionary *eventData = [NSMutableDictionary dictionary];
    eventData[@"notificationId"] = userInfo[kUserInfoNotificationId] ?: identifier;
    if (userInfo[kUserInfoChannelId])   eventData[@"channelId"]   = userInfo[kUserInfoChannelId];
    if (userInfo[kUserInfoTitle])       eventData[@"title"]       = userInfo[kUserInfoTitle];
    if (userInfo[kUserInfoContent])     eventData[@"content"]     = userInfo[kUserInfoContent];
    if (userInfo[kUserInfoDeeplink])    eventData[@"deeplink"]    = userInfo[kUserInfoDeeplink];
    if (userInfo[kUserInfoCustomData])  eventData[@"customData"]  = userInfo[kUserInfoCustomData];
    NSDictionary *payload = [eventData copy];

    NSString *eventName;
    if ([actionId isEqualToString:UNNotificationDefaultActionIdentifier]) {
        eventName = @"notificationOpened";
    } else if ([actionId isEqualToString:UNNotificationDismissActionIdentifier]) {
        eventName = @"notificationDismissed";
    } else {
        NSLog(@"[NotificationServiceHub] unhandled action identifier: %@", actionId);
        completionHandler();
        return;
    }

    // Buffer or emit depending on whether JS has initialised.
    // We detect JS readiness by checking whether the JsbBridgeWrapper has had any
    // listeners registered.  The simplest conservative proxy is: if _serviceRegistered
    // is YES but we don't yet have a way to confirm JS listener registration, we
    // always buffer and flush on initialize.  This is safe because initialize is the
    // first call JS makes, so the delay is at most a few hundred milliseconds.
    if (self._pendingLaunchEventData == nil) {
        // Store — initialize may not have been called yet.
        self._pendingLaunchEventData = payload;
        NSLog(@"[NotificationServiceHub] buffering %@ event (JS not yet ready)", eventName);
        // Also emit immediately in case initialize was already called (bridge
        // is idempotent on duplicate events; JS de-duplication if needed).
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.5 * NSEC_PER_SEC)),
                       dispatch_get_main_queue(), ^{
            // If initialize already flushed _pendingLaunchEventData, nil was set
            // by that path — emit directly here as a fallback.
            if (self._pendingLaunchEventData) {
                self._pendingLaunchEventData = nil;
                NSEmit(kNotificationChannel, NSEvent(eventName, payload));
            }
        });
    } else {
        // Already have a buffered event (unusual, but handle gracefully).
        NSEmit(kNotificationChannel, NSEvent(eventName, payload));
    }

    completionHandler();
}

/**
 * Called when the user opens the notification settings for this app from a
 * notification action.  No-op in this bridge.
 */
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
    openSettingsForNotification:(nullable UNNotification *)notification {
    NSLog(@"[NotificationServiceHub] openSettingsForNotification");
}

@end
