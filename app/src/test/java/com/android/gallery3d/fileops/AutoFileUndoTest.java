package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/** Undo of auto-filed photos from the "Filed N new photos" notification (F-025). */
@RunWith(RobolectricTestRunner.class)
public class AutoFileUndoTest {

    private static final long MINUTE = 60L * 1000L;
    /** 2026-09-22T10:00:00Z */
    private static final long SEPT_2026 = 1790157600000L;
    private static final long RUN = SEPT_2026 + 10 * MINUTE;

    private FakeMediaStore store;
    private FileOpEngine engine;
    private AutoFileLog log;

    @Before
    public void setUp() {
        store = new FakeMediaStore();
        engine = new FileOpEngine(store);
        log = new AutoFileLog(new ArrayList<AutoFileLog.Entry>());
    }

    /** Auto-file one camera photo at the given run time, as applyRule does. */
    private Uri autoFiled(String name, long runMillis) {
        Uri item = store.addItem("DCIM/Camera", name, SEPT_2026);
        FileOpResult result = engine.move(item, "Pictures/2026/09/");
        assertTrue(result.isOk());
        log.record(result, "Pictures/2026/09/", runMillis);
        return item;
    }

    @Test
    public void undoPutsEveryPhotoInTheWindowBack() {
        Uri a = autoFiled("a.jpg", RUN);
        Uri b = autoFiled("b.jpg", RUN + MINUTE);

        AutoFileNotices.UndoOutcome outcome =
                AutoFileUndo.undoWindow(store, log, RUN, RUN + MINUTE, RUN + 2 * MINUTE);

        assertEquals(2, outcome.restored);
        assertEquals(0, outcome.notRestored());
        assertEquals("DCIM/Camera/", store.row(a).relativePath);
        assertEquals("a.jpg", store.row(a).displayName);
        assertEquals("DCIM/Camera/", store.row(b).relativePath);
        assertTrue(log.entries().isEmpty());
    }

    @Test
    public void undoLeavesPhotosFiledOutsideTheWindowAlone() {
        Uri earlier = autoFiled("earlier.jpg", RUN - MINUTE);
        Uri later = autoFiled("later.jpg", RUN + 5 * MINUTE);
        Uri inside = autoFiled("inside.jpg", RUN);

        AutoFileNotices.UndoOutcome outcome =
                AutoFileUndo.undoWindow(store, log, RUN, RUN, RUN + 6 * MINUTE);

        assertEquals(1, outcome.total());
        assertEquals("DCIM/Camera/", store.row(inside).relativePath);
        assertEquals("Pictures/2026/09/", store.row(earlier).relativePath);
        assertEquals("Pictures/2026/09/", store.row(later).relativePath);
    }

    @Test
    public void aPhotoDeletedSinceIsSkippedNotFailed() throws Exception {
        Uri kept = autoFiled("kept.jpg", RUN);
        Uri deleted = autoFiled("deleted.jpg", RUN);
        store.deletePermanently(deleted);

        AutoFileNotices.UndoOutcome outcome =
                AutoFileUndo.undoWindow(store, log, RUN, RUN, RUN + MINUTE);

        assertEquals(1, outcome.restored);
        assertEquals(1, outcome.gone);
        assertEquals(0, outcome.failed);
        assertEquals("DCIM/Camera/", store.row(kept).relativePath);
        assertNull(store.row(deleted));
    }

    @Test
    public void aPhotoTrashedSinceStaysInTheTrash() throws Exception {
        Uri trashed = autoFiled("trashed.jpg", RUN);
        store.setTrashed(trashed, true);

        AutoFileNotices.UndoOutcome outcome =
                AutoFileUndo.undoWindow(store, log, RUN, RUN, RUN + MINUTE);

        assertEquals(1, outcome.gone);
        assertTrue(store.row(trashed).trashed);
        assertEquals("Pictures/2026/09/", store.row(trashed).relativePath);
    }

    @Test
    public void aPhotoTheUserMovedSinceIsNotDraggedBack() {
        Uri moved = autoFiled("moved.jpg", RUN);
        assertTrue(engine.move(moved, "Pictures/Holiday/").isOk());

        AutoFileNotices.UndoOutcome outcome =
                AutoFileUndo.undoWindow(store, log, RUN, RUN, RUN + MINUTE);

        assertEquals(1, outcome.gone);
        assertEquals("Pictures/Holiday/", store.row(moved).relativePath);
    }

    @Test
    public void aPhotoThatNowNeedsPermissionIsReportedAndKeptForLater() {
        Uri gated = autoFiled("gated.jpg", RUN);
        store.requireConsentFor(gated, null);

        AutoFileNotices.UndoOutcome outcome =
                AutoFileUndo.undoWindow(store, log, RUN, RUN, RUN + MINUTE);

        assertEquals(1, outcome.failed);
        assertEquals("Pictures/2026/09/", store.row(gated).relativePath);
        assertNotNull("the entry stays so it can still be undone",
                log.find(gated.toString()));
        assertFalse(log.isUndone(gated.toString()));
    }

    @Test
    public void aWriteFailureIsReportedNotThrown() {
        Uri failing = autoFiled("failing.jpg", RUN);
        store.failWritesFor(failing);

        AutoFileNotices.UndoOutcome outcome =
                AutoFileUndo.undoWindow(store, log, RUN, RUN, RUN + MINUTE);

        assertEquals(1, outcome.failed);
    }

    @Test
    public void aNameTakenSinceInTheOldFolderGetsAFreeName() {
        Uri photo = autoFiled("a.jpg", RUN);
        store.addItem("DCIM/Camera", "a.jpg", SEPT_2026 + MINUTE);

        AutoFileUndo.undoWindow(store, log, RUN, RUN, RUN + MINUTE);

        assertEquals("DCIM/Camera/", store.row(photo).relativePath);
        assertFalse("a.jpg".equals(store.row(photo).displayName));
    }

    @Test
    public void undoingTwiceDoesNothingTheSecondTime() {
        autoFiled("a.jpg", RUN);
        AutoFileUndo.undoWindow(store, log, RUN, RUN, RUN + MINUTE);

        AutoFileNotices.UndoOutcome again =
                AutoFileUndo.undoWindow(store, log, RUN, RUN, RUN + MINUTE);

        assertEquals(0, again.total());
    }

    @Test
    public void aPhotoPutBackIsNeverAutoFiledAgain() {
        Map<String, Object> values = new HashMap<String, Object>();
        AutoFileSettings settings = new AutoFileSettings(new MapStore(values));
        settings.setEnabled(true, SEPT_2026 - 60 * MINUTE);
        Uri photo = autoFiled("a.jpg", RUN);
        AutoFileUndo.undoWindow(store, log, RUN, RUN, RUN + MINUTE);
        assertTrue(log.isUndone(photo.toString()));

        List<AutoFileScheduler.Plan> plans = new AutoFileScheduler(store, settings,
                TimeZone.getTimeZone("UTC"), log.undoneUris())
                .planFor(RUN + 60 * MINUTE);

        assertTrue("the user's Undo must stick", plans.isEmpty());
        // Without the record the same photo would be planned straight back.
        assertEquals(1, new AutoFileScheduler(store, settings, TimeZone.getTimeZone("UTC"))
                .planFor(RUN + 60 * MINUTE).size());
    }

    @Test
    public void theUndoneRecordIsCapped() {
        for (int i = 0; i < AutoFileLog.MAX_ENTRIES + 5; i++) {
            log.markUndone("media://" + i, i);
        }
        assertEquals(AutoFileLog.MAX_ENTRIES, log.undoneUris().size());
        assertFalse("the oldest are forgotten first", log.isUndone("media://0"));
        assertTrue(log.isUndone("media://" + (AutoFileLog.MAX_ENTRIES + 4)));
    }

    private static final class MapStore implements AutoFileSettings.Store {
        final Map<String, Object> values;

        MapStore(Map<String, Object> values) {
            this.values = values;
        }

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
}
