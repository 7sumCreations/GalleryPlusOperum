package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class FakeMediaStoreTest {

    private FakeMediaStore store;

    @Before
    public void setUp() {
        store = new FakeMediaStore();
    }

    @Test
    public void queryReturnsWhatWasAdded() {
        Uri item = store.addItem("DCIM/Camera", "IMG_0001.jpg", 1790157600000L);
        MediaItemInfo info = store.query(item);
        assertEquals("IMG_0001.jpg", info.displayName);
        assertEquals("DCIM/Camera/", info.relativePath);
        assertEquals(1790157600000L, info.dateTakenMillis);
        assertFalse(info.trashed);
        assertFalse(info.favourite);
    }

    @Test
    public void queryOfAnUnknownUriIsNull() {
        assertNull(store.query(FakeMediaStore.uriOf(999999)));
    }

    @Test
    public void updateLocationRewritesPathAndName() throws Exception {
        Uri item = store.addItem("DCIM/Camera", "IMG_0001.jpg", 1L);
        store.updateLocation(item, "Pictures/Test", "IMG_0001.jpg");
        assertEquals("Pictures/Test/", store.query(item).relativePath);
    }

    @Test
    public void copyToKeepsDateTakenAndLeavesTheOriginal() throws Exception {
        Uri item = store.addItem("DCIM/Camera", "IMG_0001.jpg", 1790157600000L);
        Uri copy = store.copyTo(item, "Pictures/Test", "IMG_0001.jpg");
        assertEquals(1790157600000L, store.query(copy).dateTakenMillis);
        assertEquals("DCIM/Camera/", store.query(item).relativePath);
        assertEquals(2, store.rowCount());
    }

    @Test
    public void consentGateThrowsUntilGranted() {
        Uri item = store.addItem("DCIM/Camera", "IMG_0001.jpg", 1L);
        store.requireConsentFor(item, null);
        try {
            store.updateLocation(item, "Pictures/Test", "IMG_0001.jpg");
            fail("expected PendingConsentException");
        } catch (PendingConsentException expected) {
            // correct
        } catch (Exception other) {
            fail("expected PendingConsentException, got " + other);
        }
    }

    @Test
    public void displayNamesInSkipsTrashedRows() throws Exception {
        Uri kept = store.addItem("Pictures/Test", "a.jpg", 1L);
        Uri gone = store.addItem("Pictures/Test", "b.jpg", 1L);
        store.setTrashed(gone, true);
        assertEquals(Arrays.asList("a.jpg"), store.displayNamesIn("Pictures/Test"));
        assertTrue(store.query(kept).relativePath.equals("Pictures/Test/"));
    }

    @Test
    public void itemsUnderIsRecursive() {
        store.addItem("Pictures/Lisbon", "a.jpg", 1L);
        store.addItem("Pictures/Lisbon/Day1", "b.jpg", 1L);
        store.addItem("Pictures/Other", "c.jpg", 1L);
        assertEquals(2, store.itemsUnder("Pictures/Lisbon").size());
    }
}
