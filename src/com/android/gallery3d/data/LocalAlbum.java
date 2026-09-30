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

package com.android.gallery3d.data;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.VideoColumns;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.fileops.MediaQueryArgs;
import com.android.gallery3d.util.BucketNames;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.MediaSetUtils;

import java.io.File;
import java.util.ArrayList;

// LocalAlbumSet lists all media items in one bucket on local storage.
// The media items need to be all images or all videos, but not both.
public class LocalAlbum extends MediaSet {
    private static final String TAG = "LocalAlbum";
    private static final String[] COUNT_PROJECTION = { "COUNT(_id)" };

    private static final int INVALID_COUNT = -1;
    private final String mWhereClause;
    private final String mOrderClause;
    protected final Uri mBaseUri;
    private final String[] mProjection;

    private final GalleryApp mApplication;
    private final ContentResolver mResolver;
    private final int mBucketId;
    private final String mName;
    private final boolean mIsImage;
    private final ChangeNotifier mNotifier;
    private final Path mItemPath;
    private int mCachedCount = INVALID_COUNT;

    public LocalAlbum(Path path, GalleryApp application, int bucketId,
            boolean isImage, String name) {
        super(path, nextVersionNumber());
        mApplication = application;
        mResolver = application.getContentResolver();
        mBucketId = bucketId;
        mName = name;
        mIsImage = isImage;

        if (isImage) {
            mWhereClause = ImageColumns.BUCKET_ID + " = ?";
            mOrderClause = "CASE WHEN + " + ImageColumns.DATE_TAKEN + " != 0 THEN "
                    + ImageColumns.DATE_TAKEN + " ELSE ("
                    + ImageColumns.DATE_ADDED + " * 1000) END DESC, "
                    + ImageColumns._ID + " DESC";
            mBaseUri = Images.Media.EXTERNAL_CONTENT_URI;
            mProjection = LocalImage.PROJECTION;
            mItemPath = LocalImage.ITEM_PATH;
        } else {
            mWhereClause = VideoColumns.BUCKET_ID + " = ?";
            mOrderClause = "CASE WHEN + " + VideoColumns.DATE_TAKEN + " != 0 THEN "
                    + VideoColumns.DATE_TAKEN + " ELSE ("
                    + VideoColumns.DATE_ADDED + " * 1000) END DESC, "
                    + VideoColumns._ID + " DESC";
            mBaseUri = Video.Media.EXTERNAL_CONTENT_URI;
            mProjection = LocalVideo.PROJECTION;
            mItemPath = LocalVideo.ITEM_PATH;
        }

        mNotifier = new ChangeNotifier(this, mBaseUri, application);
    }

    public LocalAlbum(Path path, GalleryApp application, int bucketId,
            boolean isImage) {
        this(path, application, bucketId, isImage,
                BucketHelper.getBucketName(
                application.getContentResolver(), bucketId));
    }

    protected String getWhereClause() {
        return mWhereClause;
    }

    protected String[] getWhereArgs() {
        return new String[]{String.valueOf(mBucketId)};
    }

    /**
     * How this album's queries treat trashed rows, as a MediaStore MATCH_*
     * value. The default keeps MediaProvider's own default, which excludes
     * them. Overridden by the Trash, which wants {@link MediaStore#MATCH_ONLY}.
     *
     * This must travel in the query-args Bundle: MediaProvider ignores it as a
     * uri query parameter.
     */
    protected int getMatchTrashed() {
        return MediaStore.MATCH_DEFAULT;
    }

    /** Query args for this album's rows: selection, args, sort and trash filter. */
    private Bundle queryArgs(String sortOrder) {
        return MediaQueryArgs.matchTrashed(
                MediaQueryArgs.sql(getWhereClause(), getWhereArgs(), sortOrder),
                getMatchTrashed());
    }

    /**
     * Queries MediaStore, turning a provider failure into a null cursor. A
     * RuntimeException here would escape album loading and blank the grid.
     */
    private Cursor queryQuietly(String[] projection, Bundle args) {
        try {
            return mResolver.query(mBaseUri, projection, args, null);
        } catch (RuntimeException failure) {
            Log.w(TAG, "query failed: " + mBaseUri + " " + failure);
            return null;
        }
    }

    @Override
    public boolean isCameraRoll() {
        return mBucketId == MediaSetUtils.CAMERA_BUCKET_ID;
    }

    @Override
    public Uri getContentUri() {
        if (mIsImage) {
            return MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon()
                    .appendQueryParameter(LocalSource.KEY_BUCKET_ID,
                            String.valueOf(mBucketId)).build();
        } else {
            return MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon()
                    .appendQueryParameter(LocalSource.KEY_BUCKET_ID,
                            String.valueOf(mBucketId)).build();
        }
    }

    @Override
    public ArrayList<MediaItem> getMediaItem(int start, int count) {
        DataManager dataManager = mApplication.getDataManager();
        ArrayList<MediaItem> list = new ArrayList<MediaItem>();
        GalleryUtils.assertNotInRenderThread();
        // Paging goes in the Bundle: a "limit" uri parameter is ignored for apps
        // targeting API 30+, which would return the whole album from row 0.
        Cursor cursor = queryQuietly(mProjection,
                MediaQueryArgs.page(queryArgs(mOrderClause), start, count));
        if (cursor == null) {
            Log.w(TAG, "query fail: " + mBaseUri);
            return list;
        }

        try {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);  // _id must be in the first column
                Path childPath = mItemPath.getChild(id);
                MediaItem item = loadOrUpdateItem(childPath, cursor,
                        dataManager, mApplication, mIsImage);
                list.add(item);
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    private static MediaItem loadOrUpdateItem(Path path, Cursor cursor,
            DataManager dataManager, GalleryApp app, boolean isImage) {
        synchronized (DataManager.LOCK) {
            LocalMediaItem item = (LocalMediaItem) dataManager.peekMediaObject(path);
            if (item == null) {
                if (isImage) {
                    item = new LocalImage(path, app, cursor);
                } else {
                    item = new LocalVideo(path, app, cursor);
                }
            } else {
                item.updateContent(cursor);
            }
            return item;
        }
    }

    // The pids array are sorted by the (path) id.
    public static MediaItem[] getMediaItemById(
            GalleryApp application, boolean isImage, ArrayList<Integer> ids) {
        // get the lower and upper bound of (path) id
        MediaItem[] result = new MediaItem[ids.size()];
        if (ids.isEmpty()) return result;
        int idLow = ids.get(0);
        int idHigh = ids.get(ids.size() - 1);

        // prepare the query parameters
        Uri baseUri;
        String[] projection;
        Path itemPath;
        if (isImage) {
            baseUri = Images.Media.EXTERNAL_CONTENT_URI;
            projection = LocalImage.PROJECTION;
            itemPath = LocalImage.ITEM_PATH;
        } else {
            baseUri = Video.Media.EXTERNAL_CONTENT_URI;
            projection = LocalVideo.PROJECTION;
            itemPath = LocalVideo.ITEM_PATH;
        }

        ContentResolver resolver = application.getContentResolver();
        DataManager dataManager = application.getDataManager();
        Cursor cursor = resolver.query(baseUri, projection, "_id BETWEEN ? AND ?",
                new String[]{String.valueOf(idLow), String.valueOf(idHigh)},
                "_id");
        if (cursor == null) {
            Log.w(TAG, "query fail" + baseUri);
            return result;
        }
        try {
            int n = ids.size();
            int i = 0;

            while (i < n && cursor.moveToNext()) {
                int id = cursor.getInt(0);  // _id must be in the first column

                // Match id with the one on the ids list.
                if (ids.get(i) > id) {
                    continue;
                }

                while (ids.get(i) < id) {
                    if (++i >= n) {
                        return result;
                    }
                }

                Path childPath = itemPath.getChild(id);
                MediaItem item = loadOrUpdateItem(childPath, cursor, dataManager,
                        application, isImage);
                result[i] = item;
                ++i;
            }
            return result;
        } finally {
            cursor.close();
        }
    }

    /**
     * One item by id, trashed or not. A Path to a trashed item (selected in
     * the Trash) is rebuilt through here once its object has been collected,
     * and must still resolve, or Restore and Delete forever lose the item.
     * Returns null when the provider refuses the query.
     */
    public static Cursor getItemCursor(ContentResolver resolver, Uri uri,
            String[] projection, int id) {
        Bundle args = MediaQueryArgs.matchTrashed(
                MediaQueryArgs.sql("_id=?", new String[]{String.valueOf(id)}, null),
                MediaStore.MATCH_INCLUDE);
        try {
            return resolver.query(uri, projection, args, null);
        } catch (RuntimeException failure) {
            Log.w(TAG, "item query failed: " + uri + "/" + id + " " + failure);
            return null;
        }
    }

    @Override
    public int getMediaItemCount() {
        if (mCachedCount == INVALID_COUNT) {
            Cursor cursor = queryQuietly(COUNT_PROJECTION, queryArgs(null));
            if (cursor == null) {
                Log.w(TAG, "query fail");
                return 0;
            }
            try {
                if (!cursor.moveToNext()) return 0;
                mCachedCount = cursor.getInt(0);
            } finally {
                cursor.close();
            }
        }
        return mCachedCount;
    }

    @Override
    public String getName() {
        return getLocalizedName(mApplication.getResources(), mBucketId, mName);
    }

    @Override
    public long reload() {
        if (mNotifier.isDirty()) {
            mDataVersion = nextVersionNumber();
            mCachedCount = INVALID_COUNT;
        }
        return mDataVersion;
    }

    /**
     * The folder this album lives in, as a MediaStore RELATIVE_PATH.
     *
     * AOSP resolves bucket ids by hashing directory paths with no reverse map;
     * asking MediaStore for the RELATIVE_PATH of one item in the bucket is both
     * exact and scoped-storage-safe.
     */
    public String getRelativePath() {
        Cursor cursor = mResolver.query(
                mBaseUri,
                new String[]{MediaStore.MediaColumns.RELATIVE_PATH},
                getWhereClause(), getWhereArgs(), null);
        if (cursor == null) return "";
        try {
            if (!cursor.moveToFirst()) return "";
            return com.android.gallery3d.fileops.RelativePaths.normalise(cursor.getString(0));
        } finally {
            cursor.close();
        }
    }

    @Override
    public int getSupportedOperations() {
        return SUPPORT_SHARE | SUPPORT_INFO | SUPPORT_RENAME_FOLDER | SUPPORT_MOVE_FOLDER
                | SUPPORT_DELETE;
    }

    @Override
    public void delete() {
        // F-024: deleting a folder sends its contents to Trash. The files stay
        // on disk until the trash is emptied or the 30-day purge runs.
        com.android.gallery3d.fileops.FolderOpResult result =
                new com.android.gallery3d.fileops.FileOpEngine(
                        new com.android.gallery3d.fileops.ContentResolverGateway(
                                mApplication.getAndroidContext()))
                        .trashFolder(getRelativePath());
        if (!result.ok) {
            throw new UnsupportedOperationException(result.failureReason);
        }
    }

    @Override
    public boolean isLeafAlbum() {
        return true;
    }

    public static String getLocalizedName(Resources res, int bucketId,
            String name) {
        if (bucketId == MediaSetUtils.CAMERA_BUCKET_ID) {
            return res.getString(R.string.folder_camera);
        } else if (bucketId == MediaSetUtils.DOWNLOAD_BUCKET_ID) {
            return res.getString(R.string.folder_download);
        } else if (bucketId == MediaSetUtils.IMPORTED_BUCKET_ID) {
            return res.getString(R.string.folder_imported);
        } else if (bucketId == MediaSetUtils.SNAPSHOT_BUCKET_ID) {
            return res.getString(R.string.folder_screenshot);
        } else if (bucketId == MediaSetUtils.EDITED_ONLINE_PHOTOS_BUCKET_ID) {
            return res.getString(R.string.folder_edited_online_photos);
        } else {
            return name;
        }
    }

    // Relative path is the absolute path minus external storage path
    public static String getRelativePath(int bucketId, android.content.Context context) {
        String relativePath = "/";
        if (bucketId == MediaSetUtils.CAMERA_BUCKET_ID) {
            relativePath += BucketNames.CAMERA;
        } else if (bucketId == MediaSetUtils.DOWNLOAD_BUCKET_ID) {
            relativePath += BucketNames.DOWNLOAD;
        } else if (bucketId == MediaSetUtils.IMPORTED_BUCKET_ID) {
            relativePath += BucketNames.IMPORTED;
        } else if (bucketId == MediaSetUtils.SNAPSHOT_BUCKET_ID) {
            relativePath += BucketNames.SCREENSHOTS;
        } else if (bucketId == MediaSetUtils.EDITED_ONLINE_PHOTOS_BUCKET_ID) {
            relativePath += BucketNames.EDITED_ONLINE_PHOTOS;
        } else {
            return com.android.gallery3d.fileops.BucketPathResolver.resolve(
                    new com.android.gallery3d.fileops.ContentResolverGateway(context),
                    bucketId);
        }
        return relativePath;
    }

}
