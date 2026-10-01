package com.android.gallery3d.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * LocalImage / LocalVideo load IS_FAVORITE with the rest of the row so the
 * viewer's star can show the real state without querying on the UI thread.
 * Android 10 has no such column, and an unknown column would blank every album.
 */
public class FavouriteColumnTest {

    @Test
    public void android11AndLaterReadIsFavorite() {
        assertEquals("is_favorite", FavouriteColumn.column(30));
        assertEquals("is_favorite", FavouriteColumn.column(33));
    }

    @Test
    public void android10ReadsAColumnThatExists() {
        assertEquals("_id", FavouriteColumn.column(29));
    }

    @Test
    public void theFlagIsTheRawValue() {
        assertTrue(FavouriteColumn.isFavourite(33, 1));
        assertFalse(FavouriteColumn.isFavourite(33, 0));
    }

    @Test
    public void onAndroid10NothingIsAFavourite() {
        // The slot holds _ID there, which is never 0.
        assertFalse(FavouriteColumn.isFavourite(29, 1234));
    }
}
