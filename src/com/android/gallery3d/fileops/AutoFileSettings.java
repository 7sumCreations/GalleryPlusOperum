package com.android.gallery3d.fileops;

import android.content.Context;
import android.content.SharedPreferences;

/** The auto-file rule: on/off, which folder to watch, and how long to wait. */
public final class AutoFileSettings {

    public static final String PREFS_NAME = "auto_file";
    public static final String KEY_ENABLED = "auto_file_enabled";
    public static final String KEY_WATCHED_FOLDER = "auto_file_watched_folder";
    public static final String KEY_DELAY_MINUTES = "auto_file_delay_minutes";

    /** Opt-in: nothing moves automatically until the user says so. */
    public static final boolean DEFAULT_ENABLED = false;
    public static final String DEFAULT_WATCHED_FOLDER = "DCIM/Camera/";
    /** Long enough for a backup app watching DCIM/Camera to finish with the file. */
    public static final int DEFAULT_DELAY_MINUTES = 5;

    private static final int MIN_DELAY_MINUTES = 1;
    private static final int MAX_DELAY_MINUTES = 1440;   // one day

    /** Test seam so the logic here does not need an Android context. */
    public interface Store {
        boolean getBoolean(String key, boolean fallback);

        String getString(String key, String fallback);

        int getInt(String key, int fallback);

        void putBoolean(String key, boolean value);

        void putString(String key, String value);

        void putInt(String key, int value);
    }

    private final Store mStore;

    public AutoFileSettings(Store store) {
        mStore = store;
    }

    public static AutoFileSettings from(Context context) {
        final SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return new AutoFileSettings(new Store() {
            @Override
            public boolean getBoolean(String key, boolean fallback) {
                return prefs.getBoolean(key, fallback);
            }

            @Override
            public String getString(String key, String fallback) {
                return prefs.getString(key, fallback);
            }

            @Override
            public int getInt(String key, int fallback) {
                return prefs.getInt(key, fallback);
            }

            @Override
            public void putBoolean(String key, boolean value) {
                prefs.edit().putBoolean(key, value).apply();
            }

            @Override
            public void putString(String key, String value) {
                prefs.edit().putString(key, value).apply();
            }

            @Override
            public void putInt(String key, int value) {
                prefs.edit().putInt(key, value).apply();
            }
        });
    }

    public boolean isEnabled() {
        return mStore.getBoolean(KEY_ENABLED, DEFAULT_ENABLED);
    }

    public void setEnabled(boolean enabled) {
        mStore.putBoolean(KEY_ENABLED, enabled);
    }

    public String watchedFolder() {
        return RelativePaths.normalise(
                mStore.getString(KEY_WATCHED_FOLDER, DEFAULT_WATCHED_FOLDER));
    }

    public void setWatchedFolder(String relativePath) {
        mStore.putString(KEY_WATCHED_FOLDER, RelativePaths.normalise(relativePath));
    }

    public int delayMinutes() {
        return clamp(mStore.getInt(KEY_DELAY_MINUTES, DEFAULT_DELAY_MINUTES));
    }

    public void setDelayMinutes(int minutes) {
        mStore.putInt(KEY_DELAY_MINUTES, clamp(minutes));
    }

    public long delayMillis() {
        return delayMinutes() * 60L * 1000L;
    }

    private static int clamp(int minutes) {
        if (minutes < MIN_DELAY_MINUTES) return MIN_DELAY_MINUTES;
        if (minutes > MAX_DELAY_MINUTES) return MAX_DELAY_MINUTES;
        return minutes;
    }
}
