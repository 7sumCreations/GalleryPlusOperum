package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class FavouriteTest {

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
    public void favouritingSetsTheFlag() {
        Uri item = store.addItem("DCIM/Camera", "a.jpg", 1L);

        FileOpResult result = engine.setFavourite(item, true);

        assertEquals(FileOpResult.Status.OK, result.status);
        assertTrue(store.query(item).favourite);
    }

    @Test
    public void favouritingDoesNotMoveTheFile() {
        Uri item = store.addItem("DCIM/Camera", "a.jpg", 1790157600000L);

        engine.setFavourite(item, true);

        assertEquals("DCIM/Camera/", store.query(item).relativePath);
        assertEquals("a.jpg", store.query(item).displayName);
        assertEquals(1790157600000L, store.query(item).dateTakenMillis);
    }

    @Test
    public void unfavouritingClearsTheFlag() {
        Uri item = store.addItem("DCIM/Camera", "a.jpg", 1L);
        engine.setFavourite(item, true);

        engine.setFavourite(item, false);

        assertFalse(store.query(item).favourite);
    }

    @Test
    public void theResultRecordsThePreviousFlagForUndo() {
        Uri item = store.addItem("DCIM/Camera", "a.jpg", 1L);

        FileOpResult result = engine.setFavourite(item, true);

        assertFalse("previousFavourite must be the value before the change",
                result.previousFavourite);
    }

    @Test
    public void favouritingRunsAsABatch() {
        Uri a = store.addItem("DCIM/Camera", "a.jpg", 1L);
        Uri b = store.addItem("DCIM/Camera", "b.jpg", 2L);
        FileOpBatch batch = new FileOpBatch("f1", FileOpBatch.Kind.FAVOURITE,
                Arrays.asList(a, b), null);

        engine.runBatch(batch, new SilentCallback());

        assertEquals(2, batch.okCount());
        assertTrue(store.query(a).favourite);
        assertTrue(store.query(b).favourite);
    }
}
