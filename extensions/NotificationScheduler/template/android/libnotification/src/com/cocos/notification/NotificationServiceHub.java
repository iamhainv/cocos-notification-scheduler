package com.cocos.notification;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;

import com.cocos.lib.GlobalObject;
import com.cocos.notification.model.ChannelData;
import com.cocos.notification.model.NotificationData;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Central coordinator for all notification scheduling logic.
 *
 * <p>Receives method-dispatch calls from {@link NotificationBridge}, executes
 * the platform work, and sends responses/events back via
 * {@link NotificationBridge#respondOk}, {@link NotificationBridge#respondErr},
 * and {@link NotificationBridge#emitEvent}.</p>
 *
 * <p>Lifecycle events (resumed activity) are observed via
 * {@link Application.ActivityLifecycleCallbacks} to:</p>
 * <ul>
 *   <li>Re-check the activity intent for a {@code notificationOpened} event
 *       on each resume.</li>
 *   <li>Resolve pending permission outcomes for battery-optimisation and
 *       exact-alarm requests (which return via settings-screen, not a system
 *       dialog callback).</li>
 * </ul>
 *
 * <p><b>Porting notes:</b> The Godot plugin used Godot's
 * {@code onMainCreate}/{@code onGodotSetupCompleted}/{@code onMainActivityResult}/
 * {@code onMainRequestPermissionsResult} hooks.  In Cocos there are no
 * equivalent extension hooks, so:
 * <ul>
 *   <li>Init is done from a ContentProvider ({@link NotificationInitProvider}).</li>
 *   <li>Activity-result callbacks (battery, exact-alarm) are detected by
 *       re-checking state on the next {@code onActivityResumed}.</li>
 *   <li>POST_NOTIFICATIONS is requested via a separate transparent
 *       {@link PermissionRequestActivity}.</li>
 * </ul>
 * </p>
 */
public class NotificationServiceHub {
    private static final String TAG = "cocos::NotificationServiceHub";

    // SharedPreferences names
    private static final String PREFS_DISMISSED   = "NotificationServiceHub_prefs";
    private static final String PREFS_SCHEDULED   = "scheduled_notifications";
    private static final String KEY_PENDING_DISMISSED = "pending_dismissed_ids";

    // Pending permission state (set before launching settings; resolved on resume)
    private static volatile int  _pendingBatteryPermId      = -1;
    private static volatile int  _pendingExactAlarmPermId   = -1;
    private static volatile int  _pendingPostNotifPermId    = -1;

    // Pending opened/dismissed events queued before JS is ready
    private static final List<NotificationData> _pendingOpened    = new ArrayList<>();
    private static final Set<Integer>           _processedIds     = new HashSet<>();
    private static volatile boolean             _jsReady          = false;

    private static Context _appContext;

    // -----------------------------------------------------------------------
    // Initialisation
    // -----------------------------------------------------------------------

    /**
     * Called once from {@link NotificationInitProvider#onCreate()}.
     *
     * <p>Registers {@link Application.ActivityLifecycleCallbacks} to detect
     * activity resume events needed for permission-result handling and
     * notification-opened detection.</p>
     *
     * @param context Application context.
     */
    public static void init(Context context) {
        _appContext = context.getApplicationContext();
        Log.d(TAG, "init()");

        Application app = (Application) _appContext;
        app.registerActivityLifecycleCallbacks(new ActivityLifecycleAdapter() {
            @Override
            public void onActivityResumed(Activity activity) {
                handleActivityResumed(activity);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Method dispatch (called from NotificationBridge)
    // -----------------------------------------------------------------------

    /**
     * Routes an incoming JS method call to the correct handler.
     *
     * @param id     JS request ID for response correlation.
     * @param method Method name from the wire envelope.
     * @param args   Method arguments from the wire envelope.
     */
    public static void dispatch(int id, String method, JSONObject args) {
        switch (method) {
            case "initialize":
                handleInitialize(id);
                break;
            case "createNotificationChannel":
                handleCreateChannel(id, args);
                break;
            case "schedule":
                handleSchedule(id, args);
                break;
            case "cancel":
                handleCancel(id, args);
                break;
            case "cancelAll":
                handleCancelAll(id);
                break;
            case "setBadgeCount":
                handleSetBadgeCount(id, args);
                break;
            case "getNotificationId":
                handleGetNotificationId(id, args);
                break;
            case "hasPostNotificationsPermission":
                handleHasPostNotifPerm(id);
                break;
            case "requestPostNotificationsPermission":
                handleRequestPostNotifPerm(id);
                break;
            case "hasBatteryOptimizationsPermission":
                handleHasBatteryPerm(id);
                break;
            case "requestBatteryOptimizationsPermission":
                handleRequestBatteryPerm(id);
                break;
            case "hasScheduleExactAlarmPermission":
                handleHasExactAlarmPerm(id);
                break;
            case "requestScheduleExactAlarmPermission":
                handleRequestExactAlarmPerm(id);
                break;
            case "openAppInfoSettings":
                handleOpenAppInfo(id);
                break;
            default:
                NotificationBridge.respondErr(id, "E_NOT_SUPPORTED",
                        "Unknown method: " + method);
        }
    }

    // -----------------------------------------------------------------------
    // Method handlers
    // -----------------------------------------------------------------------

    /** initialize — wires state; flushes any queued opened/dismissed events. */
    private static void handleInitialize(int id) {
        _jsReady = true;
        Log.i(TAG, "initialize(): JS layer ready.");

        // Flush pending opened events
        for (NotificationData data : _pendingOpened) {
            emitOpened(data);
            _processedIds.add(data.getId());
        }
        _pendingOpened.clear();

        // Flush pending dismissed events from SharedPreferences
        SharedPreferences prefs = _appContext.getSharedPreferences(
                PREFS_DISMISSED, Context.MODE_PRIVATE);
        Set<String> dismissedSet = prefs.getStringSet(KEY_PENDING_DISMISSED, new HashSet<String>());
        if (!dismissedSet.isEmpty()) {
            Log.i(TAG, "Flushing " + dismissedSet.size() + " persisted dismissed events.");
            for (String json : dismissedSet) {
                try {
                    NotificationData d = new NotificationData(new JSONObject(json));
                    emitDismissed(d);
                    _processedIds.add(d.getId());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse persisted dismissed notification.", e);
                }
            }
            prefs.edit().remove(KEY_PENDING_DISMISSED).apply();
        }

        // Re-check launch intent (cold-start opened via notification)
        Activity activity = GlobalObject.getActivity();
        if (activity != null) {
            NotificationData intentData = new NotificationData(activity.getIntent());
            if (intentData.isValid() && !_processedIds.contains(intentData.getId())) {
                handleNotificationOpened(intentData);
            }
        }

        try {
            NotificationBridge.respondOk(id, new JSONObject());
        } catch (Exception e) {
            NotificationBridge.respondErr(id, "E_INTERNAL", e.getMessage());
        }
    }

    /** createNotificationChannel — creates an Android notification channel (API 26+). */
    private static void handleCreateChannel(int id, JSONObject args) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Channels not required below API 26; report success silently.
            try {
                NotificationBridge.respondOk(id, new JSONObject());
            } catch (Exception e) {
                NotificationBridge.respondErr(id, "E_INTERNAL", e.getMessage());
            }
            return;
        }

        ChannelData channelData = new ChannelData(args);
        if (!channelData.isValid()) {
            NotificationBridge.respondErr(id, "E_INVALID_ARGS",
                    "createNotificationChannel: missing channelId or channelName.");
            return;
        }

        NotificationManager mgr = (NotificationManager)
                _appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr == null) {
            NotificationBridge.respondErr(id, "E_INTERNAL", "NotificationManager unavailable.");
            return;
        }

        if (mgr.getNotificationChannel(channelData.getId()) != null) {
            Log.d(TAG, "createNotificationChannel(): channel '" + channelData.getId()
                    + "' already exists.");
        } else {
            mgr.createNotificationChannel(channelData.toNotificationChannel());
            Log.d(TAG, "createNotificationChannel(): created '" + channelData.getId() + "'.");
        }

        try {
            NotificationBridge.respondOk(id, channelData.toJson());
        } catch (Exception e) {
            NotificationBridge.respondErr(id, "E_INTERNAL", e.getMessage());
        }
    }

    /** schedule — persists and schedules an alarm via AlarmManager. */
    private static void handleSchedule(int id, JSONObject args) {
        // POST_NOTIFICATIONS check on API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(_appContext,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                NotificationBridge.respondErr(id, "E_PERMISSION_DENIED",
                        "POST_NOTIFICATIONS permission not granted.");
                return;
            }
        }

        NotificationData data = new NotificationData(args);
        if (!data.isValid()) {
            NotificationBridge.respondErr(id, "E_INVALID_ARGS",
                    "schedule: missing required fields (notificationId, channelId, title, "
                            + "content, delaySeconds).");
            return;
        }

        long fireTimeMs = System.currentTimeMillis() + (data.getDelaySeconds() * 1000L);
        saveScheduledNotification(_appContext, data, fireTimeMs);

        if (data.hasInterval()) {
            scheduleRepeating(_appContext, data, fireTimeMs);
        } else {
            scheduleOnce(_appContext, data, fireTimeMs);
        }

        Log.i(TAG, "schedule(): notificationId=" + data.getId()
                + " delay=" + data.getDelaySeconds() + "s fireAt=" + fireTimeMs);

        try {
            NotificationBridge.respondOk(id, new JSONObject());
        } catch (Exception e) {
            NotificationBridge.respondErr(id, "E_INTERNAL", e.getMessage());
        }
    }

    /** cancel — cancels a single scheduled notification and its alarm. */
    private static void handleCancel(int id, JSONObject args) {
        int notifId = args.optInt("notificationId", -1);
        if (notifId < 0) {
            NotificationBridge.respondErr(id, "E_INVALID_ARGS",
                    "cancel: notificationId is required.");
            return;
        }
        removeScheduledNotification(_appContext, notifId);
        cancelAlarmAndNotification(_appContext, notifId);
        Log.d(TAG, "cancel(): notificationId=" + notifId);

        try {
            NotificationBridge.respondOk(id, new JSONObject());
        } catch (Exception e) {
            NotificationBridge.respondErr(id, "E_INTERNAL", e.getMessage());
        }
    }

    /** cancelAll — cancels all notifications; best-effort cancels known alarms. */
    private static void handleCancelAll(int id) {
        NotificationManagerCompat.from(_appContext).cancelAll();

        // Cancel alarms for all persisted IDs
        SharedPreferences prefs = _appContext.getSharedPreferences(
                PREFS_SCHEDULED, Context.MODE_PRIVATE);
        for (String key : prefs.getAll().keySet()) {
            try {
                int notifId = Integer.parseInt(key);
                cancelAlarm(_appContext, notifId);
            } catch (NumberFormatException e) {
                Log.w(TAG, "cancelAll: non-integer key '" + key + "' in prefs.");
            }
        }
        prefs.edit().clear().apply();
        Log.d(TAG, "cancelAll(): done.");

        try {
            NotificationBridge.respondOk(id, new JSONObject());
        } catch (Exception e) {
            NotificationBridge.respondErr(id, "E_INTERNAL", e.getMessage());
        }
    }

    /**
     * setBadgeCount — badge count is set via NotificationCompat's setNumber()
     * on individual notifications.  Android has no global app badge API without
     * a third-party library, matching the original plugin's behaviour (returns
     * ERR_UNAVAILABLE on Android).
     */
    private static void handleSetBadgeCount(int id, JSONObject args) {
        Log.i(TAG, "setBadgeCount(): not supported on Android; no-op.");
        // Mirror original: report success so JS side is not broken, but log.
        try {
            NotificationBridge.respondOk(id, new JSONObject());
        } catch (Exception e) {
            NotificationBridge.respondErr(id, "E_INTERNAL", e.getMessage());
        }
    }

    /**
     * getNotificationId — returns the notification ID stored in the current
     * activity intent (set by {@link ResultActivity} when the user taps a
     * notification), or the provided defaultValue.
     */
    private static void handleGetNotificationId(int id, JSONObject args) {
        int defaultValue = args.optInt("defaultValue", -1);
        int result = defaultValue;

        Activity activity = GlobalObject.getActivity();
        if (activity != null) {
            Intent intent = activity.getIntent();
            if (intent != null && intent.hasExtra(NotificationData.KEY_ID)) {
                result = intent.getIntExtra(NotificationData.KEY_ID, defaultValue);
                Log.i(TAG, "getNotificationId(): found id=" + result + " in intent.");
            }
        }

        try {
            JSONObject data = new JSONObject();
            data.put("notificationId", result);
            NotificationBridge.respondOk(id, data);
        } catch (Exception e) {
            NotificationBridge.respondErr(id, "E_INTERNAL", e.getMessage());
        }
    }

    /** hasPostNotificationsPermission — checks POST_NOTIFICATIONS (API 33+). */
    private static void handleHasPostNotifPerm(int id) {
        boolean granted = isPostNotificationsGranted();
        try {
            JSONObject data = new JSONObject();
            data.put("granted", granted);
            NotificationBridge.respondOk(id, data);
        } catch (Exception e) {
            NotificationBridge.respondErr(id, "E_INTERNAL", e.getMessage());
        }
    }

    /**
     * requestPostNotificationsPermission — below API 33 responds immediately
     * with {granted:true}.  On API 33+ launches {@link PermissionRequestActivity}
     * which calls ActivityCompat.requestPermissions and reports back via the
     * static callback {@link #onPostNotifPermResult}.
     */
    private static void handleRequestPostNotifPerm(int id) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            // Below API 33: permission not required, respond granted
            respondGranted(id);
            return;
        }
        if (isPostNotificationsGranted()) {
            respondGranted(id);
            return;
        }
        // Launch transparent PermissionRequestActivity to do the request
        _pendingPostNotifPermId = id;
        Activity activity = GlobalObject.getActivity();
        if (activity != null) {
            Intent intent = new Intent(activity, PermissionRequestActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } else {
            NotificationBridge.respondErr(id, "E_INTERNAL", "No activity available.");
        }
    }

    /** hasBatteryOptimizationsPermission — checks PowerManager whitelist. */
    private static void handleHasBatteryPerm(int id) {
        boolean granted = isBatteryOptimisationsGranted();
        try {
            JSONObject data = new JSONObject();
            data.put("granted", granted);
            NotificationBridge.respondOk(id, data);
        } catch (Exception e) {
            NotificationBridge.respondErr(id, "E_INTERNAL", e.getMessage());
        }
    }

    /**
     * requestBatteryOptimizationsPermission — fires the system settings intent.
     * The result is detected on the next {@code onActivityResumed} by
     * re-checking {@link PowerManager#isIgnoringBatteryOptimizations}.
     */
    private static void handleRequestBatteryPerm(int id) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            respondGranted(id);
            return;
        }
        if (isBatteryOptimisationsGranted()) {
            respondGranted(id);
            return;
        }
        _pendingBatteryPermId = id;
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + _appContext.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            _appContext.startActivity(intent);
        } catch (Exception e) {
            _pendingBatteryPermId = -1;
            NotificationBridge.respondErr(id, "E_INTENT_FAILED", e.getMessage());
        }
    }

    /** hasScheduleExactAlarmPermission — checks AlarmManager.canScheduleExactAlarms (API 31+). */
    private static void handleHasExactAlarmPerm(int id) {
        boolean granted = isExactAlarmGranted();
        try {
            JSONObject data = new JSONObject();
            data.put("granted", granted);
            NotificationBridge.respondOk(id, data);
        } catch (Exception e) {
            NotificationBridge.respondErr(id, "E_INTERNAL", e.getMessage());
        }
    }

    /**
     * requestScheduleExactAlarmPermission — fires the system settings intent on API 31+.
     * The result is detected on the next {@code onActivityResumed}.
     */
    private static void handleRequestExactAlarmPerm(int id) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            respondGranted(id);
            return;
        }
        if (isExactAlarmGranted()) {
            respondGranted(id);
            return;
        }
        _pendingExactAlarmPermId = id;
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + _appContext.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            _appContext.startActivity(intent);
        } catch (Exception e) {
            _pendingExactAlarmPermId = -1;
            NotificationBridge.respondErr(id, "E_INTENT_FAILED", e.getMessage());
        }
    }

    /** openAppInfoSettings — opens the app's system settings page. */
    private static void handleOpenAppInfo(int id) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.fromParts("package", _appContext.getPackageName(), null));
            _appContext.startActivity(intent);
            NotificationBridge.respondOk(id, new JSONObject());
        } catch (Exception e) {
            NotificationBridge.respondErr(id, "E_INTENT_FAILED", e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Activity lifecycle — permission result detection
    // -----------------------------------------------------------------------

    private static void handleActivityResumed(Activity activity) {
        // Resolve pending battery optimisation permission request
        if (_pendingBatteryPermId >= 0) {
            int pendingId = _pendingBatteryPermId;
            _pendingBatteryPermId = -1;
            try {
                JSONObject data = new JSONObject();
                data.put("granted", isBatteryOptimisationsGranted());
                NotificationBridge.respondOk(pendingId, data);
            } catch (Exception e) {
                NotificationBridge.respondErr(pendingId, "E_INTERNAL", e.getMessage());
            }
        }

        // Resolve pending exact-alarm permission request
        if (_pendingExactAlarmPermId >= 0) {
            int pendingId = _pendingExactAlarmPermId;
            _pendingExactAlarmPermId = -1;
            try {
                JSONObject data = new JSONObject();
                data.put("granted", isExactAlarmGranted());
                NotificationBridge.respondOk(pendingId, data);
            } catch (Exception e) {
                NotificationBridge.respondErr(pendingId, "E_INTERNAL", e.getMessage());
            }
        }

        // Re-check intent for notificationOpened (handles resume from tray)
        if (_jsReady && activity.getIntent() != null) {
            NotificationData intentData = new NotificationData(activity.getIntent());
            if (intentData.isValid() && !_processedIds.contains(intentData.getId())) {
                handleNotificationOpened(intentData);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Notification open / dismiss event handling
    // -----------------------------------------------------------------------

    /**
     * Called by {@link ResultActivity} (tap) or on resume from intent.
     * If JS is ready, emits the {@code notificationOpened} event immediately.
     * Otherwise queues it for emission on {@code initialize}.
     */
    public static void handleNotificationOpened(NotificationData data) {
        if (_jsReady) {
            emitOpened(data);
            _processedIds.add(data.getId());
        } else {
            Log.i(TAG, "handleNotificationOpened(): JS not ready, queuing id=" + data.getId());
            _pendingOpened.add(data);
        }
    }

    /**
     * Called by {@link CancelNotificationReceiver} when a notification is dismissed.
     * If JS is ready, emits the {@code notificationDismissed} event immediately.
     * Otherwise persists it to SharedPreferences for emission on next {@code initialize}.
     */
    public static void handleNotificationDismissed(Context context, NotificationData data) {
        if (_jsReady) {
            emitDismissed(data);
        } else {
            Log.i(TAG, "handleNotificationDismissed(): JS not ready, persisting id=" + data.getId());
            persistDismissed(context, data);
        }
    }

    /** Called by {@link PermissionRequestActivity} with the permission result. */
    public static void onPostNotifPermResult(boolean granted) {
        int pendingId = _pendingPostNotifPermId;
        _pendingPostNotifPermId = -1;
        if (pendingId < 0) return;

        try {
            JSONObject data = new JSONObject();
            data.put("granted", granted);
            NotificationBridge.respondOk(pendingId, data);
        } catch (Exception e) {
            NotificationBridge.respondErr(pendingId, "E_INTERNAL", e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Alarm scheduling helpers
    // -----------------------------------------------------------------------

    /**
     * Schedules a one-shot exact alarm via AlarmManager.
     * Uses the appropriate API level variant:
     * <ul>
     *   <li>API 31+ with exact-alarm permission: {@code setExactAndAllowWhileIdle}</li>
     *   <li>API 31+ without permission: {@code setAndAllowWhileIdle} (inexact fallback)</li>
     *   <li>API 23–30: {@code setExactAndAllowWhileIdle}</li>
     *   <li>API 19–22: {@code setExact}</li>
     *   <li>Below API 19: {@code set}</li>
     * </ul>
     */
    static void scheduleOnce(Context context, NotificationData data, long fireTimeMs) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        data.populateIntent(intent);
        populateChannelIntoIntent(context, data.getChannelId(), intent);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(context, data.getId(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireTimeMs, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireTimeMs, pi);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireTimeMs, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                am.setExact(AlarmManager.RTC_WAKEUP, fireTimeMs, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, fireTimeMs, pi);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "scheduleOnce: SecurityException: " + e.getMessage());
        }
    }

    /**
     * Schedules a repeating alarm.  Android 12+ deprecated exact repeating;
     * we use {@code setRepeating} (inexact) as the Godot original does.
     */
    static void scheduleRepeating(Context context, NotificationData data, long fireTimeMs) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        data.populateIntent(intent);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long intervalMs = data.getIntervalSeconds() * 1000L;

        PendingIntent pi = PendingIntent.getBroadcast(context, data.getId(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        am.setRepeating(AlarmManager.RTC_WAKEUP, fireTimeMs, intervalMs, pi);
        Log.i(TAG, "scheduleRepeating(): id=" + data.getId() + " interval=" + data.getIntervalSeconds() + "s");
    }

    /** Cancels an alarm PendingIntent for the given notification ID. */
    static void cancelAlarm(Context context, int notifId) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.putExtra(NotificationData.KEY_ID, notifId);
        PendingIntent pi = PendingIntent.getBroadcast(context, notifId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }

    private static void cancelAlarmAndNotification(Context context, int notifId) {
        cancelAlarm(context, notifId);
        NotificationManagerCompat.from(context).cancel(notifId);
    }

    // -----------------------------------------------------------------------
    // Persistence (BootReceiver / reschedule support)
    // -----------------------------------------------------------------------

    /**
     * Persists a scheduled notification to SharedPreferences so
     * {@link BootReceiver} can restore it after device reboot.
     */
    public static void saveScheduledNotification(Context context, NotificationData data,
                                                 long fireTimeMs) {
        try {
            JSONObject json = data.toJson();
            json.put(NotificationData.KEY_FIRE_TIME_MS, fireTimeMs);
            SharedPreferences prefs = context.getSharedPreferences(
                    PREFS_SCHEDULED, Context.MODE_PRIVATE);
            prefs.edit().putString(String.valueOf(data.getId()), json.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "saveScheduledNotification: " + e.getMessage());
        }
    }

    /**
     * Removes a notification from persistence (on cancel or after one-shot fires).
     */
    public static void removeScheduledNotification(Context context, int notifId) {
        SharedPreferences prefs = context.getSharedPreferences(
                PREFS_SCHEDULED, Context.MODE_PRIVATE);
        prefs.edit().remove(String.valueOf(notifId)).apply();
        Log.d(TAG, "removeScheduledNotification(): id=" + notifId);
    }

    /**
     * Re-schedules all persisted notifications after device reboot.
     * Called by {@link BootReceiver}.
     */
    public static void rescheduleAll(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                PREFS_SCHEDULED, Context.MODE_PRIVATE);
        Map<String, ?> all = prefs.getAll();
        if (all.isEmpty()) {
            Log.i(TAG, "rescheduleAll(): nothing to restore.");
            return;
        }

        for (Map.Entry<String, ?> entry : all.entrySet()) {
            try {
                String jsonStr  = (String) entry.getValue();
                JSONObject json = new JSONObject(jsonStr);
                long fireTimeMs = json.optLong(NotificationData.KEY_FIRE_TIME_MS, -1L);
                NotificationData data = new NotificationData(json);

                if (!data.isValid() || fireTimeMs < 0) {
                    removeScheduledNotification(context, Integer.parseInt(entry.getKey()));
                    continue;
                }

                if (data.hasInterval()) {
                    scheduleRepeating(context, data, fireTimeMs);
                } else {
                    if (System.currentTimeMillis() > fireTimeMs) {
                        Log.w(TAG, "rescheduleAll(): id=" + data.getId()
                                + " expired while device was off — dropping.");
                        removeScheduledNotification(context, data.getId());
                        continue;
                    }
                    scheduleOnce(context, data, fireTimeMs);
                }
            } catch (Exception e) {
                Log.e(TAG, "rescheduleAll: failed to restore entry: " + e.getMessage());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static boolean isPostNotificationsGranted() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
            return NotificationManagerCompat.from(_appContext).areNotificationsEnabled();
        }
        return true;
    }

    private static boolean isBatteryOptimisationsGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager)
                    _appContext.getSystemService(Context.POWER_SERVICE);
            return pm != null
                    && pm.isIgnoringBatteryOptimizations(_appContext.getPackageName());
        }
        return true;
    }

    private static boolean isExactAlarmGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager)
                    _appContext.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    private static void respondGranted(int id) {
        try {
            JSONObject data = new JSONObject();
            data.put("granted", true);
            NotificationBridge.respondOk(id, data);
        } catch (Exception e) {
            NotificationBridge.respondErr(id, "E_INTERNAL", e.getMessage());
        }
    }

    private static void emitOpened(NotificationData data) {
        try {
            NotificationBridge.emitEvent("notificationOpened", data.toJson());
        } catch (Exception e) {
            Log.e(TAG, "emitOpened: serialisation failed", e);
        }
    }

    private static void emitDismissed(NotificationData data) {
        try {
            NotificationBridge.emitEvent("notificationDismissed", data.toJson());
        } catch (Exception e) {
            Log.e(TAG, "emitDismissed: serialisation failed", e);
        }
    }

    private static void persistDismissed(Context context, NotificationData data) {
        try {
            JSONObject json = data.toJson();
            SharedPreferences prefs = context.getSharedPreferences(
                    PREFS_DISMISSED, Context.MODE_PRIVATE);
            Set<String> existing = prefs.getStringSet(KEY_PENDING_DISMISSED, new HashSet<String>());
            Set<String> updated  = new HashSet<>(existing);
            updated.add(json.toString());
            prefs.edit().putStringSet(KEY_PENDING_DISMISSED, updated).apply();
        } catch (Exception e) {
            Log.e(TAG, "persistDismissed: " + e.getMessage());
        }
    }

    /**
     * Copies channel data into the alarm intent for self-healing in
     * {@link NotificationReceiver} if the channel was deleted between scheduling
     * and firing (matches original plugin behaviour).
     */
    private static void populateChannelIntoIntent(Context context, String channelId, Intent intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager mgr = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr == null) return;
        NotificationChannel ch = mgr.getNotificationChannel(channelId);
        if (ch != null) {
            new ChannelData(ch).populateIntent(intent);
        }
    }

    // -----------------------------------------------------------------------
    // ActivityLifecycleAdapter — empty no-op base to reduce boilerplate
    // -----------------------------------------------------------------------

    /**
     * No-op adapter for {@link Application.ActivityLifecycleCallbacks}.
     * Subclasses override only the methods they need.
     */
    private static class ActivityLifecycleAdapter
            implements Application.ActivityLifecycleCallbacks {
        @Override public void onActivityCreated(Activity a, android.os.Bundle b) {}
        @Override public void onActivityStarted(Activity a) {}
        @Override public void onActivityResumed(Activity a) {}
        @Override public void onActivityPaused(Activity a) {}
        @Override public void onActivityStopped(Activity a) {}
        @Override public void onActivitySaveInstanceState(Activity a, android.os.Bundle b) {}
        @Override public void onActivityDestroyed(Activity a) {}
    }
}
