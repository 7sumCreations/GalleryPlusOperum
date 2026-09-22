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
public class PlaceholderCleanupTest {

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
    public void aSuccessfulMoveIntoANewFolderRemovesThePlaceholder() {
        FolderCreator.create(store, "Pictures", "Trip 2026");
        assertTrue(store.displayNamesIn("Pictures/Trip 2026/")
                .contains(FileOpEngine.PLACEHOLDER_NAME));
        Uri photo = store.addItem("DCIM/Camera", "a.jpg", 1L);

        FileOpBatch batch = new FileOpBatch("b1", FileOpBatch.Kind.MOVE,
                Arrays.asList(photo), "Pictures/Trip 2026");
        engine.runBatch(batch, new SilentCallback());

        assertFalse(store.displayNamesIn("Pictures/Trip 2026/")
                .contains(FileOpEngine.PLACEHOLDER_NAME));
        assertEquals(Arrays.asList("a.jpg"), store.displayNamesIn("Pictures/Trip 2026/"));
    }

    @Test
    public void aFailedBatchLeavesThePlaceholderSoTheFolderStaysVisible() {
        FolderCreator.create(store, "Pictures", "Trip 2026");
        Uri photo = store.addItem("DCIM/Camera", "a.jpg", 1L);
        store.destinationVolume = "abcd-1234";
        store.failWritesFor(photo);

        FileOpBatch batch = new FileOpBatch("b2", FileOpBatch.Kind.MOVE,
                Arrays.asList(photo), "Pictures/Trip 2026");
        engine.runBatch(batch, new SilentCallback());

        assertTrue(store.displayNamesIn("Pictures/Trip 2026/")
                .contains(FileOpEngine.PLACEHOLDER_NAME));
    }
}
