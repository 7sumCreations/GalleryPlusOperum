/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.util;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.VideoColumns;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.app.MovieActivity;

import com.android.gallery3d.filtershow.tools.SaveImage.ContentResolverQueryCallback;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SaveVideoFileUtils {
    // This function can decide which folder to save the video file, and generate
    // the needed information for the video file including filename.
    public static SaveVideoFileInfo getDstMp4FileInfo(String fileNameFormat,
            ContentResolver contentResolver, Uri uri, String defaultFolderName) {
        SaveVideoFileInfo dstFileInfo = new SaveVideoFileInfo();
        // Use the default save directory if the source directory cannot be
        // saved.
        dstFileInfo.mDirectory = getSaveDirectory(contentResolver, uri);
        if ((dstFileInfo.mDirectory == null) || !dstFileInfo.mDirectory.canWrite()) {
            dstFileInfo.mDirectory = new File(Environment.getExternalStorageDirectory(),
                    BucketNames.DOWNLOAD);
            dstFileInfo.mFolderName = defaultFolderName;
        } else {
            dstFileInfo.mFolderName = dstFileInfo.mDirectory.getName();
        }
        dstFileInfo.mFileName = new SimpleDateFormat(fileNameFormat).format(
                new Date(System.currentTimeMillis()));

        dstFileInfo.mFile = new File(dstFileInfo.mDirectory, dstFileInfo.mFileName + ".mp4");
        return dstFileInfo;
    }

    private static void querySource(ContentResolver contentResolver, Uri uri,
            String[] projection, ContentResolverQueryCallback callback) {
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(uri, projection, null, null, null);
            if ((cursor != null) && cursor.moveToNext()) {
                callback.onCursorResult(cursor);
            }
        } catch (Exception e) {
            // Ignore error for lacking the data column from the source.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private static File getSaveDirectory(ContentResolver contentResolver, Uri uri) {
        final File[] dir = new File[1];
        querySource(contentResolver, uri,
                new String[] { VideoColumns.DATA },
                new ContentResolverQueryCallback() {
            @Override
            public void onCursorResult(Cursor cursor) {
                dir[0] = new File(cursor.getString(0)).getParentFile();
            }
        });
        return dir[0];
    }


    /**
     * Register the saved file with MediaStore and return its content uri, or
     * null if MediaStore has no row for it.
     *
     * Since Android 11 the file was written through the FUSE path, and the
     * provider already created a row (owned by this app) when the file was
     * opened. Inserting a second row for the same _DATA path is not reliable
     * there, so reuse that row and only copy the source's metadata onto it.
     * The plain insert remains the fallback when no row exists yet.
     */
    public static Uri insertContent(SaveVideoFileInfo mDstFileInfo,
            ContentResolver contentResolver, Uri uri ) {
        long nowInMs = System.currentTimeMillis();
        long nowInSec = nowInMs / 1000;
        final ContentValues values = new ContentValues(13);
        values.put(Video.Media.TITLE, mDstFileInfo.mFileName);
        values.put(Video.Media.DISPLAY_NAME, mDstFileInfo.mFile.getName());
        values.put(Video.Media.MIME_TYPE, "video/mp4");
        values.put(Video.Media.DATE_TAKEN, nowInMs);
        values.put(Video.Media.DATE_MODIFIED, nowInSec);
        values.put(Video.Media.DATE_ADDED, nowInSec);
        values.put(Video.Media.DATA, mDstFileInfo.mFile.getAbsolutePath());
        values.put(Video.Media.SIZE, mDstFileInfo.mFile.length());
        int durationMs = retrieveVideoDurationMs(mDstFileInfo.mFile.getPath());
        values.put(Video.Media.DURATION, durationMs);
        // Copy the data taken and location info from src.
        String[] projection = new String[] {
                VideoColumns.DATE_TAKEN,
                VideoColumns.LATITUDE,
                VideoColumns.LONGITUDE,
                VideoColumns.RESOLUTION,
        };

        // Copy some info from the source file.
        querySource(contentResolver, uri, projection,
                new ContentResolverQueryCallback() {
                @Override
                    public void onCursorResult(Cursor cursor) {
                        long timeTaken = cursor.getLong(0);
                        if (timeTaken > 0) {
                            values.put(Video.Media.DATE_TAKEN, timeTaken);
                        }
                        double latitude = cursor.getDouble(1);
                        double longitude = cursor.getDouble(2);
                        // TODO: Change || to && after the default location
                        // issue is
                        // fixed.
                        if ((latitude != 0f) || (longitude != 0f)) {
                            values.put(Video.Media.LATITUDE, latitude);
                            values.put(Video.Media.LONGITUDE, longitude);
                        }
                        values.put(Video.Media.RESOLUTION, cursor.getString(3));

                    }
                });

        Uri existing = findExistingRow(contentResolver, mDstFileInfo.mFile);
        if (existing != null) {
            ContentValues update = new ContentValues();
            update.put(Video.Media.DATE_TAKEN, values.getAsLong(Video.Media.DATE_TAKEN));
            if (values.containsKey(Video.Media.LATITUDE)) {
                update.put(Video.Media.LATITUDE, values.getAsDouble(Video.Media.LATITUDE));
                update.put(Video.Media.LONGITUDE, values.getAsDouble(Video.Media.LONGITUDE));
            }
            try {
                contentResolver.update(existing, update, null, null);
            } catch (RuntimeException e) {
                // The row exists and points at the file; metadata is cosmetic.
            }
            return existing;
        }
        return contentResolver.insert(Video.Media.EXTERNAL_CONTENT_URI, values);
    }

    private static Uri findExistingRow(ContentResolver contentResolver, File file) {
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(Video.Media.EXTERNAL_CONTENT_URI,
                    new String[] { VideoColumns._ID },
                    VideoColumns.DATA + "=?",
                    new String[] { file.getAbsolutePath() }, null);
            if (cursor != null && cursor.moveToFirst()) {
                return ContentUris.withAppendedId(
                        Video.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0));
            }
        } catch (RuntimeException e) {
            // Fall through to the insert.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private static int retrieveVideoDurationMs(String path) {
        int durationMs = 0;
        // Calculate the duration of the destination file.
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            String duration = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration != null) {
                durationMs = Integer.parseInt(duration);
            }
        } catch (RuntimeException e) {
            // Unreadable output: leave the duration for the scanner to fill in.
        }
        try {
            retriever.release();
        } catch (IOException e) {
            // Ignore errors occurred while releasing the retriever.
        }
        return durationMs;
    }

    /**
     * Open a freshly saved video in this app's player through its MediaStore
     * content uri. A null uri or any failure shows a toast instead of crashing.
     * Must be called on the main thread.
     */
    public static void viewSavedVideo(Activity activity, Uri uri) {
        if (uri == null) {
            Toast.makeText(activity.getApplicationContext(),
                    R.string.video_open_err, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setClass(activity, MovieActivity.class)
                    .setDataAndType(uri, "video/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(MediaStore.EXTRA_FINISH_ON_COMPLETION, false);
            activity.startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(activity.getApplicationContext(),
                    R.string.video_open_err, Toast.LENGTH_SHORT).show();
        }
    }

    /** Remove a half-written output after a failed trim or mute. */
    public static void deleteQuietly(SaveVideoFileInfo info) {
        try {
            if (info != null && info.mFile != null && info.mFile.exists()) {
                info.mFile.delete();
            }
        } catch (RuntimeException e) {
            // Nothing more to do.
        }
    }
}
