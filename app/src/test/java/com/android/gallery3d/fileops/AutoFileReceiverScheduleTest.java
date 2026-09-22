package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class AutoFileReceiverScheduleTest {

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

    @Test
    public void theCheckIntervalIsHalfTheDelaySoNothingWaitsMuchLongerThanItShould() {
        AutoFileSettings settings = new AutoFileSettings(new MapStore());
        settings.setDelayMinutes(10);

        assertEquals(5L * 60L * 1000L,
                AutoFileReceiver.checkIntervalMillis(settings));
    }

    @Test
    public void theCheckIntervalNeverDropsBelowOneMinute() {
        AutoFileSettings settings = new AutoFileSettings(new MapStore());
        settings.setDelayMinutes(1);

        assertEquals(60L * 1000L, AutoFileReceiver.checkIntervalMillis(settings));
    }

    @Test
    public void theDefaultFiveMinuteDelayChecksEveryTwoAndAHalfMinutes() {
        AutoFileSettings settings = new AutoFileSettings(new MapStore());

        long interval = AutoFileReceiver.checkIntervalMillis(settings);

        assertEquals(150_000L, interval);
        assertTrue("the check must be more frequent than the delay itself",
                interval < settings.delayMillis());
    }
}
