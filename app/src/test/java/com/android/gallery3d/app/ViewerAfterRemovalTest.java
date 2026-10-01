package com.android.gallery3d.app;

import static org.junit.Assert.assertEquals;

import com.android.gallery3d.app.PhotoPage.AfterRemoval;

import org.junit.Test;

/**
 * Delete, Restore and Delete forever from the single-photo viewer take the
 * item out of the album on show. The viewer then returns to the grid it was
 * opened from rather than show a neighbour (or, in Trash, a blank page).
 */
public class ViewerAfterRemovalTest {

    @Test
    public void openedFromAGridGoesBackToIt() {
        // AlbumPage (an ordinary album or Trash) started the viewer for result.
        assertEquals(AfterRemoval.FINISH_PAGE,
                PhotoPage.afterRemoval(false, 3, false, false, true));
    }

    @Test
    public void aFilmstripThatReplacedItsGridRebuildsTheGrid() {
        assertEquals(AfterRemoval.OPEN_ALBUM,
                PhotoPage.afterRemoval(false, 2, true, false, true));
    }

    @Test
    public void aViewerLaunchedWithUpSemanticsOpensItsAlbum() {
        assertEquals(AfterRemoval.OPEN_ALBUM,
                PhotoPage.afterRemoval(false, 1, false, true, true));
    }

    @Test
    public void aPlainViewIntentStaysAndMovesToTheNeighbour() {
        // Back would leave the app, so there is no grid to return to.
        assertEquals(AfterRemoval.STAY,
                PhotoPage.afterRemoval(false, 1, false, false, true));
    }

    @Test
    public void aLoneItemWithNoAlbumFinishesTheActivity() {
        assertEquals(AfterRemoval.FINISH_ACTIVITY,
                PhotoPage.afterRemoval(false, 1, false, false, false));
    }

    @Test
    public void theCameraFilmstripAndSecureAlbumKeepTheirBehaviour() {
        assertEquals(AfterRemoval.STAY,
                PhotoPage.afterRemoval(true, 3, false, false, true));
        assertEquals(AfterRemoval.STAY,
                PhotoPage.afterRemoval(true, 1, true, true, true));
    }

    @Test
    public void removingTheLastItemMovesFocusOntoTheNewLastItem() {
        // Five items, the fifth (index 4) removed: focus must land on index 3,
        // not stay on 4, which no longer exists and drew a blank viewer.
        assertEquals(3, PhotoDataAdapter.clampFocusIndex(4, 4));
    }

    @Test
    public void removingAnEarlierItemKeepsTheIndex() {
        assertEquals(2, PhotoDataAdapter.clampFocusIndex(2, 4));
    }

    @Test
    public void anEmptyAlbumLeavesTheIndexAlone() {
        assertEquals(0, PhotoDataAdapter.clampFocusIndex(0, 0));
    }
}
