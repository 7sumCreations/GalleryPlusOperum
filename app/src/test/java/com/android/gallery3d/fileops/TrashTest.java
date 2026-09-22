package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class TrashTest {

    private static final long DAY = 24L * 60L * 60L * 1000L;

    private FakeMediaStore store;
    private FileOpEngine engine;

    @Before
    public void setUp() {
        store = new FakeMediaStore();
        engine = new FileOpEngine(store);
    }

    private static final class SilentCallback implements FileOpEngine.ProgressCallback {
        @Override
        public void onItemDone(int indexDone, int total, FileOpResult result) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    @Test
    public void trashingHidesTheItemWithoutDestroyingIt() {
        Uri item = store.addItem("Pictures/Test", "a.jpg", 1L);

        FileOpResult result = engine.trash(item);

        assertEquals(FileOpResult.Status.OK, result.status);
        assertTrue(store.query(item).trashed);
        assertFalse(store.displayNamesIn("Pictures/Test/").contains("a.jpg"));
    }

    @Test
    public void restoringPutsItBackInItsOriginalFolderWithItsOriginalDate() {
        Uri item = store.addItem("Pictures/Test", "a.jpg", 1790157600000L);
        engine.trash(item);

        FileOpResult result = engine.restore(item);

        assertEquals(FileOpResult.Status.OK, result.status);
        assertFalse(store.query(item).trashed);
        assertEquals("Pictures/Test/", store.query(item).relativePath);
        assertEquals("a.jpg", store.query(item).displayName);
        assertEquals(1790157600000L, store.query(item).dateTakenMillis);
    }

    @Test
    public void deleteForeverRemovesTheRow() {
        Uri item = store.addItem("Pictures/Test", "a.jpg", 1L);
        engine.trash(item);

        FileOpResult result = engine.deleteForever(item);

        assertEquals(FileOpResult.Status.OK, result.status);
        assertNull(store.query(item));
    }

    @Test
    public void purgeRemovesTrashOlderThanThirtyDays() {
        store.now = 0L;
        Uri old = store.addItem("Pictures/Test", "old.jpg", 1L);
        engine.trash(old);
        store.now = 31L * DAY;
        Uri fresh = store.addItem("Pictures/Test", "fresh.jpg", 2L);
        engine.trash(fresh);

        int purged = engine.purgeExpiredTrash(31L * DAY);

        assertEquals(1, purged);
        assertNull(store.query(old));
        assertTrue(store.query(fresh).trashed);
    }

    @Test
    public void purgeAtTwentyNineDaysRemovesNothing() {
        store.now = 0L;
        Uri item = store.addItem("Pictures/Test", "a.jpg", 1L);
        engine.trash(item);

        int purged = engine.purgeExpiredTrash(29L * DAY);

        assertEquals(0, purged);
        assertTrue(store.query(item).trashed);
    }

    @Test
    public void trashRunsAsABatch() {
        Uri a = store.addItem("Pictures/Test", "a.jpg", 1L);
        Uri b = store.addItem("Pictures/Test", "b.jpg", 2L);
        FileOpBatch batch = new FileOpBatch("t1", FileOpBatch.Kind.TRASH,
                Arrays.asList(a, b), null);

        engine.runBatch(batch, new SilentCallback());

        assertEquals(2, batch.okCount());
        assertTrue(store.query(a).trashed);
        assertTrue(store.query(b).trashed);
    }

    @Test
    public void theTrashResultRecordsTheOriginalFolderForUndo() {
        Uri item = store.addItem("Pictures/Test", "a.jpg", 1L);

        FileOpResult result = engine.trash(item);

        assertEquals("Pictures/Test/", result.previousRelativePath);
        assertEquals("a.jpg", result.previousDisplayName);
    }
}
