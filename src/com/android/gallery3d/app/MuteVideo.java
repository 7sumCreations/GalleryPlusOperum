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

package com.android.gallery3d.app;

import android.app.Activity;
import android.app.ProgressDialog;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.util.SaveVideoFileInfo;
import com.android.gallery3d.util.SaveVideoFileUtils;

public class MuteVideo {

    private static final String TAG = "MuteVideo";

    private ProgressDialog mMuteProgress;

    private String mFilePath = null;
    private Uri mUri = null;
    private SaveVideoFileInfo mDstFileInfo = null;
    private Activity mActivity = null;
    private final Handler mHandler = new Handler();

    final String TIME_STAMP_NAME = "'MUTE'_yyyyMMdd_HHmmss";

    public MuteVideo(String filePath, Uri uri, Activity activity) {
        mUri = uri;
        mFilePath = filePath;
        mActivity = activity;
    }

    public void muteInBackground() {
        mDstFileInfo = SaveVideoFileUtils.getDstMp4FileInfo(TIME_STAMP_NAME,
                mActivity.getContentResolver(), mUri,
                mActivity.getString(R.string.folder_download));

        showProgressDialog();
        new Thread(new Runnable() {
                @Override
            public void run() {
                Uri savedUri = null;
                boolean ok = false;
                try {
                    VideoUtils.startMute(mFilePath, mDstFileInfo);
                    savedUri = SaveVideoFileUtils.insertContent(
                            mDstFileInfo, mActivity.getContentResolver(), mUri);
                    ok = true;
                } catch (Exception e) {
                    // IOException from the muxer, or a RuntimeException from
                    // MediaStore / the codec stack. Never let it kill the app.
                    Log.w(TAG, "mute failed", e);
                    SaveVideoFileUtils.deleteQuietly(mDstFileInfo);
                }
                final boolean succeeded = ok;
                final Uri resultUri = savedUri;
                // After muting is done, trigger the UI changed. Toasts and
                // the viewer must run on the main thread.
                mHandler.post(new Runnable() {
                        @Override
                    public void run() {
                        if (!succeeded) {
                            Toast.makeText(mActivity.getApplicationContext(),
                                    R.string.video_mute_err, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(mActivity.getApplicationContext(),
                                    mActivity.getString(R.string.save_into,
                                            mDstFileInfo.mFolderName),
                                    Toast.LENGTH_SHORT)
                                    .show();
                        }

                        if (mMuteProgress != null) {
                            try {
                                mMuteProgress.dismiss();
                            } catch (RuntimeException e) {
                                // Window already gone.
                            }
                            mMuteProgress = null;

                            // Show the result only when the activity not
                            // stopped.
                            if (succeeded) {
                                SaveVideoFileUtils.viewSavedVideo(mActivity, resultUri);
                            }
                        }
                    }
                });
            }
        }).start();
    }

    private void showProgressDialog() {
        mMuteProgress = new ProgressDialog(mActivity);
        mMuteProgress.setTitle(mActivity.getString(R.string.muting));
        mMuteProgress.setMessage(mActivity.getString(R.string.please_wait));
        mMuteProgress.setCancelable(false);
        mMuteProgress.setCanceledOnTouchOutside(false);
        mMuteProgress.show();
    }
}
