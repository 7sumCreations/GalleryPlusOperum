/*
 * Copyright (C) 2026 The GrapheneGallery Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.app;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.MediaStore;
import android.widget.EditText;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.FavouritesAlbum;
import com.android.gallery3d.data.LocalAlbum;
import com.android.gallery3d.data.LocalMergeAlbum;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.data.TrashAlbum;
import com.android.gallery3d.fileops.ContentResolverGateway;
import com.android.gallery3d.fileops.FileOpEngine;
import com.android.gallery3d.fileops.FolderOpResult;
import com.android.gallery3d.fileops.FolderPicker;
import com.android.gallery3d.fileops.RelativePaths;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;

import java.util.List;

/**
 * Rename folder, Move folder and the camera guard on Delete, for the folder
 * long-pressed in AlbumSetPage.
 *
 * In the main grid every folder is a LocalMergeAlbum (its images and its
 * videos), not a LocalAlbum, so anything that only recognises LocalAlbum
 * silently ignores every real folder.
 *
 * Folder ops rewrite RELATIVE_PATH on every item under the folder, and most of
 * those items belong to other apps (the camera). Unless the app holds the
 * standing media-management grant, MediaStore needs one write-consent dialog
 * for the lot first, so this asks for it up front and runs the op on its
 * answer. The op itself runs off the UI thread; every outcome ends in a toast.
 */
final class FolderMenuActions {

    private static final String TAG = "FolderMenuActions";

    /** Request code for the write-consent dialog; routed back via onStateResult. */
    static final int REQUEST_FOLDER_CONSENT = 0x0F01;

    private static final int OP_RENAME = 1;
    private static final int OP_MOVE = 2;

    private final AbstractGalleryActivity mActivity;
    private final Handler mMainHandler;

    /** The op waiting on the consent dialog; only touched on the main thread. */
    private PendingOp mAwaitingConsent;

    private static final class PendingOp {
        final int op;
        final String from;
        /** New name for OP_RENAME, new parent RELATIVE_PATH for OP_MOVE. */
        final String argument;

        PendingOp(int op, String from, String argument) {
            this.op = op;
            this.from = from;
            this.argument = argument;
        }
    }

    FolderMenuActions(AbstractGalleryActivity activity) {
        mActivity = activity;
        mMainHandler = new Handler(activity.getMainLooper());
    }

    /**
     * @return true when the click was a folder action and has been handled
     *         (including refused with a toast); false to let the caller's
     *         default handling run.
     */
    boolean onActionItemClicked(int itemId, List<Path> selected) {
        if (itemId == R.id.action_rename_folder || itemId == R.id.action_move_folder) {
            if (selected.size() != 1) return false;
            final MediaObject object = mActivity.getDataManager().getMediaObject(selected.get(0));
            if (object instanceof MediaSet && ((MediaSet) object).isCameraRoll()) {
                toast(itemId == R.id.action_rename_folder
                        ? R.string.cannot_rename_camera_folder
                        : R.string.cannot_move_camera_folder);
                return true;
            }
            String from;
            try {
                from = folderOf(mActivity.getDataManager(), object);
            } catch (RuntimeException failure) {
                Log.w(TAG, "Could not resolve the folder of " + selected.get(0), failure);
                from = null;
            }
            if (from == null || from.isEmpty()) {
                toast(R.string.folder_not_found);
                return true;
            }
            if (itemId == R.id.action_rename_folder) {
                promptForNewName(from);
            } else {
                promptForNewParent(from);
            }
            return true;
        }
        if (itemId == R.id.action_delete) {
            // Deleting a folder goes through the ordinary trash batch (consent
            // and Undo included). Refuse up front when the camera's folder is in
            // the selection: trashing it breaks the camera app.
            DataManager manager = mActivity.getDataManager();
            for (Path path : selected) {
                MediaObject object = manager.getMediaObject(path);
                if (object instanceof MediaSet && ((MediaSet) object).isCameraRoll()) {
                    toast(R.string.cannot_delete_camera_folder);
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    /**
     * The RELATIVE_PATH of a real folder, or null for anything else (Favourites,
     * Trash, or a set this screen cannot rename or move).
     */
    static String folderOf(DataManager manager, MediaObject object) {
        if (object instanceof FavouritesAlbum || object instanceof TrashAlbum) return null;
        if (object instanceof LocalAlbum) return ((LocalAlbum) object).getRelativePath();
        if (object instanceof LocalMergeAlbum) {
            // /local/all/<bucket> merges /local/image/<bucket> and
            // /local/video/<bucket>; either half knows the folder, but a folder
            // holding only videos has an empty image half.
            String bucket = object.getPath().getSuffix();
            String[] halves = {"/local/image/", "/local/video/"};
            for (String half : halves) {
                MediaObject part = manager.getMediaObject(half + bucket);
                if (part instanceof LocalAlbum) {
                    String relativePath = ((LocalAlbum) part).getRelativePath();
                    if (relativePath != null && !relativePath.isEmpty()) return relativePath;
                }
            }
        }
        return null;
    }

    private void promptForNewName(final String from) {
        final Activity activity = mActivity;
        final EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setText(RelativePaths.lastSegment(from));
        input.selectAll();
        new AlertDialog.Builder(activity)
                .setTitle(R.string.rename_folder_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        run(new PendingOp(OP_RENAME, from, input.getText().toString().trim()),
                                false);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void promptForNewParent(final String from) {
        try {
            FolderPicker.showWithNewFolder(mActivity, gateway(), R.string.choose_parent_folder,
                    new FolderPicker.Listener() {
                        @Override
                        public void onFolderChosen(String newParent) {
                            run(new PendingOp(OP_MOVE, from, newParent), false);
                        }
                    });
        } catch (RuntimeException failure) {
            Log.w(TAG, "Could not show the folder picker", failure);
            toast(R.string.folder_op_failed);
        }
    }

    /** Called from AlbumSetPage.onStateResult with the consent dialog's answer. */
    void onConsentResult(int resultCode) {
        PendingOp pending = mAwaitingConsent;
        mAwaitingConsent = null;
        if (pending == null) return;
        if (resultCode != Activity.RESULT_OK) {
            toast(R.string.folder_op_permission_denied);
            return;
        }
        run(pending, true);
    }

    private void run(final PendingOp op, final boolean consentGranted) {
        mActivity.getThreadPool().submit(new Job<Void>() {
            @Override
            public Void run(JobContext jc) {
                String message;
                try {
                    message = execute(op, consentGranted);
                } catch (RuntimeException failure) {
                    // A MediaStore failure must reach the user, not the crash handler.
                    Log.w(TAG, "Folder op on " + op.from + " failed", failure);
                    message = mActivity.getString(R.string.folder_op_failed);
                }
                if (message != null) postToast(message);
                return null;
            }
        });
    }

    /**
     * Runs on a pool thread.
     *
     * @return the toast to show, or null when a consent dialog was launched
     *         instead and the op will resume in onConsentResult.
     */
    private String execute(final PendingOp op, boolean consentGranted) {
        ContentResolverGateway gateway = gateway();
        FileOpEngine engine = new FileOpEngine(gateway);

        FolderOpResult refused = op.op == OP_RENAME
                ? engine.checkRenameFolder(op.from, op.argument)
                : engine.checkMoveFolder(op.from, op.argument);
        if (refused != null) return describe(op, refused);

        if (!consentGranted && needsConsentDialog()) {
            List<Uri> items = gateway.itemsUnder(op.from);
            if (!items.isEmpty()) {
                final IntentSender sender = writeRequestFor(items);
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        askForConsent(op, sender);
                    }
                });
                return null;
            }
        }

        FolderOpResult result = op.op == OP_RENAME
                ? engine.renameFolder(op.from, op.argument)
                : engine.moveFolder(op.from, op.argument);
        return describe(op, result);
    }

    private void askForConsent(PendingOp op, IntentSender sender) {
        try {
            mAwaitingConsent = op;
            mActivity.startIntentSenderForResult(sender, REQUEST_FOLDER_CONSENT,
                    null, 0, 0, 0);
        } catch (IntentSender.SendIntentException | RuntimeException failure) {
            Log.w(TAG, "Could not ask for write consent", failure);
            mAwaitingConsent = null;
            toast(R.string.folder_op_failed);
        }
    }

    private String describe(PendingOp op, FolderOpResult result) {
        if (!result.ok) {
            return result.failureReason != null
                    ? result.failureReason
                    : mActivity.getString(R.string.folder_op_failed);
        }
        return mActivity.getString(op.op == OP_RENAME
                ? R.string.renamed_folder : R.string.moved_folder, result.itemsChanged);
    }

    /**
     * Android 11+ lets us ask once for every item. With the standing grant
     * (Android 12+ "media management") no dialog is needed. Android 10 has no
     * batch request, so the op runs and items it may not touch fail with a toast.
     */
    private boolean needsConsentDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (canManageMedia()) return false;
            } catch (RuntimeException failure) {
                Log.w(TAG, "canManageMedia failed", failure);
            }
        }
        return true;
    }

    @TargetApi(Build.VERSION_CODES.S)
    private boolean canManageMedia() {
        return MediaStore.canManageMedia(mActivity);
    }

    @TargetApi(Build.VERSION_CODES.R)
    private IntentSender writeRequestFor(List<Uri> items) {
        return MediaStore.createWriteRequest(mActivity.getContentResolver(), items)
                .getIntentSender();
    }

    private ContentResolverGateway gateway() {
        return new ContentResolverGateway(mActivity.getAndroidContext());
    }

    private void postToast(final String message) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mActivity, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void toast(int messageRes) {
        Toast.makeText(mActivity, messageRes, Toast.LENGTH_LONG).show();
    }
}
