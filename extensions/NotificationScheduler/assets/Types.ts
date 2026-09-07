/**
 * NotificationScheduler extension — public types and enumerations.
 *
 * Import via the barrel:
 *   import { NotificationImportance, NotificationData, ... } from 'db://notification-scheduler/index';
 */

// ============================================================================
// Enums
// ============================================================================

/**
 * Android notification channel / notification importance level.
 *
 * Maps directly to Android's NotificationManager.IMPORTANCE_* constants.
 * On iOS this field is accepted but ignored (iOS controls prominence via
 * system-level Do Not Disturb and Focus settings, not per-channel importance).
 *
 * Values:
 *   NONE    (0) — notifications do not appear at all.
 *   MIN     (1) — no sound/vibration, hidden from the status bar.
 *   LOW     (2) — no sound/vibration, shown in the notification shade.
 *   DEFAULT (3) — sound or vibration; shown in the shade.
 *   HIGH    (4) — makes a heads-up notification; sound and vibration.
 *   MAX     (5) — same as HIGH; reserved for telephone-quality interruptions.
 */
export enum NotificationImportance {
    NONE    = 0,
    MIN     = 1,
    LOW     = 2,
    DEFAULT = 3,
    HIGH    = 4,
    MAX     = 5,
}

// ============================================================================
// Option types
// ============================================================================

/**
 * Options for creating an Android notification channel.
 *
 * Android 8.0+ (API 26+) requires that all notifications be posted to a
 * channel. Create a channel once at app startup before scheduling any
 * notifications.
 *
 * On iOS this call is a no-op — the native side responds with success
 * immediately without creating any platform object.
 */
export interface NotificationChannelOptions {
    /** Unique identifier for the channel; referenced by {@link NotificationData.channelId}. */
    channelId: string;
    /** User-visible name for the channel, displayed in the system settings. */
    channelName: string;
    /**
     * Optional user-visible description shown in system notification settings.
     * Android only; ignored on iOS.
     */
    channelDescription?: string;
    /**
     * Importance level for notifications posted to this channel.
     * Defaults to {@link NotificationImportance.DEFAULT} (3) if omitted.
     * Android only; ignored on iOS.
     */
    importance?: NotificationImportance;
    /**
     * Whether the notification badge count should be reflected on the app icon
     * for this channel.
     * Android only; iOS badge count is controlled per-notification via
     * {@link NotificationData.badgeCount}.
     */
    badgeEnabled?: boolean;
}

/**
 * Arguments for scheduling a local notification, and the shape of the payload
 * delivered in `notificationOpened` / `notificationDismissed` events.
 */
export interface NotificationData {
    /**
     * App-assigned integer identifier for this notification.
     * Use the same id to cancel a specific notification via `cancel()`.
     * Must be unique per pending notification; re-using an id replaces the
     * existing pending notification.
     */
    notificationId: number;
    /**
     * Channel to post the notification on.
     * Must match a `channelId` previously created via `createNotificationChannel`.
     * Android only; ignored on iOS.
     */
    channelId: string;
    /** Short one-line title shown in the notification header. */
    title: string;
    /** Body text of the notification. */
    content: string;
    /**
     * Seconds from now until the notification fires.
     * Must be greater than 0.
     */
    delaySeconds: number;
    /**
     * If provided (and > 0), the notification repeats at this interval in
     * seconds after the first delivery.
     *
     * Omit or set to 0 for a one-shot notification.
     *
     * iOS minimum: 60 seconds. iOS silently clamps values below 60 to 60
     * when repeat is enabled. Android has no enforced minimum.
     */
    intervalSeconds?: number;
    /**
     * Optional deep-link URI opened when the user taps the notification.
     * The app is responsible for parsing and routing this URI on resume.
     */
    deeplink?: string;
    /**
     * Name of the small (monochrome) icon resource to use on Android.
     * Must be a drawable resource name present in the Android project
     * (e.g. `"ic_stat_notify"`). Ignored on iOS.
     */
    smallIconName?: string;
    /**
     * Name of the large (colour) icon resource to use on Android.
     * Must be a drawable resource name present in the Android project
     * (e.g. `"ic_launcher_foreground"`). Ignored on iOS.
     */
    largeIconName?: string;
    /**
     * Number to display on the app icon badge when this notification fires.
     * On Android, also requires the channel to have `badgeEnabled: true`.
     * Pass 0 to clear the badge.
     */
    badgeCount?: number;
    /**
     * Arbitrary string key-value pairs forwarded to the app when the
     * notification is opened or dismissed.
     */
    customData?: Record<string, string>;
    /**
     * When `true` the notification triggers an app restart (or cold launch)
     * rather than resuming the existing process.
     * Primarily useful for Android work-manager scenarios.
     * Android only; ignored on iOS.
     */
    restartApp?: boolean;
}

// ============================================================================
// Error type
// ============================================================================

/**
 * Error object rejected by any NotificationScheduler call.
 *
 * Built-in codes:
 *   `E_NOT_SUPPORTED` — method called on a non-native platform (web / editor),
 *                       or Android-only method called on iOS.
 *   `E_TIMEOUT`       — no response from the native side within the timeout window.
 *   Any platform-specific code returned by the native layer.
 */
export interface NotificationError {
    /** Machine-readable error code string. */
    code: string;
    /** Human-readable description of the error. */
    message: string;
}
