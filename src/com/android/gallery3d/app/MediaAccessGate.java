/*
 * Copyright (C) 2026 The Gallery2 fork authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import com.android.gallery3d.R;

import java.util.ArrayList;

/**
 * Asks for photo and video access (and, on Android 13+, notifications) when
 * the gallery browses the library, and explains calmly when access is missing.
 * Decisions come from {@link MediaAccessPolicy}; this class only talks to the
 * platform. Every entry point swallows RuntimeExceptions: a crash here would
 * be a crash at launch, every launch.
 */
final class MediaAccessGate {
    private static final String TAG = "MediaAccessGate";

    static final int REQUEST_CODE = 0x4d41; // "MA"

    private static final String PREFS = "media_access";
    private static final String KEY_READ_ASKED = "read_asked";
    private static final String KEY_NOTIFICATIONS_ASKED = "notifications_asked";
    private static final String STATE_WAS_MISSING = "media_access_was_missing";

    private static final String POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";

    private final Activity mActivity;
    private final Runnable mOnAccessGained;
    /** The system dialog is up; don't evaluate (or ask) again until it answers. */
    private boolean mInFlight;
    /** This screen has run without access, so its albums were loaded empty. */
    private boolean mWasMissing;
    private AlertDialog mExplanation;

    MediaAccessGate(Activity activity, Runnable onAccessGained) {
        mActivity = activity;
        mOnAccessGained = onAccessGained;
    }

    void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;
        mWasMissing = savedInstanceState.getBoolean(STATE_WAS_MISSING, false);
    }

    void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_WAS_MISSING, mWasMissing);
    }

    void onResume() {
        try {
            check();
        } catch (RuntimeException e) {
            Log.w(TAG, "media access check failed", e);
        }
    }

    void onPause() {
        // Re-shown on the next resume if access is still missing (for
        // example after a trip to Settings).
        dismissExplanation();
    }

    /**
     * @param answered how many permissions came back. Zero means the request
     *        was cancelled or refused because another one was showing; the
     *        next resume re-checks, so don't re-check (and maybe re-ask) here.
     * @return true if the result was this gate's request.
     */
    boolean onRequestPermissionsResult(int requestCode, int answered) {
        if (requestCode != REQUEST_CODE) return false;
        mInFlight = false;
        if (answered > 0) onResume();
        return true;
    }

    private void check() {
        if (mInFlight || mActivity.isFinishing()) return;
        int sdk = Build.VERSION.SDK_INT;
        String[] read = MediaAccessPolicy.readPermissions(sdk);
        boolean granted = anyGranted(read);
        MediaAccessPolicy.Action action = MediaAccessPolicy.decide(
                granted, readPref(KEY_READ_ASKED), anyRationale(read));
        boolean askNotifications = MediaAccessPolicy.shouldAskNotifications(sdk,
                sdk >= 33 && isGranted(POST_NOTIFICATIONS),
                readPref(KEY_NOTIFICATIONS_ASKED));

        switch (action) {
            case SHOW:
                dismissExplanation();
                if (mWasMissing) {
                    mWasMissing = false;
                    mOnAccessGained.run();
                }
                if (askNotifications) request(new String[0], true);
                break;
            case REQUEST:
                mWasMissing = true;
                request(read, askNotifications);
                break;
            case EXPLAIN_CAN_RETRY:
            case EXPLAIN_OPEN_SETTINGS:
                mWasMissing = true;
                showExplanation(action == MediaAccessPolicy.Action.EXPLAIN_CAN_RETRY);
                break;
        }
    }

    private void request(String[] read, boolean withNotifications) {
        ArrayList<String> perms = new ArrayList<String>();
        for (String p : read) perms.add(p);
        if (withNotifications) perms.add(POST_NOTIFICATIONS);
        if (perms.isEmpty()) return;
        // Record the ask first, so a denial with no rationale reads as
        // "don't ask again" rather than "never asked" (which would loop).
        SharedPreferences.Editor editor = prefs().edit();
        if (read.length > 0) editor.putBoolean(KEY_READ_ASKED, true);
        if (withNotifications) editor.putBoolean(KEY_NOTIFICATIONS_ASKED, true);
        editor.apply();
        mInFlight = true;
        try {
            mActivity.requestPermissions(perms.toArray(new String[perms.size()]), REQUEST_CODE);
        } catch (RuntimeException e) {
            mInFlight = false;
            throw e;
        }
    }

    private void showExplanation(boolean canRetry) {
        if (mExplanation != null && mExplanation.isShowing()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity)
                .setTitle(R.string.media_access_needed_title)
                .setMessage(canRetry
                        ? R.string.media_access_needed_message
                        : R.string.media_access_needed_message_settings)
                .setNegativeButton(R.string.media_access_not_now, null);
        DialogInterface.OnClickListener openSettings = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                openAppSettings();
            }
        };
        if (canRetry) {
            builder.setPositiveButton(R.string.media_access_allow,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                request(MediaAccessPolicy.readPermissions(
                                        Build.VERSION.SDK_INT), false);
                            } catch (RuntimeException e) {
                                Log.w(TAG, "permission request failed", e);
                            }
                        }
                    });
            builder.setNeutralButton(R.string.media_access_open_settings, openSettings);
        } else {
            builder.setPositiveButton(R.string.media_access_open_settings, openSettings);
        }
        mExplanation = builder.show();
    }

    private void dismissExplanation() {
        if (mExplanation == null) return;
        try {
            mExplanation.dismiss();
        } catch (RuntimeException e) {
            Log.w(TAG, "dismiss failed", e);
        }
        mExplanation = null;
    }

    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", mActivity.getPackageName(), null));
            mActivity.startActivity(intent);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not open app settings", e);
            Toast.makeText(mActivity, R.string.media_access_open_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean anyGranted(String[] perms) {
        for (String p : perms) {
            if (isGranted(p)) return true;
        }
        return false;
    }

    private boolean isGranted(String perm) {
        return mActivity.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean anyRationale(String[] perms) {
        for (String p : perms) {
            if (mActivity.shouldShowRequestPermissionRationale(p)) return true;
        }
        return false;
    }

    private SharedPreferences prefs() {
        return mActivity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private boolean readPref(String key) {
        try {
            return prefs().getBoolean(key, false);
        } catch (RuntimeException e) {
            // A wrongly typed value must not crash the launch; treat as asked
            // so the user gets the settings explanation, never a dialog loop.
            Log.w(TAG, "unreadable pref " + key, e);
            return true;
        }
    }
}
