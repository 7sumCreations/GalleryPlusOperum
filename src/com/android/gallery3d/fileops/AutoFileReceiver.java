package com.android.gallery3d.fileops;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import java.util.List;
import java.util.TimeZone;

/**
 * Wakes up periodically, asks AutoFileScheduler what is due, and hands the work
 * to FileOpService.
 *
 * Going through FileOpService is what keeps auto-file from overlapping a manual
 * move: the service runs every batch on one executor, so an auto-file batch
 * simply queues behind whatever the user started.
 */
public class AutoFileReceiver extends BroadcastReceiver {

    private static final String TAG = "AutoFileReceiver";

    public static final String ACTION_AUTO_FILE = "com.android.gallery3d.fileops.AUTO_FILE";

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

    public static void schedule(Context context) {
        AutoFileSettings settings = AutoFileSettings.from(context);
        if (!settings.isEnabled()) {
            cancel(context);
            return;
        }
        long interval = checkIntervalMillis(settings);
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarms.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                android.os.SystemClock.elapsedRealtime() + interval, interval,
                pendingFor(context));
    }

    /** Turning the rule off must stop every automatic move, immediately. */
    public static void cancel(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarms.cancel(pendingFor(context));
    }

    /** Run the rule once, synchronously. Safe to call off the main thread only. */
    public static void applyRule(Context context, long nowMillis) {
        AutoFileSettings settings = AutoFileSettings.from(context);
        if (!settings.isEnabled()) return;

        ContentResolverGateway gateway = new ContentResolverGateway(context);
        AutoFileScheduler scheduler =
                new AutoFileScheduler(gateway, settings, TimeZone.getDefault());
        List<AutoFileScheduler.Plan> plans = scheduler.planFor(nowMillis);
        if (plans.isEmpty()) return;

        FileOpEngine engine = new FileOpEngine(gateway);
        AutoFileLog log = AutoFileLog.load(context);
        int moved = 0;
        for (AutoFileScheduler.Plan plan : plans) {
            // Make sure the destination folder exists before moving into it.
            FolderCreator.create(gateway,
                    RelativePaths.parentOf(plan.destRelativePath),
                    RelativePaths.lastSegment(plan.destRelativePath));
            FileOpResult result = engine.move(plan.item, plan.destRelativePath);
            if (result.status == FileOpResult.Status.CONSENT_REQUIRED) {
                // An automatic rule must never pop a dialog. Skip and retry later.
                continue;
            }
            log.record(result, plan.destRelativePath, nowMillis);
            if (result.isOk()) moved++;
        }
        log.save(context);
        Log.i(TAG, "Auto-filed " + moved + " of " + plans.size() + " candidates");
    }

    /**
     * Put one auto-filed photo back where it came from, using the log entry the
     * automatic move wrote.
     *
     * @return true when the photo was moved back.
     */
    public static boolean undoOne(Context context, String itemUri) {
        AutoFileLog log = AutoFileLog.load(context);
        AutoFileLog.Entry entry = log.find(itemUri);
        if (entry == null) return false;

        ContentResolverGateway gateway = new ContentResolverGateway(context);
        try {
            gateway.updateLocation(Uri.parse(entry.itemUri), entry.fromRelativePath,
                    entry.displayName);
        } catch (PendingConsentException consent) {
            return false;
        } catch (java.io.IOException failure) {
            Log.w(TAG, "Could not undo auto-file of " + itemUri, failure);
            return false;
        }
        log.remove(itemUri);
        log.save(context);
        return true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_AUTO_FILE.equals(intent.getAction())) return;
        final Context appContext = context.getApplicationContext();
        final PendingResult pendingResult = goAsync();
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    applyRule(appContext, System.currentTimeMillis());
                } catch (Throwable failure) {
                    Log.e(TAG, "Auto-file run threw", failure);
                } finally {
                    pendingResult.finish();
                }
            }
        });
    }
}
