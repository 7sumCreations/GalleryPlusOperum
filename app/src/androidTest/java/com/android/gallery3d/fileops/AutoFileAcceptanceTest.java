package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
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

/**
 * AC9 on a real device. The watched folder is a fixture folder rather than the
 * real DCIM/Camera, so the test cannot disturb actual photos.
 */
@RunWith(AndroidJUnit4.class)
public class AutoFileAcceptanceTest {

    private static final String WATCHED = "DCIM/GalleryAutoFileSrc/";
    /** 2026-09-22T10:00:00Z */
    private static final long SEPT_2026 = 1790157600000L;
    private static final long HOUR = 60L * 60L * 1000L;

    private Context context;
    private ContentResolverGateway gateway;
    private AutoFileSettings settings;
    private final List<Uri> fixtures = new ArrayList<Uri>();

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        gateway = new ContentResolverGateway(context);
        settings = AutoFileSettings.from(context);
        settings.setWatchedFolder(WATCHED);
        settings.setDelayMinutes(1);
        settings.setEnabled(true);
    }

    @After
    public void tearDown() {
        settings.setEnabled(false);
        settings.setWatchedFolder(AutoFileSettings.DEFAULT_WATCHED_FOLDER);
        settings.setDelayMinutes(AutoFileSettings.DEFAULT_DELAY_MINUTES);
        for (Uri uri : fixtures) {
            try {
                context.getContentResolver().delete(uri, null, null);
            } catch (Exception ignored) {
                // Already gone.
            }
        }
        fixtures.clear();
    }

    private Uri insertFixture(String displayName, long dateTakenMillis) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, WATCHED);
        if (dateTakenMillis > 0) {
            values.put(MediaStore.MediaColumns.DATE_TAKEN, dateTakenMillis);
        }
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
        return uri;
    }

    @Test
    public void aDatedPhotoIsFiledIntoPicturesYearMonth() throws Exception {
        Uri photo = insertFixture("autofile_dated.jpg", SEPT_2026);

        AutoFileReceiver.applyRule(context, System.currentTimeMillis() + HOUR);

        MediaItemInfo info = gateway.query(photo);
        assertNotNull(info);
        assertEquals("Pictures/2026/09/", info.relativePath);
        assertEquals("autofile_dated.jpg", info.displayName);
        assertEquals(SEPT_2026, info.dateTakenMillis);
    }

    @Test
    public void aPhotoWithNoDateTakenStaysPut() throws Exception {
        Uri photo = insertFixture("autofile_nodate.jpg", 0L);

        AutoFileReceiver.applyRule(context, System.currentTimeMillis() + HOUR);

        assertEquals(WATCHED, gateway.query(photo).relativePath);
    }

    @Test
    public void turningTheRuleOffStopsEveryAutomaticMove() throws Exception {
        Uri photo = insertFixture("autofile_off.jpg", SEPT_2026);
        settings.setEnabled(false);

        AutoFileReceiver.applyRule(context, System.currentTimeMillis() + HOUR);

        assertEquals(WATCHED, gateway.query(photo).relativePath);
    }

    @Test
    public void aPhotoIsNeverMovedTwice() throws Exception {
        Uri photo = insertFixture("autofile_once.jpg", SEPT_2026);
        AutoFileReceiver.applyRule(context, System.currentTimeMillis() + HOUR);

        AutoFileReceiver.applyRule(context, System.currentTimeMillis() + 2 * HOUR);

        assertEquals("Pictures/2026/09/", gateway.query(photo).relativePath);
    }

    @Test
    public void everyAutomaticMoveIsLogged() throws Exception {
        Uri photo = insertFixture("autofile_logged.jpg", SEPT_2026);

        AutoFileReceiver.applyRule(context, System.currentTimeMillis() + HOUR);

        AutoFileLog.Entry entry = AutoFileLog.load(context).find(photo.toString());
        assertNotNull("the move must be in the log", entry);
        assertEquals(WATCHED, entry.fromRelativePath);
        assertEquals("Pictures/2026/09/", entry.toRelativePath);
    }

    @Test
    public void oneAutoFiledPhotoCanBeUndoneFromTheLog() throws Exception {
        Uri photo = insertFixture("autofile_undo.jpg", SEPT_2026);
        AutoFileReceiver.applyRule(context, System.currentTimeMillis() + HOUR);
        assertEquals("Pictures/2026/09/", gateway.query(photo).relativePath);

        assertTrue(AutoFileReceiver.undoOne(context, photo.toString()));

        MediaItemInfo info = gateway.query(photo);
        assertEquals(WATCHED, info.relativePath);
        assertEquals("autofile_undo.jpg", info.displayName);
        assertEquals(SEPT_2026, info.dateTakenMillis);
    }
}
