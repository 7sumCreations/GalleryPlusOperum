package com.android.gallery3d.fileops;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Wakes up periodically, asks AutoFileScheduler what is due, and hands the work
 * to FileOpService.
 *
 * Going through FileOpService is what keeps auto-file from overlapping a manual
 * move: the service runs every batch on one executor, so an auto-file batch
 * simply queues behind whatever the user started.
 *
 * Besides the alarm, a check also runs when the gallery opens
 * ({@link #runOnOpen}) and from "File now" in Settings ({@link #runNow}). All
 * of them go through the same serial executor and the same rule.
 */
public class AutoFileReceiver extends BroadcastReceiver {

    private static final String TAG = "AutoFileReceiver";

    public static final String ACTION_AUTO_FILE = "com.android.gallery3d.fileops.AUTO_FILE";
    /** The Undo button on "Filed N new photos". Explicit intent only. */
    static final String ACTION_UNDO_FILED = "com.android.gallery3d.fileops.AUTO_FILE_UNDO";
    /** "Filed N new photos" was swiped away. Explicit intent only. */
    static final String ACTION_FILED_DISMISSED =
            "com.android.gallery3d.fileops.AUTO_FILE_DISMISSED";
    static final String EXTRA_WINDOW_START = "com.android.gallery3d.fileops.WINDOW_START";
    static final String EXTRA_WINDOW_END = "com.android.gallery3d.fileops.WINDOW_END";

    /**
     * Runs, Undos and dismissals one at a time: each loads, edits and saves
     * the same AutoFileLog, and two at once would lose one side's changes.
     */
    private static final Executor SERIAL = Executors.newSingleThreadExecutor();

    /** Opening the gallery checks at most every 30 seconds. */
    private static final AutoFileRunNow.Throttle OPEN_THROTTLE =
            new AutoFileRunNow.Throttle(AutoFileRunNow.OPEN_THROTTLE_MILLIS);

    /** Told, on the main thread, how a {@link #runNow} check went. */
    public interface RunListener {
        /**
         * @param enabled the rule was on when the check ran
         * @param outcome what it did, or null when the check failed
         */
        void onRunFinished(boolean enabled, RunOutcome outcome);
    }

    /** What one run did, for the notifications. */
    public static final class RunOutcome {
        public static final RunOutcome NOTHING = new RunOutcome(0, 0);

        public final int moved;
        /** Skipped because Android wants the user's permission (CONSENT_REQUIRED). */
        public final int needPermission;

        public RunOutcome(int moved, int needPermission) {
            this.moved = moved;
            this.needPermission = needPermission;
        }
    }

    static Intent undoIntent(Context context, long windowStart, long windowEnd) {
        Intent intent = new Intent(context, AutoFileReceiver.class);
        intent.setAction(ACTION_UNDO_FILED);
        intent.putExtra(EXTRA_WINDOW_START, windowStart);
        intent.putExtra(EXTRA_WINDOW_END, windowEnd);
        return intent;
    }

    static Intent dismissedIntent(Context context, long windowStart) {
        Intent intent = new Intent(context, AutoFileReceiver.class);
        intent.setAction(ACTION_FILED_DISMISSED);
        intent.putExtra(EXTRA_WINDOW_START, windowStart);
        return intent;
    }

    private static final long MIN_INTERVAL_MILLIS = 60L * 1000L;

    /** Check twice per delay period, so nothing waits much longer than the delay. */
    public static long checkIntervalMillis(AutoFileSettings settings) {
        long half = settings.delayMillis() / 2L;
        return half < MIN_INTERVAL_MILLIS ? MIN_INTERVAL_MILLIS : half;
    }

    private static PendingIntent pendingFor(Context context) {
        Intent intent = new Intent(context, AutoFileReceiver.class);
        intent.setAction(ACTION_AUTO_FILE);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Arms (or, when the rule is off, tears down) the repeating check. Runs from
     * Application.onCreate on every process start, so it must never throw: a
     * failure here is logged and auto-file simply does not run until next time.
     * Uses an inexact alarm, which needs no SCHEDULE_EXACT_ALARM grant.
     */
    public static void schedule(Context context) {
        try {
            AutoFileSettings settings = AutoFileSettings.from(context);
            if (!settings.isEnabled()) {
                cancel(context);
                return;
            }
            // Migration: a rule switched on by a build that predates the
            // "enabled since" stamp starts counting from now.
            settings.ensureEnabledSinceSeconds(System.currentTimeMillis());
            long interval = checkIntervalMillis(settings);
            AlarmManager alarms =
                    (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms == null) return;
            alarms.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                    android.os.SystemClock.elapsedRealtime() + interval, interval,
                    pendingFor(context));
        } catch (RuntimeException failure) {
            Log.e(TAG, "Could not schedule auto-file", failure);
        }
    }

    /** Turning the rule off must stop every automatic move, immediately. */
    public static void cancel(Context context) {
        try {
            AlarmManager alarms =
                    (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms != null) alarms.cancel(pendingFor(context));
        } catch (RuntimeException failure) {
            Log.e(TAG, "Could not cancel auto-file", failure);
        }
    }

    /**
     * The gallery's main screen came to the foreground: run one check in the
     * background if Auto-file is on and no check ran in the last 30 seconds.
     * Same path as the alarm (serialised, same notifications, same delay
     * rule). Never blocks and never throws.
     */
    public static void runOnOpen(Context context) {
        try {
            if (!OPEN_THROTTLE.tryClaim(SystemClock.elapsedRealtime())) return;
            runNow(context, null);
        } catch (RuntimeException failure) {
            Log.w(TAG, "Could not start the auto-file check on open", failure);
        }
    }

    /**
     * "File now": run one check straight away, queued behind any run, Undo
     * or dismissal already in progress. Posts the same notifications as the
     * alarm run. The listener (optional) hears the result on the main thread.
     * Never throws.
     */
    public static void runNow(Context context, final RunListener listener) {
        final Context appContext = context.getApplicationContext();
        try {
            SERIAL.execute(new Runnable() {
                @Override
                public void run() {
                    boolean enabled = false;
                    RunOutcome outcome = null;
                    try {
                        OPEN_THROTTLE.markRan(SystemClock.elapsedRealtime());
                        enabled = AutoFileSettings.from(appContext).isEnabled();
                        long now = System.currentTimeMillis();
                        outcome = applyRule(appContext, now);
                        AutoFileNotifier.afterRun(appContext, outcome, now);
                    } catch (Throwable failure) {
                        Log.e(TAG, "Auto-file check threw", failure);
                        outcome = null;
                    }
                    report(listener, enabled, outcome);
                }
            });
        } catch (RuntimeException failure) {
            Log.w(TAG, "Could not queue the auto-file check", failure);
            report(listener, false, null);
        }
    }

    private static void report(final RunListener listener, final boolean enabled,
            final RunOutcome outcome) {
        if (listener == null) return;
        try {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        listener.onRunFinished(enabled, outcome);
                    } catch (RuntimeException failure) {
                        Log.w(TAG, "Auto-file result listener threw", failure);
                    }
                }
            });
        } catch (RuntimeException failure) {
            Log.w(TAG, "Could not report the auto-file result", failure);
        }
    }

    /** Run the rule once, synchronously. Safe to call off the main thread only. */
    public static RunOutcome applyRule(Context context, long nowMillis) {
        AutoFileSettings settings = AutoFileSettings.from(context);
        if (!settings.isEnabled()) return RunOutcome.NOTHING;

        ContentResolverGateway gateway = new ContentResolverGateway(context);
        AutoFileLog log = AutoFileLog.load(context);
        AutoFileScheduler scheduler = new AutoFileScheduler(gateway, settings,
                TimeZone.getDefault(), log.undoneUris());
        List<AutoFileScheduler.Plan> plans = scheduler.planFor(nowMillis);
        if (plans.isEmpty()) return RunOutcome.NOTHING;

        FileOpEngine engine = new FileOpEngine(gateway);
        int moved = 0;
        int needPermission = 0;
        for (AutoFileScheduler.Plan plan : plans) {
            // Make sure the destination folder exists before moving into it.
            FolderCreator.create(gateway,
                    RelativePaths.parentOf(plan.destRelativePath),
                    RelativePaths.lastSegment(plan.destRelativePath));
            FileOpResult result = engine.move(plan.item, plan.destRelativePath);
            if (result.status == FileOpResult.Status.CONSENT_REQUIRED) {
                // An automatic rule must never pop a dialog. Skip, count it so
                // the user is told why, and retry next run.
                needPermission++;
                continue;
            }
            log.record(result, plan.destRelativePath, nowMillis);
            if (result.isOk()) moved++;
        }
        log.save(context);
        Log.i(TAG, "Auto-filed " + moved + " of " + plans.size() + " candidates, "
                + needPermission + " need permission");
        return new RunOutcome(moved, needPermission);
    }

    /**
     * Put one auto-filed photo back where it came from, using the log entry the
     * automatic move wrote. It is then never auto-filed again.
     *
     * @return true when the photo was moved back.
     */
    public static boolean undoOne(Context context, String itemUri) {
        AutoFileLog log = AutoFileLog.load(context);
        AutoFileLog.Entry entry = log.find(itemUri);
        if (entry == null) return false;
        AutoFileUndo.Result result = AutoFileUndo.undoEntry(
                new ContentResolverGateway(context), log, entry, System.currentTimeMillis());
        log.save(context);
        return result == AutoFileUndo.Result.RESTORED;
    }

    /**
     * The Undo button: put back every photo the notification counted. Refuses
     * a window that is past its lifetime (a stale PendingIntent). Never throws.
     */
    static AutoFileNotices.UndoOutcome undoFiled(Context context, long windowStart,
            long windowEnd, long nowMillis) {
        if (!AutoFileNotices.isUndoWindowOpen(windowStart, nowMillis)) {
            return new AutoFileNotices.UndoOutcome();
        }
        AutoFileLog log = AutoFileLog.load(context);
        AutoFileNotices.UndoOutcome outcome = AutoFileUndo.undoWindow(
                new ContentResolverGateway(context), log, windowStart, windowEnd, nowMillis);
        log.save(context);
        Log.i(TAG, "Undo put back " + outcome.restored + ", " + outcome.gone
                + " gone, " + outcome.failed + " failed");
        return outcome;
    }

    @Override
    public void onReceive(Context context, final Intent intent) {
        final String action = intent == null ? null : intent.getAction();
        if (!ACTION_AUTO_FILE.equals(action) && !ACTION_UNDO_FILED.equals(action)
                && !ACTION_FILED_DISMISSED.equals(action)) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        final PendingResult pendingResult = goAsync();
        SERIAL.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    long now = System.currentTimeMillis();
                    if (ACTION_AUTO_FILE.equals(action)) {
                        OPEN_THROTTLE.markRan(SystemClock.elapsedRealtime());
                        RunOutcome outcome = applyRule(appContext, now);
                        AutoFileNotifier.afterRun(appContext, outcome, now);
                    } else if (ACTION_UNDO_FILED.equals(action)) {
                        long start = intent.getLongExtra(EXTRA_WINDOW_START,
                                AutoFileNotices.NONE);
                        long end = intent.getLongExtra(EXTRA_WINDOW_END, AutoFileNotices.NONE);
                        AutoFileNotifier.afterUndo(appContext,
                                undoFiled(appContext, start, end, now));
                    } else {
                        AutoFileNotifier.afterDismissed(appContext,
                                intent.getLongExtra(EXTRA_WINDOW_START, AutoFileNotices.NONE));
                    }
                } catch (Throwable failure) {
                    Log.e(TAG, "Auto-file " + action + " threw", failure);
                } finally {
                    pendingResult.finish();
                }
            }
        });
    }
}
