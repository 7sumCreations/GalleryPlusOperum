package com.android.gallery3d.fileops;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;

/**
 * Transparent shim that launches a MediaStore consent IntentSender and hands
 * the user's answer back to FileOpService. It exists because an IntentSender
 * can only be started from an Activity, and file-op batches run in a Service.
 *
 * It reports exactly once per dialog: the answer from onActivityResult, or
 * "not granted" if it is finished any other way. A rotation recreates it
 * without re-launching the dialog, and the answer is delivered to the new
 * instance.
 */
public class ConsentActivity extends Activity {

    private static final String TAG = "ConsentActivity";
    private static final int REQUEST_CODE = 0x6C0D;
    private static final String STATE_LAUNCHED = "launched";
    private static final String STATE_REPORTED = "reported";

    public static final String EXTRA_INTENT_SENDER =
            "com.android.gallery3d.fileops.INTENT_SENDER";
    public static final String EXTRA_REQUEST_TOKEN =
            "com.android.gallery3d.fileops.REQUEST_TOKEN";

    private String mRequestToken;
    private boolean mLaunched;
    private boolean mReported;

    public static Intent intentFor(Context context, IntentSender sender, String requestToken) {
        Intent intent = new Intent(context, ConsentActivity.class);
        intent.putExtra(EXTRA_INTENT_SENDER, (android.os.Parcelable) sender);
        intent.putExtra(EXTRA_REQUEST_TOKEN, requestToken);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRequestToken = getIntent().getStringExtra(EXTRA_REQUEST_TOKEN);
        if (savedInstanceState != null) {
            mLaunched = savedInstanceState.getBoolean(STATE_LAUNCHED);
            mReported = savedInstanceState.getBoolean(STATE_REPORTED);
        }
        if (mReported) {
            finish();
            return;
        }
        // Recreated while the dialog is up: its result comes to this instance.
        if (mLaunched) return;

        IntentSender sender;
        try {
            sender = getIntent().getParcelableExtra(EXTRA_INTENT_SENDER);
        } catch (RuntimeException malformed) {
            sender = null;
        }
        if (sender == null) {
            finishWith(false);
            return;
        }
        try {
            startIntentSenderForResult(sender, REQUEST_CODE, null, 0, 0, 0);
            mLaunched = true;
        } catch (IntentSender.SendIntentException failure) {
            Log.w(TAG, "Could not start the consent dialog", failure);
            finishWith(false);
        } catch (RuntimeException failure) {
            Log.w(TAG, "Could not start the consent dialog", failure);
            finishWith(false);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_LAUNCHED, mLaunched);
        outState.putBoolean(STATE_REPORTED, mReported);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            finishWith(resultCode == Activity.RESULT_OK);
        }
    }

    @Override
    protected void onDestroy() {
        // Finished without an answer (e.g. the task was torn down). Not while
        // merely being recreated: isFinishing() is false for a rotation, and
        // for a "don't keep activities" destroy whose result still comes back.
        if (!mReported && isFinishing()) report(false);
        super.onDestroy();
    }

    private void finishWith(boolean granted) {
        report(granted);
        finish();
    }

    private void report(boolean granted) {
        if (mReported) return;
        mReported = true;
        try {
            // The activity is in the foreground, so starting the service is
            // allowed; the catch covers the process-restart case where it is not.
            startService(FileOpService.consentResultIntent(this, mRequestToken, granted));
        } catch (RuntimeException failure) {
            Log.w(TAG, "Could not report consent for " + mRequestToken, failure);
        }
    }
}
