package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class FileOpEngineCopyTest {

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
    public void copyLeavesTheOriginalExactlyWhereItWas() {
        Uri item = store.addItem("DCIM/Camera", "a.jpg", 1790157600000L);

        FileOpResult result = engine.copy(item, "Pictures/Test");

        assertEquals(FileOpResult.Status.OK, result.status);
        assertNotNull(store.query(item));
        assertEquals("DCIM/Camera/", store.query(item).relativePath);
        assertEquals("a.jpg", store.query(item).displayName);
    }

    @Test
    public void copyCreatesASecondRowAtTheDestination() {
        Uri item = store.addItem("DCIM/Camera", "a.jpg", 1790157600000L);

        FileOpResult result = engine.copy(item, "Pictures/Test");

        assertNotEquals(item, result.resultUri);
        assertEquals("Pictures/Test/", store.query(result.resultUri).relativePath);
        assertEquals(2, store.rowCount());
    }

    @Test
    public void copyPreservesDateTaken() {
        Uri item = store.addItem("DCIM/Camera", "a.jpg", 1790157600000L);

        FileOpResult result = engine.copy(item, "Pictures/Test");

        assertEquals(1790157600000L, store.query(result.resultUri).dateTakenMillis);
    }

    @Test
    public void copyingIntoTheSameFolderKeepsBothWithASuffix() {
        Uri item = store.addItem("Pictures/Test", "a.jpg", 1L);

        FileOpResult result = engine.copy(item, "Pictures/Test");

        assertEquals("a.jpg", store.query(item).displayName);
        assertEquals("a (1).jpg", store.query(result.resultUri).displayName);
    }

    @Test
    public void copyRejectsADestinationOutsideTheMediaRoots() {
        Uri item = store.addItem("DCIM/Camera", "a.jpg", 1L);

        FileOpResult result = engine.copy(item, "Download/Stuff");

        assertEquals(FileOpResult.Status.FAILED, result.status);
        assertEquals(1, store.rowCount());
    }

    @Test
    public void copyRunsAsABatchKind() {
        Uri a = store.addItem("DCIM/Camera", "a.jpg", 1L);
        Uri b = store.addItem("DCIM/Camera", "b.jpg", 2L);
        FileOpBatch batch = new FileOpBatch("c1", FileOpBatch.Kind.COPY,
                Arrays.asList(a, b), "Pictures/Test");

        engine.runBatch(batch, new SilentCallback());

        assertEquals(2, batch.okCount());
        assertEquals(4, store.rowCount());
    }
}
