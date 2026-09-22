package com.android.gallery3d.data;

import android.net.Uri;
import android.provider.MediaStore;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;

/**
 * A virtual album of every item whose MediaStore IS_TRASHED flag is set.
 *
 * The files have not moved: a trashed row keeps its RELATIVE_PATH and its
 * DATE_TAKEN, which is exactly why Restore can put a photo back in its original
 * folder with its original date without storing anything ourselves.
 */
public class TrashAlbum extends LocalAlbum {

    public static final Path PATH = Path.fromString("/local/trash");

    public TrashAlbum(Path path, GalleryApp application) {
        super(path, application, 0, true,
                application.getResources().getString(R.string.trash));
    }

    @Override
    protected String getWhereClause() {
        return MediaStore.MediaColumns.IS_TRASHED + " = 1";
    }

    @Override
    protected String[] getWhereArgs() {
        return new String[0];
    }

    @Override
    protected Uri getQueryUri() {
        return mBaseUri.buildUpon()
                .appendQueryParameter(MediaStore.QUERY_ARG_MATCH_TRASHED, "include")
                .build();
    }

    @Override
    public int getSupportedOperations() {
        // The Trash album itself cannot be renamed, moved or deleted.
        return SUPPORT_INFO;
    }
}
