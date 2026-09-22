package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.OutputStream;

/**
 * AC7: hearting a photo sets the MediaStore favourite flag, and the favourites
 * query finds it. The GL album-grid placement is checked by hand in Task 30.
 */
@RunWith(AndroidJUnit4.class)
public class FavouritesAlbumTest {

    private static final String SOURCE = "Pictures/GalleryFavSrc/";

    private Context context;
    private ContentResolverGateway gateway;
    private FileOpEngine engine;
    private Uri fixture;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        gateway = new ContentResolverGateway(context);
        engine = new FileOpEngine(gateway);

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "fav_fixture.jpg");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, SOURCE);
        values.put(MediaStore.MediaColumns.DATE_TAKEN, 1790157600000L);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        fixture = context.getContentResolver().insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values);
        assertNotNull(fixture);
        OutputStream out = context.getContentResolver().openOutputStream(fixture);
        out.write(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9});
        out.close();
        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        context.getContentResolver().update(fixture, done, null, null);
    }

    @After
    public void tearDown() {
        try {
            context.getContentResolver().delete(fixture, null, null);
        } catch (Exception ignored) {
            // Already gone.
        }
    }

    private int favouriteCountFor(String displayName) {
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                new String[]{MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.IS_FAVORITE + " = 1 AND "
                        + MediaStore.MediaColumns.DISPLAY_NAME + " = ?",
                new String[]{displayName}, null);
        if (cursor == null) return 0;
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    @Test
    public void heartingAPhotoMakesItShowUpInTheFavouritesQuery() {
        assertEquals(0, favouriteCountFor("fav_fixture.jpg"));

        FileOpResult result = engine.setFavourite(fixture, true);

        assertEquals(FileOpResult.Status.OK, result.status);
        assertEquals(1, favouriteCountFor("fav_fixture.jpg"));
    }

    @Test
    public void thePhotoStaysInItsOriginalFolder() {
        engine.setFavourite(fixture, true);

        assertEquals(SOURCE, gateway.query(fixture).relativePath);
        assertTrue(gateway.query(fixture).favourite);
    }

    @Test
    public void unheartingRemovesItFromTheFavouritesQuery() {
        engine.setFavourite(fixture, true);

        engine.setFavourite(fixture, false);

        assertEquals(0, favouriteCountFor("fav_fixture.jpg"));
    }
}
