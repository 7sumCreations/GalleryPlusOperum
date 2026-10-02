package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.gallery3d.fileops.AutoFileRunNow.Kind;
import com.android.gallery3d.fileops.AutoFileRunNow.Throttle;

import org.junit.Test;

public class AutoFileRunNowTest {

    private static final long GAP = AutoFileRunNow.OPEN_THROTTLE_MILLIS;

    @Test
    public void throttleIsThirtySeconds() {
        assertEquals(30_000L, GAP);
    }

    @Test
    public void firstOpenRuns() {
        assertTrue(new Throttle(GAP).tryClaim(1_000L));
    }

    @Test
    public void reopeningWithinTheGapDoesNotRunAgain() {
        Throttle t = new Throttle(GAP);
        assertTrue(t.tryClaim(1_000L));
        assertFalse(t.tryClaim(1_000L));
        assertFalse(t.tryClaim(1_000L + GAP - 1));
        assertTrue(t.tryClaim(1_000L + GAP));
    }

    @Test
    public void aRefusedClaimDoesNotPushTheWindowOut() {
        Throttle t = new Throttle(GAP);
        assertTrue(t.tryClaim(0L));
        assertFalse(t.tryClaim(GAP - 1));
        assertTrue(t.tryClaim(GAP));
    }

    @Test
    public void anAlarmOrFileNowRunCountsAsTheLastRun() {
        Throttle t = new Throttle(GAP);
        t.markRan(10_000L);
        assertFalse(t.tryClaim(10_000L + GAP - 1));
        assertTrue(t.tryClaim(10_000L + GAP));
    }

    @Test
    public void aClockThatWentBackwardsRuns() {
        Throttle t = new Throttle(GAP);
        t.markRan(500_000L);
        assertTrue(t.tryClaim(1_000L));
    }

    @Test
    public void startOfClockIsNotMistakenForARecentRun() {
        // elapsedRealtime can be small right after boot.
        assertTrue(new Throttle(GAP).tryClaim(0L));
    }

    @Test
    public void filedOnly() {
        assertEquals(Kind.FILED, AutoFileRunNow.kindOf(true, 3, 0));
    }

    @Test
    public void filedAndSomeNeedPermission() {
        assertEquals(Kind.FILED_AND_NEED_PERMISSION, AutoFileRunNow.kindOf(true, 2, 1));
    }

    @Test
    public void onlyNeedPermission() {
        assertEquals(Kind.NEED_PERMISSION, AutoFileRunNow.kindOf(true, 0, 4));
    }

    @Test
    public void nothingYet() {
        assertEquals(Kind.NOTHING_YET, AutoFileRunNow.kindOf(true, 0, 0));
    }

    @Test
    public void switchedOffBeforeTheCheckRan() {
        assertEquals(Kind.OFF, AutoFileRunNow.kindOf(false, 0, 0));
    }

    @Test
    public void aFailedCheckSaysSoEvenIfOff() {
        assertEquals(Kind.FAILED, AutoFileRunNow.kindOf(true, -1, 0));
        assertEquals(Kind.FAILED, AutoFileRunNow.kindOf(false, -1, 0));
    }
}
