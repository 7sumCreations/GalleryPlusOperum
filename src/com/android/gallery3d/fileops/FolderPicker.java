package com.android.gallery3d.fileops;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import com.android.gallery3d.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Destination chooser for Move and Copy. */
public final class FolderPicker {

    public interface Listener {
        void onFolderChosen(String relativePath);
    }

    private FolderPicker() {
    }

    /** Every folder under a media root that currently holds at least one item. */
    public static List<String> foldersFor(MediaStoreGateway gateway) {
        List<String> folders = new ArrayList<String>();
        for (String path : gateway.folderPathsUnder("")) {
            if (RelativePaths.isUnderMediaRoot(path)) folders.add(path);
        }
        Collections.sort(folders);
        return folders;
    }

    /** "Pictures/Trips/Lisbon/" -> "Lisbon  ·  Pictures/Trips". */
    public static String labelFor(String relativePath) {
        String name = RelativePaths.lastSegment(relativePath);
        String parent = RelativePaths.parentOf(relativePath);
        if (parent.isEmpty()) return name;
        // Strip the trailing slash from the parent for display.
        return name + "  ·  " + parent.substring(0, parent.length() - 1);
    }

    public static void show(Context context, final MediaStoreGateway gateway,
            int titleResId, final Listener listener) {
        final List<String> folders = foldersFor(gateway);
        final String[] labels = new String[folders.size()];
        for (int i = 0; i < folders.size(); i++) {
            labels[i] = labelFor(folders.get(i));
        }
        new AlertDialog.Builder(context)
                .setTitle(titleResId)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        listener.onFolderChosen(folders.get(which));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
