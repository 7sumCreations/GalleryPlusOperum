package com.android.gallery3d.filtershow.tools;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EditedCopiesTest {

    private static final List<String> NONE = Collections.emptyList();

    // --- target folder ---

    @Test
    public void cameraPhotoCopyStaysInTheCameraFolder() {
        assertEquals("DCIM/Camera/", EditedCopies.targetFolder("DCIM/Camera/"));
    }

    @Test
    public void picturesSubfolderIsKept() {
        assertEquals("Pictures/Trips/Lisbon/",
                EditedCopies.targetFolder("Pictures/Trips/Lisbon"));
    }

    @Test
    public void folderOutsideTheImagesRootsFallsBack() {
        // The Images collection refuses e.g. Download/ or an app's own folder.
        assertEquals(EditedCopies.FALLBACK_FOLDER, EditedCopies.targetFolder("Download/"));
        assertEquals(EditedCopies.FALLBACK_FOLDER, EditedCopies.targetFolder("Signal/Media/"));
        assertEquals(EditedCopies.FALLBACK_FOLDER, EditedCopies.targetFolder("Movies/"));
    }

    @Test
    public void unknownFolderFallsBack() {
        // Foreign content (not in MediaStore) has no RELATIVE_PATH.
        assertEquals(EditedCopies.FALLBACK_FOLDER, EditedCopies.targetFolder(null));
        assertEquals(EditedCopies.FALLBACK_FOLDER, EditedCopies.targetFolder(""));
    }

    @Test
    public void hiddenFolderFallsBack() {
        assertEquals(EditedCopies.FALLBACK_FOLDER, EditedCopies.targetFolder("DCIM/Camera/.aux/"));
    }

    @Test
    public void fallbackIsItselfAnImagesFolder() {
        assertEquals(EditedCopies.FALLBACK_FOLDER,
                EditedCopies.targetFolder(EditedCopies.FALLBACK_FOLDER));
    }

    // --- copy name ---

    @Test
    public void copyIsNamedAfterTheSource() {
        assertEquals("PXL_20261001_101010123_edited.jpg",
                EditedCopies.copyName("PXL_20261001_101010123.jpg", "jpg", NONE, 0L));
    }

    @Test
    public void extensionFollowsTheOutputFormat() {
        assertEquals("IMG_1_edited.jpg", EditedCopies.copyName("IMG_1.heic", "jpg", NONE, 0L));
        assertEquals("IMG_1_edited.png", EditedCopies.copyName("IMG_1.jpg", "png", NONE, 0L));
    }

    @Test
    public void secondEditOfTheSamePhotoKeepsBoth() {
        assertEquals("IMG_1_edited (1).jpg",
                EditedCopies.copyName("IMG_1.jpg", "jpg", Arrays.asList("IMG_1_edited.jpg"), 0L));
    }

    @Test
    public void clashIsCaseInsensitiveLikeTheProvider() {
        assertEquals("IMG_1_edited (1).jpg",
                EditedCopies.copyName("IMG_1.jpg", "jpg", Arrays.asList("img_1_EDITED.JPG"), 0L));
    }

    @Test
    public void reEditingACopyDoesNotStackSuffixes() {
        List<String> taken = Arrays.asList("IMG_1.jpg", "IMG_1_edited.jpg");
        assertEquals("IMG_1_edited (1).jpg",
                EditedCopies.copyName("IMG_1_edited.jpg", "jpg", taken, 0L));
        assertEquals("IMG_1_edited (2).jpg",
                EditedCopies.copyName("IMG_1_edited (1).jpg", "jpg",
                        Arrays.asList("IMG_1_edited.jpg", "IMG_1_edited (1).jpg"), 0L));
    }

    @Test
    public void panoramaPrefixIsKept() {
        assertEquals("PANO_20261001_edited.jpg",
                EditedCopies.copyName("PANO_20261001.jpg", "jpg", NONE, 0L));
    }

    @Test
    public void missingNameGetsATimestampName() {
        long noonUtc = 1790000000000L;
        String name = EditedCopies.copyName(null, "jpg", NONE, noonUtc);
        assertEquals(true, name.matches("IMG_\\d{8}_\\d{6}_edited\\.jpg"));
    }

    @Test
    public void dotOnlyNameGetsATimestampName() {
        String name = EditedCopies.copyName(".jpg", "jpg", NONE, 0L);
        assertEquals(true, name.matches("IMG_\\d{8}_\\d{6}_edited\\.jpg"));
    }

    @Test
    public void nameWithoutExtensionStillGetsOne() {
        assertEquals("photo_edited.jpg", EditedCopies.copyName("photo", "jpg", NONE, 0L));
    }
}
