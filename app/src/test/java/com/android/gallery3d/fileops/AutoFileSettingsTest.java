package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class AutoFileSettingsTest {

    /** In-memory Store so the settings logic is testable without Android. */
    private static final class MapStore implements AutoFileSettings.Store {
        final Map<String, Object> values = new HashMap<String, Object>();

        @Override
        public boolean getBoolean(String key, boolean fallback) {
            Object v = values.get(key);
            return v == null ? fallback : (Boolean) v;
        }

        @Override
        public String getString(String key, String fallback) {
            Object v = values.get(key);
            return v == null ? fallback : (String) v;
        }

        @Override
        public int getInt(String key, int fallback) {
            Object v = values.get(key);
            return v == null ? fallback : (Integer) v;
        }

        @Override
        public void putBoolean(String key, boolean value) {
            values.put(key, value);
        }

        @Override
        public void putString(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void putInt(String key, int value) {
            values.put(key, value);
        }
    }

    private MapStore store;
    private AutoFileSettings settings;

    @Before
    public void setUp() {
        store = new MapStore();
        settings = new AutoFileSettings(store);
    }

    @Test
    public void autoFileIsOffUntilTheUserTurnsItOn() {
        assertFalse("the rule must be opt-in", settings.isEnabled());
    }

    @Test
    public void theDefaultWatchedFolderIsTheCameraFolder() {
        assertEquals("DCIM/Camera/", settings.watchedFolder());
    }

    @Test
    public void theDefaultDelayIsFiveMinutes() {
        assertEquals(5, settings.delayMinutes());
        assertEquals(5L * 60L * 1000L, settings.delayMillis());
    }

    @Test
    public void turningItOnAndOffRoundTrips() {
        settings.setEnabled(true);
        assertTrue(settings.isEnabled());

        settings.setEnabled(false);
        assertFalse(settings.isEnabled());
    }

    @Test
    public void theWatchedFolderIsStoredCanonically() {
        settings.setWatchedFolder("/DCIM//Screenshots");

        assertEquals("DCIM/Screenshots/", settings.watchedFolder());
    }

    @Test
    public void theDelayIsClampedToSaneBounds() {
        settings.setDelayMinutes(0);
        assertEquals("zero would defeat the purpose of the delay", 1, settings.delayMinutes());

        settings.setDelayMinutes(10_000);
        assertEquals(1440, settings.delayMinutes());
    }

    // --- Launch crash: the stored type must never decide whether the app opens ---

    @Test
    public void aDelayStoredAsAStringByTheSettingsScreenIsReadNotCast() {
        // Exactly what the EditTextPreference left on the user's phone. The old
        // getInt read threw ClassCastException from Application.onCreate.
        store.values.put(AutoFileSettings.KEY_ENABLED, true);
        store.values.put(AutoFileSettings.KEY_DELAY_MINUTES, "5");

        assertEquals(5, settings.delayMinutes());
        assertEquals(5L * 60L * 1000L, settings.delayMillis());
        assertEquals(150_000L, AutoFileReceiver.checkIntervalMillis(settings));
    }

    @Test
    public void aDelayStoredAsAnIntByAnOlderBuildStillReads() {
        store.values.put(AutoFileSettings.KEY_DELAY_MINUTES, 12);

        assertEquals(12, settings.delayMinutes());
    }

    @Test
    public void aDelayOfAnyOtherTypeFallsBackToTheDefault() {
        store.values.put(AutoFileSettings.KEY_DELAY_MINUTES, 3.5f);

        assertEquals(AutoFileSettings.DEFAULT_DELAY_MINUTES, settings.delayMinutes());
    }

    @Test
    public void typedDelaysAreParsedWithDefaultsAndClamped() {
        assertEquals(5, AutoFileSettings.parseDelayMinutes(null));
        assertEquals(5, AutoFileSettings.parseDelayMinutes(""));
        assertEquals(5, AutoFileSettings.parseDelayMinutes("   "));
        assertEquals(5, AutoFileSettings.parseDelayMinutes("five"));
        assertEquals(5, AutoFileSettings.parseDelayMinutes("2.5"));
        assertEquals(7, AutoFileSettings.parseDelayMinutes(" 7 "));
        assertEquals(1, AutoFileSettings.parseDelayMinutes("0"));
        assertEquals(1, AutoFileSettings.parseDelayMinutes("-3"));
        assertEquals(1440, AutoFileSettings.parseDelayMinutes("99999"));
        assertEquals(1440, AutoFileSettings.parseDelayMinutes("99999999999999"));
        assertEquals(5, AutoFileSettings.parseDelayMinutes("999999999999999999999999"));
    }

    @Test
    public void theDelayIsWrittenAsAStringSoTheSettingsScreenCanReadItBack() {
        settings.setDelayMinutes(9);

        assertEquals("9", store.values.get(AutoFileSettings.KEY_DELAY_MINUTES));
    }

    @Test
    public void aWrongTypedOrBlankWatchedFolderFallsBackToTheCameraFolder() {
        store.values.put(AutoFileSettings.KEY_WATCHED_FOLDER, 42);
        assertEquals("DCIM/Camera/", settings.watchedFolder());

        store.values.put(AutoFileSettings.KEY_WATCHED_FOLDER, "  /  ");
        assertEquals("DCIM/Camera/", settings.watchedFolder());
    }

    @Test
    public void aWrongTypedEnabledFlagIsReadSafely() {
        store.values.put(AutoFileSettings.KEY_ENABLED, "true");
        assertTrue(settings.isEnabled());

        store.values.put(AutoFileSettings.KEY_ENABLED, 1);
        assertFalse(settings.isEnabled());
    }

    @Test
    public void repairRewritesEveryValueToTheTypeItsWidgetCasts() {
        store.values.put(AutoFileSettings.KEY_ENABLED, "true");
        store.values.put(AutoFileSettings.KEY_WATCHED_FOLDER, 7);
        store.values.put(AutoFileSettings.KEY_DELAY_MINUTES, 12);

        settings.repairStoredTypes();

        assertEquals(Boolean.TRUE, store.values.get(AutoFileSettings.KEY_ENABLED));
        assertEquals("DCIM/Camera/", store.values.get(AutoFileSettings.KEY_WATCHED_FOLDER));
        assertEquals("12", store.values.get(AutoFileSettings.KEY_DELAY_MINUTES));
    }

    @Test
    public void repairLeavesCorrectAndAbsentValuesAlone() {
        store.values.put(AutoFileSettings.KEY_DELAY_MINUTES, "5");

        settings.repairStoredTypes();

        assertEquals("5", store.values.get(AutoFileSettings.KEY_DELAY_MINUTES));
        assertFalse(store.values.containsKey(AutoFileSettings.KEY_ENABLED));
        assertFalse(store.values.containsKey(AutoFileSettings.KEY_WATCHED_FOLDER));
    }
}
