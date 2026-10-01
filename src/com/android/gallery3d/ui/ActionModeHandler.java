/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.android.gallery3d.R;
import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaObject.PanoramaSupportCallback;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.ui.MenuExecutor.ProgressListener;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ShareIntents;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;

import java.util.ArrayList;

public class ActionModeHandler implements Callback, PopupList.OnPopupItemClickListener {

    @SuppressWarnings("unused")
    private static final String TAG = "ActionModeHandler";

    private static final int MAX_SELECTED_ITEMS_FOR_SHARE_INTENT = 300;
    // Panorama support is only probed (to hide Rotate) for small selections.
    private static final int MAX_SELECTED_ITEMS_FOR_PANORAMA_SHARE_INTENT = 10;

    // Folder verbs (rename/move folder) are deliberately absent: they act on a
    // single album, so they must not survive a multi-item selection.
    // Package-visible so MultiSelectMaskTest can guard it: every capability bit
    // added to MediaObject must be consciously listed here or silently dropped
    // the moment a second item is selected.
    static final int SUPPORT_MULTIPLE_MASK = MediaObject.SUPPORT_DELETE
            | MediaObject.SUPPORT_ROTATE | MediaObject.SUPPORT_SHARE
            | MediaObject.SUPPORT_CACHE | MediaObject.SUPPORT_MOVE
            | MediaObject.SUPPORT_COPY | MediaObject.SUPPORT_FAVOURITE
            | MediaObject.SUPPORT_RESTORE | MediaObject.SUPPORT_DELETE_FOREVER;

    public interface ActionModeListener {
        public boolean onActionItemClicked(MenuItem item);
    }

    private final AbstractGalleryActivity mActivity;
    private final MenuExecutor mMenuExecutor;
    private final SelectionManager mSelectionManager;
    private Menu mMenu;
    private MenuItem mShareMenuItem;
    // Built off the UI thread for the current selection; read on the UI thread
    // when Share is tapped. Null while being rebuilt or when nothing can be shared.
    private Intent mShareIntent;
    private SelectionMenu mSelectionMenu;
    private ActionModeListener mListener;
    private Future<?> mMenuTask;
    private final Handler mMainHandler;
    private ActionMode mActionMode;

    private static class GetAllPanoramaSupports implements PanoramaSupportCallback {
        private int mNumInfoRequired;
        private JobContext mJobContext;
        public boolean mAllPanoramas = true;
        public boolean mAllPanorama360 = true;
        public boolean mHasPanorama360 = false;
        private Object mLock = new Object();

        public GetAllPanoramaSupports(ArrayList<MediaObject> mediaObjects, JobContext jc) {
            mJobContext = jc;
            mNumInfoRequired = mediaObjects.size();
            for (MediaObject mediaObject : mediaObjects) {
                mediaObject.getPanoramaSupport(this);
            }
        }

        @Override
        public void panoramaInfoAvailable(MediaObject mediaObject, boolean isPanorama,
                boolean isPanorama360) {
            synchronized (mLock) {
                mNumInfoRequired--;
                mAllPanoramas = isPanorama && mAllPanoramas;
                mAllPanorama360 = isPanorama360 && mAllPanorama360;
                mHasPanorama360 = mHasPanorama360 || isPanorama360;
                if (mNumInfoRequired == 0 || mJobContext.isCancelled()) {
                    mLock.notifyAll();
                }
            }
        }

        public void waitForPanoramaSupport() {
            synchronized (mLock) {
                while (mNumInfoRequired != 0 && !mJobContext.isCancelled()) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException e) {
                        // May be a cancelled job context
                    }
                }
            }
        }
    }

    public ActionModeHandler(
            AbstractGalleryActivity activity, SelectionManager selectionManager) {
        mActivity = Utils.checkNotNull(activity);
        mSelectionManager = Utils.checkNotNull(selectionManager);
        mMenuExecutor = new MenuExecutor(activity, selectionManager);
        mMainHandler = new Handler(activity.getMainLooper());
    }

    public void startActionMode() {
        Activity a = mActivity;
        mActionMode = a.startActionMode(this);
        View customView = LayoutInflater.from(a).inflate(
                R.layout.action_mode, null);
        mActionMode.setCustomView(customView);
        mSelectionMenu = new SelectionMenu(a,
                (Button) customView.findViewById(R.id.selection_menu), this);
        updateSelectionMenu();
    }

    public void finishActionMode() {
        mActionMode.finish();
    }

    public void setTitle(String title) {
        mSelectionMenu.setTitle(title);
    }

    public void setActionModeListener(ActionModeListener listener) {
        mListener = listener;
    }

    private WakeLockHoldingProgressListener mDeleteProgressListener;

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        GLRoot root = mActivity.getGLRoot();
        root.lockRenderThread();
        try {
            boolean result;
            // Give listener a chance to process this command before it's routed to
            // ActionModeHandler, which handles command only based on the action id.
            // Sometimes the listener may have more background information to handle
            // an action command.
            if (mListener != null) {
                result = mListener.onActionItemClicked(item);
                if (result) {
                    mSelectionManager.leaveSelectionMode();
                    return result;
                }
            }
            ProgressListener listener = null;
            String confirmMsg = null;
            int action = item.getItemId();
            if (action == R.id.action_share) {
                // The item stays disabled until the background job has built
                // the intent, so mShareIntent is normally ready here.
                if (ShareIntents.launchChooser((Activity) mActivity, mShareIntent)) {
                    mSelectionManager.leaveSelectionMode();
                }
                return true;
            }
            if (action == R.id.action_delete) {
                final int count = mSelectionManager.getSelectedCount();
                new android.app.AlertDialog.Builder((android.app.Activity) mActivity)
                        .setMessage(mActivity.getResources().getQuantityString(
                                R.plurals.delete_selection, count))
                        .setPositiveButton(android.R.string.ok,
                                new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(
                                            android.content.DialogInterface d, int w) {
                                        mMenuExecutor.startFileOpBatch(
                                                com.android.gallery3d.fileops.FileOpBatch
                                                        .Kind.TRASH, null);
                                        mSelectionManager.leaveSelectionMode();
                                    }
                                })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            }
            if (action == R.id.action_move) {
                com.android.gallery3d.fileops.FolderPicker.showWithNewFolder(
                        (android.app.Activity) mActivity,
                        new com.android.gallery3d.fileops.ContentResolverGateway(
                                mActivity.getAndroidContext()),
                        R.string.choose_folder,
                        new com.android.gallery3d.fileops.FolderPicker.Listener() {
                            @Override
                            public void onFolderChosen(String relativePath) {
                                mMenuExecutor.startFileOpBatch(
                                        com.android.gallery3d.fileops.FileOpBatch.Kind.MOVE,
                                        relativePath);
                                mSelectionManager.leaveSelectionMode();
                            }
                        });
                return true;
            }
            if (action == R.id.action_copy) {
                com.android.gallery3d.fileops.FolderPicker.showWithNewFolder(
                        (android.app.Activity) mActivity,
                        new com.android.gallery3d.fileops.ContentResolverGateway(
                                mActivity.getAndroidContext()),
                        R.string.choose_folder,
                        new com.android.gallery3d.fileops.FolderPicker.Listener() {
                            @Override
                            public void onFolderChosen(String relativePath) {
                                mMenuExecutor.startFileOpBatch(
                                        com.android.gallery3d.fileops.FileOpBatch.Kind.COPY,
                                        relativePath);
                                mSelectionManager.leaveSelectionMode();
                            }
                        });
                return true;
            }
            if (action == R.id.action_favourite) {
                mMenuExecutor.startFileOpBatch(
                        com.android.gallery3d.fileops.FileOpBatch.Kind.FAVOURITE, null);
                mSelectionManager.leaveSelectionMode();
                return true;
            }
            if (action == R.id.action_restore) {
                mMenuExecutor.startFileOpBatch(
                        com.android.gallery3d.fileops.FileOpBatch.Kind.RESTORE, null);
                mSelectionManager.leaveSelectionMode();
                return true;
            }
            if (action == R.id.action_delete_forever) {
                int selected = mSelectionManager.getSelectedCount();
                new android.app.AlertDialog.Builder((android.app.Activity) mActivity)
                        .setMessage(((android.app.Activity) mActivity).getResources()
                                .getQuantityString(R.plurals.delete_forever_confirm_selected,
                                        selected, selected))
                        .setPositiveButton(android.R.string.ok,
                                new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(
                                            android.content.DialogInterface d, int w) {
                                        mMenuExecutor.startFileOpBatch(
                                                com.android.gallery3d.fileops.FileOpBatch
                                                        .Kind.DELETE_FOREVER, null);
                                        mSelectionManager.leaveSelectionMode();
                                    }
                                })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            }
            mMenuExecutor.onMenuClicked(item, confirmMsg, listener);
        } finally {
            root.unlockRenderThread();
        }
        return true;
    }

    @Override
    public boolean onPopupItemClick(int itemId) {
        GLRoot root = mActivity.getGLRoot();
        root.lockRenderThread();
        try {
            if (itemId == R.id.action_select_all) {
                updateSupportedOperation();
                mMenuExecutor.onMenuClicked(itemId, null, false, true);
            }
            return true;
        } finally {
            root.unlockRenderThread();
        }
    }

    private void updateSelectionMenu() {
        // update title
        int count = mSelectionManager.getSelectedCount();
        String format = mActivity.getResources().getQuantityString(
                R.plurals.number_of_items_selected, count);
        setTitle(String.format(format, count));

        // For clients who call SelectionManager.selectAll() directly, we need to ensure the
        // menu status is consistent with selection manager.
        mSelectionMenu.updateSelectAllMode(mSelectionManager.inSelectAllMode());
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.operation, menu);

        mMenu = menu;
        mShareMenuItem = menu.findItem(R.id.action_share);
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mSelectionManager.leaveSelectionMode();
    }

    private ArrayList<MediaObject> getSelectedMediaObjects(JobContext jc) {
        ArrayList<Path> unexpandedPaths = mSelectionManager.getSelected(false);
        if (unexpandedPaths.isEmpty()) {
            // This happens when starting selection mode from overflow menu
            // (instead of long press a media object)
            return null;
        }
        ArrayList<MediaObject> selected = new ArrayList<MediaObject>();
        DataManager manager = mActivity.getDataManager();
        for (Path path : unexpandedPaths) {
            if (jc.isCancelled()) {
                return null;
            }
            MediaObject object = manager.getMediaObject(path);
            // Skip a row that vanished while selected rather than NPE below.
            if (object != null) selected.add(object);
        }
        if (selected.isEmpty()) return null;

        return selected;
    }
    // Menu options are determined by selection set itself.
    // We cannot expand it because MenuExecuter executes it based on
    // the selection set instead of the expanded result.
    // e.g. LocalImage can be rotated but collections of them (LocalAlbum) can't.
    private int computeMenuOptions(ArrayList<MediaObject> selected) {
        int operation = MediaObject.SUPPORT_ALL;
        int type = 0;
        for (MediaObject mediaObject: selected) {
            int support = mediaObject.getSupportedOperations();
            type |= mediaObject.getMediaType();
            operation &= support;
        }

        switch (selected.size()) {
            case 1:
                final String mimeType = MenuExecutor.getMimeType(type);
                if (!GalleryUtils.isEditorAvailable(mActivity, mimeType)) {
                    operation &= ~MediaObject.SUPPORT_EDIT;
                }
                break;
            default:
                operation &= SUPPORT_MULTIPLE_MASK;
        }

        return operation;
    }

    // Share intent needs to expand the selection set so we can get URI of
    // each media item. Returns null when there is nothing to share (or the
    // expanded selection is over maxItems) and when the job was cancelled.
    private Intent computeSharingIntent(JobContext jc, int maxItems) {
        ArrayList<Path> expandedPaths = mSelectionManager.getSelected(true, maxItems);
        if (expandedPaths == null || expandedPaths.size() == 0) {
            return null;
        }
        final ArrayList<Uri> uris = new ArrayList<Uri>();
        DataManager manager = mActivity.getDataManager();
        int type = 0;
        for (Path path : expandedPaths) {
            if (jc.isCancelled()) return null;
            MediaObject object = manager.getMediaObject(path);
            // Skip a row that vanished while selected.
            if (object == null) continue;
            int support = object.getSupportedOperations();

            if ((support & MediaObject.SUPPORT_SHARE) != 0) {
                type |= object.getMediaType();
                uris.add(object.getContentUri());
            }
        }
        return ShareIntents.build(uris, MenuExecutor.getMimeType(type));
    }

    public void updateSupportedOperation(Path path, boolean selected) {
        // TODO: We need to improve the performance
        updateSupportedOperation();
    }

    public void updateSupportedOperation() {
        // Interrupt previous unfinished task, mMenuTask is only accessed in main thread
        if (mMenuTask != null) mMenuTask.cancel();

        updateSelectionMenu();

        // Disable share until the share intent for this selection is built
        mShareIntent = null;
        if (mShareMenuItem != null) mShareMenuItem.setEnabled(false);

        // Generate sharing intent and update supported operations in the background
        // The task can take a long time and be canceled in the mean time.
        mMenuTask = mActivity.getThreadPool().submit(new Job<Void>() {
            @Override
            public Void run(final JobContext jc) {
                // Pass1: Deal with unexpanded media object list for menu operation.
                ArrayList<MediaObject> selected = getSelectedMediaObjects(jc);
                if (selected == null) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mMenuTask = null;
                            if (jc.isCancelled()) return;
                            // Disable all the operations when no item is selected
                            MenuExecutor.updateMenuOperation(mMenu, 0);
                        }
                    });
                    return null;
                }
                final int operation = computeMenuOptions(selected);
                if (jc.isCancelled()) {
                    return null;
                }
                int numSelected = selected.size();
                final boolean canSharePanoramas =
                        numSelected < MAX_SELECTED_ITEMS_FOR_PANORAMA_SHARE_INTENT;
                final boolean canShare =
                        numSelected < MAX_SELECTED_ITEMS_FOR_SHARE_INTENT;

                final GetAllPanoramaSupports supportCallback = canSharePanoramas ?
                        new GetAllPanoramaSupports(selected, jc)
                        : null;

                // Pass2: Deal with expanded media object list for sharing operation.
                final Intent share_intent = canShare ?
                        computeSharingIntent(jc, MAX_SELECTED_ITEMS_FOR_SHARE_INTENT)
                        : null;

                if (canSharePanoramas) {
                    supportCallback.waitForPanoramaSupport();
                }
                if (jc.isCancelled()) {
                    return null;
                }
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mMenuTask = null;
                        if (jc.isCancelled()) return;
                        MenuExecutor.updateMenuOperation(mMenu, operation);
                        MenuExecutor.updateMenuForPanorama(mMenu,
                                canSharePanoramas && supportCallback.mHasPanorama360);
                        mShareIntent = share_intent;
                        if (mShareMenuItem != null) {
                            mShareMenuItem.setEnabled(share_intent != null);
                        }
                    }
                });
                return null;
            }
        });
    }

    public void pause() {
        if (mMenuTask != null) {
            mMenuTask.cancel();
            mMenuTask = null;
        }
        mMenuExecutor.pause();
    }

    public void destroy() {
        mMenuExecutor.destroy();
    }

    public void resume() {
        if (mSelectionManager.inSelectionMode()) updateSupportedOperation();
        mMenuExecutor.resume();
    }
}
