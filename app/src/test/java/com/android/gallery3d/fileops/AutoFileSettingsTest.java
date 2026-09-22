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
}
