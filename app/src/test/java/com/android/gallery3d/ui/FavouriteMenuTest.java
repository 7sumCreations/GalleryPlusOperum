package com.android.gallery3d.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.gallery3d.R;
import com.android.gallery3d.fileops.FileOpBatch;

import org.junit.Test;

/**
 * The star shows whether the photo (or every selected photo) is a favourite,
 * and a tap does the opposite.
 */
public class FavouriteMenuTest {

    @Test
    public void aFavouriteShowsAFilledStarAndOffersRemove() {
        assertEquals(android.R.drawable.btn_star_big_on, FavouriteMenu.iconFor(true));
        assertEquals(R.string.unfavourite, FavouriteMenu.titleFor(true));
        assertEquals(FileOpBatch.Kind.UNFAVOURITE, FavouriteMenu.kindFor(true));
    }

    @Test
    public void anOrdinaryPhotoShowsAnOutlineStarAndOffersAdd() {
        assertEquals(android.R.drawable.btn_star_big_off, FavouriteMenu.iconFor(false));
        assertEquals(R.string.add_to_favourites, FavouriteMenu.titleFor(false));
        assertEquals(FileOpBatch.Kind.FAVOURITE, FavouriteMenu.kindFor(false));
    }

    @Test
    public void aSelectionIsFavouriteOnlyWhenEveryItemIs() {
        assertTrue(FavouriteMenu.selectionIsFavourite(3, 3));
        assertFalse("a mixed selection is offered Add",
                FavouriteMenu.selectionIsFavourite(2, 3));
        assertFalse(FavouriteMenu.selectionIsFavourite(0, 3));
        assertFalse("nothing selected", FavouriteMenu.selectionIsFavourite(0, 0));
    }

    @Test
    public void applyToleratesAMissingMenuItem() {
        FavouriteMenu.apply(null, true);
    }
}
