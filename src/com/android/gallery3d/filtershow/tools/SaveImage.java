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

package com.android.gallery3d.filtershow.tools;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.exif.ExifInterface;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.FiltersManager;
import com.android.gallery3d.filtershow.imageshow.PrimaryImage;
import com.android.gallery3d.filtershow.pipeline.CachingPipeline;
import com.android.gallery3d.filtershow.pipeline.ImagePreset;
import com.android.gallery3d.filtershow.pipeline.ProcessingService;
import com.android.gallery3d.util.XmpUtilHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Handles saving an edited photo.
 *
 * Every save produces a NEW image (see {@link EditedCopies} for where it goes
 * and what it is called). The photo being edited is only ever read: the old
 * AOSP behaviour (overwrite the original's MediaStore row and hide the
 * original in a ".aux" folder) crashed with a RecoverableSecurityException
 * for any photo another app owns, such as every camera photo.
 *
 * Copies made by older builds may carry XMP naming a ".aux" original; the
 * editor still loads that original (FilterShowActivity), and saving it is
 * just another new copy. The copy written here carries no filter XMP: it is
 * a self-contained photo.
 */
public class SaveImage {
    private static final String LOGTAG = "SaveImage";

    /**
     * Callback for updates
     */
    public interface Callback {
        void onProgress(int max, int current);
    }

    public interface ContentResolverQueryCallback {
        void onCursorResult(Cursor cursor);
    }

    private static final String JPEG_EXTENSION = "jpg";
    private static final String SHARE_DIR = "filtershow-share";
    private static final String RENDER_DIR = "filtershow-save";

    private final Context mContext;
    private final Uri mSourceUri;
    private final Callback mCallback;
    private final File mRenderFile;
    private final Uri mSelectedImageUri;

    private int mCurrentProcessingStep = 1;

    public static final int MAX_PROCESSING_STEPS = 6;

    /**
     * @param sourceUri the pixels to render: the photo being edited, or the
     *  original an older build's copy points at.
     * @param selectedImageUri the photo the user opened. Only read, for the
     *  copy's folder, name and date taken.
     * @param renderFile where the rendered JPEG is written before it is
     *  published; kept afterwards (share needs it). When null a temporary
     *  file in the cache is used and deleted.
     */
    public SaveImage(Context context, Uri sourceUri, Uri selectedImageUri,
                     File renderFile, Callback callback) {
        mContext = context;
        mSourceUri = sourceUri;
        mCallback = callback;
        mRenderFile = renderFile;
        mSelectedImageUri = selectedImageUri;
    }

    /** A private file for the editor's share output, served by SharedImageProvider. */
    public static File newShareFile(Context context) {
        File dir = new File(context.getCacheDir(), SHARE_DIR);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date(System.currentTimeMillis()));
        return new File(dir, "IMG_" + stamp + "." + JPEG_EXTENSION);
    }

    private Object getPanoramaXMPData(Uri source, ImagePreset preset) {
        Object xmp = null;
        if (preset.isPanoramaSafe()) {
            InputStream is = null;
            try {
                is = mContext.getContentResolver().openInputStream(source);
                xmp = XmpUtilHelper.extractXMPMeta(is);
            } catch (IOException | RuntimeException e) {
                Log.w(LOGTAG, "Failed to get XMP data from image: ", e);
            } finally {
                Utils.closeSilently(is);
            }
        }
        return xmp;
    }

    private void putPanoramaXMPData(File file, Object xmp) {
        if (xmp != null) {
            try {
                XmpUtilHelper.writeXMPMeta(file.getAbsolutePath(), xmp);
            } catch (RuntimeException e) {
                Log.w(LOGTAG, "Failed to write XMP data: ", e);
            }
        }
    }

    private ExifInterface getExifData(Uri source) {
        ExifInterface exif = new ExifInterface();
        InputStream inStream = null;
        try {
            String mimeType = mContext.getContentResolver().getType(mSelectedImageUri);
            if (mimeType == null) {
                mimeType = ImageLoader.getMimeType(mSelectedImageUri);
            }
            if ((mimeType != null) && mimeType.equals(ImageLoader.JPEG_MIME_TYPE)) {
                inStream = mContext.getContentResolver().openInputStream(source);
                exif.readExif(inStream);
            }
        } catch (IOException | RuntimeException e) {
            // A copy without the original's EXIF is better than no copy.
            Log.w(LOGTAG, "Cannot read exif for: " + source, e);
            exif = new ExifInterface();
        } finally {
            Utils.closeSilently(inStream);
        }
        return exif;
    }

    private static boolean putExifData(File file, ExifInterface exif, Bitmap image,
            int jpegCompressQuality) {
        boolean ret = false;
        OutputStream s = null;
        try {
            s = exif.getExifWriterStream(file.getAbsolutePath());
            image.compress(Bitmap.CompressFormat.JPEG,
                    (jpegCompressQuality > 0) ? jpegCompressQuality : 1, s);
            s.flush();
            s.close();
            s = null;
            ret = true;
        } catch (IOException e) {
            Log.w(LOGTAG, "Could not write " + file.getAbsolutePath(), e);
        } finally {
            Utils.closeSilently(s);
        }
        return ret;
    }

    private void resetProgress() {
        mCurrentProcessingStep = 0;
    }

    private void updateProgress() {
        if (mCallback != null) {
            mCallback.onProgress(MAX_PROCESSING_STEPS, ++mCurrentProcessingStep);
        }
    }

    private static void updateExifData(ExifInterface exif, long time) {
        // Set tags
        exif.addDateTimeStampTag(ExifInterface.TAG_DATE_TIME, time,
                TimeZone.getDefault());
        // The rendered bitmap is already upright.
        exif.setTag(exif.buildTag(ExifInterface.TAG_ORIENTATION,
                ExifInterface.Orientation.TOP_LEFT));
        // Remove old thumbnail
        exif.removeCompressedThumbnail();
    }

    /**
     * Render the edit and publish it as a new photo.
     *
     * @return the new photo's uri, or null when the save failed. Never throws:
     *  this runs on the editor's HandlerThread, where an exception kills the app.
     */
    public Uri processAndSaveImage(ImagePreset preset, boolean flatten,
                                   int quality, float sizeFactor, boolean exit) {
        File renderFile = mRenderFile;
        boolean temporary = renderFile == null;
        try {
            if (temporary) {
                File dir = new File(mContext.getCacheDir(), RENDER_DIR);
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new IOException("Cannot create " + dir);
                }
                renderFile = File.createTempFile("edit", "." + JPEG_EXTENSION, dir);
            } else {
                File dir = renderFile.getParentFile();
                if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
                    throw new IOException("Cannot create " + dir);
                }
            }
            if (!render(preset, quality, sizeFactor, renderFile)) {
                return null;
            }
            EditedCopyWriter.Target target = EditedCopyWriter.plan(mContext,
                    mSelectedImageUri, JPEG_EXTENSION);
            Uri saved = EditedCopyWriter.saveFile(mContext, target, renderFile,
                    ImageLoader.JPEG_MIME_TYPE);
            updateProgress();
            Log.i(LOGTAG, "Saved the edit as " + target.relativePath + target.displayName);
            return saved;
        } catch (IOException | RuntimeException e) {
            Log.w(LOGTAG, "Could not save the edited photo", e);
            return null;
        } catch (OutOfMemoryError e) {
            Log.w(LOGTAG, "Out of memory saving the edited photo", e);
            return null;
        } finally {
            if (temporary && renderFile != null && !renderFile.delete()) {
                renderFile.deleteOnExit();
            }
        }
    }

    /** Render {@code preset} over the source into {@code out}; false when it can't. */
    private boolean render(ImagePreset preset, int quality, float sizeFactor, File out) {
        int numTries = 0;
        int sampleSize = 1;
        resetProgress();
        // Stopgap fix for low-memory devices.
        while (true) {
            try {
                updateProgress();
                // Try to do bitmap operations, downsample if low-memory
                Bitmap bitmap = ImageLoader.loadOrientedBitmapWithBackouts(mContext, mSourceUri,
                        sampleSize);
                if (bitmap == null) {
                    Log.w(LOGTAG, "Cannot decode " + mSourceUri);
                    return false;
                }
                if (sizeFactor != 1f) {
                    // if we have a valid size
                    int w = (int) (bitmap.getWidth() * sizeFactor);
                    int h = (int) (bitmap.getHeight() * sizeFactor);
                    if (w == 0 || h == 0) {
                        w = 1;
                        h = 1;
                    }
                    bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
                }
                updateProgress();
                CachingPipeline pipeline = new CachingPipeline(FiltersManager.getManager(),
                        "Saving");

                bitmap = pipeline.renderFinalImage(bitmap, preset);
                if (bitmap == null) {
                    return false;
                }
                updateProgress();

                Object xmp = getPanoramaXMPData(mSourceUri, preset);
                ExifInterface exif = getExifData(mSourceUri);
                long time = System.currentTimeMillis();
                updateProgress();

                updateExifData(exif, time);
                updateProgress();

                if (!putExifData(out, exif, bitmap, quality)) {
                    return false;
                }
                putPanoramaXMPData(out, xmp);
                return true;
            } catch (OutOfMemoryError e) {
                // Try 5 times before failing for good.
                if (++numTries >= 5) {
                    throw e;
                }
                System.gc();
                sampleSize *= 2;
                resetProgress();
            }
        }
    }

    public static void saveImage(ImagePreset preset, final FilterShowActivity filterShowActivity,
            File destination) {
        Uri selectedImageUri = filterShowActivity.getSelectedImageUri();
        Uri sourceImageUri = PrimaryImage.getImage().getUri();
        boolean flatten = false;
        if (preset.contains(FilterRepresentation.TYPE_TINYPLANET)){
            flatten = true;
        }
        Intent processIntent = ProcessingService.getSaveIntent(filterShowActivity, preset,
                destination, selectedImageUri, sourceImageUri, flatten, 90, 1f, true);

        filterShowActivity.startService(processIntent);

        if (!filterShowActivity.isSimpleEditAction()) {
            String toastMessage = filterShowActivity.getResources().getString(
                    R.string.save_and_processing);
            Toast.makeText(filterShowActivity,
                    toastMessage,
                    Toast.LENGTH_SHORT).show();
        }
    }
}
