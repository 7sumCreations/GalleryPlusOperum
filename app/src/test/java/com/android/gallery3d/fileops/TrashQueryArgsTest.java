package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.provider.MediaStore;


import com.android.gallery3d.data.LocalAlbum;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * F-022: MediaProvider only honours QUERY_ARG_MATCH_TRASHED / _PENDING (and,
 * for apps targeting API 30+, paging) from the query-args Bundle. As uri query
 * parameters they are ignored, so the Trash, Empty Trash and the purge all saw
 * nothing. These tests pin what actually reaches the provider.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class TrashQueryArgsTest {

    /** Records every query and answers with one row of the requested columns. */
    public static class RecordingProvider extends ContentProvider {
        static final List<Uri> uris = new ArrayList<Uri>();
        static final List<Bundle> args = new ArrayList<Bundle>();

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, Bundle queryArgs,
                CancellationSignal signal) {
            uris.add(uri);
            args.add(queryArgs == null ? new Bundle() : new Bundle(queryArgs));
            MatrixCursor cursor = new MatrixCursor(projection);
            Object[] row = new Object[projection.length];
            for (int i = 0; i < row.length; i++) row[i] = 1;
            cursor.addRow(row);
            return cursor;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) {
            throw new AssertionError("expected the Bundle overload");
        }

        @Override
        public String getType(Uri uri) {
            return null;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection,
                String[] selectionArgs) {
            return 0;
        }
    }

    private ContentResolverGateway gateway;

    @Before
    public void setUp() {
        RecordingProvider.uris.clear();
        RecordingProvider.args.clear();
        Robolectric.buildContentProvider(RecordingProvider.class).create(MediaStore.AUTHORITY);
        gateway = new ContentResolverGateway(RuntimeEnvironment.getApplication());
    }

    private Bundle lastArgs() {
        return RecordingProvider.args.get(RecordingProvider.args.size() - 1);
    }

    private Uri lastUri() {
        return RecordingProvider.uris.get(RecordingProvider.uris.size() - 1);
    }

    @Test
    public void trashedItemsAsksForTrashedRowsInTheBundle() {
        assertEquals(1, gateway.trashedItems().size());

        Bundle args = lastArgs();
        assertEquals(MediaStore.MATCH_ONLY,
                args.getInt(MediaStore.QUERY_ARG_MATCH_TRASHED, -1));
        assertEquals(MediaStore.MATCH_INCLUDE,
                args.getInt(MediaStore.QUERY_ARG_MATCH_PENDING, -1));
        assertTrue(args.getString(ContentResolver.QUERY_ARG_SQL_SELECTION)
                .contains(MediaStore.MediaColumns.IS_TRASHED + " = 1"));
        assertTrue(lastUri().getQueryParameterNames().isEmpty());
    }

    @Test
    public void purgeOnlyConsidersTrashedRowsPastTheirExpiry() {
        gateway.trashedOlderThan(5_000_000L);

        Bundle args = lastArgs();
        assertEquals(MediaStore.MATCH_ONLY,
                args.getInt(MediaStore.QUERY_ARG_MATCH_TRASHED, -1));
        String selection = args.getString(ContentResolver.QUERY_ARG_SQL_SELECTION);
        assertTrue(selection.contains(MediaStore.MediaColumns.IS_TRASHED + " = 1"));
        assertTrue(selection.contains(MediaStore.MediaColumns.DATE_EXPIRES + " <= ?"));
        assertEquals("5000",
                args.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)[0]);
    }

    @Test
    public void querySeesATrashedItem() {
        Uri item = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "7");
        assertNotNull(gateway.query(item));

        assertEquals(MediaStore.MATCH_INCLUDE,
                lastArgs().getInt(MediaStore.QUERY_ARG_MATCH_TRASHED, -1));
        assertEquals(item, lastUri());
    }

    @Test
    public void ordinaryFolderQueriesKeepTheProviderDefault() {
        gateway.itemsUnder("Pictures/Lisbon/");

        assertFalse(lastArgs().containsKey(MediaStore.QUERY_ARG_MATCH_TRASHED));
        assertFalse(lastArgs().containsKey(MediaStore.QUERY_ARG_MATCH_PENDING));
    }

    @Test
    public void itemCursorByIdIncludesTrashedRows() {
        Cursor cursor = LocalAlbum.getItemCursor(
                RuntimeEnvironment.getApplication().getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.MediaColumns._ID}, 42);
        assertNotNull(cursor);
        cursor.close();

        Bundle args = lastArgs();
        assertEquals(MediaStore.MATCH_INCLUDE,
                args.getInt(MediaStore.QUERY_ARG_MATCH_TRASHED, -1));
        assertEquals("42",
                args.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)[0]);
    }

    @Test
    public void pagingTravelsAsLimitAndOffset() {
        Bundle args = MediaQueryArgs.page(MediaQueryArgs.sql("a = ?", new String[]{"1"}, "b"),
                64, 32);

        assertEquals(64, args.getInt(ContentResolver.QUERY_ARG_OFFSET));
        assertEquals(32, args.getInt(ContentResolver.QUERY_ARG_LIMIT));
        assertEquals("b", args.getString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER));
    }

    @Test
    public void matchDefaultLeavesTheKeyOut() {
        Bundle args = MediaQueryArgs.matchTrashed(new Bundle(), MediaStore.MATCH_DEFAULT);

        assertNull(args.get(MediaStore.QUERY_ARG_MATCH_TRASHED));
    }
}
