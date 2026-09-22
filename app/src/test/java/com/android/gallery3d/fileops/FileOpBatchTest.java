package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class FileOpBatchTest {

    private FakeMediaStore store;
    private FileOpEngine engine;

    @Before
    public void setUp() {
        store = new FakeMediaStore();
        engine = new FileOpEngine(store);
    }

    private static final class RecordingCallback implements FileOpEngine.ProgressCallback {
        final List<Integer> indices = new ArrayList<Integer>();
        int total = -1;
        int cancelAfter = Integer.MAX_VALUE;

        @Override
        public void onItemDone(int indexDone, int totalItems, FileOpResult result) {
            indices.add(indexDone);
            total = totalItems;
        }

        @Override
        public boolean isCancelled() {
            return indices.size() >= cancelAfter;
        }
    }

    @Test
    public void runBatchMovesEveryItem() {
        Uri a = store.addItem("DCIM/Camera", "a.jpg", 1L);
        Uri b = store.addItem("DCIM/Camera", "b.jpg", 2L);
        Uri c = store.addItem("DCIM/Camera", "c.jpg", 3L);
        FileOpBatch batch = new FileOpBatch("batch-1", FileOpBatch.Kind.MOVE,
                Arrays.asList(a, b, c), "Pictures/Trip 2026");

        engine.runBatch(batch, new RecordingCallback());

        assertEquals(3, batch.okCount());
        assertEquals("Pictures/Trip 2026/", store.query(a).relativePath);
        assertEquals("Pictures/Trip 2026/", store.query(b).relativePath);
        assertEquals("Pictures/Trip 2026/", store.query(c).relativePath);
    }

    @Test
    public void runBatchReportsProgressPerItem() {
        Uri a = store.addItem("DCIM/Camera", "a.jpg", 1L);
        Uri b = store.addItem("DCIM/Camera", "b.jpg", 2L);
        FileOpBatch batch = new FileOpBatch("batch-2", FileOpBatch.Kind.MOVE,
                Arrays.asList(a, b), "Pictures/Trip 2026");
        RecordingCallback callback = new RecordingCallback();

        engine.runBatch(batch, callback);

        assertEquals(Arrays.asList(1, 2), callback.indices);
        assertEquals(2, callback.total);
    }

    @Test
    public void runBatchStopsWhenCancelled() {
        Uri a = store.addItem("DCIM/Camera", "a.jpg", 1L);
        Uri b = store.addItem("DCIM/Camera", "b.jpg", 2L);
        Uri c = store.addItem("DCIM/Camera", "c.jpg", 3L);
        FileOpBatch batch = new FileOpBatch("batch-3", FileOpBatch.Kind.MOVE,
                Arrays.asList(a, b, c), "Pictures/Trip 2026");
        RecordingCallback callback = new RecordingCallback();
        callback.cancelAfter = 1;

        engine.runBatch(batch, callback);

        assertEquals(1, batch.okCount());
        assertEquals("cancel must take effect after the current file, not mid-file",
                "DCIM/Camera/", store.query(c).relativePath);
    }

    @Test
    public void runBatchStopsAndReportsOnTheFirstFailure() {
        Uri a = store.addItem("DCIM/Camera", "a.jpg", 1L);
        Uri b = store.addItem("DCIM/Camera", "b.jpg", 2L);
        Uri c = store.addItem("DCIM/Camera", "c.jpg", 3L);
        store.destinationVolume = "abcd-1234";
        store.failWritesFor(b);
        FileOpBatch batch = new FileOpBatch("batch-4", FileOpBatch.Kind.MOVE,
                Arrays.asList(a, b, c), "Pictures/Trip 2026");

        engine.runBatch(batch, new RecordingCallback());

        assertEquals(1, batch.okCount());
        assertEquals(1, batch.failureCount());
        assertEquals("the batch must stop at the failure, not carry on",
                2, batch.results.size());
    }

    @Test
    public void everyResultRecordsWhereTheItemCameFrom() {
        Uri a = store.addItem("DCIM/Camera", "a.jpg", 1L);
        FileOpBatch batch = new FileOpBatch("batch-5", FileOpBatch.Kind.MOVE,
                Arrays.asList(a), "Pictures/Trip 2026");

        engine.runBatch(batch, new RecordingCallback());

        assertEquals("DCIM/Camera/", batch.results.get(0).previousRelativePath);
        assertEquals("a.jpg", batch.results.get(0).previousDisplayName);
        assertTrue(batch.results.get(0).isOk());
    }
}
