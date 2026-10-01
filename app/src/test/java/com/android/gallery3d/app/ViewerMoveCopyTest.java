package com.android.gallery3d.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.gallery3d.fileops.FileOpBatch.Kind;

import org.junit.Test;

/**
 * Move to… and Copy to… from the single-photo viewer run the same FileOpService
 * batch as the grid. A Move takes the photo out of the folder on show, so the
 * viewer leaves the way Delete does; a Copy leaves the original in place, so
 * the viewer stays on it.
 */
public class ViewerMoveCopyTest {

    @Test
    public void aMoveLeavesTheViewerLikeDelete() {
        assertTrue(PhotoPage.leavesViewerAfter(Kind.MOVE));
        assertTrue(PhotoPage.leavesViewerAfter(Kind.TRASH));
        assertTrue(PhotoPage.leavesViewerAfter(Kind.RESTORE));
        assertTrue(PhotoPage.leavesViewerAfter(Kind.DELETE_FOREVER));
    }

    @Test
    public void aCopyStaysInTheViewer() {
        assertFalse(PhotoPage.leavesViewerAfter(Kind.COPY));
    }

    @Test
    public void favouritingStaysInTheViewer() {
        assertFalse(PhotoPage.leavesViewerAfter(Kind.FAVOURITE));
        assertFalse(PhotoPage.leavesViewerAfter(Kind.UNFAVOURITE));
    }

    @Test
    public void everyKindHasADecision() {
        // Fails to compile-or-run only if a new Kind throws; each must answer.
        for (Kind kind : Kind.values()) {
            PhotoPage.leavesViewerAfter(kind);
        }
    }
}
