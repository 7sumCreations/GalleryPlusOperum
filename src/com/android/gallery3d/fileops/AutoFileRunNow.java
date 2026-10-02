/*
 * Copyright (C) 2026 The Gallery2 fork authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.fileops;

/**
 * Pure logic for the Auto-file runs the user causes, as opposed to the
 * background alarm: one check when the gallery opens (throttled) and the
 * "File now" button in Settings (not throttled). No Android types, so the JVM
 * tests cover it.
 */
public final class AutoFileRunNow {

    /** Opening the gallery runs a check at most this often. */
    public static final long OPEN_THROTTLE_MILLIS = 30L * 1000L;

    /** What the "File now" Toast says. */
    public enum Kind {
        /** The rule was switched off before the check ran. */
        OFF,
        /** Photos were filed and none are waiting for permission. */
        FILED,
        /** Some were filed, others are waiting for permission. */
        FILED_AND_NEED_PERMISSION,
        /** Nothing filed; some photos are waiting for permission. */
        NEED_PERMISSION,
        /** Nothing is old enough (or new enough) to file yet. */
        NOTHING_YET,
        /** The check itself failed. */
        FAILED,
    }

    private AutoFileRunNow() {}

    /**
     * @param enabled the rule was on when the check ran
     * @param moved photos filed, or -1 when the check failed
     * @param needPermission photos skipped because Android wants consent
     */
    public static Kind kindOf(boolean enabled, int moved, int needPermission) {
        if (moved < 0) return Kind.FAILED;
        if (!enabled) return Kind.OFF;
        if (moved > 0) return needPermission > 0 ? Kind.FILED_AND_NEED_PERMISSION : Kind.FILED;
        return needPermission > 0 ? Kind.NEED_PERMISSION : Kind.NOTHING_YET;
    }

    /**
     * Remembers when Auto-file last ran (any trigger) on a monotonic clock, so
     * resuming the gallery over and over doesn't queue a check each time.
     * Thread-safe; one per process is enough.
     */
    public static final class Throttle {
        private static final long NEVER = Long.MIN_VALUE;

        private final long mMinGapMillis;
        private long mLastRun = NEVER;

        public Throttle(long minGapMillis) {
            mMinGapMillis = minGapMillis;
        }

        /** A run happened (alarm or File now): the next open waits the gap out. */
        public synchronized void markRan(long nowElapsedMillis) {
            mLastRun = nowElapsedMillis;
        }

        /**
         * True, and the slot is taken, when enough time has passed since the
         * last run. A clock that went backwards (a new boot with a stale
         * value) counts as enough.
         */
        public synchronized boolean tryClaim(long nowElapsedMillis) {
            if (mLastRun != NEVER && nowElapsedMillis >= mLastRun
                    && nowElapsedMillis - mLastRun < mMinGapMillis) {
                return false;
            }
            mLastRun = nowElapsedMillis;
            return true;
        }
    }
}
