package com.android.gallery3d.ui;

import android.view.MenuItem;

import com.android.gallery3d.R;
import com.android.gallery3d.fileops.FileOpBatch;

/**
 * The Favourite star in the viewer and in the selection menu: what it shows
 * and what tapping it does, from whether the item (or every selected item) is
 * already a favourite.
 */
public final class FavouriteMenu {

    private FavouriteMenu() {
    }

    /** Filled star for a favourite, outline star otherwise. */
    public static int iconFor(boolean favourite) {
        return favourite ? android.R.drawable.btn_star_big_on
                : android.R.drawable.btn_star_big_off;
    }

    /** The title says what a tap will do. */
    public static int titleFor(boolean favourite) {
        return favourite ? R.string.unfavourite : R.string.add_to_favourites;
    }

    /** A tap toggles: a favourite is removed, anything else is added. */
    public static FileOpBatch.Kind kindFor(boolean favourite) {
        return favourite ? FileOpBatch.Kind.UNFAVOURITE : FileOpBatch.Kind.FAVOURITE;
    }

    /**
     * A selection counts as favourite only when it is non-empty and every
     * selected item is one; any other selection is offered Add.
     */
    public static boolean selectionIsFavourite(int favourites, int selected) {
        return selected > 0 && favourites == selected;
    }

    /** Show the state on the menu item, if the menu has one. */
    public static void apply(MenuItem item, boolean favourite) {
        if (item == null) return;
        item.setIcon(iconFor(favourite));
        item.setTitle(titleFor(favourite));
    }
}
