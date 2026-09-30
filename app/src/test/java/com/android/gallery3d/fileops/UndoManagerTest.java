package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class UndoManagerTest {

    private UndoManager undo;
    private FakeMediaStore store;

    @Before
    public void setUp() {
        undo = UndoManager.getInstance();
        undo.clear();
        store = new FakeMediaStore();
    }

    private FileOpBatch completedMove() {
        Uri item = store.addItem("DCIM/Camera", "a.jpg", 1L);
        FileOpBatch batch = new FileOpBatch("u1", FileOpBatch.Kind.MOVE,
                Arrays.asList(item), "Pictures/Test");
        new FileOpEngine(store).runBatch(batch, new FileOpEngine.ProgressCallback() {
            @Override
            public void onItemDone(int indexDone, int total, FileOpResult result) {
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        });
        return batch;
    }

    @Test
    public void theWindowIsTenSeconds() {
        assertEquals(10_000L, UndoManager.UNDO_WINDOW_MILLIS);
    }

    @Test
    public void aRememberedBatchIsUndoableInsideTheWindow() {
        FileOpBatch batch = completedMove();
        undo.rememberAt(batch, 1_000L);

        assertTrue(undo.hasUndoable(1_000L + 9_999L));
        assertSame(batch, undo.takeUndoable(1_000L + 9_999L));
    }

    @Test
    public void aBatchExpiresAfterTenSeconds() {
        FileOpBatch batch = completedMove();
        undo.rememberAt(batch, 1_000L);

        assertFalse(undo.hasUndoable(1_000L + 10_001L));
        assertNull(undo.takeUndoable(1_000L + 10_001L));
    }

    @Test
    public void takingTheBatchConsumesIt() {
        FileOpBatch batch = completedMove();
        undo.rememberAt(batch, 1_000L);

        undo.takeUndoable(2_000L);

        assertNull("undo is single-shot", undo.takeUndoable(2_000L));
    }

    @Test
    public void rememberingASecondBatchReplacesTheFirst() {
        FileOpBatch first = completedMove();
        FileOpBatch second = completedMove();
        undo.rememberAt(first, 1_000L);

        undo.rememberAt(second, 2_000L);

        assertSame("only one batch is remembered at a time",
                second, undo.takeUndoable(3_000L));
    }

    @Test
    public void aBatchWithNoSuccessfulItemsIsNotWorthOffering() {
        FileOpBatch empty = new FileOpBatch("u2", FileOpBatch.Kind.MOVE,
                Collections.<Uri>emptyList(), "Pictures/Test");
        undo.rememberAt(empty, 1_000L);

        assertFalse(undo.hasUndoable(2_000L));
    }
    private FileOpBatch completedDeleteForever() {
        Uri item = store.addItem("DCIM/Camera", "gone.jpg", 1L);
        FileOpBatch batch = new FileOpBatch("u3", FileOpBatch.Kind.DELETE_FOREVER,
                Arrays.asList(item), null);
        new FileOpEngine(store).runBatch(batch, new FileOpEngine.ProgressCallback() {
            @Override
            public void onItemDone(int indexDone, int total, FileOpResult result) {
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        });
        return batch;
    }

    @Test
    public void onlyAPermanentDeleteIsUnundoable() {
        for (FileOpBatch.Kind kind : FileOpBatch.Kind.values()) {
            assertEquals(kind.name(), kind != FileOpBatch.Kind.DELETE_FOREVER,
                    UndoManager.isUndoable(kind));
        }
    }

    @Test
    public void aPermanentDeleteIsNeverOfferedUndo() {
        FileOpBatch deleted = completedDeleteForever();
        assertEquals("the delete itself succeeded", 1, deleted.okCount());

        undo.rememberAt(deleted, 1_000L);

        assertFalse(undo.hasUndoable(1_001L));
        assertNull(undo.takeUndoable(1_001L));
    }

    @Test
    public void aPermanentDeleteAlsoRetiresTheBatchBeforeIt() {
        // Otherwise the snackbar after Empty trash would offer an Undo that
        // reverses whatever move or trash happened a few seconds earlier.
        undo.rememberAt(completedMove(), 1_000L);

        undo.rememberAt(completedDeleteForever(), 2_000L);

        assertFalse(undo.hasUndoable(3_000L));
        assertNull(undo.takeUndoable(3_000L));
    }
}
