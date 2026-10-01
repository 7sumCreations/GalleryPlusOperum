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

    /*
     * Every read below tolerates a value of the wrong type. GallerySettings binds
     * these keys to stock preference widgets, and an EditTextPreference always
     * persists a String; an earlier build wrote the delay with putInt and read it
     * with getInt, and the resulting ClassCastException in Application.onCreate
     * stopped the app from launching at all. A bad stored value must degrade to
     * the default, never throw.
     */

    public boolean isEnabled() {
        try {
            return mStore.getBoolean(KEY_ENABLED, DEFAULT_ENABLED);
        } catch (ClassCastException wrongType) {
            try {
                return parseEnabled(mStore.getString(KEY_ENABLED, null));
            } catch (ClassCastException stillWrong) {
                return DEFAULT_ENABLED;
            }
        }
    }

    public void setEnabled(boolean enabled) {
        mStore.putBoolean(KEY_ENABLED, enabled);
    }

    public String watchedFolder() {
        String raw;
        try {
            raw = mStore.getString(KEY_WATCHED_FOLDER, DEFAULT_WATCHED_FOLDER);
        } catch (ClassCastException wrongType) {
            raw = DEFAULT_WATCHED_FOLDER;
        }
        return parseWatchedFolder(raw);
    }

    public void setWatchedFolder(String relativePath) {
        mStore.putString(KEY_WATCHED_FOLDER, RelativePaths.normalise(relativePath));
    }

    public int delayMinutes() {
        try {
            // What the EditTextPreference in GallerySettings writes.
            return parseDelayMinutes(mStore.getString(KEY_DELAY_MINUTES, null));
        } catch (ClassCastException notAString) {
            try {
                // What builds before the launch-crash fix wrote.
                return clamp(mStore.getInt(KEY_DELAY_MINUTES, DEFAULT_DELAY_MINUTES));
            } catch (ClassCastException notAnIntEither) {
                return DEFAULT_DELAY_MINUTES;
            }
        }
    }

    /** Stored as a String, because that is what the Settings screen reads back. */
    public void setDelayMinutes(int minutes) {
        mStore.putString(KEY_DELAY_MINUTES, String.valueOf(clamp(minutes)));
    }

    public long delayMillis() {
        return delayMinutes() * 60L * 1000L;
    }

    /**
     * Rewrites any value whose stored type does not match the preference widget
     * bound to it (Boolean for the switch, String for both text fields), so the
     * Settings screen cannot throw ClassCastException while inflating. Keys that
     * are absent stay absent. Never throws.
     */
    public void repairStoredTypes() {
        try {
            mStore.getBoolean(KEY_ENABLED, DEFAULT_ENABLED);
        } catch (ClassCastException wrongType) {
            mStore.putBoolean(KEY_ENABLED, isEnabled());
        }
        try {
            mStore.getString(KEY_WATCHED_FOLDER, DEFAULT_WATCHED_FOLDER);
        } catch (ClassCastException wrongType) {
            mStore.putString(KEY_WATCHED_FOLDER, DEFAULT_WATCHED_FOLDER);
        }
        try {
            mStore.getString(KEY_DELAY_MINUTES, null);
        } catch (ClassCastException wrongType) {
            mStore.putString(KEY_DELAY_MINUTES, String.valueOf(delayMinutes()));
        }
    }

    /** Parses a typed delay; blank, non-numeric or absurd input falls back or clamps. */
    public static int parseDelayMinutes(String raw) {
        if (raw == null) return DEFAULT_DELAY_MINUTES;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return DEFAULT_DELAY_MINUTES;
        long value;
        try {
            value = Long.parseLong(trimmed);
        } catch (NumberFormatException notANumber) {
            return DEFAULT_DELAY_MINUTES;
        }
        if (value < MIN_DELAY_MINUTES) return MIN_DELAY_MINUTES;
        if (value > MAX_DELAY_MINUTES) return MAX_DELAY_MINUTES;
        return (int) value;
    }

    /** A blank folder would match nothing useful, so it means the default. */
    public static String parseWatchedFolder(String raw) {
        String normalised = RelativePaths.normalise(raw);
        return normalised.isEmpty()
                ? RelativePaths.normalise(DEFAULT_WATCHED_FOLDER) : normalised;
    }

    static boolean parseEnabled(String raw) {
        if (raw == null) return DEFAULT_ENABLED;
        return Boolean.parseBoolean(raw.trim());
    }

    private static int clamp(int minutes) {
        if (minutes < MIN_DELAY_MINUTES) return MIN_DELAY_MINUTES;
        if (minutes > MAX_DELAY_MINUTES) return MAX_DELAY_MINUTES;
        return minutes;
    }
}
