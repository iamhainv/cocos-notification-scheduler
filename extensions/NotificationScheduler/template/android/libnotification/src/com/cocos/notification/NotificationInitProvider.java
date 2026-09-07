package com.cocos.notification;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Initialisation trigger for the NotificationScheduler extension.
 *
 * <p>Android launches ContentProviders before {@code Application.onCreate()},
 * which guarantees the {@link NotificationBridge} JsbBridgeWrapper listener is
 * registered before the Cocos JS engine begins executing.  This mirrors the
 * pattern used by the sibling {@code ShareInitProvider} in {@code libshare}.</p>
 *
 * <p>All ContentProvider query methods are no-ops — this provider is never
 * queried; it is only used for its lifecycle hook.</p>
 */
public class NotificationInitProvider extends ContentProvider {
    private static final String TAG = "cocos::NotificationInitProvider";

    @Override
    public boolean onCreate() {
        Log.d(TAG, "onCreate(): registering NotificationBridge.");
        NotificationBridge.register(getContext());
        return true;
    }

    @Override
    public @Nullable Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                                  @Nullable String selection, @Nullable String[] selectionArgs,
                                  @Nullable String sortOrder) {
        return null;
    }

    @Override
    public @Nullable String getType(@NonNull Uri uri) {
        return null;
    }

    @Override
    public @Nullable Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
                      @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
