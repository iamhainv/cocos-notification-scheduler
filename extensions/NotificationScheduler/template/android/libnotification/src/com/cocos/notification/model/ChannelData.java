package com.cocos.notification.model;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Value object for a notification channel.
 *
 * <p>Key names use camelCase to match the JS wire protocol.  The original
 * Godot plugin used snake_case; the renamed fields are:</p>
 * <ul>
 *   <li>{@code channel_id}          → {@code channelId}</li>
 *   <li>{@code channel_name}        → {@code channelName}</li>
 *   <li>{@code channel_description} → {@code channelDescription}</li>
 *   <li>{@code channel_importance}  → {@code importance}</li>
 *   <li>{@code badge_enabled}       → {@code badgeEnabled}</li>
 * </ul>
 */
public class ChannelData {
    private static final String TAG = "cocos::ChannelData";

    /** Wire-protocol key constants (camelCase). */
    public static final String KEY_ID          = "channelId";
    public static final String KEY_NAME        = "channelName";
    public static final String KEY_DESCRIPTION = "channelDescription";
    public static final String KEY_IMPORTANCE  = "importance";
    public static final String KEY_BADGE       = "badgeEnabled";

    private static final String DEFAULT_NAME        = "Default Channel";
    private static final String DEFAULT_DESCRIPTION = "Notifications channel";
    private static final int    DEFAULT_IMPORTANCE  = NotificationManager.IMPORTANCE_DEFAULT;
    private static final boolean DEFAULT_BADGE      = true;

    private String  id;
    private String  name;
    private String  description;
    private int     importance;
    private boolean badgeEnabled;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    /** Minimal constructor — fills defaults for name, description, importance. */
    public ChannelData(String id) {
        this.id          = id;
        this.name        = DEFAULT_NAME;
        this.description = DEFAULT_DESCRIPTION;
        this.importance  = DEFAULT_IMPORTANCE;
        this.badgeEnabled= DEFAULT_BADGE;
    }

    /** Build from a camelCase JSON object (JS wire args). */
    public ChannelData(JSONObject json) {
        id          = json.optString(KEY_ID,          null);
        name        = json.optString(KEY_NAME,        DEFAULT_NAME);
        description = json.optString(KEY_DESCRIPTION, DEFAULT_DESCRIPTION);
        importance  = json.optInt(KEY_IMPORTANCE,     DEFAULT_IMPORTANCE);
        badgeEnabled= json.optBoolean(KEY_BADGE,      DEFAULT_BADGE);
    }

    /** Build from a live {@link NotificationChannel} (self-healing in receiver). */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public ChannelData(NotificationChannel channel) {
        id          = channel.getId();
        name        = channel.getName() != null ? channel.getName().toString() : DEFAULT_NAME;
        description = channel.getDescription() != null ? channel.getDescription() : DEFAULT_DESCRIPTION;
        importance  = channel.getImportance();
        badgeEnabled= channel.canShowBadge();
    }

    /** Build from broadcast intent extras (alarm self-healing). */
    public ChannelData(Intent intent) {
        id          = intent.getStringExtra(KEY_ID);
        name        = intent.getStringExtra(KEY_NAME);        if (name == null) name = DEFAULT_NAME;
        description = intent.getStringExtra(KEY_DESCRIPTION); if (description == null) description = DEFAULT_DESCRIPTION;
        importance  = intent.getIntExtra(KEY_IMPORTANCE, DEFAULT_IMPORTANCE);
        badgeEnabled= intent.getBooleanExtra(KEY_BADGE, DEFAULT_BADGE);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    /** Returns the channel ID. */
    public String getId()          { return id; }

    /** Returns the channel display name. */
    public String getName()        { return name; }

    /** Returns the channel description. */
    public String getDescription() { return description; }

    /** Returns the importance level (0–5). */
    public int getImportance()     { return importance; }

    /** Returns whether badge display is enabled. */
    public boolean getBadgeEnabled() { return badgeEnabled; }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    /**
     * Returns true if the minimum required fields are present.
     * Fills in optional fields with defaults if absent (mirrors original).
     */
    public boolean isValid() {
        if (importance == 0 && !isImportanceSet()) importance = DEFAULT_IMPORTANCE;
        return id != null && !id.isEmpty()
                && name != null && !name.isEmpty()
                && description != null;
    }

    private boolean isImportanceSet() {
        // importance can legitimately be 0 (IMPORTANCE_NONE), so the 0-check
        // in isValid() above is only used as a "not explicitly set" sentinel.
        // We rely on the constructor defaults to be correct.
        return true;
    }

    // -----------------------------------------------------------------------
    // Intent serialisation
    // -----------------------------------------------------------------------

    /** Writes channel fields into an intent for self-healing in the receiver. */
    public void populateIntent(Intent intent) {
        intent.putExtra(KEY_ID,          id);
        intent.putExtra(KEY_NAME,        name);
        intent.putExtra(KEY_DESCRIPTION, description);
        intent.putExtra(KEY_IMPORTANCE,  importance);
        intent.putExtra(KEY_BADGE,       badgeEnabled);
    }

    // -----------------------------------------------------------------------
    // JSON serialisation
    // -----------------------------------------------------------------------

    /**
     * Serialises to camelCase JSON for the JS response payload.
     *
     * @throws JSONException on JSON construction failure.
     */
    public JSONObject toJson() throws JSONException {
        JSONObject j = new JSONObject();
        j.put(KEY_ID,          id);
        j.put(KEY_NAME,        name);
        j.put(KEY_DESCRIPTION, description);
        j.put(KEY_IMPORTANCE,  importance);
        j.put(KEY_BADGE,       badgeEnabled);
        return j;
    }

    // -----------------------------------------------------------------------
    // NotificationChannel factory
    // -----------------------------------------------------------------------

    /**
     * Creates an Android {@link NotificationChannel} from this data.
     * Only callable on API 26+.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public NotificationChannel toNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(id, name, importance);
        ch.setDescription(description);
        ch.setShowBadge(badgeEnabled);
        return ch;
    }
}
