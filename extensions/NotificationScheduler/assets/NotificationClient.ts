/**
 * NotificationScheduler — NotificationClient — Cocos Creator 3.8.8
 *
 * Public API for scheduling, cancelling, and querying local push notifications
 * on iOS and Android. All methods are async and delegate to the native side via
 * notificationBridge.call().
 *
 * Usage:
 *   import { notificationScheduler } from 'db://notification-scheduler/index';
 *
 *   // 1. Initialise once on app startup (before scheduling anything).
 *   await notificationScheduler.initialize();
 *
 *   // 2. Android: create a channel (iOS: this call is a no-op).
 *   await notificationScheduler.createNotificationChannel({
 *       channelId: 'daily',
 *       channelName: 'Daily Reminders',
 *       importance: NotificationImportance.DEFAULT,
 *   });
 *
 *   // 3. Schedule a notification.
 *   await notificationScheduler.schedule({
 *       notificationId: 1,
 *       channelId: 'daily',
 *       title: 'Come back!',
 *       content: 'Your puzzle awaits.',
 *       delaySeconds: 86400,
 *   });
 *
 *   // 4. Listen for the notification being tapped.
 *   notificationScheduler.onNotificationOpened((data) => {
 *       console.log('opened via notification', data.deeplink);
 *   });
 *
 * Platform notes:
 *   - Android-only methods (battery optimisations, exact-alarm, openAppInfoSettings)
 *     reject with { code: 'E_NOT_SUPPORTED' } on iOS.
 *   - `notificationDismissed` events are Android-only; iOS does not deliver a
 *     callback when the user swipes away a notification.
 *   - `requestPostNotificationsPermission` always resolves `{ granted: true }` on
 *     Android below API 33 (Android 12 and earlier), because the permission did not
 *     exist on those versions.
 */

import { notificationBridge } from './core/Bridge';
import {
    NotificationChannelOptions,
    NotificationData,
    NotificationError,
} from './Types';

// ---------------------------------------------------------------------------
// Wire result types (match native response `data` payloads)
// ---------------------------------------------------------------------------

interface _VoidData {}

interface _GrantedData {
    granted: boolean;
}

interface _NotificationIdData {
    notificationId: number;
}

// ---------------------------------------------------------------------------
// NotificationClient
// ---------------------------------------------------------------------------

/**
 * Cross-platform local notification client.
 *
 * Obtain the singleton via the exported `notificationScheduler` constant.
 * Do not instantiate directly.
 */
export class NotificationClient {

    // ------------------------------------------------------------------
    // Initialisation
    // ------------------------------------------------------------------

    /**
     * Initialise the notification system. Must be called once before any
     * scheduling or permission calls.
     *
     * On iOS: sets up the UNUserNotificationCenter delegate and, if the app
     * was opened by tapping a notification, flushes that launch-notification
     * payload so that a `notificationOpened` event fires synchronously within
     * this call.
     *
     * On Android: ensures the notification channels are ready to receive posts.
     *
     * @throws {@link NotificationError} with code `E_NOT_SUPPORTED` on
     *         non-native platforms (web / Cocos editor preview).
     */
    public initialize(): Promise<void> {
        return notificationBridge.call<_VoidData>('initialize', {}).then(() => undefined);
    }

    // ------------------------------------------------------------------
    // Channel management (Android; no-op on iOS)
    // ------------------------------------------------------------------

    /**
     * Create an Android notification channel.
     *
     * Channels are required on Android 8.0+ (API 26+) before posting any
     * notification. Call this once per channel at app startup. If a channel
     * with the same `channelId` already exists the call is a no-op.
     *
     * On iOS this always resolves successfully without creating any platform
     * object.
     *
     * @param options  Channel configuration. See {@link NotificationChannelOptions}.
     * @throws {@link NotificationError} on unexpected native failure.
     */
    public createNotificationChannel(options: NotificationChannelOptions): Promise<void> {
        return notificationBridge.call<_VoidData>('createNotificationChannel', options)
            .then(() => undefined);
    }

    // ------------------------------------------------------------------
    // Scheduling
    // ------------------------------------------------------------------

    /**
     * Schedule a local notification to fire after `delaySeconds`.
     *
     * Scheduling a notification with a `notificationId` that already has a
     * pending notification replaces the existing one.
     *
     * iOS repeat minimum: when `intervalSeconds` is provided, iOS silently
     * clamps values below 60 to 60 seconds.
     *
     * @param data  Notification content and timing parameters.
     *              See {@link NotificationData} for field documentation.
     * @throws {@link NotificationError} on validation failure or native error.
     */
    public schedule(data: NotificationData): Promise<void> {
        return notificationBridge.call<_VoidData>('schedule', data).then(() => undefined);
    }

    /**
     * Cancel a previously scheduled notification by its id.
     *
     * If the notification has already fired (and not been re-scheduled due to
     * `intervalSeconds`), this is a no-op.
     *
     * @param notificationId  The id passed to {@link schedule}.
     * @throws {@link NotificationError} on native failure.
     */
    public cancel(notificationId: number): Promise<void> {
        return notificationBridge.call<_VoidData>('cancel', { notificationId })
            .then(() => undefined);
    }

    /**
     * Cancel all pending notifications scheduled by this app.
     *
     * Equivalent to calling `cancel()` for every pending notificationId.
     *
     * @throws {@link NotificationError} on native failure.
     */
    public cancelAll(): Promise<void> {
        return notificationBridge.call<_VoidData>('cancelAll', {}).then(() => undefined);
    }

    // ------------------------------------------------------------------
    // Badge
    // ------------------------------------------------------------------

    /**
     * Set the app icon badge count.
     *
     * Pass `0` to clear the badge. On Android, the badge is usually updated
     * automatically when notifications are posted/cleared; calling this
     * explicitly allows manual control.
     *
     * @param count  Non-negative integer badge count.
     * @throws {@link NotificationError} on native failure.
     */
    public setBadgeCount(count: number): Promise<void> {
        return notificationBridge.call<_VoidData>('setBadgeCount', { count })
            .then(() => undefined);
    }

    // ------------------------------------------------------------------
    // Launch-notification query
    // ------------------------------------------------------------------

    /**
     * Return the notificationId of the notification that most recently
     * launched or resumed the app, if any.
     *
     * After `initialize()` flushes a launch-notification, the id is held in
     * native memory until queried or until the app is cold-started again.
     * Returns `defaultValue` (or `-1` if `defaultValue` is omitted) when the
     * app was not opened via a notification.
     *
     * @param defaultValue  Value to return when no launch-notification is
     *                      present. Defaults to `-1` on the native side when
     *                      omitted.
     */
    public getNotificationId(defaultValue?: number): Promise<number> {
        const args = defaultValue !== undefined ? { defaultValue } : {};
        return notificationBridge.call<_NotificationIdData>('getNotificationId', args)
            .then((data) => data.notificationId);
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    /**
     * Check whether the app currently has the POST_NOTIFICATIONS permission.
     *
     * On Android below API 33 (Android 12 and earlier) the permission did not
     * exist, so this always resolves `{ granted: true }` on those versions.
     * On iOS this checks UNAuthorizationStatus.
     *
     * @returns `{ granted: true }` if notifications are permitted.
     */
    public hasPostNotificationsPermission(): Promise<{ granted: boolean }> {
        return notificationBridge.call<_GrantedData>('hasPostNotificationsPermission', {});
    }

    /**
     * Request the POST_NOTIFICATIONS runtime permission from the user.
     *
     * The promise resolves only after the user answers the OS dialog — which
     * may take an arbitrary amount of time — so the call uses an unlimited
     * timeout internally.
     *
     * On Android below API 33 (Android 12 and earlier) the permission did not
     * exist, so this always resolves `{ granted: true }` immediately without
     * showing any dialog.
     *
     * @returns `{ granted: true }` if the user granted permission.
     */
    public requestPostNotificationsPermission(): Promise<{ granted: boolean }> {
        // timeout 0 — user may sit on the OS dialog indefinitely
        return notificationBridge.call<_GrantedData>(
            'requestPostNotificationsPermission', {}, 0,
        );
    }

    // ------------------------------------------------------------------
    // Android-only: Battery optimisations
    // ------------------------------------------------------------------

    /**
     * Check whether the app is currently exempt from Android battery
     * optimisations (i.e. is on the "unrestricted" / whitelist).
     *
     * Exact-alarm and background-wakeup reliability improves significantly
     * when battery optimisations are disabled.
     *
     * @throws {@link NotificationError} with code `E_NOT_SUPPORTED` on iOS.
     */
    public hasBatteryOptimizationsPermission(): Promise<{ granted: boolean }> {
        return notificationBridge.call<_GrantedData>('hasBatteryOptimizationsPermission', {});
    }

    /**
     * Open the Android system screen that lets the user exempt this app from
     * battery optimisations. The promise resolves when the user returns to
     * the app (i.e. after they have accepted or declined on the settings
     * screen). Uses an unlimited timeout internally.
     *
     * @throws {@link NotificationError} with code `E_NOT_SUPPORTED` on iOS.
     * @returns `{ granted: true }` if the app is now exempt from optimisations.
     */
    public requestBatteryOptimizationsPermission(): Promise<{ granted: boolean }> {
        // timeout 0 — user navigates to system settings; duration is arbitrary
        return notificationBridge.call<_GrantedData>(
            'requestBatteryOptimizationsPermission', {}, 0,
        );
    }

    // ------------------------------------------------------------------
    // Android-only: Exact alarms
    // ------------------------------------------------------------------

    /**
     * Check whether the app has the SCHEDULE_EXACT_ALARM or
     * USE_EXACT_ALARM permission on Android 12+ (API 31+).
     *
     * Exact alarms are required for notifications that must fire at a precise
     * time (e.g. countdown timers). On earlier Android versions this always
     * resolves `{ granted: true }`.
     *
     * @throws {@link NotificationError} with code `E_NOT_SUPPORTED` on iOS.
     */
    public hasScheduleExactAlarmPermission(): Promise<{ granted: boolean }> {
        return notificationBridge.call<_GrantedData>('hasScheduleExactAlarmPermission', {});
    }

    /**
     * Open the Android "Alarms & reminders" settings screen to let the user
     * grant SCHEDULE_EXACT_ALARM. The promise resolves when the user returns
     * to the app. Uses an unlimited timeout internally.
     *
     * On Android below API 31 this always resolves `{ granted: true }`
     * immediately.
     *
     * @throws {@link NotificationError} with code `E_NOT_SUPPORTED` on iOS.
     * @returns `{ granted: true }` if the permission is now granted.
     */
    public requestScheduleExactAlarmPermission(): Promise<{ granted: boolean }> {
        // timeout 0 — user navigates to system settings; duration is arbitrary
        return notificationBridge.call<_GrantedData>(
            'requestScheduleExactAlarmPermission', {}, 0,
        );
    }

    // ------------------------------------------------------------------
    // Android-only: App info settings
    // ------------------------------------------------------------------

    /**
     * Open the Android system App Info settings screen for this app.
     *
     * Useful when the user has previously denied a permission and you need to
     * direct them to grant it manually. The promise resolves once the user
     * returns to the app.
     *
     * @throws {@link NotificationError} with code `E_NOT_SUPPORTED` on iOS.
     */
    public openAppInfoSettings(): Promise<void> {
        // timeout 0 — duration on the settings screen is arbitrary
        return notificationBridge.call<_VoidData>('openAppInfoSettings', {}, 0)
            .then(() => undefined);
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    /**
     * Subscribe to the `notificationOpened` event, fired when the user taps a
     * notification to open or resume the app.
     *
     * `initialize()` also flushes a launch-notification payload as this event
     * if the app was cold-started via a notification tap, so subscribe before
     * calling `initialize()` to capture it.
     *
     * The payload is the {@link NotificationData} shape that was passed to
     * `schedule()`, plus `notificationId`.
     *
     * @param handler  Callback receiving the notification data.
     */
    public onNotificationOpened(handler: (data: NotificationData) => void): void {
        notificationBridge.on('notificationOpened', handler as (p: unknown) => void);
    }

    /**
     * Unsubscribe from the `notificationOpened` event.
     *
     * @param handler  The exact handler reference passed to {@link onNotificationOpened}.
     */
    public offNotificationOpened(handler: (data: NotificationData) => void): void {
        notificationBridge.off('notificationOpened', handler as (p: unknown) => void);
    }

    /**
     * Subscribe to the `notificationDismissed` event, fired when the user
     * explicitly dismisses (swipes away) a notification without tapping it.
     *
     * Android only. iOS does not deliver a callback when the user clears a
     * notification from the shade — this handler will never fire on iOS.
     *
     * @param handler  Callback receiving the notification data.
     */
    public onNotificationDismissed(handler: (data: NotificationData) => void): void {
        notificationBridge.on('notificationDismissed', handler as (p: unknown) => void);
    }

    /**
     * Unsubscribe from the `notificationDismissed` event.
     *
     * @param handler  The exact handler reference passed to
     *                 {@link onNotificationDismissed}.
     */
    public offNotificationDismissed(handler: (data: NotificationData) => void): void {
        notificationBridge.off('notificationDismissed', handler as (p: unknown) => void);
    }
}

/** Singleton notification client. Import this in your game code. */
export const notificationScheduler = new NotificationClient();
