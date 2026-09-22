package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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

@RunWith(AndroidJUnit4.class)
public class DeleteGoesToTrashTest {

    private static final String SOURCE = "Pictures/GalleryTrashSrc/";

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
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "trash_fixture.jpg");
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

    @Test
    public void trashingHidesThePhotoFromItsFolderButKeepsTheRow() {
        engine.trash(fixture);

        MediaItemInfo info = gateway.query(fixture);
        assertNotNull("trashing must not destroy the row", info);
        assertTrue(info.trashed);
        assertFalse(gateway.displayNamesIn(SOURCE).contains("trash_fixture.jpg"));
    }

    @Test
    public void restoringReturnsItToTheOriginalFolderWithTheOriginalDate() {
        engine.trash(fixture);

        engine.restore(fixture);

        MediaItemInfo info = gateway.query(fixture);
        assertFalse(info.trashed);
        assertEquals(SOURCE, info.relativePath);
        assertEquals("trash_fixture.jpg", info.displayName);
        assertEquals(1790157600000L, info.dateTakenMillis);
        assertTrue(gateway.displayNamesIn(SOURCE).contains("trash_fixture.jpg"));
    }
}
