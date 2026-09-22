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

@RunWith(RobolectricTestRunner.class)
public class TrashFolderTest {

    private FakeMediaStore store;
    private FileOpEngine engine;

    @Before
    public void setUp() {
        store = new FakeMediaStore();
        engine = new FileOpEngine(store);
    }

    @Test
    public void deletingAFolderTrashesEverythingInIt() {
        Uri a = store.addItem("Pictures/Old", "a.jpg", 1L);
        Uri b = store.addItem("Pictures/Old", "b.jpg", 2L);

        FolderOpResult result = engine.trashFolder("Pictures/Old");

        assertTrue(result.ok);
        assertEquals(2, result.itemsChanged);
        assertTrue(store.query(a).trashed);
        assertTrue(store.query(b).trashed);
    }

    @Test
    public void deletingAFolderTrashesItsSubfoldersToo() {
        Uri deep = store.addItem("Pictures/Old/Day1", "a.jpg", 1L);

        engine.trashFolder("Pictures/Old");

        assertTrue(store.query(deep).trashed);
    }

    @Test
    public void theFolderDisappearsFromTheFolderListing() {
        store.addItem("Pictures/Old", "a.jpg", 1L);

        engine.trashFolder("Pictures/Old");

        assertFalse(store.folderPathsUnder("Pictures").contains("Pictures/Old/"));
    }

    @Test
    public void trashedItemsKeepTheirOriginalFolderSoRestoreWorks() {
        Uri a = store.addItem("Pictures/Old", "a.jpg", 1790157600000L);

        engine.trashFolder("Pictures/Old");

        assertEquals("Pictures/Old/", store.query(a).relativePath);
        assertEquals(1790157600000L, store.query(a).dateTakenMillis);
    }

    @Test
    public void theCameraFolderCannotBeDeleted() {
        Uri a = store.addItem("DCIM/Camera", "a.jpg", 1L);

        FolderOpResult result = engine.trashFolder("DCIM/Camera");

        assertFalse(result.ok);
        assertNotNull(result.failureReason);
        assertFalse(store.query(a).trashed);
    }

    @Test
    public void theCameraGuardIsNotCaseSensitive() {
        store.addItem("DCIM/Camera", "a.jpg", 1L);

        assertFalse(engine.trashFolder("dcim/camera").ok);
    }
}
