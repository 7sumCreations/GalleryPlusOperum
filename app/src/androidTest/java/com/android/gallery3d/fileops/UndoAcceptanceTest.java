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

/** AC3 / L2-2: undo after a move restores folder, name and date taken. */
@RunWith(AndroidJUnit4.class)
public class UndoAcceptanceTest {

    private static final int COUNT = 12;
    private static final String SOURCE = "Pictures/GalleryUndoSrc/";
    private static final String DEST = "Pictures/GalleryUndoDst/";

    private Context context;
    private ContentResolverGateway gateway;
    private FileOpEngine engine;
    private final List<Uri> fixtures = new ArrayList<Uri>();

    private static final class SilentCallback implements FileOpEngine.ProgressCallback {
        @Override
        public void onItemDone(int indexDone, int total, FileOpResult result) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        gateway = new ContentResolverGateway(context);
        engine = new FileOpEngine(gateway);
        for (int i = 0; i < COUNT; i++) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "undo_" + i + ".jpg");
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, SOURCE);
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
    public void undoAfterAMoveRestoresFolderNameAndDateForEveryFile() {
        FileOpBatch batch = new FileOpBatch(FileOpBatch.nextToken(),
                FileOpBatch.Kind.MOVE, fixtures, DEST);
        engine.runBatch(batch, new SilentCallback());
        assertEquals(COUNT, batch.okCount());

        FileOpBatch reversal = engine.reverseBatch(batch, new SilentCallback());

        assertEquals(COUNT, reversal.okCount());
        for (int i = 0; i < COUNT; i++) {
            MediaItemInfo info = gateway.query(fixtures.get(i));
            assertNotNull("item " + i + " vanished", info);
            assertEquals(SOURCE, info.relativePath);
            assertEquals("undo_" + i + ".jpg", info.displayName);
            assertEquals(1790157600000L + i, info.dateTakenMillis);
        }
    }

    @Test
    public void theDestinationIsEmptyAgainAfterUndo() {
        FileOpBatch batch = new FileOpBatch(FileOpBatch.nextToken(),
                FileOpBatch.Kind.MOVE, fixtures, DEST);
        engine.runBatch(batch, new SilentCallback());

        engine.reverseBatch(batch, new SilentCallback());

        assertEquals(0, gateway.displayNamesIn(DEST).size());
        assertEquals(COUNT, gateway.displayNamesIn(SOURCE).size());
    }

    @Test
    public void undoRespectsTheTenSecondWindow() {
        FileOpBatch batch = new FileOpBatch(FileOpBatch.nextToken(),
                FileOpBatch.Kind.MOVE, fixtures, DEST);
        engine.runBatch(batch, new SilentCallback());
        UndoManager undo = UndoManager.getInstance();
        undo.clear();
        undo.rememberAt(batch, 1_000L);

        assertNotNull(undo.takeUndoable(1_000L + 9_000L));
        undo.rememberAt(batch, 1_000L);
        org.junit.Assert.assertNull(undo.takeUndoable(1_000L + 11_000L));
    }
}
