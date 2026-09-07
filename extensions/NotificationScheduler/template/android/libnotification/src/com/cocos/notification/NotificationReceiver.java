package com.cocos.notification;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

import com.cocos.notification.model.ChannelData;
import com.cocos.notification.model.NotificationData;

/**
 * BroadcastReceiver that fires when an AlarmManager alarm triggers.
 *
 * <p>Builds and posts the notification via {@link NotificationManagerCompat}.
 * Also performs self-healing: if the notification channel was deleted between
 * scheduling and firing it attempts to recreate it from intent extras (the
 * original Godot plugin behaviour).</p>
 *
 * <p>For non-repeating notifications, removes the persisted entry from
 * SharedPreferences on fire.</p>
 */
public class NotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "cocos::NotificationReceiver";

    public NotificationReceiver() {}

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            Log.e(TAG, "onReceive(): intent is null.");
            return;
        }

        if (!intent.hasExtra(NotificationData.KEY_ID)) {
            Log.e(TAG, "onReceive(): intent missing '" + NotificationData.KEY_ID + "'.");
            return;
        }

        NotificationData data = new NotificationData(intent);
        Log.d(TAG, "onReceive(): notificationId=" + data.getId());

        // Self-healing: ensure the channel exists (may have been deleted)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager mgr =
                    context.getSystemService(NotificationManager.class);
            if (mgr != null && mgr.getNotificationChannel(data.getChannelId()) == null) {
                Log.w(TAG, "Channel '" + data.getChannelId()
                        + "' missing; attempting to restore from intent extras.");
                ChannelData channelData = new ChannelData(intent);
                if (channelData.isValid()) {
                    mgr.createNotificationChannel(channelData.toNotificationChannel());
                    Log.i(TAG, "Re-created channel: " + data.getChannelId());
                } else {
                    ChannelData fallback = new ChannelData(data.getChannelId());
                    mgr.createNotificationChannel(fallback.toNotificationChannel());
                    Log.i(TAG, "Re-created channel with defaults: " + data.getChannelId());
                }
            }
        }

        // Remove persistence for one-shot notifications
        if (!data.hasInterval()) {
            NotificationServiceHub.removeScheduledNotification(context, data.getId());
        }

        Notification notification = data.buildNotification(context);
        if (notification != null) {
            NotificationManagerCompat.from(context).notify(data.getId(), notification);
        } else {
            Log.w(TAG, "onReceive(): buildNotification returned null for id=" + data.getId());
        }
    }
}
