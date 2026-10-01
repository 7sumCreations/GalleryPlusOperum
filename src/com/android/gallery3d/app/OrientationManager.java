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
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.view.Surface;

import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.ui.OrientationSource;

public class OrientationManager implements OrientationSource {
    private static final String TAG = "OrientationManager";

    private Activity mActivity;
    // If the framework orientation is locked.
    private boolean mOrientationLocked = false;

    public OrientationManager(Activity activity) {
        mActivity = activity;
    }

    // AOSP enabled an OrientationEventListener (accelerometer) here whose
    // result was computed and thrown away; getCompensation() is always 0.
    // It only made GrapheneOS report sensor access, so it is gone. Screen
    // rotation is the framework's job and is unaffected.
    public void resume() {
    }

    public void pause() {
    }

    ////////////////////////////////////////////////////////////////////////////
    //  Orientation handling
    //
    //  We can choose to lock the framework orientation or not. If we lock the
    //  framework orientation, we calculate a a compensation value according to
    //  current device orientation and send it to listeners. If we don't lock
    //  the framework orientation, we always set the compensation value to 0.
    ////////////////////////////////////////////////////////////////////////////

    // Lock the framework orientation to the current device orientation
    public void lockOrientation() {
        if (mOrientationLocked) return;
        mOrientationLocked = true;
        if (ApiHelper.HAS_ORIENTATION_LOCK) {
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        } else {
            mActivity.setRequestedOrientation(calculateCurrentScreenOrientation());
        }
    }

    // Unlock the framework orientation, so it can change when the device
    // rotates.
    public void unlockOrientation() {
        if (!mOrientationLocked) return;
        mOrientationLocked = false;
        Log.d(TAG, "unlock orientation");
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
    }

    private int calculateCurrentScreenOrientation() {
        int displayRotation = getDisplayRotation();
        // Display rotation >= 180 means we need to use the REVERSE landscape/portrait
        boolean standard = displayRotation < 180;
        if (mActivity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            return standard
                    ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        } else {
            if (displayRotation == 90 || displayRotation == 270) {
                // If displayRotation = 90 or 270 then we are on a landscape
                // device. On landscape devices, portrait is a 90 degree
                // clockwise rotation from landscape, so we need
                // to flip which portrait we pick as display rotation is counter clockwise
                standard = !standard;
            }
            return standard
                    ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    : ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        }
    }

    @Override
    public int getDisplayRotation() {
        return getDisplayRotation(mActivity);
    }

    @Override
    public int getCompensation() {
        return 0;
    }

    private static int getDisplayRotation(Activity activity) {
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        switch (rotation) {
            case Surface.ROTATION_0: return 0;
            case Surface.ROTATION_90: return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
        }
        return 0;
    }
}
