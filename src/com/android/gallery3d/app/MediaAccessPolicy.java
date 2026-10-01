/*
 * Copyright (C) 2026 The Gallery2 fork authors
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

/**
 * Pure decision logic for the first-run photo access request. No Android
 * types, so the JVM tests can cover every state.
 *
 * Any granted read permission counts as access: on Android 14+ a target-33
 * app that gets "Select photos" receives a temporary READ_MEDIA_* grant, and
 * GrapheneOS Storage Scopes reports the permission as granted. In both cases
 * the gallery shows what it can see rather than asking again.
 */
public final class MediaAccessPolicy {

    /** What the browse screen should do about photo access. */
    public enum Action {
        /** Access is there: show the library. */
        SHOW,
        /** Show the system permission dialog. */
        REQUEST,
        /** Explain, and offer to ask again (the system will still show its dialog). */
        EXPLAIN_CAN_RETRY,
        /** Explain, and offer only the app's system settings page. */
        EXPLAIN_OPEN_SETTINGS,
    }

    private MediaAccessPolicy() {}

    /**
     * @param readGranted any photo/video read permission is granted now
     * @param askedBefore this app has shown the system dialog at least once
     * @param showRationale {@code shouldShowRequestPermissionRationale} for
     *        the read permission. False after a denial means "don't ask again".
     */
    public static Action decide(boolean readGranted, boolean askedBefore,
            boolean showRationale) {
        if (readGranted) return Action.SHOW;
        if (!askedBefore) return Action.REQUEST;
        return showRationale ? Action.EXPLAIN_CAN_RETRY : Action.EXPLAIN_OPEN_SETTINGS;
    }

    /**
     * Whether to include the notification permission in the next request.
     * Asked once, never again, and only where the platform has it (API 33+).
     */
    public static boolean shouldAskNotifications(int sdkInt, boolean notificationsGranted,
            boolean notificationsAskedBefore) {
        return sdkInt >= 33 && !notificationsGranted && !notificationsAskedBefore;
    }

    /**
     * Whether a GalleryActivity launch with this action browses the library
     * (and so needs media access). Pick, get-content and view intents come
     * from other apps: a view carries its own uri grant, and a picker must not
     * be interrupted by a permission dialog it did not ask for.
     */
    public static boolean browsesLibrary(String action) {
        if (action == null) return true;
        String a = action.toLowerCase(java.util.Locale.ROOT);
        return !a.equals("android.intent.action.get_content")
                && !a.equals("android.intent.action.pick")
                && !a.equals("android.intent.action.view")
                && !a.equals("com.android.camera.action.review");
    }

    /** The read permissions to check and request on this API level. */
    public static String[] readPermissions(int sdkInt) {
        if (sdkInt >= 33) {
            return new String[] {
                    "android.permission.READ_MEDIA_IMAGES",
                    "android.permission.READ_MEDIA_VIDEO",
            };
        }
        return new String[] {"android.permission.READ_EXTERNAL_STORAGE"};
    }
}
