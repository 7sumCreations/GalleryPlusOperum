package com.android.gallery3d.data;

import android.os.Build;
import android.provider.MediaStore;

/**
 * MediaStore's IS_FAVORITE flag as part of LocalImage / LocalVideo's
 * projection, so a loaded item already knows whether it is a favourite and the
 * viewer never has to query MediaStore on the UI thread to draw its star.
 *
 * IS_FAVORITE only exists from Android 11 (API 30). On Android 10 an unknown
 * column would fail every album query (a blank grid), so there the slot reads
 * the always-present _ID instead and the value is ignored: nothing is a
 * favourite there.
 */
public final class FavouriteColumn {

    static final int FIRST_SDK = Build.VERSION_CODES.R;

    private FavouriteColumn() {
    }

    /** The projection entry for the favourite slot on this platform version. */
    static String column(int sdk) {
        return sdk >= FIRST_SDK ? MediaStore.MediaColumns.IS_FAVORITE
                : MediaStore.MediaColumns._ID;
    }

    /** Reads the slot's raw value: only a non-zero IS_FAVORITE is a favourite. */
    static boolean isFavourite(int sdk, int raw) {
        return sdk >= FIRST_SDK && raw != 0;
    }

    static String column() {
        return column(Build.VERSION.SDK_INT);
    }

    static boolean isFavourite(int raw) {
        return isFavourite(Build.VERSION.SDK_INT, raw);
    }
}
