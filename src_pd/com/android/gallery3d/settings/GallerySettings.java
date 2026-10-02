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
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.fileops.AutoFileReceiver;
import com.android.gallery3d.fileops.AutoFileRunNow;
import com.android.gallery3d.fileops.AutoFileSettings;
import com.android.gallery3d.help.HelpActivity;

public class GallerySettings extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "GallerySettings";

    /** Category holding the standing media-management grant (API 31+ only). */
    static final String KEY_MEDIA_ACCESS_CATEGORY = "media_access_category";
    /** Non-persistent entry that shows the real grant and opens system settings. */
    static final String KEY_MEDIA_ACCESS_MANAGE = "media_access_manage";
    /** Non-persistent entry that opens the in-app Help screen. */
    static final String KEY_HELP_OPEN = "help_open";
    /** Category holding the Auto-file switch and its settings. */
    static final String KEY_AUTO_FILE_CATEGORY = "auto_file_category";
    /** Note under the Auto-file switch while camera photos cannot be filed. */
    static final String KEY_AUTO_FILE_PERMISSION_NOTE = "auto_file_permission_note";
    /** Non-persistent entry that runs one Auto-file check now. */
    static final String KEY_AUTO_FILE_RUN_NOW = "auto_file_run_now";

    /** Held while removed from the screen, so it can be put back in its place. */
    private Preference mPermissionNote;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(AutoFileSettings.PREFS_NAME);
        // The widgets below cast what they read back; fix any value of the wrong
        // type (e.g. a delay an older build stored as an int) before inflating.
        try {
            AutoFileSettings.from(this).repairStoredTypes();
        } catch (RuntimeException e) {
            Log.w(TAG, "could not repair auto-file preferences", e);
        }
        addPreferencesFromResource(R.xml.auto_file_preferences);
        findPreference(AutoFileSettings.KEY_ENABLED).setOnPreferenceChangeListener(this);
        findPreference(AutoFileSettings.KEY_DELAY_MINUTES).setOnPreferenceChangeListener(this);
        setUpMediaAccess();
        setUpPermissionNote();
        setUpRunNow();
        setUpHelp();
    }

    @SuppressWarnings("deprecation")
    private void setUpRunNow() {
        Preference runNow = findPreference(KEY_AUTO_FILE_RUN_NOW);
        if (runNow == null) return;
        runNow.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // A click handler must never let an exception escape.
                try {
                    final Context app = getApplicationContext();
                    AutoFileReceiver.runNow(app, new AutoFileReceiver.RunListener() {
                        @Override
                        public void onRunFinished(boolean enabled,
                                AutoFileReceiver.RunOutcome outcome) {
                            Toast.makeText(app, runNowMessage(app, enabled, outcome),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (RuntimeException e) {
                    Log.w(TAG, "could not run auto-file now", e);
                }
                return true;
            }
        });
    }

    /** The Toast after "File now". */
    static String runNowMessage(Context context, boolean enabled,
            AutoFileReceiver.RunOutcome outcome) {
        Resources res = context.getResources();
        int moved = outcome == null ? -1 : outcome.moved;
        int need = outcome == null ? 0 : outcome.needPermission;
        switch (AutoFileRunNow.kindOf(enabled, moved, need)) {
            case OFF:
                return res.getString(R.string.auto_file_run_now_off);
            case FILED:
                return res.getQuantityString(R.plurals.auto_file_run_now_filed, moved, moved);
            case FILED_AND_NEED_PERMISSION:
                return res.getQuantityString(R.plurals.auto_file_run_now_filed, moved, moved)
                        + "\n" + res.getQuantityString(
                                R.plurals.auto_file_run_now_need_permission, need, need);
            case NEED_PERMISSION:
                return res.getQuantityString(
                        R.plurals.auto_file_run_now_need_permission, need, need);
            case NOTHING_YET:
                int minutes;
                try {
                    minutes = AutoFileSettings.from(context).delayMinutes();
                } catch (RuntimeException e) {
                    minutes = AutoFileSettings.DEFAULT_DELAY_MINUTES;
                }
                return res.getQuantityString(R.plurals.auto_file_run_now_nothing,
                        minutes, minutes);
            case FAILED:
            default:
                return res.getString(R.string.auto_file_run_now_failed);
        }
    }

    /**
     * Camera photos belong to the Camera app, so moving one in the background
     * needs "Manage media without asking". Say so under the switch, honestly,
     * whenever Auto-file is on without it. Never requests the grant itself.
     */
    static boolean shouldShowPermissionNote(int sdkInt, boolean autoFileOn,
            boolean canManageMedia) {
        return isMediaAccessSupported(sdkInt) && autoFileOn && !canManageMedia;
    }

    @SuppressWarnings("deprecation")
    private void setUpPermissionNote() {
        mPermissionNote = findPreference(KEY_AUTO_FILE_PERMISSION_NOTE);
        if (mPermissionNote == null) return;
        mPermissionNote.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        openManageMediaSettings();
                        return true;
                    }
                });
        refreshPermissionNote(isAutoFileOn());
    }

    private boolean isAutoFileOn() {
        try {
            return AutoFileSettings.from(this).isEnabled();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private void refreshPermissionNote(boolean autoFileOn) {
        if (mPermissionNote == null) return;
        try {
            boolean allowed = isMediaAccessSupported(Build.VERSION.SDK_INT)
                    && canManageMedia(this);
            boolean show = shouldShowPermissionNote(Build.VERSION.SDK_INT, autoFileOn, allowed);
            Preference category = findPreference(KEY_AUTO_FILE_CATEGORY);
            if (!(category instanceof PreferenceGroup)) return;
            PreferenceGroup group = (PreferenceGroup) category;
            boolean shown = group.findPreference(KEY_AUTO_FILE_PERMISSION_NOTE) != null;
            // The note keeps the order it was inflated with, so re-adding it
            // puts it straight back under the switch.
            if (show && !shown) group.addPreference(mPermissionNote);
            if (!show && shown) group.removePreference(mPermissionNote);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not refresh the auto-file permission note", e);
        }
    }

    @SuppressWarnings("deprecation")
    private void setUpHelp() {
        Preference help = findPreference(KEY_HELP_OPEN);
        if (help == null) return;
        help.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // A click handler must never let an exception escape.
                try {
                    startActivity(new Intent(GallerySettings.this, HelpActivity.class));
                } catch (RuntimeException e) {
                    Log.w(TAG, "could not open help", e);
                    Toast.makeText(GallerySettings.this, R.string.help_unavailable,
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user may have just changed the grant in system settings.
        refreshMediaAccessSummary();
        refreshPermissionNote(isAutoFileOn());
    }

    /** MediaStore.canManageMedia and ACTION_REQUEST_MANAGE_MEDIA arrived in API 31. */
    public static boolean isMediaAccessSupported(int sdkInt) {
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
        // it off really does stop every automatic move. Nothing in here may
        // throw: a crash in a preference listener takes the whole app down.
        try {
            if (AutoFileSettings.KEY_ENABLED.equals(preference.getKey())) {
                if (Boolean.TRUE.equals(newValue)) {
                    AutoFileSettings.from(this).setEnabled(true);
                    AutoFileReceiver.schedule(this);
                } else {
                    AutoFileSettings.from(this).setEnabled(false);
                    AutoFileReceiver.cancel(this);
                }
                refreshPermissionNote(Boolean.TRUE.equals(newValue));
                return true;
            }
            if (AutoFileSettings.KEY_DELAY_MINUTES.equals(preference.getKey())) {
                String typed = newValue == null ? "" : String.valueOf(newValue).trim();
                if (typed.isEmpty()) return false;
                try {
                    Long.parseLong(typed);
                } catch (NumberFormatException notANumber) {
                    return false;
                }
                // The widget persists the typed String once we return true;
                // AutoFileSettings clamps it whenever it is read.
                AutoFileSettings.from(this)
                        .setDelayMinutes(AutoFileSettings.parseDelayMinutes(typed));
                AutoFileReceiver.schedule(this);
                return true;
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "auto-file preference change failed", e);
        }
        return true;
    }
}
