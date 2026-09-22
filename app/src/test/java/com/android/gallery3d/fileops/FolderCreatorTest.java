package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FolderCreatorTest {

    private FakeMediaStore store;

    @Before
    public void setUp() {
        store = new FakeMediaStore();
    }

    @Test
    public void createMakesTheFolderVisibleViaAPlaceholder() {
        FolderCreator.Outcome outcome = FolderCreator.create(store, "Pictures", "Trip 2026");

        assertTrue(outcome.created);
        assertEquals("Pictures/Trip 2026/", outcome.relativePath);
        assertNull(outcome.errorMessage);
        assertTrue(store.folderPathsUnder("Pictures").contains("Pictures/Trip 2026/"));
    }

    @Test
    public void createRejectsAnInvalidName() {
        FolderCreator.Outcome outcome = FolderCreator.create(store, "Pictures", "a/b");

        assertFalse(outcome.created);
        assertNotNull(outcome.errorMessage);
        assertFalse(store.folderPathsUnder("Pictures").contains("Pictures/a/b/"));
    }

    @Test
    public void createRejectsAParentOutsideTheMediaRoots() {
        FolderCreator.Outcome outcome = FolderCreator.create(store, "Download", "Trip 2026");

        assertFalse(outcome.created);
        assertNotNull(outcome.errorMessage);
    }

    @Test
    public void createRejectsANestedNameBecauseItIsOneLevelAtATime() {
        FolderCreator.Outcome outcome = FolderCreator.create(store, "Pictures", "a/b/c");

        assertFalse(outcome.created);
        assertNotNull(outcome.errorMessage);
    }

    @Test
    public void createIsIdempotentWhenTheFolderAlreadyHasPhotos() {
        store.addItem("Pictures/Trip 2026", "a.jpg", 1L);
        int before = store.rowCount();

        FolderCreator.Outcome outcome = FolderCreator.create(store, "Pictures", "Trip 2026");

        assertTrue(outcome.created);
        assertEquals("no placeholder needed for a folder that already exists",
                before, store.rowCount());
    }
}
