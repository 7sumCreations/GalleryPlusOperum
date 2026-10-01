package com.android.gallery3d.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.gallery3d.app.MediaAccessPolicy.Action;

import org.junit.Test;

/** First-run photo access: granted / never asked / denied / permanently denied. */
public class MediaAccessPolicyTest {

    @Test
    public void anyGrantedAccessShowsTheLibrary() {
        // Full access, Android 14 "Select photos" (temporary grant for a
        // target-33 app) and GrapheneOS Storage Scopes all read as granted.
        assertEquals(Action.SHOW, MediaAccessPolicy.decide(true, false, false));
        assertEquals(Action.SHOW, MediaAccessPolicy.decide(true, true, false));
        assertEquals(Action.SHOW, MediaAccessPolicy.decide(true, true, true));
    }

    @Test
    public void neverAskedRequests() {
        assertEquals(Action.REQUEST, MediaAccessPolicy.decide(false, false, false));
        assertEquals(Action.REQUEST, MediaAccessPolicy.decide(false, false, true));
    }

    @Test
    public void deniedOnceExplainsAndOffersToAskAgain() {
        assertEquals(Action.EXPLAIN_CAN_RETRY, MediaAccessPolicy.decide(false, true, true));
    }

    @Test
    public void permanentlyDeniedGoesStraightToSettingsAndNeverRequests() {
        assertEquals(Action.EXPLAIN_OPEN_SETTINGS,
                MediaAccessPolicy.decide(false, true, false));
    }

    @Test
    public void onceAskedTheSystemDialogIsNeverReShownAutomatically() {
        for (boolean rationale : new boolean[] {false, true}) {
            assertFalse(MediaAccessPolicy.decide(false, true, rationale) == Action.REQUEST);
        }
    }

    @Test
    public void notificationsAreAskedOnceAndOnlyOnAndroid13Plus() {
        assertTrue(MediaAccessPolicy.shouldAskNotifications(33, false, false));
        assertTrue(MediaAccessPolicy.shouldAskNotifications(35, false, false));
        assertFalse(MediaAccessPolicy.shouldAskNotifications(32, false, false));
        assertFalse(MediaAccessPolicy.shouldAskNotifications(29, false, false));
        assertFalse(MediaAccessPolicy.shouldAskNotifications(33, true, false));
        assertFalse(MediaAccessPolicy.shouldAskNotifications(33, false, true));
    }

    @Test
    public void onlyTheLibraryLaunchAsks() {
        assertTrue(MediaAccessPolicy.browsesLibrary(null));
        assertTrue(MediaAccessPolicy.browsesLibrary("android.intent.action.MAIN"));
        assertFalse(MediaAccessPolicy.browsesLibrary("android.intent.action.GET_CONTENT"));
        assertFalse(MediaAccessPolicy.browsesLibrary("android.intent.action.PICK"));
        assertFalse(MediaAccessPolicy.browsesLibrary("android.intent.action.VIEW"));
        assertFalse(MediaAccessPolicy.browsesLibrary("com.android.camera.action.REVIEW"));
        // GalleryActivity matches actions ignoring case; so does the policy.
        assertFalse(MediaAccessPolicy.browsesLibrary("android.intent.action.view"));
    }

    @Test
    public void readPermissionsFollowTheApiLevel() {
        assertArrayEquals(new String[] {
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO"},
                MediaAccessPolicy.readPermissions(33));
        assertArrayEquals(new String[] {"android.permission.READ_EXTERNAL_STORAGE"},
                MediaAccessPolicy.readPermissions(32));
        assertArrayEquals(new String[] {"android.permission.READ_EXTERNAL_STORAGE"},
                MediaAccessPolicy.readPermissions(29));
    }
}
