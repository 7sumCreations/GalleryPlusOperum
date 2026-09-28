package com.android.gallery3d.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import com.android.gallery3d.R;

import org.junit.Test;

/** SDK gating and summary choice for the opt-in MANAGE_MEDIA entry (F-026). */
public class GallerySettingsMediaAccessTest {

    @Test
    public void mediaAccessIsHiddenBelowAndroid12() {
        assertFalse(GallerySettings.isMediaAccessSupported(Build.VERSION_CODES.Q));
        assertFalse(GallerySettings.isMediaAccessSupported(Build.VERSION_CODES.R));
    }

    @Test
    public void mediaAccessIsShownFromAndroid12() {
        assertTrue(GallerySettings.isMediaAccessSupported(Build.VERSION_CODES.S));
        assertTrue(GallerySettings.isMediaAccessSupported(Build.VERSION_CODES.TIRAMISU));
    }

    @Test
    public void summaryFollowsTheRealGrant() {
        assertEquals(R.string.media_access_manage_summary_allowed,
                GallerySettings.mediaAccessSummaryRes(true));
        assertEquals(R.string.media_access_manage_summary_not_allowed,
                GallerySettings.mediaAccessSummaryRes(false));
        assertNotEquals(GallerySettings.mediaAccessSummaryRes(true),
                GallerySettings.mediaAccessSummaryRes(false));
    }
}
