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
import java.util.ArrayList;
import java.util.List;

/** AC5 / L2-3: deleting a folder puts its contents in Trash, restorably. */
@RunWith(AndroidJUnit4.class)
public class DeleteFolderAcceptanceTest {

    private static final String FOLDER = "Pictures/GalleryDelFolder/";

    private Context context;
    private ContentResolverGateway gateway;
    private FileOpEngine engine;
    private final List<Uri> fixtures = new ArrayList<Uri>();

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        gateway = new ContentResolverGateway(context);
        engine = new FileOpEngine(gateway);
        for (int i = 0; i < 3; i++) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "delfolder_" + i + ".jpg");
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER);
            values.put(MediaStore.MediaColumns.DATE_TAKEN, 1790157600000L + i);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    values);
            assertNotNull(uri);
            OutputStream out = context.getContentResolver().openOutputStream(uri);
            out.write(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9});
            out.close();
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            context.getContentResolver().update(uri, done, null, null);
            fixtures.add(uri);
        }
    }

    @After
    public void tearDown() {
        for (Uri uri : fixtures) {
            try {
                context.getContentResolver().delete(uri, null, null);
            } catch (Exception ignored) {
                // Already gone.
            }
        }
        fixtures.clear();
    }

    @Test
    public void deletingTheFolderTrashesEveryPhotoAndHidesTheFolder() {
        FolderOpResult result = engine.trashFolder(FOLDER);

        assertTrue(result.ok);
        assertEquals(3, result.itemsChanged);
        for (Uri uri : fixtures) {
            assertTrue(gateway.query(uri).trashed);
        }
        assertFalse(gateway.folderPathsUnder("Pictures/").contains(FOLDER));
    }

    @Test
    public void restoringFromTrashBringsTheFolderBack() {
        engine.trashFolder(FOLDER);

        for (Uri uri : fixtures) {
            engine.restore(uri);
        }

        assertTrue(gateway.folderPathsUnder("Pictures/").contains(FOLDER));
        assertEquals(3, gateway.displayNamesIn(FOLDER).size());
        assertEquals(1790157600000L, gateway.query(fixtures.get(0)).dateTakenMillis);
    }

    @Test
    public void theCameraFolderIsProtected() {
        FolderOpResult result = engine.trashFolder("DCIM/Camera");

        assertFalse(result.ok);
    }
}
