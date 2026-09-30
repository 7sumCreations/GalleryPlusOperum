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
public class RenameFolderTest {

    private FakeMediaStore store;
    private FileOpEngine engine;

    @Before
    public void setUp() {
        store = new FakeMediaStore();
        engine = new FileOpEngine(store);
    }

    @Test
    public void renameMovesEveryItemInTheFolder() {
        Uri a = store.addItem("Pictures/Lisbon", "a.jpg", 1L);
        Uri b = store.addItem("Pictures/Lisbon", "b.jpg", 2L);

        FolderOpResult result = engine.renameFolder("Pictures/Lisbon", "Portugal");

        assertTrue(result.ok);
        assertEquals(2, result.itemsChanged);
        assertEquals("Pictures/Portugal/", store.query(a).relativePath);
        assertEquals("Pictures/Portugal/", store.query(b).relativePath);
    }

    @Test
    public void renamePreservesNamesAndDates() {
        Uri a = store.addItem("Pictures/Lisbon", "a.jpg", 1790157600000L);

        engine.renameFolder("Pictures/Lisbon", "Portugal");

        assertEquals("a.jpg", store.query(a).displayName);
        assertEquals(1790157600000L, store.query(a).dateTakenMillis);
    }

    @Test
    public void renameAlsoMovesNestedSubfolders() {
        Uri nested = store.addItem("Pictures/Lisbon/Day1", "a.jpg", 1L);

        engine.renameFolder("Pictures/Lisbon", "Portugal");

        assertEquals("Pictures/Portugal/Day1/", store.query(nested).relativePath);
    }

    @Test
    public void renameRefusesWhenTheTargetNameIsTaken() {
        Uri a = store.addItem("Pictures/Lisbon", "a.jpg", 1L);
        store.addItem("Pictures/Portugal", "b.jpg", 2L);

        FolderOpResult result = engine.renameFolder("Pictures/Lisbon", "Portugal");

        assertFalse(result.ok);
        assertNotNull(result.failureReason);
        assertEquals("nothing may move on a refused rename",
                "Pictures/Lisbon/", store.query(a).relativePath);
    }

    @Test
    public void renameRejectsAnInvalidName() {
        store.addItem("Pictures/Lisbon", "a.jpg", 1L);

        FolderOpResult result = engine.renameFolder("Pictures/Lisbon", "bad/name");

        assertFalse(result.ok);
        assertNotNull(result.failureReason);
    }

    @Test
    public void renameOfAFolderWithNoItemsReportsZeroChanged() {
        FolderOpResult result = engine.renameFolder("Pictures/Empty", "Renamed");

        assertTrue(result.ok);
        assertEquals(0, result.itemsChanged);
    }

    @Test
    public void theCameraFolderCannotBeRenamed() {
        Uri a = store.addItem("DCIM/Camera", "a.jpg", 1L);

        FolderOpResult result = engine.renameFolder("dcim/camera", "Holiday");

        assertFalse(result.ok);
        assertEquals("DCIM/Camera/", store.query(a).relativePath);
    }

    @Test
    public void checkRenameWritesNothingAndClearsAValidRename() {
        Uri a = store.addItem("Pictures/Lisbon", "a.jpg", 1L);

        assertEquals(null, engine.checkRenameFolder("Pictures/Lisbon", "Portugal"));
        assertEquals("Pictures/Lisbon/", store.query(a).relativePath);
    }
}
