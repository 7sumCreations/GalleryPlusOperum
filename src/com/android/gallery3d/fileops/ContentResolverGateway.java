package com.android.gallery3d.fileops;

import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Supplier;

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

    /**
     * Query args that also return trashed and pending rows.
     *
     * MediaProvider reads QUERY_ARG_MATCH_TRASHED / QUERY_ARG_MATCH_PENDING only
     * from the query-args Bundle, as ints; as uri query parameters they are
     * ignored and hidden rows stay hidden.
     */
    private static Bundle includeHidden(String selection, String[] args, int matchTrashed) {
        return MediaQueryArgs.matchPending(
                MediaQueryArgs.matchTrashed(
                        MediaQueryArgs.sql(selection, args, null), matchTrashed),
                MediaStore.MATCH_INCLUDE);
    }

    @Override
    public MediaItemInfo query(Uri item) {
        // The interface has no checked exception here, and null already means
        // "gone" to the engine: a provider failure is logged and reported as that.
        try {
            // An item uri already matches trashed and pending rows in MediaProvider;
            // the explicit args keep that true for any uri shape.
            Cursor cursor = mResolver.query(item, PROJECTION,
                    includeHidden(null, null, MediaStore.MATCH_INCLUDE), null);
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
        } catch (RuntimeException failure) {
            Log.w(TAG, "Query failed for " + item, failure);
            return null;
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

        // Images rejects video MIME types, so a video copy has to go into the
        // Video collection. Both allow DCIM/ and Pictures/, which is all the
        // engine ever passes. A missing MIME type keeps the old Images path.
        Uri collection = isVideo(info.mimeType)
                ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri destinationUri;
        try {
            destinationUri = mResolver.insert(collection, values);
        } catch (SecurityException security) {
            throw new IOException("Not permitted to create a copy at " + destination
                    + ": " + security.getMessage());
        } catch (IllegalArgumentException bad) {
            // MediaStore refuses paths or MIME types the collection does not
            // allow. Surface it as a failure, never as a crash.
            throw new IOException(bad.getMessage());
        }
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
            // Close here, not quietly: some providers only report a failed write
            // when the stream closes, and a swallowed error would publish a
            // truncated file. The finally block's close is then a no-op.
            out.close();
        } catch (SecurityException security) {
            deleteQuietly(destinationUri);
            throw consentFor(security, source);
        } catch (IOException failure) {
            deleteQuietly(destinationUri);
            throw failure;
        } catch (RuntimeException failure) {
            // IllegalArgumentException for a uri the provider rejects, or any
            // other provider failure: fail this item, keep the batch going.
            deleteQuietly(destinationUri);
            throw new IOException("Could not copy " + source + ": " + failure, failure);
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }

        ContentValues published = new ContentValues();
        published.put(MediaStore.MediaColumns.IS_PENDING, 0);
        // DATE_TAKEN has to be re-asserted: the provider rewrites it when the
        // pending flag clears and it re-scans the file's EXIF.
        published.put(MediaStore.MediaColumns.DATE_TAKEN, info.dateTakenMillis);
        int rows;
        try {
            rows = mResolver.update(destinationUri, published, null, null);
        } catch (RuntimeException failure) {
            deleteQuietly(destinationUri);
            throw new IOException("Could not publish the copy at " + destinationUri
                    + ": " + failure, failure);
        }
        if (rows == 0) {
            // Still pending, so invisible: never report it as done, or a
            // cross-volume move would delete the source.
            deleteQuietly(destinationUri);
            throw new IOException("Could not publish the copy at " + destinationUri
                    + ": no row updated");
        }
        return destinationUri;
    }

    private static boolean isVideo(String mimeType) {
        return mimeType != null && mimeType.regionMatches(true, 0, "video/", 0, 6);
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
            throw consentRequest("trash", item, () -> MediaStore.createTrashRequest(
                    mResolver, Collections.singletonList(item), trashed));
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
            throw consentRequest("favourite", item, () -> MediaStore.createFavoriteRequest(
                    mResolver, Collections.singletonList(item), favourite));
        }
    }

    @Override
    public void deletePermanently(Uri item) throws PendingConsentException, IOException {
        try {
            mResolver.delete(item, null, null);
        } catch (SecurityException security) {
            throw consentRequest("delete", item, () -> MediaStore.createDeleteRequest(
                    mResolver, Collections.singletonList(item)));
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
        // DATE_EXPIRES is set by MediaProvider to (time trashed + 30 days) when
        // IS_TRASHED goes to 1, and cleared on restore. Pending rows carry one
        // too (7 days), so the IS_TRASHED clause keeps them out of the purge.
        return queryUris(MediaStore.MediaColumns.IS_TRASHED + " = 1 AND "
                        + MediaStore.MediaColumns.DATE_EXPIRES + " <= ?",
                new String[]{String.valueOf(cutoffMillis / 1000L)}, true);
    }

    /**
     * Photos only, by design: F-025 auto-files new camera photos. Videos in
     * the watched folder are left where they are.
     */
    @Override
    public List<Uri> itemsAddedSince(String relativePath, long sinceEpochSeconds) {
        return queryUris(
                MediaStore.MediaColumns.RELATIVE_PATH + " = ? AND "
                        + MediaStore.MediaColumns.DATE_ADDED + " >= ?",
                new String[]{RelativePaths.normalise(relativePath),
                        String.valueOf(sinceEpochSeconds)},
                false, IMAGES_ONLY);
    }

    /**
     * Name of the zero-byte file that keeps a new empty folder visible.
     *
     * It must carry an image extension and be inserted into the Images
     * collection. The files collection permits only Download/ and Documents/ as
     * primary directories, so inserting into Pictures/ or DCIM/ through it is
     * rejected outright with IllegalArgumentException.
     */
    public static final String PLACEHOLDER_NAME = ".nomedia_placeholder.jpg";

    @Override
    public Uri createPlaceholder(String relativePath)
            throws PendingConsentException, IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, PLACEHOLDER_NAME);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                RelativePaths.normalise(relativePath));
        Uri collection =
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri;
        try {
            uri = mResolver.insert(collection, values);
        } catch (SecurityException security) {
            throw new IOException("Not permitted to create a folder at " + relativePath
                    + ": " + security.getMessage());
        } catch (IllegalArgumentException bad) {
            // MediaStore refuses paths outside a collection's allowed primary
            // directories. Surface it as a failure, never as a crash.
            throw new IOException(bad.getMessage());
        }
        if (uri == null) {
            throw new IOException("Could not create a folder at " + relativePath);
        }
        OutputStream out = null;
        try {
            out = mResolver.openOutputStream(uri);
            if (out != null) out.flush();
        } catch (SecurityException security) {
            throw new IOException("Could not write the folder placeholder: "
                    + security.getMessage());
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

    private PendingConsentException consentFor(SecurityException security, Uri item)
            throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && security instanceof RecoverableSecurityException) {
            try {
                return new PendingConsentException(((RecoverableSecurityException) security)
                        .getUserAction().getActionIntent().getIntentSender());
            } catch (RuntimeException failure) {
                throw new IOException("Could not get the consent action for " + item
                        + ": " + failure, failure);
            }
        }
        Log.w(TAG, "Falling back to createWriteRequest for " + item, security);
        return consentRequest("write", item, () -> MediaStore.createWriteRequest(
                mResolver, Collections.singletonList(item)));
    }

    /**
     * Builds the system consent dialog for one item. The create*Request APIs
     * exist only on API 30+, and they throw for uris MediaStore rejects; both
     * cases become an IOException so the item fails and the batch carries on.
     */
    private static PendingConsentException consentRequest(String what, Uri item,
            Supplier<PendingIntent> request) throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw new IOException("Cannot ask for " + what + " consent for " + item
                    + " on API " + Build.VERSION.SDK_INT);
        }
        try {
            return new PendingConsentException(request.get().getIntentSender());
        } catch (RuntimeException failure) {
            throw new IOException("Could not build the " + what + " request for " + item
                    + ": " + failure, failure);
        }
    }

    /** MEDIA_TYPE filter for every list query that must see photos and videos. */
    private static final String IMAGES_AND_VIDEOS =
            MediaStore.Files.FileColumns.MEDIA_TYPE + " IN ("
                    + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + ","
                    + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")";

    /** MEDIA_TYPE filter for queries that are deliberately about photos only. */
    private static final String IMAGES_ONLY =
            MediaStore.Files.FileColumns.MEDIA_TYPE + " = "
                    + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;

    /**
     * Restricts a caller's selection to the given media types. The caller's
     * clause is parenthesised so an OR inside it cannot escape the filter.
     */
    private static String withMediaTypes(String selection, String mediaTypes) {
        if (selection == null || selection.isEmpty()) return mediaTypes;
        return "(" + selection + ") AND " + mediaTypes;
    }

    /**
     * Queries the Files collection, which holds both images and videos, so a
     * folder operation or name check sees every item in a folder.
     */
    private List<String> queryStrings(String column, String selection, String[] args) {
        List<String> values = new ArrayList<String>();
        try {
            Cursor cursor = mResolver.query(
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                    new String[]{column}, withMediaTypes(selection, IMAGES_AND_VIDEOS),
                    args, null);
            if (cursor == null) return values;
            try {
                while (cursor.moveToNext()) {
                    String value = cursor.getString(0);
                    if (value != null) values.add(value);
                }
            } finally {
                cursor.close();
            }
        } catch (RuntimeException failure) {
            Log.w(TAG, "Query for " + column + " failed", failure);
            return new ArrayList<String>();
        }
        return values;
    }

    private List<Uri> queryUris(String selection, String[] args, boolean trashedOnly) {
        return queryUris(selection, args, trashedOnly, IMAGES_AND_VIDEOS);
    }

    /**
     * Queries the Files collection and hands back each row's uri in its own
     * per-type collection (Images or Video): the rest of the code, and the
     * create*Request consent APIs, need media uris, not Files uris.
     */
    private List<Uri> queryUris(String selection, String[] args, boolean trashedOnly,
            String mediaTypes) {
        List<Uri> uris = new ArrayList<Uri>();
        try {
            Uri collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
            String filtered = withMediaTypes(selection, mediaTypes);
            Bundle queryArgs = trashedOnly
                    ? includeHidden(filtered, args, MediaStore.MATCH_ONLY)
                    : MediaQueryArgs.sql(filtered, args, null);
            Cursor cursor = mResolver.query(collection,
                    new String[]{
                            MediaStore.MediaColumns._ID,
                            MediaStore.Files.FileColumns.MEDIA_TYPE},
                    queryArgs, null);
            if (cursor == null) return uris;
            try {
                while (cursor.moveToNext()) {
                    Uri base = cursor.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                            ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    uris.add(Uri.withAppendedPath(base, String.valueOf(cursor.getLong(0))));
                }
            } finally {
                cursor.close();
            }
        } catch (RuntimeException failure) {
            Log.w(TAG, "Uri query failed", failure);
            return new ArrayList<Uri>();
        }
        return uris;
    }

    /** Removes a half-made destination row without masking the error in flight. */
    private void deleteQuietly(Uri uri) {
        try {
            mResolver.delete(uri, null, null);
        } catch (RuntimeException failure) {
            Log.w(TAG, "Could not clean up " + uri, failure);
        }
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
