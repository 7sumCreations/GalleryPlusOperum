package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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

/**
 * AC1: move one photo from DCIM/Camera to Pictures/Test and prove it landed,
 * keeping its name and date taken, without a rescan.
 */
@RunWith(AndroidJUnit4.class)
public class MoveOnePhotoAcceptanceTest {

    private static final long DATE_TAKEN = 1790157600000L;

    private Context context;
    private FileOpEngine engine;
    private ContentResolverGateway gateway;
    private Uri fixture;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        gateway = new ContentResolverGateway(context);
        engine = new FileOpEngine(gateway);

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "ac1_photo.jpg");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera/");
        values.put(MediaStore.MediaColumns.DATE_TAKEN, DATE_TAKEN);
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
            // The move may have replaced the row; nothing to clean up.
        }
    }

    @Test
    public void movingOnePhotoLandsItInPicturesTestWithNameAndDateIntact() {
        FileOpResult result = engine.move(fixture, "Pictures/Test");

        assertEquals(FileOpResult.Status.OK, result.status);
        MediaItemInfo after = gateway.query(result.resultUri);
        assertNotNull("the moved row must still exist", after);
        assertEquals("Pictures/Test/", after.relativePath);
        assertEquals("ac1_photo.jpg", after.displayName);
        assertEquals(DATE_TAKEN, after.dateTakenMillis);
    }

    @Test
    public void theSourceFolderNoLongerListsThePhoto() {
        engine.move(fixture, "Pictures/Test");

        assertEquals(false, gateway.displayNamesIn("DCIM/Camera/").contains("ac1_photo.jpg"));
        assertEquals(true, gateway.displayNamesIn("Pictures/Test/").contains("ac1_photo.jpg"));
    }
}
