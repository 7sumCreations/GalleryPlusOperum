package com.android.gallery3d.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import org.junit.Test;

/** The honest note under the Auto-file switch (F-025). */
public class GallerySettingsPermissionNoteTest {

    @Test
    public void shownWhenAutoFileIsOnWithoutManageMedia() {
        assertTrue(GallerySettings.shouldShowPermissionNote(
                Build.VERSION_CODES.TIRAMISU, true, false));
    }

    @Test
    public void hiddenOnceManageMediaIsAllowed() {
        assertFalse(GallerySettings.shouldShowPermissionNote(
                Build.VERSION_CODES.TIRAMISU, true, true));
    }

    @Test
    public void hiddenWhileAutoFileIsOff() {
        assertFalse(GallerySettings.shouldShowPermissionNote(
                Build.VERSION_CODES.TIRAMISU, false, false));
    }

    @Test
    public void hiddenBelowAndroid12WhereThereIsNoSuchSetting() {
        assertFalse(GallerySettings.shouldShowPermissionNote(
                Build.VERSION_CODES.R, true, false));
    }
}
