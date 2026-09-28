/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.gallery3d.settings;

import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.fileops.AutoFileReceiver;
import com.android.gallery3d.fileops.AutoFileSettings;

public class GallerySettings extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "GallerySettings";

    /** Category holding the standing media-management grant (API 31+ only). */
    static final String KEY_MEDIA_ACCESS_CATEGORY = "media_access_category";
    /** Non-persistent entry that shows the real grant and opens system settings. */
    static final String KEY_MEDIA_ACCESS_MANAGE = "media_access_manage";

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(AutoFileSettings.PREFS_NAME);
        addPreferencesFromResource(R.xml.auto_file_preferences);
        findPreference(AutoFileSettings.KEY_ENABLED).setOnPreferenceChangeListener(this);
        findPreference(AutoFileSettings.KEY_DELAY_MINUTES).setOnPreferenceChangeListener(this);
        setUpMediaAccess();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user may have just changed the grant in system settings.
        refreshMediaAccessSummary();
    }

    /** MediaStore.canManageMedia and ACTION_REQUEST_MANAGE_MEDIA arrived in API 31. */
    static boolean isMediaAccessSupported(int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.S;
    }

    /** Summary for the current grant; the system is the only source of truth. */
    static int mediaAccessSummaryRes(boolean allowed) {
        return allowed
                ? R.string.media_access_manage_summary_allowed
                : R.string.media_access_manage_summary_not_allowed;
    }

    @SuppressWarnings("deprecation")
    private void setUpMediaAccess() {
        Preference category = findPreference(KEY_MEDIA_ACCESS_CATEGORY);
        if (!isMediaAccessSupported(Build.VERSION.SDK_INT)) {
            // Nothing to grant below Android 12, so show nothing at all.
            if (category != null) getPreferenceScreen().removePreference(category);
            return;
        }
        Preference manage = findPreference(KEY_MEDIA_ACCESS_MANAGE);
        if (manage == null) return;
        manage.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                openManageMediaSettings();
                return true;
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void refreshMediaAccessSummary() {
        if (!isMediaAccessSupported(Build.VERSION.SDK_INT)) return;
        Preference manage = findPreference(KEY_MEDIA_ACCESS_MANAGE);
        if (manage == null) return;
        boolean allowed;
        try {
            allowed = canManageMedia(this);
        } catch (RuntimeException e) {
            Log.w(TAG, "canManageMedia failed", e);
            allowed = false;
        }
        manage.setSummary(mediaAccessSummaryRes(allowed));
    }

    @TargetApi(Build.VERSION_CODES.S)
    private static boolean canManageMedia(Context context) {
        return MediaStore.canManageMedia(context);
    }

    /**
     * Opens the system page where the user grants or revokes the standing
     * grant. Only ever called from a tap in this screen; never prompted
     * anywhere else. Exceptions must not escape a click handler.
     */
    private void openManageMediaSettings() {
        Uri packageUri = Uri.fromParts("package", getPackageName(), null);
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, packageUri));
            return;
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "manage media settings unavailable", e);
        } catch (RuntimeException e) {
            // SecurityException and friends from a vendor Settings build.
            Log.w(TAG, "manage media settings failed", e);
        }
        try {
            // Fallback: the app's own details page, where special access is listed.
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri));
        } catch (RuntimeException e) {
            Log.w(TAG, "app details settings failed", e);
            Toast.makeText(this, R.string.media_access_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        // Re-arm or tear down the alarm the moment the rule changes, so turning
        // it off really does stop every automatic move.
        if (AutoFileSettings.KEY_ENABLED.equals(preference.getKey())) {
            if (Boolean.TRUE.equals(newValue)) {
                AutoFileSettings.from(this).setEnabled(true);
                AutoFileReceiver.schedule(this);
            } else {
                AutoFileSettings.from(this).setEnabled(false);
                AutoFileReceiver.cancel(this);
            }
            return true;
        }
        if (AutoFileSettings.KEY_DELAY_MINUTES.equals(preference.getKey())) {
            try {
                AutoFileSettings.from(this)
                        .setDelayMinutes(Integer.parseInt(String.valueOf(newValue)));
            } catch (NumberFormatException notANumber) {
                return false;
            }
            AutoFileReceiver.schedule(this);
            return true;
        }
        return true;
    }
}
