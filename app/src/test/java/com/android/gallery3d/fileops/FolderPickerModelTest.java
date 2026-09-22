package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class FolderPickerModelTest {

    private FakeMediaStore store;

    @Before
    public void setUp() {
        store = new FakeMediaStore();
    }

    @Test
    public void foldersForListsEveryMediaRootFolderOnce() {
        store.addItem("DCIM/Camera", "a.jpg", 1L);
        store.addItem("DCIM/Camera", "b.jpg", 2L);
        store.addItem("Pictures/Trip 2026", "c.jpg", 3L);

        List<String> folders = FolderPicker.foldersFor(store);

        assertEquals(2, folders.size());
        assertTrue(folders.contains("DCIM/Camera/"));
        assertTrue(folders.contains("Pictures/Trip 2026/"));
    }

    @Test
    public void foldersForExcludesNonMediaRoots() {
        store.addItem("Download/Stuff", "a.jpg", 1L);
        store.addItem("Pictures/Keep", "b.jpg", 2L);

        List<String> folders = FolderPicker.foldersFor(store);

        assertFalse(folders.contains("Download/Stuff/"));
        assertTrue(folders.contains("Pictures/Keep/"));
    }

    @Test
    public void foldersForIsSorted() {
        store.addItem("Pictures/Zebra", "a.jpg", 1L);
        store.addItem("Pictures/Apple", "b.jpg", 2L);

        List<String> folders = FolderPicker.foldersFor(store);

        assertEquals("Pictures/Apple/", folders.get(0));
        assertEquals("Pictures/Zebra/", folders.get(1));
    }

    @Test
    public void labelForShowsTheNameThenItsParent() {
        assertEquals("Trip 2026  ·  Pictures", FolderPicker.labelFor("Pictures/Trip 2026/"));
        assertEquals("Lisbon  ·  Pictures/Trips", FolderPicker.labelFor("Pictures/Trips/Lisbon/"));
        assertEquals("Pictures", FolderPicker.labelFor("Pictures/"));
    }
}
