package com.android.gallery3d.fileops;

import java.util.Locale;

/**
 * Turns a MediaStore BUCKET_ID back into a RELATIVE_PATH without walking the
 * filesystem.
 *
 * MediaStore derives BUCKET_ID as the lower-cased absolute directory path's
 * String.hashCode(). AOSP recovers the path by recursively listing every
 * directory on external storage and hashing each one, which needs
 * MANAGE_EXTERNAL_STORAGE under scoped storage. Instead we hash the handful of
 * RELATIVE_PATH values MediaStore already knows about and compare.
 */
public final class BucketPathResolver {

    /** Absolute prefix MediaStore hashes; RELATIVE_PATH values sit under it. */
    private static final String STORAGE_PREFIX = "/storage/emulated/0/";

    private BucketPathResolver() {
    }

    /** The bucket id MediaStore would assign to this relative path. */
    public static int bucketIdOf(String relativePath) {
        String normalised = RelativePaths.normalise(relativePath);
        // MediaStore hashes the directory path with no trailing slash.
        String withoutTrailing = normalised.isEmpty()
                ? "" : normalised.substring(0, normalised.length() - 1);
        return (STORAGE_PREFIX + withoutTrailing).toLowerCase(Locale.US).hashCode();
    }

    /** @return the canonical relative path, or "" when no known folder matches. */
    public static String resolve(MediaStoreGateway gateway, int bucketId) {
        for (String path : gateway.folderPathsUnder("")) {
            if (bucketIdOf(path) == bucketId) return path;
        }
        return "";
    }
}
