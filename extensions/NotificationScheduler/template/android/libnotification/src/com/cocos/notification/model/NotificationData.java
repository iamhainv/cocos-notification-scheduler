package com.cocos.notification.model;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.cocos.notification.CancelNotificationReceiver;
import com.cocos.notification.ResultActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * Value object carrying all data for a single notification.
 *
 * <p>Key names use camelCase to match the JS wire protocol.  The original
 * Godot plugin used snake_case; every field has been renamed accordingly:</p>
 * <ul>
 *   <li>{@code notification_id}  → {@code notificationId}</li>
 *   <li>{@code channel_id}       → {@code channelId}</li>
 *   <li>{@code small_icon_name}  → {@code smallIconName}</li>
 *   <li>{@code large_icon_name}  → {@code largeIconName}</li>
 *   <li>{@code badge_count}      → {@code badgeCount}</li>
 *   <li>{@code custom_data}      → {@code customData}</li>
 *   <li>{@code restart_app}      → {@code restartApp}</li>
 * </ul>
 */
public class NotificationData {
    private static final String TAG = "cocos::NotificationData";

    /** Intent / SharedPreferences key constants (camelCase). */
    public static final String KEY_ID             = "notificationId";
    public static final String KEY_CHANNEL_ID     = "channelId";
    public static final String KEY_TITLE          = "title";
    public static final String KEY_CONTENT        = "content";
    public static final String KEY_SMALL_ICON     = "smallIconName";
    public static final String KEY_LARGE_ICON     = "largeIconName";
    public static final String KEY_DELAY          = "delaySeconds";
    public static final String KEY_INTERVAL       = "intervalSeconds";
    public static final String KEY_DEEPLINK       = "deeplink";
    public static final String KEY_BADGE_COUNT    = "badgeCount";
    public static final String KEY_CUSTOM_DATA    = "customData";
    public static final String KEY_RESTART_APP    = "restartApp";

    /** SharedPreferences key used internally to record the absolute fire time. */
    public static final String KEY_FIRE_TIME_MS   = "fireTimeMs";

    private static final String DEFAULT_SMALL_ICON = "ic_default_notification";
    private static final int    DEFAULT_LARGE_ICON_SIZE = 512;
    private static final String DRAWABLE_TYPE = "drawable";
    private static final String MIPMAP_TYPE   = "mipmap";

    // Core fields
    private int    id        = -1;
    private String channelId;
    private String title;
    private String content;
    private String smallIconName = DEFAULT_SMALL_ICON;
    private String largeIconName;
    private int    delaySeconds  = -1;
    private int    intervalSeconds = -1;
    private String deeplink;
    private int    badgeCount = -1;
    private Bundle customData;
    private boolean restartApp = false;

    // -----------------------------------------------------------------------
    // Construction from JSON (wire protocol / SharedPreferences)
    // -----------------------------------------------------------------------

    /** Build from a camelCase JSON object as transmitted on the JS wire. */
    public NotificationData(JSONObject json) {
        id           = json.optInt(KEY_ID, -1);
        channelId    = json.optString(KEY_CHANNEL_ID, null);
        title        = json.optString(KEY_TITLE, null);
        content      = json.optString(KEY_CONTENT, null);
        smallIconName= json.optString(KEY_SMALL_ICON, DEFAULT_SMALL_ICON);
        largeIconName= json.optString(KEY_LARGE_ICON, null);
        delaySeconds = json.optInt(KEY_DELAY, -1);
        intervalSeconds = json.optInt(KEY_INTERVAL, -1);
        deeplink     = json.optString(KEY_DEEPLINK, null);
        badgeCount   = json.optInt(KEY_BADGE_COUNT, -1);
        restartApp   = json.optBoolean(KEY_RESTART_APP, false);

        JSONObject cd = json.optJSONObject(KEY_CUSTOM_DATA);
        if (cd != null) {
            customData = jsonObjectToBundle(cd);
        }
    }

    // -----------------------------------------------------------------------
    // Construction from Intent extras (alarm receiver / ResultActivity)
    // -----------------------------------------------------------------------

    /** Build from broadcast/activity intent extras. */
    public NotificationData(Intent intent) {
        if (intent == null) return;
        id            = intent.getIntExtra(KEY_ID, -1);
        channelId     = intent.getStringExtra(KEY_CHANNEL_ID);
        title         = intent.getStringExtra(KEY_TITLE);
        content       = intent.getStringExtra(KEY_CONTENT);
        smallIconName = intent.getStringExtra(KEY_SMALL_ICON);
        if (smallIconName == null) smallIconName = DEFAULT_SMALL_ICON;
        largeIconName = intent.getStringExtra(KEY_LARGE_ICON);
        delaySeconds  = intent.getIntExtra(KEY_DELAY, -1);
        intervalSeconds = intent.getIntExtra(KEY_INTERVAL, -1);
        deeplink      = intent.getStringExtra(KEY_DEEPLINK);
        badgeCount    = intent.getIntExtra(KEY_BADGE_COUNT, -1);
        restartApp    = intent.getBooleanExtra(KEY_RESTART_APP, false);
        customData    = intent.getBundleExtra(KEY_CUSTOM_DATA);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    /** Returns the notification ID, or -1 if not set. */
    public int getId() { return id; }

    /** Returns the notification channel ID. */
    public String getChannelId() { return channelId; }

    /** Returns the notification title. */
    public String getTitle() { return title; }

    /** Returns the notification body text. */
    public String getContent() { return content; }

    /** Returns the small icon drawable name, defaulting to {@value #DEFAULT_SMALL_ICON}. */
    public String getSmallIconName() { return smallIconName; }

    /** Returns true if a large icon name was provided. */
    public boolean hasLargeIcon() { return largeIconName != null; }

    /** Returns the large icon drawable name, or null. */
    public String getLargeIconName() { return largeIconName; }

    /** Returns the initial delay in seconds. */
    public int getDelaySeconds() { return delaySeconds; }

    /** Returns true if a repeat interval was provided. */
    public boolean hasInterval() { return intervalSeconds > 0; }

    /** Returns the repeat interval in seconds. */
    public int getIntervalSeconds() { return intervalSeconds; }

    /** Returns true if a deep-link URI was provided. */
    public boolean hasDeeplink() { return deeplink != null && !deeplink.isEmpty(); }

    /** Returns the deep-link URI string, or null. */
    public String getDeeplink() { return deeplink; }

    /** Returns true if a badge count was provided. */
    public boolean hasBadgeCount() { return badgeCount >= 0; }

    /** Returns the badge count, or 0 if not set. */
    public int getBadgeCount() { return badgeCount >= 0 ? badgeCount : 0; }

    /** Returns true if {@code restartApp} flag is set. */
    public boolean hasRestartApp() { return restartApp; }

    /** Returns the custom data bundle, or null. */
    public Bundle getCustomData() { return customData; }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    /**
     * Returns true if the minimum required fields are present for scheduling.
     * Mirrors {@code NotificationData.isValid()} from the Godot original.
     */
    public boolean isValid() {
        return id >= 0
                && channelId != null && !channelId.isEmpty()
                && title     != null && !title.isEmpty()
                && content   != null && !content.isEmpty()
                && delaySeconds >= 0;
    }

    // -----------------------------------------------------------------------
    // Intent serialisation
    // -----------------------------------------------------------------------

    /**
     * Writes all fields into {@code intent} extras so the data survives
     * IPC across AlarmManager broadcast boundaries.
     */
    public void populateIntent(Intent intent) {
        intent.putExtra(KEY_ID,           id);
        intent.putExtra(KEY_CHANNEL_ID,   channelId);
        intent.putExtra(KEY_TITLE,        title);
        intent.putExtra(KEY_CONTENT,      content);
        intent.putExtra(KEY_SMALL_ICON,   smallIconName);
        intent.putExtra(KEY_DELAY,        delaySeconds);

        if (hasLargeIcon())    intent.putExtra(KEY_LARGE_ICON,   largeIconName);
        if (hasInterval())     intent.putExtra(KEY_INTERVAL,     intervalSeconds);
        if (hasDeeplink())     intent.putExtra(KEY_DEEPLINK,     deeplink);
        if (hasBadgeCount())   intent.putExtra(KEY_BADGE_COUNT,  badgeCount);
        if (restartApp)        intent.putExtra(KEY_RESTART_APP,  true);
        if (customData != null) intent.putExtra(KEY_CUSTOM_DATA, customData);
    }

    // -----------------------------------------------------------------------
    // JSON serialisation (for persistence / wire response)
    // -----------------------------------------------------------------------

    /**
     * Serialises this object to a camelCase {@link JSONObject} suitable for
     * SharedPreferences persistence or the JS response payload.
     *
     * @throws JSONException on JSON construction failure.
     */
    public JSONObject toJson() throws JSONException {
        JSONObject j = new JSONObject();
        j.put(KEY_ID,       id);
        j.put(KEY_CHANNEL_ID, channelId);
        j.put(KEY_TITLE,    title);
        j.put(KEY_CONTENT,  content);
        j.put(KEY_SMALL_ICON, smallIconName);
        j.put(KEY_DELAY,    delaySeconds);

        if (hasLargeIcon())  j.put(KEY_LARGE_ICON,  largeIconName);
        if (hasInterval())   j.put(KEY_INTERVAL,    intervalSeconds);
        if (hasDeeplink())   j.put(KEY_DEEPLINK,    deeplink);
        if (hasBadgeCount()) j.put(KEY_BADGE_COUNT, badgeCount);
        if (restartApp)      j.put(KEY_RESTART_APP, true);

        if (customData != null) {
            JSONObject cd = new JSONObject();
            for (String k : customData.keySet()) {
                cd.put(k, customData.get(k));
            }
            j.put(KEY_CUSTOM_DATA, cd);
        }
        return j;
    }

    // -----------------------------------------------------------------------
    // Notification building
    // -----------------------------------------------------------------------

    /**
     * Builds the {@link Notification} that {@link NotificationReceiver} posts.
     *
     * <p>Small icon lookup: tries {@code drawable/smallIconName}; if the
     * resource is missing falls back to {@code mipmap/ic_launcher} so the
     * notification is never silently dropped due to a missing asset.</p>
     *
     * @param context Application context.
     * @return The built {@link Notification}, or null if the platform is too
     *         old or the POST_NOTIFICATIONS permission is absent on API 34+.
     */
    public Notification buildNotification(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.w(TAG, "buildNotification(): SDK " + Build.VERSION.SDK_INT
                    + " < 23; skipping.");
            return null;
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "buildNotification(): POST_NOTIFICATIONS not granted.");
            return null;
        }

        // Build the tap intent (trampoline → game activity)
        Intent tapIntent = new Intent(context, ResultActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NO_HISTORY);
        populateIntent(tapIntent);

        PendingIntent tapPending = PendingIntent.getActivity(context, id, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Build the delete (dismiss) intent
        Intent dismissIntent = new Intent(context, CancelNotificationReceiver.class);
        populateIntent(dismissIntent);
        PendingIntent dismissPending = PendingIntent.getBroadcast(context, id,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Resources res = context.getResources();
        String pkg   = context.getPackageName();

        int smallIconResId = res.getIdentifier(smallIconName, DRAWABLE_TYPE, pkg);
        if (smallIconResId == 0) {
            Log.w(TAG, "Small icon '" + smallIconName + "' not found; falling back to ic_launcher.");
            smallIconResId = res.getIdentifier("ic_launcher", MIPMAP_TYPE, pkg);
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(smallIconResId)
                        .setContentTitle(title)
                        .setContentText(content)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentIntent(tapPending)
                        .setDeleteIntent(dismissPending)
                        .setAutoCancel(true);

        if (hasLargeIcon()) {
            int largeId = res.getIdentifier(largeIconName, DRAWABLE_TYPE, pkg);
            if (largeId != 0) {
                try {
                    Drawable d = context.getDrawable(largeId);
                    if (d != null) builder.setLargeIcon(drawableToBitmap(d));
                } catch (Resources.NotFoundException e) {
                    Log.w(TAG, "Large icon '" + largeIconName + "' not found.");
                }
            }
        }

        if (hasBadgeCount()) {
            builder.setNumber(badgeCount);
        }

        return builder.build();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();
        if (w <= 0) w = DEFAULT_LARGE_ICON_SIZE;
        if (h <= 0) h = DEFAULT_LARGE_ICON_SIZE;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, w, h);
        drawable.draw(canvas);
        return bmp;
    }

    private static Bundle jsonObjectToBundle(JSONObject json) {
        Bundle b = new Bundle();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            Object v = json.opt(k);
            if (v instanceof Boolean) b.putBoolean(k, (Boolean) v);
            else if (v instanceof Integer) b.putInt(k, (Integer) v);
            else if (v instanceof Long) b.putLong(k, (Long) v);
            else if (v instanceof Double) b.putDouble(k, (Double) v);
            else if (v instanceof String) b.putString(k, (String) v);
            else if (v != null) b.putString(k, v.toString());
        }
        return b;
    }
}
