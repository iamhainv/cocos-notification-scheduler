package com.cocos.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Reschedules persisted notification alarms after a device reboot.
 *
 * <p>AlarmManager alarms are lost when the device reboots.  This receiver
 * listens for {@code android.intent.action.BOOT_COMPLETED} and restores all
 * notifications that were saved to SharedPreferences by
 * {@link NotificationServiceHub#saveScheduledNotification}.</p>
 *
 * <p>Requires {@code RECEIVE_BOOT_COMPLETED} permission in the manifest.</p>
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "cocos::BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent != null ? intent.getAction() : null)) {
            Log.i(TAG, "onReceive(): device rebooted; rescheduling notifications.");
            NotificationServiceHub.rescheduleAll(context);
        }
    }
}
