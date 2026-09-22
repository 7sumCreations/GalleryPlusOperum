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
import java.util.ArrayList;
import java.util.List;

/**
 * AC2, automated half: a large batch move preserves every name and date taken.
 * 120 items rather than 500 so the suite stays under a minute; the manual
 * 500-item run is the checklist step at the end of F-016.
 */
@RunWith(AndroidJUnit4.class)
public class BulkMoveAcceptanceTest {

    private static final int COUNT = 120;
    private static final String SOURCE = "Pictures/GalleryBulkSrc/";
    private static final String DEST = "Pictures/Trip 2026/";

    private Context context;
    private ContentResolverGateway gateway;
    private FileOpEngine engine;
    private final List<Uri> fixtures = new ArrayList<Uri>();

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        gateway = new ContentResolverGateway(context);
        engine = new FileOpEngine(gateway);
        for (int i = 0; i < COUNT; i++) {
            fixtures.add(insertFixture("bulk_" + i + ".jpg", 1790157600000L + i));
        }
    }

    @After
    public void tearDown() {
        for (Uri uri : fixtures) {
            try {
                context.getContentResolver().delete(uri, null, null);
            } catch (Exception ignored) {
                // Already removed by the move under test.
            }
        }
        fixtures.clear();
    }

    private Uri insertFixture(String displayName, long dateTakenMillis) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, SOURCE);
        values.put(MediaStore.MediaColumns.DATE_TAKEN, dateTakenMillis);
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
        return uri;
    }

    private static final class SilentCallback implements FileOpEngine.ProgressCallback {
        int lastIndex;
        int total;

        @Override
        public void onItemDone(int indexDone, int totalItems, FileOpResult result) {
            lastIndex = indexDone;
            total = totalItems;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    @Test
    public void everyItemKeepsItsNameAndDateTaken() {
        FileOpBatch batch = new FileOpBatch(FileOpBatch.nextToken(),
                FileOpBatch.Kind.MOVE, fixtures, DEST);
        SilentCallback callback = new SilentCallback();

        engine.runBatch(batch, callback);

        assertEquals(COUNT, batch.okCount());
        assertEquals(COUNT, callback.total);
        assertEquals(COUNT, callback.lastIndex);
        for (int i = 0; i < COUNT; i++) {
            MediaItemInfo info = gateway.query(batch.results.get(i).resultUri);
            assertNotNull("item " + i + " vanished", info);
            assertEquals(DEST, info.relativePath);
            assertEquals("bulk_" + i + ".jpg", info.displayName);
            assertEquals(1790157600000L + i, info.dateTakenMillis);
        }
    }

    @Test
    public void theDestinationFolderContainsEveryName() {
        FileOpBatch batch = new FileOpBatch(FileOpBatch.nextToken(),
                FileOpBatch.Kind.MOVE, fixtures, DEST);

        engine.runBatch(batch, new SilentCallback());

        assertEquals(COUNT, gateway.displayNamesIn(DEST).size());
    }
}
