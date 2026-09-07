/*
 * PermissionRequestActivity — transparent trampoline that calls
 * ActivityCompat.requestPermissions() for POST_NOTIFICATIONS (Android 13+,
 * API 33+) and reports the result back to {@link NotificationServiceHub} via
 * its static callback {@link NotificationServiceHub#onPostNotifPermResult}.
 *
 * Cocos Creator extensions do not have an {@code onRequestPermissionsResult}
 * hook, so this transparent Activity acts as the surrogate requester.
 * It is declared in the library's AndroidManifest.xml with
 * {@code Theme.Translucent.NoTitleBar} and {@code excludeFromRecents="true"}.
 *
 * MIT License
 */
package com.cocos.notification;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;

/**
 * Transparent Activity that requests the {@link android.Manifest.permission#POST_NOTIFICATIONS}
 * permission on behalf of the game when running on Android 13+ (API 33+).
 *
 * <p>Launch contract (set by {@link NotificationServiceHub#handleRequestPostNotifPerm}):
 * <ul>
 *   <li>No intent extras are required — the permission name is hard-coded to
 *       {@code POST_NOTIFICATIONS} which is the only permission this activity
 *       ever requests.</li>
 *   <li>On result, calls {@link NotificationServiceHub#onPostNotifPermResult(boolean)}
 *       with {@code true} if {@link PackageManager#PERMISSION_GRANTED}, {@code false}
 *       otherwise, then finishes with no enter/exit animation.</li>
 * </ul>
 */
public class PermissionRequestActivity extends Activity {

    /** Request code used for {@link ActivityCompat#requestPermissions}. */
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 1001;

    /**
     * Immediately fires {@link ActivityCompat#requestPermissions} for
     * POST_NOTIFICATIONS, then waits for the system callback.
     *
     * @param savedInstanceState unused — activity has no UI state.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Request POST_NOTIFICATIONS permission (API 33+).
        // The hub already checks Build.VERSION.SDK_INT before launching this
        // activity, so we can assume we are on API 33+ here.
        ActivityCompat.requestPermissions(
            this,
            new String[]{ android.Manifest.permission.POST_NOTIFICATIONS },
            REQUEST_CODE_POST_NOTIFICATIONS
        );
    }

    /**
     * Receives the system grant/deny result, forwards it to the hub's static
     * callback, then finishes this activity with no transition animation.
     *
     * @param requestCode  must equal {@link #REQUEST_CODE_POST_NOTIFICATIONS}.
     * @param permissions  the requested permission names (unused — we only ever
     *                     request POST_NOTIFICATIONS in one shot).
     * @param grantResults one element: {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}.
     */
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;

            // Report result back to hub before finishing, so the pending bridge
            // id is resolved before this activity is destroyed.
            NotificationServiceHub.onPostNotifPermResult(granted);
        }

        // Finish with no enter/exit animation so the user never sees a
        // transparent black flash.
        finish();
        overridePendingTransition(0, 0);
    }
}
