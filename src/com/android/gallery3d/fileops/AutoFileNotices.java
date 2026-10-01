package com.android.gallery3d.fileops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * The rules behind what Auto-file tells the user. Pure logic, no Android
 * context, so every decision here is JVM-testable; AutoFileNotifier only turns
 * the answers into notifications.
 */
public final class AutoFileNotices {

    /**
     * How long the "Filed N new photos" notification, and its Undo, stays
     * offered. Later runs inside the window add to the same notification, so
     * Undo always covers everything it says it filed. Dismissing it, tapping
     * Undo, or this lifetime passing closes the window.
     */
    public static final long UNDO_LIFETIME_MILLIS = 24L * 60L * 60L * 1000L;

    /**
     * When the permission-needed notice is repeated even though nothing new
     * was skipped. Once a week is a reminder, not nagging.
     */
    public static final long SKIP_RENOTIFY_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    /** "No window open" / "never told": stored as 0. */
    public static final long NONE = 0L;

    private AutoFileNotices() {
    }

    /**
     * Whether to post (or re-post) "Auto-file couldn't move N photos because
     * Android needs your permission".
     *
     * Tell once; tell again only when more photos are waiting than last time,
     * or when the last notice is older than SKIP_RENOTIFY_MILLIS. A clock that
     * went backwards makes the old stamp meaningless, so that counts as stale.
     *
     * @param skippedNow how many photos this run skipped for want of permission
     * @param lastToldCount the count in the last notice actually shown, 0 if none
     * @param lastToldAtMillis when it was shown, NONE if never
     */
    public static boolean shouldNotifyAboutSkips(int skippedNow, int lastToldCount,
            long lastToldAtMillis, long nowMillis) {
        if (skippedNow <= 0) return false;
        if (lastToldAtMillis == NONE || lastToldCount <= 0) return true;
        if (skippedNow > lastToldCount) return true;
        long elapsed = nowMillis - lastToldAtMillis;
        return elapsed < 0 || elapsed >= SKIP_RENOTIFY_MILLIS;
    }

    /**
     * The start of the Undo window a run that just moved photos belongs to:
     * the open window if there is one still alive, otherwise a new one
     * starting at this run.
     */
    public static long undoWindowStartFor(long openWindowStart, long runMillis) {
        if (isUndoWindowOpen(openWindowStart, runMillis)) return openWindowStart;
        return runMillis;
    }

    /** True while a window that started at windowStart can still be undone. */
    public static boolean isUndoWindowOpen(long windowStart, long nowMillis) {
        if (windowStart == NONE) return false;
        long elapsed = nowMillis - windowStart;
        return elapsed >= 0 && elapsed <= UNDO_LIFETIME_MILLIS;
    }

    /** How much longer the notification for this window should stay up. */
    public static long remainingUndoMillis(long windowStart, long nowMillis) {
        if (!isUndoWindowOpen(windowStart, nowMillis)) return 0L;
        return UNDO_LIFETIME_MILLIS - (nowMillis - windowStart);
    }

    /**
     * The log entries an Undo for the window [start, end] puts back: the moves
     * the notification counted, in the order they were made. Entries already
     * undone are not in the log any more, so they are never touched twice.
     */
    public static List<AutoFileLog.Entry> entriesInWindow(List<AutoFileLog.Entry> entries,
            long windowStart, long windowEnd) {
        List<AutoFileLog.Entry> picked = new ArrayList<AutoFileLog.Entry>();
        if (windowStart == NONE || windowEnd < windowStart) return picked;
        for (AutoFileLog.Entry entry : entries) {
            if (entry.whenMillis >= windowStart && entry.whenMillis <= windowEnd) {
                picked.add(entry);
            }
        }
        return picked;
    }

    /** What the "Filed N new photos into ..." notification says. */
    public static final class FiledSummary {
        public final int count;
        /** Distinct destinations, without trailing slash, sorted. */
        public final List<String> folders;

        FiledSummary(int count, List<String> folders) {
            this.count = count;
            this.folders = Collections.unmodifiableList(folders);
        }

        /** The one folder everything went to, or null when there were several. */
        public String singleFolder() {
            return folders.size() == 1 ? folders.get(0) : null;
        }
    }

    public static FiledSummary summarise(List<AutoFileLog.Entry> entries) {
        TreeSet<String> folders = new TreeSet<String>();
        for (AutoFileLog.Entry entry : entries) {
            folders.add(withoutTrailingSlash(entry.toRelativePath));
        }
        return new FiledSummary(entries.size(), new ArrayList<String>(folders));
    }

    /** The outcome of one Undo, for the follow-up notification. */
    public static final class UndoOutcome {
        /** Put back where they came from. */
        public int restored;
        /** Deleted, trashed, or moved by the user since: deliberately left alone. */
        public int gone;
        /** Could not be moved back (permission revoked, provider refused). */
        public int failed;

        public int total() {
            return restored + gone + failed;
        }

        public int notRestored() {
            return gone + failed;
        }
    }

    static String withoutTrailingSlash(String relativePath) {
        if (relativePath == null) return "";
        String path = relativePath;
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path;
    }
}
