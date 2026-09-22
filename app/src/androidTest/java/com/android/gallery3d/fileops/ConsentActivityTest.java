package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ConsentActivityTest {

    @Test
    public void intentForCarriesTheTokenAndTargetsTheActivity() {
        Context context = ApplicationProvider.getApplicationContext();

        Intent intent = ConsentActivity.intentFor(context, null, "batch-7");

        assertNotNull(intent.getComponent());
        assertEquals(ConsentActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("batch-7",
                intent.getStringExtra(ConsentActivity.EXTRA_REQUEST_TOKEN));
    }

    @Test
    public void theActivityIsDeclaredInTheManifest() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = ConsentActivity.intentFor(context, null, "batch-7");

        assertNotNull("ConsentActivity must be registered in AndroidManifest.xml",
                intent.resolveActivity(context.getPackageManager()));
    }
}
