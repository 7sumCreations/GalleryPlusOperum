package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FileOpEngineMoveTest {

    private FakeMediaStore store;
    private FileOpEngine engine;

    @Before
    public void setUp() {
        store = new FakeMediaStore();
        engine = new FileOpEngine(store);
    }

    @Test
    public void moveRewritesRelativePathInPlace() {
        Uri item = store.addItem("DCIM/Camera", "IMG_0001.jpg", 1790157600000L);

        FileOpResult result = engine.move(item, "Pictures/Test");

        assertEquals(FileOpResult.Status.OK, result.status);
        assertEquals(item, result.resultUri);
        assertEquals("Pictures/Test/", store.query(item).relativePath);
    }

    @Test
    public void movePreservesNameAndDateTaken() {
        Uri item = store.addItem("DCIM/Camera", "IMG_0001.jpg", 1790157600000L);

        engine.move(item, "Pictures/Test");

        MediaItemInfo after = store.query(item);
        assertEquals("IMG_0001.jpg", after.displayName);
        assertEquals(1790157600000L, after.dateTakenMillis);
    }

    @Test
    public void moveRecordsThePreviousLocationForUndo() {
        Uri item = store.addItem("DCIM/Camera", "IMG_0001.jpg", 1L);

        FileOpResult result = engine.move(item, "Pictures/Test");

        assertEquals("DCIM/Camera/", result.previousRelativePath);
        assertEquals("IMG_0001.jpg", result.previousDisplayName);
    }

    @Test
    public void moveIntoAFolderThatAlreadyHasThatNameKeepsBoth() {
        store.addItem("Pictures/Test", "IMG_0001.jpg", 1L);
        Uri item = store.addItem("DCIM/Camera", "IMG_0001.jpg", 1L);

        engine.move(item, "Pictures/Test");

        assertEquals("IMG_0001 (1).jpg", store.query(item).displayName);
        assertEquals("Pictures/Test/", store.query(item).relativePath);
    }

    @Test
    public void moveToADifferentVolumeCopiesThenDeletes() {
        Uri item = store.addItem("DCIM/Camera", "IMG_0001.jpg", 1790157600000L,
                "external_primary");
        store.destinationVolume = "abcd-1234";   // an SD card

        FileOpResult result = engine.move(item, "Pictures/Test");

        assertEquals(FileOpResult.Status.OK, result.status);
        assertTrue("source row must be gone after a cross-volume move",
                store.query(item) == null);
        assertNotNull(store.query(result.resultUri));
        assertEquals("Pictures/Test/", store.query(result.resultUri).relativePath);
        assertEquals(1790157600000L, store.query(result.resultUri).dateTakenMillis);
    }

    @Test
    public void crossVolumeMoveDoesNotDeleteTheSourceWhenTheCopyFails() {
        Uri item = store.addItem("DCIM/Camera", "IMG_0001.jpg", 1L, "external_primary");
        store.destinationVolume = "abcd-1234";
        store.failWritesFor(item);

        FileOpResult result = engine.move(item, "Pictures/Test");

        assertEquals(FileOpResult.Status.FAILED, result.status);
        assertNotNull("source must survive a failed cross-volume move", store.query(item));
        assertEquals("DCIM/Camera/", store.query(item).relativePath);
    }

    @Test
    public void moveSurfacesTheConsentIntentSenderInsteadOfFailing() {
        Uri item = store.addItem("DCIM/Camera", "IMG_0001.jpg", 1L);
        store.requireConsentFor(item, null);

        FileOpResult result = engine.move(item, "Pictures/Test");

        assertEquals(FileOpResult.Status.CONSENT_REQUIRED, result.status);
        assertEquals("DCIM/Camera/", store.query(item).relativePath);
    }

    @Test
    public void moveToAPathOutsideTheMediaRootsIsRejected() {
        Uri item = store.addItem("DCIM/Camera", "IMG_0001.jpg", 1L);

        FileOpResult result = engine.move(item, "Download/Stuff");

        assertEquals(FileOpResult.Status.FAILED, result.status);
        assertNotNull(result.failureReason);
        assertEquals("DCIM/Camera/", store.query(item).relativePath);
    }

    @Test
    public void movingAnItemToWhereItAlreadyIsIsANoOp() {
        Uri item = store.addItem("Pictures/Test", "IMG_0001.jpg", 1L);

        FileOpResult result = engine.move(item, "Pictures/Test");

        assertEquals(FileOpResult.Status.OK, result.status);
        assertEquals("IMG_0001.jpg", store.query(item).displayName);
    }

    @Test
    public void movingAVanishedItemFails() {
        FileOpResult result = engine.move(FakeMediaStore.uriOf(424242), "Pictures/Test");

        assertEquals(FileOpResult.Status.FAILED, result.status);
        assertNull(result.resultUri);
    }
}
