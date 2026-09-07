/**
 * NotificationServiceHub.h — NotificationScheduler extension iOS bridge singleton interface.
 *
 * Registers a JsbBridgeWrapper listener on the "notification" channel and services
 * UNUserNotificationCenter operations (schedule, cancel, permissions, badge count)
 * in response to requests from JavaScript.
 *
 * The singleton also acts as the UNUserNotificationCenterDelegate so that it can:
 *   - Present banners while the app is in the foreground.
 *   - Capture notification-tap responses (including cold-start taps) and emit
 *     the "notificationOpened" event to JS.
 *
 * Cold-start tap buffering:
 *   The UN delegate is installed during -initNotificationService (AppDelegate time),
 *   before the JS engine is ready.  Taps that arrive before the JS "initialize"
 *   call registers a listener are stored in _pendingLaunchResponse.  On the JS
 *   "initialize" call the buffered event is flushed with a short main-queue delay
 *   (500 ms) to ensure the JS listener has registered.
 *
 * Delegate chaining:
 *   As of 2026-06-10 no other code in this project sets the UNUserNotificationCenter
 *   delegate.  The singleton sets itself as the sole delegate.  If a future extension
 *   needs to share the delegate, save the previous delegate and forward all unhandled
 *   callbacks to it.
 *
 * Usage (AppDelegate.mm):
 *   #import "NotificationServiceHub.h"
 *   [[NotificationServiceHub sharedInstance] initNotificationService];
 *
 * Wire protocol (must match assets/scripts/extensions/NotificationScheduler/Bridge.ts):
 *   JS → native  : {"id":int,"method":"<method>","args":{...}}
 *   native → JS  : {"kind":"response","id":N,"success":true,"data":{...}}
 *                  {"kind":"response","id":N,"success":false,"error":{"code":"...","message":"..."}}
 *   native → JS  : {"kind":"event","name":"notificationOpened","data":{...}}
 *                  {"kind":"event","name":"notificationDismissed","data":{...}}
 */

#pragma once

#import <Foundation/Foundation.h>
#import <UserNotifications/UserNotifications.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * NotificationServiceHub — singleton owning the "notification" JS-native bridge channel.
 *
 * Lifecycle: call -initNotificationService once from AppDelegate
 * -application:didFinishLaunchingWithOptions:.  Subsequent calls are no-ops.
 */
@interface NotificationServiceHub : NSObject <UNUserNotificationCenterDelegate>

/** Returns the singleton instance. */
+ (instancetype)sharedInstance;

/**
 * Install the UNUserNotificationCenter delegate, register the "notification"
 * JsbBridgeWrapper listener, and prepare internal state.
 * Must be called from the main thread during app launch.  Idempotent.
 */
- (void)initNotificationService;

@end

NS_ASSUME_NONNULL_END
