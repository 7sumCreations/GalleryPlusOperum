package com.android.gallery3d.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.ui.MenuExecutor;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Share now goes through the system chooser, so the intent we hand it must
 * carry every uri both as EXTRA_STREAM and in ClipData, with a read grant,
 * or the receiving app cannot open the files.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShareIntentsTest {

    private static final Uri IMAGE = Uri.parse("content://media/external/images/media/1");
    private static final Uri VIDEO = Uri.parse("content://media/external/video/media/2");

    @Test
    public void singleUriIsActionSendWithGrantAndClip() {
        Intent intent = ShareIntents.build(IMAGE, "image/*");

        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals("image/*", intent.getType());
        assertEquals(IMAGE, intent.getParcelableExtra(Intent.EXTRA_STREAM));
        assertNotEquals(0, intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ClipData clip = intent.getClipData();
        assertEquals(1, clip.getItemCount());
        assertEquals(IMAGE, clip.getItemAt(0).getUri());
    }

    @Test
    public void severalUrisAreSendMultipleWithEveryUriInClip() {
        Intent intent = ShareIntents.build(Arrays.asList(IMAGE, VIDEO), "*/*");

        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.getAction());
        ArrayList<Uri> streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        assertEquals(Arrays.asList(IMAGE, VIDEO), streams);
        ClipData clip = intent.getClipData();
        assertEquals(2, clip.getItemCount());
        assertEquals(VIDEO, clip.getItemAt(1).getUri());
        assertNotEquals(0, intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    @Test
    public void vanishedItemsAreSkipped() {
        Intent intent = ShareIntents.build(Arrays.asList(null, IMAGE, null), "image/*");

        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals(IMAGE, intent.getParcelableExtra(Intent.EXTRA_STREAM));
    }

    @Test
    public void nothingToShareGivesNull() {
        assertNull(ShareIntents.build(new ArrayList<Uri>(), "image/*"));
        assertNull(ShareIntents.build(Arrays.asList((Uri) null), "image/*"));
        assertNull(ShareIntents.build((java.util.List<Uri>) null, "image/*"));
    }

    @Test
    public void mixedImageAndVideoSelectionSharesAsAnyType() {
        int type = MediaObject.MEDIA_TYPE_IMAGE | MediaObject.MEDIA_TYPE_VIDEO;
        assertEquals("*/*", MenuExecutor.getMimeType(type));
        assertEquals("image/*", MenuExecutor.getMimeType(MediaObject.MEDIA_TYPE_IMAGE));
        assertEquals("video/*", MenuExecutor.getMimeType(MediaObject.MEDIA_TYPE_VIDEO));
    }
}
