package com.cocos.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.cocos.notification.model.NotificationData;

/**
 * BroadcastReceiver that fires when the user dismisses (swipes away) a
 * notification.
 *
 * <p>The receiver is set as the {@code deleteIntent} on each notification built
 * by {@link NotificationData#buildNotification(Context)}.  It forwards the
 * dismiss event to {@link NotificationServiceHub#handleNotificationDismissed},
 * which either emits a {@code notificationDismissed} event to JS immediately
 * (if the JS layer is ready) or persists it for later delivery.</p>
 */
public class CancelNotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "cocos::CancelNotificationReceiver";

    public CancelNotificationReceiver() {}

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
        Log.d(TAG, "onReceive(): dismissed notificationId=" + data.getId());
        NotificationServiceHub.handleNotificationDismissed(context, data);
    }
}
