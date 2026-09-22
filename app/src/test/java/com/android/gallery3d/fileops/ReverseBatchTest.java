package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class ReverseBatchTest {

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
    public void undoingAMovePutsEveryItemBack() {
        Uri a = store.addItem("DCIM/Camera", "a.jpg", 1790157600000L);
        Uri b = store.addItem("DCIM/Camera", "b.jpg", 1790157600001L);
        FileOpBatch batch = new FileOpBatch("r1", FileOpBatch.Kind.MOVE,
                Arrays.asList(a, b), "Pictures/Trip 2026");
        engine.runBatch(batch, new SilentCallback());

        FileOpBatch reversal = engine.reverseBatch(batch, new SilentCallback());

        assertEquals(2, reversal.okCount());
        assertEquals("DCIM/Camera/", store.query(a).relativePath);
        assertEquals("DCIM/Camera/", store.query(b).relativePath);
    }

    @Test
    public void undoingAMoveRestoresNamesAndDates() {
        Uri a = store.addItem("DCIM/Camera", "a.jpg", 1790157600000L);
        FileOpBatch batch = new FileOpBatch("r2", FileOpBatch.Kind.MOVE,
                Arrays.asList(a), "Pictures/Trip 2026");
        engine.runBatch(batch, new SilentCallback());

        engine.reverseBatch(batch, new SilentCallback());

        assertEquals("a.jpg", store.query(a).displayName);
        assertEquals(1790157600000L, store.query(a).dateTakenMillis);
    }

    @Test
    public void undoingAMoveThatWasRenamedForACollisionRestoresTheOriginalName() {
        store.addItem("Pictures/Trip 2026", "a.jpg", 1L);
        Uri a = store.addItem("DCIM/Camera", "a.jpg", 2L);
        FileOpBatch batch = new FileOpBatch("r3", FileOpBatch.Kind.MOVE,
                Arrays.asList(a), "Pictures/Trip 2026");
        engine.runBatch(batch, new SilentCallback());
        assertEquals("a (1).jpg", store.query(a).displayName);

        engine.reverseBatch(batch, new SilentCallback());

        assertEquals("a.jpg", store.query(a).displayName);
        assertEquals("DCIM/Camera/", store.query(a).relativePath);
    }

    @Test
    public void undoingACopyDeletesTheCopyAndLeavesTheOriginal() {
        Uri a = store.addItem("DCIM/Camera", "a.jpg", 1L);
        FileOpBatch batch = new FileOpBatch("r4", FileOpBatch.Kind.COPY,
                Arrays.asList(a), "Pictures/Test");
        engine.runBatch(batch, new SilentCallback());
        Uri copy = batch.results.get(0).resultUri;

        engine.reverseBatch(batch, new SilentCallback());

        assertNotNull("the original must survive", store.query(a));
        assertEquals("DCIM/Camera/", store.query(a).relativePath);
        org.junit.Assert.assertNull("the copy must be gone", store.query(copy));
    }

    @Test
    public void undoingATrashRestoresTheItem() {
        Uri a = store.addItem("Pictures/Test", "a.jpg", 1L);
        FileOpBatch batch = new FileOpBatch("r5", FileOpBatch.Kind.TRASH,
                Arrays.asList(a), null);
        engine.runBatch(batch, new SilentCallback());

        engine.reverseBatch(batch, new SilentCallback());

        assertFalse(store.query(a).trashed);
        assertEquals("Pictures/Test/", store.query(a).relativePath);
    }

    @Test
    public void undoingAFavouriteClearsTheFlagAgain() {
        Uri a = store.addItem("Pictures/Test", "a.jpg", 1L);
        FileOpBatch batch = new FileOpBatch("r6", FileOpBatch.Kind.FAVOURITE,
                Arrays.asList(a), null);
        engine.runBatch(batch, new SilentCallback());
        assertTrue(store.query(a).favourite);

        engine.reverseBatch(batch, new SilentCallback());

        assertFalse(store.query(a).favourite);
    }

    @Test
    public void undoSkipsItemsThatFailedTheFirstTime() {
        Uri a = store.addItem("DCIM/Camera", "a.jpg", 1L);
        Uri b = store.addItem("DCIM/Camera", "b.jpg", 2L);
        store.destinationVolume = "abcd-1234";
        store.failWritesFor(b);
        FileOpBatch batch = new FileOpBatch("r7", FileOpBatch.Kind.MOVE,
                Arrays.asList(a, b), "Pictures/Trip 2026");
        engine.runBatch(batch, new SilentCallback());
        store.destinationVolume = null;

        FileOpBatch reversal = engine.reverseBatch(batch, new SilentCallback());

        assertEquals("only the item that actually moved is reversed",
                1, reversal.items.size());
    }
}
