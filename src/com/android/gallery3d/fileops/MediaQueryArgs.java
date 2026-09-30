package com.android.gallery3d.fileops;

import android.content.ContentResolver;
import android.os.Bundle;
import android.provider.MediaStore;

/**
 * Builds the query-args Bundle for {@code ContentResolver.query(Uri, String[],
 * Bundle, CancellationSignal)}.
 *
 * MediaProvider reads the trashed/pending filters ({@link
 * MediaStore#QUERY_ARG_MATCH_TRASHED}, {@link MediaStore#QUERY_ARG_MATCH_PENDING})
 * only from this Bundle, as ints. Appended to the uri as query parameters they
 * are silently ignored, so a collection query never returns a trashed row. The
 * same is true of a {@code limit} uri parameter for apps targeting API 30+:
 * paging has to go through {@link ContentResolver#QUERY_ARG_LIMIT} and
 * {@link ContentResolver#QUERY_ARG_OFFSET}.
 */
public final class MediaQueryArgs {

    private MediaQueryArgs() {
    }

    /** Selection, selection args and sort order, as the five-argument query takes them. */
    public static Bundle sql(String selection, String[] selectionArgs, String sortOrder) {
        Bundle args = new Bundle();
        if (selection != null) {
            args.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection);
        }
        if (selectionArgs != null) {
            args.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs);
        }
        if (sortOrder != null) {
            args.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder);
        }
        return args;
    }

    /** Returns {@code count} rows starting at row {@code offset}. */
    public static Bundle page(Bundle args, int offset, int count) {
        args.putInt(ContentResolver.QUERY_ARG_OFFSET, offset);
        args.putInt(ContentResolver.QUERY_ARG_LIMIT, count);
        return args;
    }

    /**
     * How trashed rows are matched: {@link MediaStore#MATCH_EXCLUDE} (the
     * provider's default), {@link MediaStore#MATCH_INCLUDE} or
     * {@link MediaStore#MATCH_ONLY}. {@link MediaStore#MATCH_DEFAULT} leaves the
     * provider's default in place.
     */
    public static Bundle matchTrashed(Bundle args, int match) {
        if (match != MediaStore.MATCH_DEFAULT) {
            args.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, match);
        }
        return args;
    }

    /** How pending rows are matched; same values as {@link #matchTrashed}. */
    public static Bundle matchPending(Bundle args, int match) {
        if (match != MediaStore.MATCH_DEFAULT) {
            args.putInt(MediaStore.QUERY_ARG_MATCH_PENDING, match);
        }
        return args;
    }
}
