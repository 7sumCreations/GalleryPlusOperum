package com.android.gallery3d.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.gallery3d.data.MediaObject;

import org.junit.Test;

/**
 * ActionModeHandler.computeMenuOptions() ANDs the selection's capabilities with
 * SUPPORT_MULTIPLE_MASK whenever more than one item is selected. AOSP's original
 * mask listed only delete/rotate/share/cache, so every capability Epic 1 added
 * was silently stripped the moment a second photo was selected — Move vanished
 * from the overflow menu and AC2 ("given 500 selected photos...") was impossible.
 *
 * These tests fail loudly if a future capability bit is added to MediaObject and
 * someone forgets to decide whether it survives a multi-item selection.
 */
public class MultiSelectMaskTest {

    @Test
    public void everyPerItemVerbSurvivesAMultiItemSelection() {
        assertTrue("Move must survive multi-select (AC2)",
                (ActionModeHandler.SUPPORT_MULTIPLE_MASK & MediaObject.SUPPORT_MOVE) != 0);
        assertTrue("Copy must survive multi-select (AC4)",
                (ActionModeHandler.SUPPORT_MULTIPLE_MASK & MediaObject.SUPPORT_COPY) != 0);
        assertTrue("Favourite must survive multi-select (AC7)",
                (ActionModeHandler.SUPPORT_MULTIPLE_MASK & MediaObject.SUPPORT_FAVOURITE) != 0);
        assertTrue("Restore must survive multi-select (AC6)",
                (ActionModeHandler.SUPPORT_MULTIPLE_MASK & MediaObject.SUPPORT_RESTORE) != 0);
        assertTrue("Delete forever must survive multi-select (AC6)",
                (ActionModeHandler.SUPPORT_MULTIPLE_MASK & MediaObject.SUPPORT_DELETE_FOREVER) != 0);
    }

    @Test
    public void folderVerbsDoNotSurviveAMultiItemSelection() {
        // Renaming or re-parenting acts on one album; offering it for a
        // multi-item selection would be meaningless.
        assertEquals(0,
                ActionModeHandler.SUPPORT_MULTIPLE_MASK & MediaObject.SUPPORT_RENAME_FOLDER);
        assertEquals(0,
                ActionModeHandler.SUPPORT_MULTIPLE_MASK & MediaObject.SUPPORT_MOVE_FOLDER);
    }

    @Test
    public void theAospVerbsAreStillThere() {
        assertTrue((ActionModeHandler.SUPPORT_MULTIPLE_MASK & MediaObject.SUPPORT_DELETE) != 0);
        assertTrue((ActionModeHandler.SUPPORT_MULTIPLE_MASK & MediaObject.SUPPORT_ROTATE) != 0);
        assertTrue((ActionModeHandler.SUPPORT_MULTIPLE_MASK & MediaObject.SUPPORT_SHARE) != 0);
        assertTrue((ActionModeHandler.SUPPORT_MULTIPLE_MASK & MediaObject.SUPPORT_CACHE) != 0);
    }
}
