package com.android.gallery3d.fileops;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;

/**
 * Transparent shim that launches a MediaStore consent IntentSender and
 * broadcasts the user's answer. It exists because an IntentSender can only be
 * started from an Activity, and file-op batches run in a Service.
 */
public class ConsentActivity extends Activity {

    private static final String TAG = "ConsentActivity";
    private static final int REQUEST_CODE = 0x6C0D;

    public static final String EXTRA_INTENT_SENDER =
            "com.android.gallery3d.fileops.INTENT_SENDER";
    public static final String EXTRA_REQUEST_TOKEN =
            "com.android.gallery3d.fileops.REQUEST_TOKEN";
    public static final String ACTION_CONSENT_RESULT =
            "com.android.gallery3d.fileops.CONSENT_RESULT";
    public static final String EXTRA_GRANTED =
            "com.android.gallery3d.fileops.GRANTED";

    private String mRequestToken;

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
        IntentSender sender = getIntent().getParcelableExtra(EXTRA_INTENT_SENDER);
        if (sender == null) {
            finishWith(false);
            return;
        }
        try {
            startIntentSenderForResult(sender, REQUEST_CODE, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException failure) {
            Log.w(TAG, "Could not start the consent dialog", failure);
            finishWith(false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            finishWith(resultCode == Activity.RESULT_OK);
        }
    }

    private void finishWith(boolean granted) {
        Intent result = new Intent(ACTION_CONSENT_RESULT);
        result.setPackage(getPackageName());
        result.putExtra(EXTRA_REQUEST_TOKEN, mRequestToken);
        result.putExtra(EXTRA_GRANTED, granted);
        sendBroadcast(result);
        finish();
    }
}
