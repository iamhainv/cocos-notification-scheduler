package com.cocos.notification;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.cocos.notification.model.NotificationData;

/**
 * Transparent trampoline activity launched when the user taps a notification.
 *
 * <p>The notification's {@code contentIntent} points to this activity.  It
 * copies the notification extras into the main game launch intent, starts (or
 * resumes) the game, then finishes itself, leaving no footprint in the task
 * stack.  This mirrors {@code ResultActivity} in the original Godot plugin.</p>
 *
 * <p>If a {@code deeplink} extra is present the launch intent's data URI is
 * set so the game can route accordingly.  If {@code restartApp} is set the
 * game is restarted with a clear task.</p>
 */
public class ResultActivity extends Activity {
    private static final String TAG = "cocos::ResultActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent thisIntent = getIntent();
        Intent gameIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());

        if (gameIntent == null) {
            Log.e(TAG, "onCreate(): could not resolve launch intent for package "
                    + getPackageName() + ". Aborting.");
            finish();
            return;
        }

        // Copy all notification extras into the game launch intent
        gameIntent.putExtras(thisIntent);

        NotificationData data = new NotificationData(thisIntent);

        if (data.hasRestartApp()) {
            gameIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        } else {
            gameIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        if (data.hasDeeplink()) {
            gameIntent.setData(Uri.parse(data.getDeeplink()));
        }

        Log.i(TAG, "onCreate(): launching game with notificationId=" + data.getId());
        startActivity(gameIntent);

        if (data.isValid()) {
            NotificationServiceHub.handleNotificationOpened(data);
        } else {
            Log.w(TAG, "onCreate(): notification data invalid; not emitting opened event.");
        }

        finish();
    }
}
