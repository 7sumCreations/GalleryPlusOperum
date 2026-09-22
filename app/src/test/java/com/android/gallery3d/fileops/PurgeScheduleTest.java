package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PurgeScheduleTest {

    private static final long DAY = 24L * 60L * 60L * 1000L;

    @Test
    public void thePurgeRunsOnceADay() {
        assertEquals(DAY, TrashPurgeReceiver.PURGE_INTERVAL_MILLIS);
    }

    @Test
    public void theNextRunIsOneIntervalAway() {
        assertEquals(1000L + DAY, TrashPurgeReceiver.nextRunAt(1000L));
    }

    @Test
    public void theRetentionWindowIsThirtyDays() {
        assertEquals(30L * DAY, FileOpEngine.TRASH_RETENTION_MILLIS);
        assertTrue("a daily purge must be finer-grained than the retention window",
                TrashPurgeReceiver.PURGE_INTERVAL_MILLIS < FileOpEngine.TRASH_RETENTION_MILLIS);
    }
}
