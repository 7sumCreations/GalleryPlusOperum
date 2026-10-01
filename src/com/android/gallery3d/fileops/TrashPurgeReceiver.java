package com.android.gallery3d.fileops;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Runs once a day and destroys every trashed item whose 30-day retention window
 * has closed. Uses an inexact alarm on purpose: nothing here is time-critical,
 * and an inexact alarm does not need SCHEDULE_EXACT_ALARM.
 */
public class TrashPurgeReceiver extends BroadcastReceiver {

    private static final String TAG = "TrashPurgeReceiver";

    public static final String ACTION_PURGE = "com.android.gallery3d.fileops.PURGE_TRASH";
    public static final long PURGE_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L;

    public static long nextRunAt(long nowMillis) {
        return nowMillis + PURGE_INTERVAL_MILLIS;
    }

    /** Runs from Application.onCreate on every process start: must never throw. */
    public static void schedule(Context context) {
        try {
            AlarmManager alarms =
                    (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms == null) return;
            Intent intent = new Intent(context, TrashPurgeReceiver.class);
            intent.setAction(ACTION_PURGE);
            PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarms.setInexactRepeating(AlarmManager.RTC,
                    nextRunAt(System.currentTimeMillis()), PURGE_INTERVAL_MILLIS, pending);
        } catch (RuntimeException failure) {
            Log.e(TAG, "Could not schedule trash purge", failure);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_PURGE.equals(intent.getAction())) return;
        final Context appContext = context.getApplicationContext();
        final PendingResult pendingResult = goAsync();
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    int purged = new FileOpEngine(new ContentResolverGateway(appContext))
                            .purgeExpiredTrash(System.currentTimeMillis());
                    Log.i(TAG, "Purged " + purged + " expired trash items");
                } catch (Throwable failure) {
                    // An uncaught throw on this pool thread would kill the process.
                    Log.e(TAG, "Trash purge threw", failure);
                } finally {
                    pendingResult.finish();
                }
            }
        });
    }
}
