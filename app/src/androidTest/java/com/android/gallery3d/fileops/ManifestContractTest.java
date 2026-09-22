package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ManifestContractTest {

    private List<String> declaredPermissions() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PackageInfo info = context.getPackageManager().getPackageInfo(
                context.getPackageName(), PackageManager.GET_PERMISSIONS);
        if (info.requestedPermissions == null) return Collections.emptyList();
        return Arrays.asList(info.requestedPermissions);
    }

    @Test
    public void declaresNoInternetPermission() throws Exception {
        assertFalse("AC10: the app must not request INTERNET",
                declaredPermissions().contains("android.permission.INTERNET"));
    }

    @Test
    public void declaresGranularMediaPermissions() throws Exception {
        List<String> permissions = declaredPermissions();
        assertTrue(permissions.contains("android.permission.READ_MEDIA_IMAGES"));
        assertTrue(permissions.contains("android.permission.READ_MEDIA_VIDEO"));
    }

    @Test
    public void targetsApi33() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals(33, context.getApplicationInfo().targetSdkVersion);
    }
}
