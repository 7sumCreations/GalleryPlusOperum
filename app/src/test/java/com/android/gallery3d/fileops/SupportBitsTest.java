package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.gallery3d.data.MediaObject;

import org.junit.Test;

/**
 * Locks the SUPPORT_* bit allocation for Epic 1 so two stories cannot claim
 * the same bit. AOSP uses 1<<0 .. 1<<17; Epic 1 starts at 1<<18.
 */
public class SupportBitsTest {

    @Test
    public void moveUsesBitEighteen() {
        assertEquals(1 << 18, MediaObject.SUPPORT_MOVE);
    }

    @Test
    public void moveDoesNotCollideWithAnyAospBit() {
        int aospBits = MediaObject.SUPPORT_DELETE | MediaObject.SUPPORT_ROTATE
                | MediaObject.SUPPORT_SHARE | MediaObject.SUPPORT_CROP
                | MediaObject.SUPPORT_SHOW_ON_MAP | MediaObject.SUPPORT_SETAS
                | MediaObject.SUPPORT_FULL_IMAGE | MediaObject.SUPPORT_PLAY
                | MediaObject.SUPPORT_CACHE | MediaObject.SUPPORT_EDIT
                | MediaObject.SUPPORT_INFO | MediaObject.SUPPORT_TRIM
                | MediaObject.SUPPORT_UNLOCK | MediaObject.SUPPORT_BACK
                | MediaObject.SUPPORT_ACTION | MediaObject.SUPPORT_CAMERA_SHORTCUT
                | MediaObject.SUPPORT_MUTE | MediaObject.SUPPORT_PRINT;
        assertEquals(0, aospBits & MediaObject.SUPPORT_MOVE);
        assertTrue(MediaObject.SUPPORT_MOVE > MediaObject.SUPPORT_PRINT);
    }

    @Test
    public void folderBitsFollowMoveWithoutOverlapping() {
        assertEquals(1 << 19, MediaObject.SUPPORT_RENAME_FOLDER);
        assertEquals(1 << 20, MediaObject.SUPPORT_MOVE_FOLDER);
        assertEquals(0, MediaObject.SUPPORT_MOVE & MediaObject.SUPPORT_RENAME_FOLDER);
        assertEquals(0, MediaObject.SUPPORT_MOVE_FOLDER & MediaObject.SUPPORT_RENAME_FOLDER);
    }

    @Test
    public void copyUsesBitTwentyOne() {
        assertEquals(1 << 21, MediaObject.SUPPORT_COPY);
        assertEquals(0, MediaObject.SUPPORT_COPY & MediaObject.SUPPORT_MOVE);
        assertEquals(0, MediaObject.SUPPORT_COPY & MediaObject.SUPPORT_MOVE_FOLDER);
    }

    @Test
    public void favouriteUsesBitTwentyTwo() {
        assertEquals(1 << 22, MediaObject.SUPPORT_FAVOURITE);
        assertEquals(0, MediaObject.SUPPORT_FAVOURITE & MediaObject.SUPPORT_COPY);
        assertEquals(0, MediaObject.SUPPORT_FAVOURITE & MediaObject.SUPPORT_MOVE);
    }
}
