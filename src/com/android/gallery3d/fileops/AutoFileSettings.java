package com.android.gallery3d.fileops;

import android.content.Context;
import android.content.SharedPreferences;

/** The auto-file rule: on/off, which folder to watch, and how long to wait. */
public final class AutoFileSettings {

    public static final String PREFS_NAME = "auto_file";
    public static final String KEY_ENABLED = "auto_file_enabled";
    public static final String KEY_WATCHED_FOLDER = "auto_file_watched_folder";
    public static final String KEY_DELAY_MINUTES = "auto_file_delay_minutes";
    /**
     * When the rule was last switched on, in epoch SECONDS (the unit of
     * MediaStore DATE_ADDED, which is what itemsAddedSince compares against).
     * Stored as a decimal String so it needs no new Store method and a bad
     * value can be parsed tolerantly. Not bound to any Settings widget.
     */
    public static final String KEY_ENABLED_SINCE = "auto_file_enabled_since_seconds";

    /** Returned by enabledSinceSeconds() when no usable timestamp is stored. */
    public static final long NO_TIMESTAMP = -1L;

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
        setEnabled(enabled, System.currentTimeMillis());
    }

    /**
     * Switching the rule on (from off) stamps "enabled since" with now, so only
     * photos added from this moment on are ever auto-filed; the existing camera
     * roll is never touched. Switching off clears the stamp, so off-then-on
     * always starts a fresh window. Turning on a rule that is already on keeps
     * the original stamp, unless it is missing or unusable.
     */
    public void setEnabled(boolean enabled, long nowMillis) {
        if (enabled) {
            if (!isEnabled() || enabledSinceSeconds() == NO_TIMESTAMP) {
                writeEnabledSince(nowMillis);
            }
        } else {
            mStore.putString(KEY_ENABLED_SINCE, "");
        }
        mStore.putBoolean(KEY_ENABLED, enabled);
    }

    /**
     * The stored "enabled since" time in epoch seconds, or NO_TIMESTAMP when it
     * is absent, of the wrong type, non-numeric, or not positive. Never throws,
     * never returns 0: a 0 here would mean "every photo ever".
     */
    public long enabledSinceSeconds() {
        String raw;
        try {
            raw = mStore.getString(KEY_ENABLED_SINCE, null);
        } catch (ClassCastException wrongType) {
            return NO_TIMESTAMP;
        }
        return parseEnabledSince(raw);
    }

    /**
     * The "enabled since" time to filter by, repairing it first when needed:
     * a missing or unusable stamp, or one in the future (the clock went back),
     * is replaced with now and persisted. This is the migration path for
     * phones that had the rule switched on before the stamp existed: they start
     * counting from the first time this runs, so nothing already in the folder
     * moves. Only meaningful while the rule is on. Never throws.
     */
    public long ensureEnabledSinceSeconds(long nowMillis) {
        long nowSeconds = nowMillis / 1000L;
        long since = enabledSinceSeconds();
        if (since == NO_TIMESTAMP || since > nowSeconds) {
            try {
                writeEnabledSince(nowMillis);
            } catch (RuntimeException couldNotPersist) {
                // Still filter by now for this run: never fall back to 0.
            }
            return nowSeconds;
        }
        return since;
    }

    private void writeEnabledSince(long nowMillis) {
        mStore.putString(KEY_ENABLED_SINCE, String.valueOf(nowMillis / 1000L));
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

    static long parseEnabledSince(String raw) {
        if (raw == null) return NO_TIMESTAMP;
        long value;
        try {
            value = Long.parseLong(raw.trim());
        } catch (NumberFormatException notANumber) {
            return NO_TIMESTAMP;
        }
        return value > 0 ? value : NO_TIMESTAMP;
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
