package com.android.gallery3d.fileops;

import android.app.RecoverableSecurityException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * The one class in Epic 1 that talks to ContentResolver and MediaStore.
 *
 * Every mutator turns the platform's "the user must approve this" signal
 * (RecoverableSecurityException, or a createWriteRequest IntentSender) into a
 * PendingConsentException, so FileOpEngine never has to know about either.
 */
public class ContentResolverGateway implements MediaStoreGateway {

    private static final String TAG = "ContentResolverGateway";

    private static final String[] PROJECTION = {
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.VOLUME_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.IS_TRASHED,
            MediaStore.MediaColumns.IS_FAVORITE,
    };

    private final Context mContext;
    private final ContentResolver mResolver;

    public ContentResolverGateway(Context context) {
        mContext = context.getApplicationContext();
        mResolver = mContext.getContentResolver();
    }

    /** Content uri that also returns trashed and pending rows. */
    private static Uri includeHidden(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(MediaStore.QUERY_ARG_MATCH_TRASHED, "include")
                .appendQueryParameter(MediaStore.QUERY_ARG_MATCH_PENDING, "include")
                .build();
    }

    @Override
    public MediaItemInfo query(Uri item) {
        Cursor cursor = mResolver.query(includeHidden(item), PROJECTION, null, null, null);
        if (cursor == null) return null;
        try {
            if (!cursor.moveToFirst()) return null;
            return new MediaItemInfo(
                    item,
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getLong(3),
                    cursor.getString(4),
                    cursor.getString(5),
                    cursor.getLong(6),
                    cursor.getInt(7) != 0,
                    cursor.getInt(8) != 0);
        } finally {
            cursor.close();
        }
    }

    @Override
    public void updateLocation(Uri item, String relativePath, String displayName)
            throws PendingConsentException, IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                RelativePaths.normalise(relativePath));
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        applyUpdate(item, values);
    }

    @Override
    public Uri copyTo(Uri source, String relativePath, String displayName)
            throws PendingConsentException, IOException {
        MediaItemInfo info = query(source);
        if (info == null) throw new IOException("Source no longer exists: " + source);

        String destination = RelativePaths.normalise(relativePath);

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, info.mimeType);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, destination);
        values.put(MediaStore.MediaColumns.DATE_TAKEN, info.dateTakenMillis);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri collection = MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri destinationUri = mResolver.insert(collection, values);
        if (destinationUri == null) {
            throw new IOException("Could not create a row at " + destination);
        }

        InputStream in = null;
        OutputStream out = null;
        try {
            in = mResolver.openInputStream(source);
            out = mResolver.openOutputStream(destinationUri);
            if (in == null || out == null) {
                throw new IOException("Could not open streams for the copy");
            }
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } catch (SecurityException security) {
            mResolver.delete(destinationUri, null, null);
            throw consentFor(security, source);
        } catch (IOException failure) {
            mResolver.delete(destinationUri, null, null);
            throw failure;
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }

        ContentValues published = new ContentValues();
        published.put(MediaStore.MediaColumns.IS_PENDING, 0);
        // DATE_TAKEN has to be re-asserted: the provider rewrites it when the
        // pending flag clears and it re-scans the file's EXIF.
        published.put(MediaStore.MediaColumns.DATE_TAKEN, info.dateTakenMillis);
        mResolver.update(destinationUri, published, null, null);
        return destinationUri;
    }

    @Override
    public void setTrashed(Uri item, boolean trashed)
            throws PendingConsentException, IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.IS_TRASHED, trashed ? 1 : 0);
        try {
            applyUpdate(item, values);
        } catch (PendingConsentException direct) {
            // MANAGE_MEDIA is not granted: fall back to the system trash dialog,
            // which is the documented path for non-owned items.
            throw new PendingConsentException(MediaStore.createTrashRequest(
                    mResolver, Collections.singletonList(item), trashed).getIntentSender());
        }
    }

    @Override
    public void setFavourite(Uri item, boolean favourite)
            throws PendingConsentException, IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.IS_FAVORITE, favourite ? 1 : 0);
        try {
            applyUpdate(item, values);
        } catch (PendingConsentException direct) {
            throw new PendingConsentException(MediaStore.createFavoriteRequest(
                    mResolver, Collections.singletonList(item), favourite).getIntentSender());
        }
    }

    @Override
    public void deletePermanently(Uri item) throws PendingConsentException, IOException {
        try {
            mResolver.delete(item, null, null);
        } catch (SecurityException security) {
            throw new PendingConsentException(MediaStore.createDeleteRequest(
                    mResolver, Collections.singletonList(item)).getIntentSender());
        }
    }

    @Override
    public List<String> displayNamesIn(String relativePath) {
        return queryStrings(MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH + " = ?",
                new String[]{RelativePaths.normalise(relativePath)});
    }

    @Override
    public List<String> folderPathsUnder(String relativePathPrefix) {
        String prefix = RelativePaths.normalise(relativePathPrefix);
        List<String> raw = queryStrings(MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                new String[]{prefix + "%"});
        TreeSet<String> distinct = new TreeSet<String>();
        for (String path : raw) {
            distinct.add(RelativePaths.normalise(path));
        }
        return new ArrayList<String>(distinct);
    }

    @Override
    public List<Uri> itemsUnder(String relativePathPrefix) {
        String prefix = RelativePaths.normalise(relativePathPrefix);
        return queryUris(MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                new String[]{prefix + "%"}, false);
    }

    @Override
    public List<Uri> trashedOlderThan(long cutoffMillis) {
        return queryUris(MediaStore.MediaColumns.DATE_EXPIRES + " <= ?",
                new String[]{String.valueOf(cutoffMillis / 1000L)}, true);
    }

    @Override
    public List<Uri> itemsAddedSince(String relativePath, long sinceEpochSeconds) {
        return queryUris(
                MediaStore.MediaColumns.RELATIVE_PATH + " = ? AND "
                        + MediaStore.MediaColumns.DATE_ADDED + " >= ?",
                new String[]{RelativePaths.normalise(relativePath),
                        String.valueOf(sinceEpochSeconds)},
                false);
    }

    /** Name used for the zero-byte file that keeps a new empty folder visible. */
    public static final String PLACEHOLDER_NAME = ".nomedia_placeholder";

    @Override
    public Uri createPlaceholder(String relativePath)
            throws PendingConsentException, IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, PLACEHOLDER_NAME);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                RelativePaths.normalise(relativePath));
        Uri collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = mResolver.insert(collection, values);
        if (uri == null) {
            throw new IOException("Could not create a folder at " + relativePath);
        }
        OutputStream out = null;
        try {
            out = mResolver.openOutputStream(uri);
            if (out != null) out.flush();
        } finally {
            closeQuietly(out);
        }
        return uri;
    }

    @Override
    public List<Uri> trashedItems() {
        return queryUris(MediaStore.MediaColumns.IS_TRASHED + " = 1", null, true);
    }

    // ---- internals -------------------------------------------------------

    private void applyUpdate(Uri item, ContentValues values)
            throws PendingConsentException, IOException {
        try {
            int rows = mResolver.update(item, values, null, null);
            if (rows == 0) throw new IOException("No row updated for " + item);
        } catch (SecurityException security) {
            throw consentFor(security, item);
        } catch (IllegalArgumentException bad) {
            throw new IOException(bad.getMessage());
        }
    }

    private PendingConsentException consentFor(SecurityException security, Uri item) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && security instanceof RecoverableSecurityException) {
            return new PendingConsentException(((RecoverableSecurityException) security)
                    .getUserAction().getActionIntent().getIntentSender());
        }
        Log.w(TAG, "Falling back to createWriteRequest for " + item, security);
        return new PendingConsentException(MediaStore.createWriteRequest(
                mResolver, Collections.singletonList(item)).getIntentSender());
    }

    private List<String> queryStrings(String column, String selection, String[] args) {
        List<String> values = new ArrayList<String>();
        Cursor cursor = mResolver.query(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                new String[]{column}, selection, args, null);
        if (cursor == null) return values;
        try {
            while (cursor.moveToNext()) {
                String value = cursor.getString(0);
                if (value != null) values.add(value);
            }
        } finally {
            cursor.close();
        }
        return values;
    }

    private List<Uri> queryUris(String selection, String[] args, boolean trashedOnly) {
        List<Uri> uris = new ArrayList<Uri>();
        Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        if (trashedOnly) collection = includeHidden(collection);
        Cursor cursor = mResolver.query(collection,
                new String[]{MediaStore.MediaColumns._ID}, selection, args, null);
        if (cursor == null) return uris;
        try {
            while (cursor.moveToNext()) {
                uris.add(Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        String.valueOf(cursor.getLong(0))));
            }
        } finally {
            cursor.close();
        }
        return uris;
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Nothing useful to do while unwinding.
        }
    }
}
