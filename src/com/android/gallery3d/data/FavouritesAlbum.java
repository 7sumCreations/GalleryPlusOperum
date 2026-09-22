package com.android.gallery3d.data;

import android.net.Uri;
import android.provider.MediaStore;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;

/**
 * A virtual album of every item whose MediaStore IS_FAVORITE flag is set.
 *
 * Not a folder: the files stay exactly where they are. This is the one place in
 * Epic 1 where an album is a query rather than a directory, and it is a query
 * over a platform flag other apps also write.
 */
public class FavouritesAlbum extends LocalAlbum {

    public static final Path PATH = Path.fromString("/local/favourites");

    private final GalleryApp mApplication;

    public FavouritesAlbum(Path path, GalleryApp application) {
        super(path, application, 0, true,
                application.getResources().getString(R.string.favourites));
        mApplication = application;
    }

    @Override
    protected String getWhereClause() {
        return MediaStore.MediaColumns.IS_FAVORITE + " = 1";
    }

    @Override
    protected String[] getWhereArgs() {
        return new String[0];
    }

    @Override
    public int getSupportedOperations() {
        // Favourites is a view, not a folder: it cannot be renamed, moved or deleted.
        return SUPPORT_SHARE | SUPPORT_INFO;
    }
}
