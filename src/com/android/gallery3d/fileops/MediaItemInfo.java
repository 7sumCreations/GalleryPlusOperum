package com.android.gallery3d.fileops;

import android.net.Uri;

/** Immutable snapshot of the MediaStore columns the file-op engine reads. */
public final class MediaItemInfo {

    public final Uri uri;
    public final String displayName;
    /** Canonical RELATIVE_PATH, always with a trailing slash. */
    public final String relativePath;
    /** DATE_TAKEN in milliseconds since epoch; 0 when the item has no date taken. */
    public final long dateTakenMillis;
    public final String volumeName;
    public final String mimeType;
    public final long sizeBytes;
    public final boolean trashed;
    public final boolean favourite;

    public MediaItemInfo(Uri uri, String displayName, String relativePath,
            long dateTakenMillis, String volumeName, String mimeType,
            long sizeBytes, boolean trashed, boolean favourite) {
        this.uri = uri;
        this.displayName = displayName;
        this.relativePath = RelativePaths.normalise(relativePath);
        this.dateTakenMillis = dateTakenMillis;
        this.volumeName = volumeName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.trashed = trashed;
        this.favourite = favourite;
    }

    public boolean hasDateTaken() {
        return dateTakenMillis > 0L;
    }

    @Override
    public String toString() {
        return "MediaItemInfo{" + relativePath + displayName + " @" + uri + "}";
    }
}
