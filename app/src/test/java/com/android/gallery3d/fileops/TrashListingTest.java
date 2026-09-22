package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TrashListingTest {

    private FakeMediaStore store;
    private FileOpEngine engine;

    @Before
    public void setUp() {
        store = new FakeMediaStore();
        engine = new FileOpEngine(store);
    }

    @Test
    public void trashedItemsListsOnlyTrashedRows() {
        Uri kept = store.addItem("Pictures/Test", "keep.jpg", 1L);
        Uri gone = store.addItem("Pictures/Test", "gone.jpg", 2L);
        engine.trash(gone);

        assertTrue(store.trashedItems().contains(gone));
        assertFalse(store.trashedItems().contains(kept));
        assertEquals(1, store.trashedItems().size());
    }

    @Test
    public void restoringTakesTheItemOutOfTheTrashListing() {
        Uri item = store.addItem("Pictures/Test", "a.jpg", 1L);
        engine.trash(item);

        engine.restore(item);

        assertEquals(0, store.trashedItems().size());
    }

    @Test
    public void emptyingTheTrashRemovesEveryTrashedRow() {
        Uri a = store.addItem("Pictures/Test", "a.jpg", 1L);
        Uri b = store.addItem("Pictures/Test", "b.jpg", 2L);
        Uri keep = store.addItem("Pictures/Test", "keep.jpg", 3L);
        engine.trash(a);
        engine.trash(b);

        int emptied = engine.emptyTrash();

        assertEquals(2, emptied);
        assertEquals(0, store.trashedItems().size());
        assertTrue(store.displayNamesIn("Pictures/Test/").contains("keep.jpg"));
    }
}
