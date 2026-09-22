package com.android.gallery3d.fileops;

import android.net.Uri;

import java.io.IOException;
import java.util.List;

/**
 * The only contract FileOpEngine knows about. One real implementation
 * (ContentResolverGateway) and one test double (FakeMediaStore).
 *
 * Every mutator may throw PendingConsentException: the platform is telling us
 * the user has to approve the write in a system dialog first.
 */
public interface MediaStoreGateway {

    /** @return null when the row no longer exists. */
    MediaItemInfo query(Uri item);

    /** Move within the same volume: rewrite RELATIVE_PATH and DISPLAY_NAME in place. */
    void updateLocation(Uri item, String relativePath, String displayName)
            throws PendingConsentException, IOException;

    /**
     * Create a second file at the destination with the same bytes and the same
     * DATE_TAKEN. Used by copy, and by move when source and destination are on
     * different volumes.
     *
     * @return the Uri of the new row.
     */
    Uri copyTo(Uri source, String relativePath, String displayName)
            throws PendingConsentException, IOException;

    void setTrashed(Uri item, boolean trashed)
            throws PendingConsentException, IOException;

    void setFavourite(Uri item, boolean favourite)
            throws PendingConsentException, IOException;

    void deletePermanently(Uri item)
            throws PendingConsentException, IOException;

    /** Display names of the non-trashed items directly in this folder. */
    List<String> displayNamesIn(String relativePath);

    /** Distinct RELATIVE_PATH values at or under the prefix, canonical form, sorted. */
    List<String> folderPathsUnder(String relativePathPrefix);

    /** Non-trashed items at or under the prefix, including sub-folders. */
    List<Uri> itemsUnder(String relativePathPrefix);

    /** Trashed items whose expiry timestamp is at or before the cutoff. */
    List<Uri> trashedOlderThan(long cutoffMillis);

    /** Non-trashed items directly in this folder with DATE_ADDED >= sinceEpochSeconds. */
    List<Uri> itemsAddedSince(String relativePath, long sinceEpochSeconds);

    /**
     * Make an otherwise-empty folder exist by writing a zero-byte placeholder
     * into it. MediaStore has no folder rows, so this is the only way a new
     * empty folder becomes visible.
     *
     * @return the placeholder's uri.
     */
    Uri createPlaceholder(String relativePath) throws PendingConsentException, IOException;
}
