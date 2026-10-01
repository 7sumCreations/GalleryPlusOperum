package com.android.gallery3d.util;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.android.gallery3d.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds share intents and hands them to the system share sheet.
 *
 * Sharing used to go through ShareActionProvider, which lists targets itself
 * via PackageManager. With targetSdk >= 30 package visibility hides nearly
 * every app from that query, so only Print and Bluetooth were offered. The
 * system chooser (Intent.createChooser) runs in the system's process and sees
 * every app, without this app needing to enumerate installed packages.
 */
public final class ShareIntents {
    private static final String TAG = "ShareIntents";

    private ShareIntents() {}

    /**
     * Returns an ACTION_SEND (one uri) or ACTION_SEND_MULTIPLE (several) intent
     * for the given content uris, or null when there is nothing to share. Null
     * entries (items that vanished) are skipped. Every uri is also put in the
     * ClipData so FLAG_GRANT_READ_URI_PERMISSION covers all of them.
     */
    public static Intent build(List<Uri> uris, String mimeType) {
        if (uris == null) return null;
        ArrayList<Uri> streams = new ArrayList<Uri>(uris.size());
        for (Uri uri : uris) {
            if (uri != null) streams.add(uri);
        }
        if (streams.isEmpty()) return null;

        Intent intent = new Intent();
        if (streams.size() == 1) {
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_STREAM, streams.get(0));
        } else {
            intent.setAction(Intent.ACTION_SEND_MULTIPLE);
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams);
        }
        intent.setType(mimeType);

        ClipData clip = ClipData.newRawUri(null, streams.get(0));
        for (int i = 1; i < streams.size(); i++) {
            clip.addItem(new ClipData.Item(streams.get(i)));
        }
        intent.setClipData(clip);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    /** Convenience for a single uri. */
    public static Intent build(Uri uri, String mimeType) {
        ArrayList<Uri> one = new ArrayList<Uri>(1);
        one.add(uri);
        return build(one, mimeType);
    }

    /**
     * Wraps the share intent in the system chooser and starts it. Returns false
     * (after telling the user) when there was nothing to share or nothing could
     * receive it.
     */
    public static boolean launchChooser(Activity activity, Intent shareIntent) {
        if (activity == null) return false;
        if (shareIntent == null) {
            Toast.makeText(activity, R.string.share_failed, Toast.LENGTH_SHORT).show();
            return false;
        }
        Intent chooser = Intent.createChooser(shareIntent,
                activity.getString(R.string.share));
        // createChooser copies the ClipData; keep the grant on the wrapper too.
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(chooser);
            return true;
        } catch (RuntimeException e) {
            // ActivityNotFoundException, or a SecurityException /
            // TransactionTooLargeException wrapper for a huge selection.
            Log.w(TAG, "Could not open the share sheet", e);
            Toast.makeText(activity, R.string.share_failed, Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
