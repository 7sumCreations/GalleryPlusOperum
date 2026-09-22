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

    /** Returned by pathForRow when the user tapped "+ New folder". */
    public static final String NEW_FOLDER_ROW = "";

    public static String[] labelsWithNewFolderRow(MediaStoreGateway gateway,
            String newFolderLabel) {
        List<String> folders = foldersFor(gateway);
        String[] labels = new String[folders.size() + 1];
        labels[0] = newFolderLabel;
        for (int i = 0; i < folders.size(); i++) {
            labels[i + 1] = labelFor(folders.get(i));
        }
        return labels;
    }

    /** @return NEW_FOLDER_ROW for row 0, otherwise the folder's relative path. */
    public static String pathForRow(MediaStoreGateway gateway, int row) {
        if (row == 0) return NEW_FOLDER_ROW;
        return foldersFor(gateway).get(row - 1);
    }

    public static void showWithNewFolder(final Context context,
            final MediaStoreGateway gateway, int titleResId, final Listener listener) {
        final String[] labels = labelsWithNewFolderRow(
                gateway, context.getString(R.string.new_folder));
        new AlertDialog.Builder(context)
                .setTitle(titleResId)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String path = pathForRow(gateway, which);
                        if (NEW_FOLDER_ROW.equals(path)) {
                            promptForNewFolder(context, gateway, listener);
                        } else {
                            listener.onFolderChosen(path);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void promptForNewFolder(final Context context, final MediaStoreGateway gateway,
            final Listener listener) {
        final android.widget.EditText input = new android.widget.EditText(context);
        input.setHint(R.string.new_folder_hint);
        new AlertDialog.Builder(context)
                .setTitle(R.string.new_folder)
                .setView(input)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        FolderCreator.Outcome outcome = FolderCreator.create(
                                gateway, "Pictures", input.getText().toString());
                        if (outcome.created) {
                            listener.onFolderChosen(outcome.relativePath);
                        } else {
                            android.widget.Toast.makeText(context, outcome.errorMessage,
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
