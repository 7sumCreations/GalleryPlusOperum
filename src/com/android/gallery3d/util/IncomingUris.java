package com.android.gallery3d.util;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Decides which uris handed in by other apps this app will open, write or
 * share on their behalf.
 *
 * Exported activities run with this app's permissions. A caller that passes a
 * {@code file:} uri, or a uri served by one of this app's own (unexported)
 * providers, would otherwise get this app to read or write data the caller
 * could never reach itself (a confused deputy). The rules here are pure string
 * checks so they can be unit tested without a device.
 */
public final class IncomingUris {
    private static final String TAG = "IncomingUris";
    private static final String SCHEME_CONTENT = "content";

    private IncomingUris() {}

    /**
     * True when {@code uri} is a {@code content:} uri served by some other app
     * (MediaStore, a document provider, ...). False for {@code file:} and other
     * schemes, null, and anything served by this app's own providers.
     */
    public static boolean isForeignContent(Context context, Uri uri) {
        if (context == null || uri == null) return false;
        String authority = uri.getAuthority();
        return isForeignContent(uri.getScheme(), authority, context.getPackageName(),
                ownerOf(context, authority));
    }

    /**
     * The string form of {@link #isForeignContent(Context, Uri)}.
     *
     * @param ownPackage this app's package name
     * @param authorityOwner the package serving {@code authority}, or null if
     *        unknown (not resolvable or not visible to this app)
     */
    public static boolean isForeignContent(String scheme, String authority,
            String ownPackage, String authorityOwner) {
        if (scheme == null || !SCHEME_CONTENT.equals(scheme.toLowerCase(Locale.ROOT))) {
            return false;
        }
        String bare = stripUserId(authority);
        if (bare == null || bare.isEmpty()) return false;
        if (ownPackage != null && !ownPackage.isEmpty()) {
            String lower = bare.toLowerCase(Locale.ROOT);
            String own = ownPackage.toLowerCase(Locale.ROOT);
            if (lower.equals(own) || lower.startsWith(own + ".")) return false;
            if (ownPackage.equals(authorityOwner)) return false;
        }
        return true;
    }

    /** True only for {@code content://media/...} (MediaStore) uris. */
    public static boolean isMediaStore(Uri uri) {
        return uri != null && isMediaStore(uri.getScheme(), uri.getAuthority());
    }

    public static boolean isMediaStore(String scheme, String authority) {
        if (scheme == null || !SCHEME_CONTENT.equals(scheme.toLowerCase(Locale.ROOT))) {
            return false;
        }
        String bare = stripUserId(authority);
        return MediaStore.AUTHORITY.equals(bare);
    }

    /**
     * True for a {@code file:} uri whose real (symlink-resolved) path lies in
     * shared storage under {@code /storage/}. Never true for this app's
     * private directories, which live under {@code /data/}.
     */
    public static boolean isSharedStorageFile(Uri uri) {
        if (uri == null || uri.getScheme() == null
                || !"file".equals(uri.getScheme().toLowerCase(Locale.ROOT))) {
            return false;
        }
        return isSharedStoragePath(canonical(uri.getPath()));
    }

    /**
     * The string form of {@link #isSharedStorageFile(Uri)}, on a canonical
     * path. App-specific external directories ({@code Android/data},
     * {@code Android/obb}) are excluded: this app's own one is private data.
     */
    public static boolean isSharedStoragePath(String canonicalPath) {
        if (canonicalPath == null || !canonicalPath.startsWith("/storage/")) return false;
        String lower = canonicalPath.toLowerCase(Locale.ROOT);
        return !lower.contains("/android/data/") && !lower.contains("/android/obb/")
                && !lower.endsWith("/android/data") && !lower.endsWith("/android/obb");
    }

    private static String canonical(String path) {
        if (path == null || path.isEmpty()) return null;
        try {
            return new File(path).getCanonicalPath();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Content uris may carry a user id ({@code content://10@media/...}); the
     * provider is looked up by what follows the {@code @}.
     */
    static String stripUserId(String authority) {
        if (authority == null) return null;
        int at = authority.lastIndexOf('@');
        return at >= 0 ? authority.substring(at + 1) : authority;
    }

    private static String ownerOf(Context context, String authority) {
        String bare = stripUserId(authority);
        if (bare == null || bare.isEmpty()) return null;
        try {
            ProviderInfo info = context.getPackageManager().resolveContentProvider(bare, 0);
            return info == null ? null : info.packageName;
        } catch (RuntimeException e) {
            Log.w(TAG, "could not resolve provider " + bare, e);
            return null;
        }
    }
}
