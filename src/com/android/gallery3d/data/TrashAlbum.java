package com.android.gallery3d.data;

import android.net.Uri;
import android.provider.MediaStore;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;

/**
 * A virtual album of every photo and video whose MediaStore IS_TRASHED flag is set.
 *
 * The files have not moved: a trashed row keeps its RELATIVE_PATH and its
 * DATE_TAKEN, which is exactly why Restore can put an item back in its original
 * folder with its original date without storing anything ourselves.
 *
 * Images and videos live in separate MediaStore collections, so this is a merge
 * of two halves ({@link #IMAGE_PATH} and {@link #VIDEO_PATH}), newest first, the
 * same way AOSP builds a folder that holds both. Empty Trash destroys photos and
 * videos alike, so the screen has to show both.
 */
public class TrashAlbum extends LocalMergeAlbum {

    public static final Path PATH = Path.fromString("/local/trash");
    public static final Path IMAGE_PATH = PATH.getChild("image");
    public static final Path VIDEO_PATH = PATH.getChild("video");

    private final GalleryApp mApplication;

    private TrashAlbum(GalleryApp application, MediaSet images, MediaSet videos) {
        super(PATH, DataManager.sDateTakenComparator, new MediaSet[] {images, videos}, 0);
        mApplication = application;
    }

    /**
     * The Trash album and its two halves are bound to fixed Paths, so they must
     * be reused rather than rebuilt: Path.setObject asserts the Path has no live
     * object, and a second construction throws.
     */
    public static MediaSet get(DataManager manager, GalleryApp application) {
        synchronized (DataManager.LOCK) {
            MediaObject cached = manager.peekMediaObject(PATH);
            if (cached != null) return (MediaSet) cached;
            return new TrashAlbum(application,
                    getHalf(manager, application, true),
                    getHalf(manager, application, false));
        }
    }

    /** The trashed-images ({@code isImage}) or trashed-videos half. */
    public static MediaSet getHalf(DataManager manager, GalleryApp application,
            boolean isImage) {
        synchronized (DataManager.LOCK) {
            Path path = isImage ? IMAGE_PATH : VIDEO_PATH;
            MediaObject cached = manager.peekMediaObject(path);
            if (cached != null) return (MediaSet) cached;
            return new TrashedMedia(path, application, isImage);
        }
    }

    @Override
    public String getName() {
        return mApplication.getResources().getString(R.string.trash);
    }

    @Override
    public int getSupportedOperations() {
        // The Trash album itself cannot be renamed, moved or deleted.
        return SUPPORT_INFO;
    }

    @Override
    public void delete() {
        throw new UnsupportedOperationException("The Trash album cannot be deleted");
    }

    /** Every trashed row of one MediaStore collection (images or videos). */
    private static class TrashedMedia extends LocalAlbum {

        TrashedMedia(Path path, GalleryApp application, boolean isImage) {
            super(path, application, 0, isImage,
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
            return SUPPORT_INFO;
        }

        @Override
        public void delete() {
            // LocalAlbum.delete() would trash a folder by RELATIVE_PATH; a half
            // of the Trash has no folder, so this must never run.
            throw new UnsupportedOperationException("The Trash album cannot be deleted");
        }
    }
}
