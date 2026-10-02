package com.android.gallery3d.filtershow.tools;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.android.gallery3d.fileops.ContentResolverGateway;
import com.android.gallery3d.fileops.MediaItemInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

/**
 * Publishes an edited image as a NEW row in the Images collection.
 *
 * The source photo is only ever read (its folder, name and date taken):
 * Android refuses writes to photos another app owns (the Camera's, for one)
 * with a RecoverableSecurityException, and the owner's rule is that an edit
 * never changes the original anyway.
 *
 * Every provider failure (SecurityException, IllegalArgumentException,
 * IllegalStateException...) is turned into an IOException here, and a
 * half-written pending row is deleted, so callers only handle one type.
 */
public final class EditedCopyWriter {

    private static final String LOGTAG = "EditedCopyWriter";

    private EditedCopyWriter() {
    }

    /** Where a copy will go and what it will be called. */
    public static final class Target {
        public final String relativePath;
        public final String displayName;
        public final String volumeName;
        /** DATE_TAKEN for the copy, in milliseconds. */
        public final long dateTakenMillis;

        Target(String relativePath, String displayName, String volumeName,
                long dateTakenMillis) {
            this.relativePath = relativePath;
            this.displayName = displayName;
            this.volumeName = volumeName;
            this.dateTakenMillis = dateTakenMillis;
        }
    }

    /** What is known about the source in MediaStore, or null (foreign content, gone). */
    private static MediaItemInfo describe(Context context, Uri source) {
        if (source == null || !ContentResolver.SCHEME_CONTENT.equals(source.getScheme())
                || !MediaStore.AUTHORITY.equals(source.getAuthority())) {
            return null;
        }
        // The gateway's query already logs and swallows provider failures.
        return new ContentResolverGateway(context).query(source);
    }

    /** The folder the copy of {@code source} goes to. Never throws. */
    public static String targetFolder(Context context, Uri source) {
        MediaItemInfo info = describe(context, source);
        return EditedCopies.targetFolder(info == null ? null : info.relativePath);
    }

    /** Plan a copy of {@code source} with the given extension ("jpg", "png"). Never throws. */
    public static Target plan(Context context, Uri source, String extension) {
        long now = System.currentTimeMillis();
        MediaItemInfo info = describe(context, source);
        String folder = EditedCopies.targetFolder(info == null ? null : info.relativePath);
        List<String> taken;
        try {
            taken = new ContentResolverGateway(context).displayNamesIn(folder);
        } catch (RuntimeException failure) {
            taken = Collections.emptyList();
        }
        String name = EditedCopies.copyName(info == null ? null : info.displayName,
                extension, taken, now);
        String volume = info != null && info.volumeName != null
                ? info.volumeName : MediaStore.VOLUME_EXTERNAL_PRIMARY;
        long dateTaken = info != null && info.hasDateTaken() ? info.dateTakenMillis : now;
        return new Target(folder, name, volume, dateTaken);
    }

    /** Insert the copy's row with IS_PENDING=1. The caller writes it, then {@link #publish}. */
    public static Uri insertPending(Context context, Target target, String mimeType)
            throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, target.displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, target.relativePath);
        values.put(MediaStore.MediaColumns.DATE_TAKEN, target.dateTakenMillis);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri inserted;
        try {
            Uri collection = MediaStore.Images.Media.getContentUri(target.volumeName);
            inserted = context.getContentResolver().insert(collection, values);
        } catch (RuntimeException failure) {
            // SecurityException, or IllegalArgumentException for a folder or
            // volume the Images collection refuses.
            throw new IOException("Could not create the edited copy in "
                    + target.relativePath + ": " + failure, failure);
        }
        if (inserted == null) {
            throw new IOException("Could not create the edited copy in " + target.relativePath);
        }
        return inserted;
    }

    /** Clear IS_PENDING so the copy shows up. Deletes the row if that fails. */
    public static void publish(Context context, Uri pending, long dateTakenMillis)
            throws IOException {
        ContentValues published = new ContentValues();
        published.put(MediaStore.MediaColumns.IS_PENDING, 0);
        // Re-asserted: the provider re-scans EXIF when the pending flag clears.
        published.put(MediaStore.MediaColumns.DATE_TAKEN, dateTakenMillis);
        int rows;
        try {
            rows = context.getContentResolver().update(pending, published, null, null);
        } catch (RuntimeException failure) {
            deleteQuietly(context, pending);
            throw new IOException("Could not publish " + pending + ": " + failure, failure);
        }
        if (rows == 0) {
            deleteQuietly(context, pending);
            throw new IOException("Could not publish " + pending + ": no row updated");
        }
    }

    /** Insert a pending row, copy {@code rendered} into it, publish it. */
    public static Uri saveFile(Context context, Target target, File rendered, String mimeType)
            throws IOException {
        Uri pending = insertPending(context, target, mimeType);
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(rendered);
            out = context.getContentResolver().openOutputStream(pending);
            if (out == null) throw new IOException("No output stream for " + pending);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            out.flush();
            // Closed here, not quietly: a provider may only report a failed
            // write on close, and a swallowed error would publish a truncated file.
            out.close();
            out = null;
        } catch (IOException failure) {
            closeQuietly(out);
            out = null;
            deleteQuietly(context, pending);
            throw failure;
        } catch (RuntimeException failure) {
            closeQuietly(out);
            out = null;
            deleteQuietly(context, pending);
            throw new IOException("Could not write " + pending + ": " + failure, failure);
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
        publish(context, pending, target.dateTakenMillis);
        return galleryUri(pending);
    }

    /**
     * The "external" form of an inserted uri. Insert returns a per-volume uri
     * (external_primary/...), but the gallery's LocalSource only matches
     * external/images/media/#, which covers every volume.
     */
    public static Uri galleryUri(Uri inserted) {
        try {
            return ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentUris.parseId(inserted));
        } catch (RuntimeException notAnId) {
            return inserted;
        }
    }

    public static void deleteQuietly(Context context, Uri uri) {
        if (uri == null) return;
        try {
            context.getContentResolver().delete(uri, null, null);
        } catch (RuntimeException failure) {
            Log.w(LOGTAG, "Could not clean up " + uri, failure);
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException | RuntimeException ignored) {
            // Already failing or already closed.
        }
    }
}
