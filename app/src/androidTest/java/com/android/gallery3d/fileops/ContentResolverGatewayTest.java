package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ContentResolverGatewayTest {

    private static final String SOURCE = "Pictures/GalleryFixtureSrc/";
    private static final String DEST = "Pictures/GalleryFixtureDst/";

    private Context context;
    private ContentResolverGateway gateway;
    private final List<Uri> created = new ArrayList<Uri>();

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        gateway = new ContentResolverGateway(context);
    }

    @After
    public void tearDown() throws Exception {
        for (Uri uri : created) {
            try {
                context.getContentResolver().delete(uri, null, null);
            } catch (Exception ignored) {
                // Already gone: the test that created it moved or deleted it.
            }
        }
        created.clear();
    }

    /** Writes a real 1x1 JPEG the app owns, so no consent dialog is involved. */
    private Uri insertOwnedFixture(String relativePath, String displayName,
            long dateTakenMillis) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
        values.put(MediaStore.MediaColumns.DATE_TAKEN, dateTakenMillis);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = context.getContentResolver().insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values);
        assertNotNull(uri);
        created.add(uri);
        OutputStream out = context.getContentResolver().openOutputStream(uri);
        // Smallest valid baseline JPEG: SOI + APP0 + EOI is enough for MediaStore.
        out.write(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9});
        out.close();
        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        context.getContentResolver().update(uri, done, null, null);
        return uri;
    }

    @Test
    public void queryReadsBackTheColumnsWeWrote() throws Exception {
        Uri uri = insertOwnedFixture(SOURCE, "gw_query.jpg", 1790157600000L);

        MediaItemInfo info = gateway.query(uri);

        assertNotNull(info);
        assertEquals("gw_query.jpg", info.displayName);
        assertEquals(SOURCE, info.relativePath);
        assertEquals(1790157600000L, info.dateTakenMillis);
    }

    @Test
    public void queryOfADeletedRowIsNull() throws Exception {
        Uri uri = insertOwnedFixture(SOURCE, "gw_gone.jpg", 1L);
        context.getContentResolver().delete(uri, null, null);

        assertNull(gateway.query(uri));
    }

    @Test
    public void updateLocationMovesTheFileAndKeepsDateTaken() throws Exception {
        Uri uri = insertOwnedFixture(SOURCE, "gw_move.jpg", 1790157600000L);

        gateway.updateLocation(uri, DEST, "gw_move.jpg");

        MediaItemInfo after = gateway.query(uri);
        assertEquals(DEST, after.relativePath);
        assertEquals("gw_move.jpg", after.displayName);
        assertEquals(1790157600000L, after.dateTakenMillis);
    }

    @Test
    public void copyToCreatesASecondFileWithTheSameDateTaken() throws Exception {
        Uri uri = insertOwnedFixture(SOURCE, "gw_copy.jpg", 1790157600000L);

        Uri copy = gateway.copyTo(uri, DEST, "gw_copy.jpg");
        created.add(copy);

        assertEquals(SOURCE, gateway.query(uri).relativePath);
        assertEquals(DEST, gateway.query(copy).relativePath);
        assertEquals(1790157600000L, gateway.query(copy).dateTakenMillis);
    }

    @Test
    public void displayNamesInSeesTheFixture() throws Exception {
        insertOwnedFixture(SOURCE, "gw_list.jpg", 1L);

        assertEquals(true, gateway.displayNamesIn(SOURCE).contains("gw_list.jpg"));
    }

    @Test
    public void setTrashedAndBack() throws Exception {
        Uri uri = insertOwnedFixture(SOURCE, "gw_trash.jpg", 1L);

        gateway.setTrashed(uri, true);
        assertEquals(true, gateway.query(uri).trashed);

        gateway.setTrashed(uri, false);
        assertEquals(false, gateway.query(uri).trashed);
        assertEquals(SOURCE, gateway.query(uri).relativePath);
    }

    @Test
    public void setFavouriteAndBack() throws Exception {
        Uri uri = insertOwnedFixture(SOURCE, "gw_fav.jpg", 1L);

        gateway.setFavourite(uri, true);
        assertEquals(true, gateway.query(uri).favourite);

        gateway.setFavourite(uri, false);
        assertEquals(false, gateway.query(uri).favourite);
    }

    @Test
    public void folderPathsUnderFindsTheFixtureFolder() throws Exception {
        insertOwnedFixture(SOURCE, "gw_folders.jpg", 1L);

        assertEquals(true, gateway.folderPathsUnder("Pictures/").contains(SOURCE));
    }

    @Test
    public void rotationIsRecordedInTheOrientationColumnOnly() throws Exception {
        Uri uri = insertOwnedFixture(SOURCE, "gw_rotate.jpg", 1L);
        long sizeBefore = gateway.query(uri).sizeBytes;

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.ORIENTATION, 90);
        assertEquals(1, context.getContentResolver().update(uri, values, null, null));

        // The file bytes must not have been rewritten.
        assertEquals(sizeBefore, gateway.query(uri).sizeBytes);
    }
}
