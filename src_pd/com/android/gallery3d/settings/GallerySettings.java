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

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;

import com.android.gallery3d.R;
import com.android.gallery3d.fileops.AutoFileReceiver;
import com.android.gallery3d.fileops.AutoFileSettings;

public class GallerySettings extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {

    @SuppressWarnings("unused")
    private static final String TAG = "GallerySettings";

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(AutoFileSettings.PREFS_NAME);
        addPreferencesFromResource(R.xml.auto_file_preferences);
        findPreference(AutoFileSettings.KEY_ENABLED).setOnPreferenceChangeListener(this);
        findPreference(AutoFileSettings.KEY_DELAY_MINUTES).setOnPreferenceChangeListener(this);
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
