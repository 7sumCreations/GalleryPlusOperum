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
public class MoveFolderTest {

    private FakeMediaStore store;
    private FileOpEngine engine;

    @Before
    public void setUp() {
        store = new FakeMediaStore();
        engine = new FileOpEngine(store);
    }

    @Test
    public void movingLisbonUnderTripsNestsItAndItsContents() {
        Uri a = store.addItem("Pictures/Lisbon", "a.jpg", 1L);
        store.addItem("Pictures/Trips", "keep.jpg", 2L);

        FolderOpResult result = engine.moveFolder("Pictures/Lisbon", "Pictures/Trips");

        assertTrue(result.ok);
        assertEquals("Pictures/Trips/Lisbon/", result.toRelativePath);
        assertEquals("Pictures/Trips/Lisbon/", store.query(a).relativePath);
    }

    @Test
    public void nestedSubfoldersComeAlong() {
        Uri deep = store.addItem("Pictures/Lisbon/Day1", "a.jpg", 1L);
        store.addItem("Pictures/Trips", "keep.jpg", 2L);

        engine.moveFolder("Pictures/Lisbon", "Pictures/Trips");

        assertEquals("Pictures/Trips/Lisbon/Day1/", store.query(deep).relativePath);
    }

    @Test
    public void namesAndDatesSurviveTheReparent() {
        Uri a = store.addItem("Pictures/Lisbon", "a.jpg", 1790157600000L);
        store.addItem("Pictures/Trips", "keep.jpg", 2L);

        engine.moveFolder("Pictures/Lisbon", "Pictures/Trips");

        assertEquals("a.jpg", store.query(a).displayName);
        assertEquals(1790157600000L, store.query(a).dateTakenMillis);
    }

    @Test
    public void aFolderCannotBeMovedIntoItself() {
        Uri a = store.addItem("Pictures/Lisbon", "a.jpg", 1L);

        FolderOpResult result = engine.moveFolder("Pictures/Lisbon", "Pictures/Lisbon/Day1");

        assertFalse(result.ok);
        assertNotNull(result.failureReason);
        assertEquals("Pictures/Lisbon/", store.query(a).relativePath);
    }

    @Test
    public void movingIntoAParentThatAlreadyHasThatNameIsRefused() {
        Uri a = store.addItem("Pictures/Lisbon", "a.jpg", 1L);
        store.addItem("Pictures/Trips/Lisbon", "clash.jpg", 2L);

        FolderOpResult result = engine.moveFolder("Pictures/Lisbon", "Pictures/Trips");

        assertFalse(result.ok);
        assertEquals("Pictures/Lisbon/", store.query(a).relativePath);
    }

    @Test
    public void theNewParentMustBeUnderAMediaRoot() {
        store.addItem("Pictures/Lisbon", "a.jpg", 1L);

        FolderOpResult result = engine.moveFolder("Pictures/Lisbon", "Download");

        assertFalse(result.ok);
    }
}
