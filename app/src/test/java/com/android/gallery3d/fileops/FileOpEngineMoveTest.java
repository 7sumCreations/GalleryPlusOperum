package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FileOpEngineMoveTest {

    /** Counts copyTo calls without touching the shared fake. */
    private static final class CountingStore extends FakeMediaStore {
        int copies;

        @Override
        public Uri copyTo(Uri source, String relativePath, String displayName)
                throws PendingConsentException, java.io.IOException {
            copies++;
            return super.copyTo(source, relativePath, displayName);
        }
    }

    private CountingStore store;
    private FileOpEngine engine;

    @Before
    public void setUp() {
        store = new CountingStore();
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
    public void moveNeverCopiesBytes() {
        Uri item = store.addItem("DCIM/Camera", "VID_0001.mp4", 1790157600000L);
        store.row(item).mimeType = "video/mp4";
        int rowsBefore = store.rowCount();

        FileOpResult result = engine.move(item, "Pictures/Test");

        assertEquals(FileOpResult.Status.OK, result.status);
        assertEquals("a move must not write a copy, not even a throwaway probe",
                0, store.copies);
        assertEquals(rowsBefore, store.rowCount());
        assertEquals(item, result.resultUri);
    }

    @Test
    public void moveOfAnSdCardItemStaysOnTheSdCard() {
        Uri item = store.addItem("DCIM/Camera", "IMG_0001.jpg", 1790157600000L,
                "abcd-1234");

        FileOpResult result = engine.move(item, "Pictures/Test");

        assertEquals(FileOpResult.Status.OK, result.status);
        assertEquals("the row keeps its identity", item, result.resultUri);
        assertEquals(0, store.copies);
        MediaItemInfo after = store.query(item);
        assertEquals("abcd-1234", after.volumeName);
        assertEquals("Pictures/Test/", after.relativePath);
        assertEquals(1790157600000L, after.dateTakenMillis);
        assertEquals("DCIM/Camera/", result.previousRelativePath);
    }

    @Test
    public void aFailedMoveIsAHandledFailureAndLeavesTheSourceWhereItWas() {
        Uri item = store.addItem("DCIM/Camera", "IMG_0001.jpg", 1L);
        store.failWritesFor(item);

        FileOpResult result = engine.move(item, "Pictures/Test");

        assertEquals(FileOpResult.Status.FAILED, result.status);
        assertNotNull(result.failureReason);
        assertNotNull("source must survive a failed move", store.query(item));
        assertEquals("DCIM/Camera/", store.query(item).relativePath);
        assertEquals(0, store.copies);
    }

    @Test
    public void moveSurfacesTheConsentIntentSenderInsteadOfFailing() {
        Uri item = store.addItem("DCIM/Camera", "IMG_0001.jpg", 1L);
        store.requireConsentFor(item, null);

        FileOpResult result = engine.move(item, "Pictures/Test");

        assertEquals(FileOpResult.Status.CONSENT_REQUIRED, result.status);
        assertEquals("DCIM/Camera/", store.query(item).relativePath);
        assertEquals(0, store.copies);
    }

    @Test
    public void aMoveRetriedAfterConsentLandsInPlace() {
        Uri item = store.addItem("DCIM/Camera", "IMG_0001.jpg", 1L);
        store.requireConsentFor(item, null);
        assertEquals(FileOpResult.Status.CONSENT_REQUIRED,
                engine.move(item, "Pictures/Test").status);

        store.grantConsentFor(item);
        FileOpResult retried = engine.move(item, "Pictures/Test");

        assertEquals(FileOpResult.Status.OK, retried.status);
        assertEquals(item, retried.resultUri);
        assertEquals("Pictures/Test/", store.query(item).relativePath);
        assertEquals(0, store.copies);
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
