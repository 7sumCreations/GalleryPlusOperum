package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class FileOpServiceTest {

    private static final String SOURCE = "Pictures/GallerySvcSrc/";
    private static final String DEST = "Pictures/GallerySvcDst/";

    private Context context;
    private ContentResolverGateway gateway;
    private final ArrayList<Uri> fixtures = new ArrayList<Uri>();

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        gateway = new ContentResolverGateway(context);
        for (int i = 0; i < 5; i++) {
            fixtures.add(insertFixture("svc_" + i + ".jpg"));
        }
    }

    @After
    public void tearDown() {
        for (Uri uri : fixtures) {
            try {
                context.getContentResolver().delete(uri, null, null);
            } catch (Exception ignored) {
                // Moved or deleted by the test.
            }
        }
        fixtures.clear();
    }

    private Uri insertFixture(String displayName) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, SOURCE);
        values.put(MediaStore.MediaColumns.DATE_TAKEN, 1790157600000L);
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

    @Test
    public void theServiceRunsTheBatchAndBroadcastsWhenDone() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger okCount = new AtomicInteger(-1);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                okCount.set(intent.getIntExtra(FileOpService.EXTRA_OK_COUNT, -1));
                latch.countDown();
            }
        };
        context.registerReceiver(receiver,
                new IntentFilter(FileOpService.ACTION_BATCH_DONE),
                Context.RECEIVER_NOT_EXPORTED);
        try {
            context.startForegroundService(FileOpService.runIntent(
                    context, FileOpBatch.Kind.MOVE, fixtures, DEST, "test-batch-1"));

            assertTrue("batch did not finish within 30s", latch.await(30, TimeUnit.SECONDS));
            assertEquals(5, okCount.get());
            assertEquals(5, gateway.displayNamesIn(DEST).size());
        } finally {
            context.unregisterReceiver(receiver);
        }
    }

    @Test
    public void runIntentCarriesEveryExtraTheServiceNeeds() {
        Intent intent = FileOpService.runIntent(context, FileOpBatch.Kind.MOVE,
                fixtures, DEST, "test-batch-2");

        assertEquals(FileOpService.ACTION_RUN, intent.getAction());
        assertEquals("test-batch-2", intent.getStringExtra(FileOpService.EXTRA_TOKEN));
        assertEquals(FileOpBatch.Kind.MOVE.name(),
                intent.getStringExtra(FileOpService.EXTRA_KIND));
        assertEquals(DEST, intent.getStringExtra(FileOpService.EXTRA_DEST));
        assertEquals(5, intent.getParcelableArrayListExtra(
                FileOpService.EXTRA_ITEMS).size());
    }
}
