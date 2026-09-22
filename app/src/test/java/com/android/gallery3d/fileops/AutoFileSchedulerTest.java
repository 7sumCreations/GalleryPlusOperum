package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

@RunWith(RobolectricTestRunner.class)
public class AutoFileSchedulerTest {

    private static final long MINUTE = 60L * 1000L;
    /** 2026-09-22T10:00:00Z */
    private static final long SEPT_2026 = 1790157600000L;

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

    private FakeMediaStore store;
    private AutoFileSettings settings;
    private AutoFileScheduler scheduler;

    @Before
    public void setUp() {
        store = new FakeMediaStore();
        settings = new AutoFileSettings(new MapStore());
        settings.setEnabled(true);
        scheduler = new AutoFileScheduler(store, settings, TimeZone.getTimeZone("UTC"));
    }

    /** Adds an item to DCIM/Camera with an explicit DATE_ADDED. */
    private Uri addCameraPhoto(String name, long dateTakenMillis, long dateAddedMillis) {
        Uri uri = store.addItem("DCIM/Camera", name, dateTakenMillis);
        store.row(uri).dateAddedSeconds = dateAddedMillis / 1000L;
        return uri;
    }

    @Test
    public void aPhotoOlderThanTheDelayIsPlannedIntoPicturesYearMonth() {
        Uri photo = addCameraPhoto("a.jpg", SEPT_2026, SEPT_2026);

        List<AutoFileScheduler.Plan> plans = scheduler.planFor(SEPT_2026 + 6 * MINUTE);

        assertEquals(1, plans.size());
        assertEquals(photo, plans.get(0).item);
        assertEquals("Pictures/2026/09/", plans.get(0).destRelativePath);
    }

    @Test
    public void aPhotoInsideTheDelayWindowIsLeftAlone() {
        addCameraPhoto("a.jpg", SEPT_2026, SEPT_2026);

        List<AutoFileScheduler.Plan> plans = scheduler.planFor(SEPT_2026 + 4 * MINUTE);

        assertTrue(plans.isEmpty());
    }

    @Test
    public void aPhotoWithNoDateTakenIsNeverMoved() {
        addCameraPhoto("nodate.jpg", 0L, SEPT_2026);

        List<AutoFileScheduler.Plan> plans = scheduler.planFor(SEPT_2026 + 60 * MINUTE);

        assertTrue("a photo with no date stays in DCIM/Camera", plans.isEmpty());
    }

    @Test
    public void nothingIsPlannedWhileTheRuleIsOff() {
        addCameraPhoto("a.jpg", SEPT_2026, SEPT_2026);
        settings.setEnabled(false);

        List<AutoFileScheduler.Plan> plans = scheduler.planFor(SEPT_2026 + 60 * MINUTE);

        assertTrue(plans.isEmpty());
    }

    @Test
    public void onlyTheWatchedFolderIsConsidered() {
        addCameraPhoto("camera.jpg", SEPT_2026, SEPT_2026);
        Uri screenshot = store.addItem("DCIM/Screenshots", "shot.png", SEPT_2026);
        store.row(screenshot).dateAddedSeconds = SEPT_2026 / 1000L;

        List<AutoFileScheduler.Plan> plans = scheduler.planFor(SEPT_2026 + 60 * MINUTE);

        assertEquals(1, plans.size());
        assertEquals("camera.jpg", store.query(plans.get(0).item).displayName);
    }

    @Test
    public void anItemAlreadyInItsDestinationIsNotPlannedAgain() {
        Uri already = store.addItem("Pictures/2026/09", "a.jpg", SEPT_2026);
        store.row(already).dateAddedSeconds = SEPT_2026 / 1000L;

        List<AutoFileScheduler.Plan> plans = scheduler.planFor(SEPT_2026 + 60 * MINUTE);

        assertTrue("never move anything twice", plans.isEmpty());
    }

    @Test
    public void eachPhotoGoesToTheMonthOfItsOwnDateTaken() {
        // 2026-01-05T00:00:00Z
        long january = 1767571200000L;
        Uri jan = addCameraPhoto("jan.jpg", january, SEPT_2026);
        Uri sep = addCameraPhoto("sep.jpg", SEPT_2026, SEPT_2026);

        List<AutoFileScheduler.Plan> plans = scheduler.planFor(SEPT_2026 + 60 * MINUTE);

        assertEquals(2, plans.size());
        for (AutoFileScheduler.Plan plan : plans) {
            if (plan.item.equals(jan)) {
                assertEquals("Pictures/2026/01/", plan.destRelativePath);
            } else if (plan.item.equals(sep)) {
                assertEquals("Pictures/2026/09/", plan.destRelativePath);
            }
        }
    }

    @Test
    public void theFolderPlaceholderIsNeverAutoFiled() {
        Uri placeholder = store.addItem("DCIM/Camera",
                FileOpEngine.PLACEHOLDER_NAME, SEPT_2026);
        store.row(placeholder).dateAddedSeconds = SEPT_2026 / 1000L;

        List<AutoFileScheduler.Plan> plans = scheduler.planFor(SEPT_2026 + 60 * MINUTE);

        assertTrue(plans.isEmpty());
    }
}
