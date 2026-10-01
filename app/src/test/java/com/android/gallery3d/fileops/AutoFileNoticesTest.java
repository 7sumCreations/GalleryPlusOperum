package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The rules behind what Auto-file tells the user (F-025). Pure logic. */
public class AutoFileNoticesTest {

    private static final long T = 1_790_157_600_000L;
    private static final long HOUR = 60L * 60L * 1000L;
    private static final long DAY = 24L * HOUR;

    private static AutoFileLog.Entry entry(String uri, String to, long when) {
        return new AutoFileLog.Entry(uri, "DCIM/Camera/", to, uri + ".jpg", when);
    }

    // ---- telling the user about photos that need permission -------------------

    @Test
    public void theFirstSkipIsAlwaysReported() {
        assertTrue(AutoFileNotices.shouldNotifyAboutSkips(3, 0, AutoFileNotices.NONE, T));
    }

    @Test
    public void nothingSkippedMeansNothingToSay() {
        assertFalse(AutoFileNotices.shouldNotifyAboutSkips(0, 0, AutoFileNotices.NONE, T));
        assertFalse(AutoFileNotices.shouldNotifyAboutSkips(0, 5, T - HOUR, T));
    }

    @Test
    public void theSameSkipsAreNotReportedEveryRun() {
        assertFalse(AutoFileNotices.shouldNotifyAboutSkips(3, 3, T - HOUR, T));
        assertFalse(AutoFileNotices.shouldNotifyAboutSkips(3, 3, T - 6 * DAY, T));
    }

    @Test
    public void fewerSkipsThanLastTimeAreNotReportedAgain() {
        assertFalse(AutoFileNotices.shouldNotifyAboutSkips(2, 3, T - HOUR, T));
    }

    @Test
    public void moreSkipsThanLastTimeAreReported() {
        assertTrue(AutoFileNotices.shouldNotifyAboutSkips(4, 3, T - HOUR, T));
    }

    @Test
    public void theSameSkipsAreReportedAgainAfterAWeek() {
        assertTrue(AutoFileNotices.shouldNotifyAboutSkips(3, 3,
                T - AutoFileNotices.SKIP_RENOTIFY_MILLIS, T));
    }

    @Test
    public void aClockThatWentBackwardsCountsAsStale() {
        assertTrue(AutoFileNotices.shouldNotifyAboutSkips(3, 3, T + HOUR, T));
    }

    // ---- the Undo window ---------------------------------------------------------

    @Test
    public void aRunWithNoOpenWindowStartsOne() {
        assertEquals(T, AutoFileNotices.undoWindowStartFor(AutoFileNotices.NONE, T));
    }

    @Test
    public void laterRunsJoinTheOpenWindowSoUndoCoversEverythingItCounted() {
        assertEquals(T - HOUR, AutoFileNotices.undoWindowStartFor(T - HOUR, T));
    }

    @Test
    public void aWindowPastItsLifetimeIsReplaced() {
        long old = T - AutoFileNotices.UNDO_LIFETIME_MILLIS - 1;
        assertFalse(AutoFileNotices.isUndoWindowOpen(old, T));
        assertEquals(T, AutoFileNotices.undoWindowStartFor(old, T));
    }

    @Test
    public void theNotificationLivesOnlyAsLongAsItsWindow() {
        assertEquals(AutoFileNotices.UNDO_LIFETIME_MILLIS,
                AutoFileNotices.remainingUndoMillis(T, T));
        assertEquals(AutoFileNotices.UNDO_LIFETIME_MILLIS - HOUR,
                AutoFileNotices.remainingUndoMillis(T, T + HOUR));
        assertEquals(0L, AutoFileNotices.remainingUndoMillis(T, T + 2 * DAY));
        assertEquals(0L, AutoFileNotices.remainingUndoMillis(AutoFileNotices.NONE, T));
    }

    @Test
    public void undoPicksExactlyTheMovesInsideTheWindow() {
        List<AutoFileLog.Entry> log = Arrays.asList(
                entry("before", "Pictures/2026/09/", T - 1),
                entry("first", "Pictures/2026/09/", T),
                entry("middle", "Pictures/2026/09/", T + HOUR),
                entry("last", "Pictures/2026/10/", T + 2 * HOUR),
                entry("after", "Pictures/2026/10/", T + 2 * HOUR + 1));

        List<AutoFileLog.Entry> picked =
                AutoFileNotices.entriesInWindow(log, T, T + 2 * HOUR);

        assertEquals(3, picked.size());
        assertEquals("first", picked.get(0).itemUri);
        assertEquals("middle", picked.get(1).itemUri);
        assertEquals("last", picked.get(2).itemUri);
    }

    @Test
    public void noWindowPicksNothing() {
        List<AutoFileLog.Entry> log = Arrays.asList(entry("a", "Pictures/2026/09/", 5L));
        assertTrue(AutoFileNotices.entriesInWindow(log, AutoFileNotices.NONE, 10L).isEmpty());
        assertTrue(AutoFileNotices.entriesInWindow(log, 10L, 5L).isEmpty());
    }

    // ---- what the notification says ---------------------------------------------

    @Test
    public void oneFolderIsNamedWithoutItsTrailingSlash() {
        AutoFileNotices.FiledSummary summary = AutoFileNotices.summarise(Arrays.asList(
                entry("a", "Pictures/2026/10/", T),
                entry("b", "Pictures/2026/10/", T)));

        assertEquals(2, summary.count);
        assertEquals("Pictures/2026/10", summary.singleFolder());
    }

    @Test
    public void severalFoldersAreCountedNotListed() {
        AutoFileNotices.FiledSummary summary = AutoFileNotices.summarise(Arrays.asList(
                entry("a", "Pictures/2026/10/", T),
                entry("b", "Pictures/2026/09/", T),
                entry("c", "Pictures/2026/10/", T)));

        assertEquals(3, summary.count);
        assertNull(summary.singleFolder());
        assertEquals(Arrays.asList("Pictures/2026/09", "Pictures/2026/10"), summary.folders);
    }

    @Test
    public void anEmptyRunSummarisesToNothing() {
        AutoFileNotices.FiledSummary summary =
                AutoFileNotices.summarise(new ArrayList<AutoFileLog.Entry>());
        assertEquals(0, summary.count);
        assertNull(summary.singleFolder());
    }

    @Test
    public void undoOutcomeCountsAddUp() {
        AutoFileNotices.UndoOutcome outcome = new AutoFileNotices.UndoOutcome();
        outcome.restored = 3;
        outcome.gone = 1;
        outcome.failed = 2;
        assertEquals(6, outcome.total());
        assertEquals(3, outcome.notRestored());
    }
}
