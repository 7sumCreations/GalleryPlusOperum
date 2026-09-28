package com.android.gallery3d.fileops;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;

import com.android.gallery3d.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs file-op batches in the foreground with a progress notification and a
 * Cancel action.
 *
 * Deliberately separate from BatchService: BatchService's single-thread pool is
 * a synchronisation guarantee MenuExecutor depends on, and a multi-minute move
 * must not occupy it. This service has its own single-thread executor, so file
 * ops still serialise against each other (which F-025 relies on) without
 * blocking rotate, delete or thumbnailing.
 */
public class FileOpService extends Service {

    private static final String TAG = "FileOpService";
    private static final String CHANNEL_ID = "gallery_file_ops";
    private static final int NOTIFICATION_ID = 0x0F0F;
    private static final int UNDO_NOTIFICATION_ID = 0x0F10;

    public static final String ACTION_RUN = "com.android.gallery3d.fileops.RUN";
    public static final String ACTION_CANCEL = "com.android.gallery3d.fileops.CANCEL";
    public static final String ACTION_UNDO = "com.android.gallery3d.fileops.UNDO";
    public static final String ACTION_BATCH_DONE = "com.android.gallery3d.fileops.BATCH_DONE";
    /**
     * ConsentActivity's answer, delivered straight back to the service that is
     * holding the half-finished batch. A start command rather than a broadcast:
     * the service is the one component that owns the pending work, and before
     * F-026 the broadcast went to nobody.
     */
    public static final String ACTION_CONSENT_RESULT =
            "com.android.gallery3d.fileops.SERVICE_CONSENT_RESULT";
    public static final String EXTRA_TOKEN = "com.android.gallery3d.fileops.TOKEN";
    public static final String EXTRA_GRANTED = "com.android.gallery3d.fileops.GRANTED";
    public static final String EXTRA_KIND = "com.android.gallery3d.fileops.KIND";
    public static final String EXTRA_ITEMS = "com.android.gallery3d.fileops.ITEMS";
    public static final String EXTRA_DEST = "com.android.gallery3d.fileops.DEST";
    public static final String EXTRA_OK_COUNT = "com.android.gallery3d.fileops.OK_COUNT";
    public static final String EXTRA_FAIL_COUNT = "com.android.gallery3d.fileops.FAIL_COUNT";

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, Boolean> mCancelled = new ConcurrentHashMap<String, Boolean>();
    /**
     * Batches parked while the user answers their one consent dialog, keyed by
     * batch token. The entry is removed the instant an answer (or a cancel)
     * arrives, so a second answer for the same batch finds nothing to retry.
     */
    private final Map<String, PendingConsent> mAwaitingConsent =
            new ConcurrentHashMap<String, PendingConsent>();
    private NotificationManager mNotifications;
    private FileOpEngine mEngine;

    /**
     * Units of work not yet finished: queued or running batches and undos, plus
     * batches parked awaiting consent. A parked batch stays counted until it is
     * settled, which is what keeps the service alive across the dialog.
     */
    private final Object mWorkLock = new Object();
    private int mOutstanding;
    private int mLatestStartId;

    /** A batch whose first pass is done but which still has items awaiting the user. */
    private static final class PendingConsent {
        final FileOpBatch batch;
        final List<Uri> uris;

        PendingConsent(FileOpBatch batch, List<Uri> uris) {
            this.batch = batch;
            this.uris = uris;
        }
    }

    public static Intent runIntent(Context context, FileOpBatch.Kind kind,
            ArrayList<Uri> items, String destRelativePath, String token) {
        Intent intent = new Intent(context, FileOpService.class);
        intent.setAction(ACTION_RUN);
        intent.putExtra(EXTRA_TOKEN, token);
        intent.putExtra(EXTRA_KIND, kind.name());
        intent.putParcelableArrayListExtra(EXTRA_ITEMS, items);
        intent.putExtra(EXTRA_DEST, destRelativePath);
        return intent;
    }

    public static Intent cancelIntent(Context context, String token) {
        Intent intent = new Intent(context, FileOpService.class);
        intent.setAction(ACTION_CANCEL);
        intent.putExtra(EXTRA_TOKEN, token);
        return intent;
    }

    public static Intent undoIntent(Context context) {
        Intent intent = new Intent(context, FileOpService.class);
        intent.setAction(ACTION_UNDO);
        return intent;
    }

    /** What ConsentActivity sends back once the user has answered (or walked away). */
    public static Intent consentResultIntent(Context context, String token, boolean granted) {
        Intent intent = new Intent(context, FileOpService.class);
        intent.setAction(ACTION_CONSENT_RESULT);
        intent.putExtra(EXTRA_TOKEN, token);
        intent.putExtra(EXTRA_GRANTED, granted);
        return intent;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifications.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, getString(R.string.file_op_channel_name),
                NotificationManager.IMPORTANCE_LOW));
        mEngine = new FileOpEngine(new ContentResolverGateway(this));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        synchronized (mWorkLock) {
            mLatestStartId = startId;
        }
        if (intent == null) {
            stopIfIdle();
            return START_NOT_STICKY;
        }
        final String token = intent.getStringExtra(EXTRA_TOKEN);

        if (ACTION_CANCEL.equals(intent.getAction())) {
            if (token == null) {
                stopIfIdle();
                return START_NOT_STICKY;
            }
            PendingConsent parked = mAwaitingConsent.remove(token);
            if (parked != null) {
                // Cancelled while the dialog was up: nothing is running, so
                // settle what is left and finish the batch here.
                settleAndFinish(parked.batch, null);
                return START_NOT_STICKY;
            }
            mCancelled.put(token, Boolean.TRUE);
            stopIfIdle();
            return START_NOT_STICKY;
        }

        if (ACTION_CONSENT_RESULT.equals(intent.getAction())) {
            onConsentResult(token, intent.getBooleanExtra(EXTRA_GRANTED, false));
            return START_NOT_STICKY;
        }

        if (ACTION_UNDO.equals(intent.getAction())) {
            final FileOpBatch undoable =
                    UndoManager.getInstance().takeUndoable(System.currentTimeMillis());
            if (undoable == null) {
                stopIfIdle();
                return START_NOT_STICKY;
            }
            beginWork();
            startForeground(NOTIFICATION_ID,
                    buildNotification("undo", 0, undoable.results.size()));
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        mEngine.reverseBatch(undoable, new FileOpEngine.ProgressCallback() {
                            @Override
                            public void onItemDone(int indexDone, int total,
                                    FileOpResult result) {
                                mNotifications.notify(NOTIFICATION_ID,
                                        buildNotification("undo", indexDone, total));
                            }

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }
                        });
                    } catch (Throwable failure) {
                        Log.e(TAG, "Undo of " + undoable.token + " threw", failure);
                    } finally {
                        endWork();
                    }
                }
            });
            return START_NOT_STICKY;
        }

        final FileOpBatch.Kind kind;
        try {
            kind = FileOpBatch.Kind.valueOf(intent.getStringExtra(EXTRA_KIND));
        } catch (RuntimeException malformed) {
            // Null or unknown kind: a malformed intent must not kill the process.
            Log.w(TAG, "Ignoring a run request with no valid kind", malformed);
            stopIfIdle();
            return START_NOT_STICKY;
        }
        ArrayList<Uri> items = intent.getParcelableArrayListExtra(EXTRA_ITEMS);
        if (items == null) items = new ArrayList<Uri>();
        final String destination = intent.getStringExtra(EXTRA_DEST);
        final FileOpBatch batch = new FileOpBatch(token, kind, items, destination);

        beginWork();
        startForeground(NOTIFICATION_ID, buildNotification(token, 0, items.size()));

        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                boolean parked = false;
                try {
                    // Items the platform will not let us touch come back as
                    // CONSENT_REQUIRED in batch.results. They are asked about all
                    // at once below, never one dialog per item.
                    mEngine.runBatch(batch, progressFor(batch));
                    boolean cancelled = Boolean.TRUE.equals(mCancelled.get(batch.token));
                    List<Uri> pending = ConsentPolicy.itemsNeedingConsent(batch.results);
                    if (!cancelled && !pending.isEmpty()) {
                        parked = askForConsent(batch, pending);
                    }
                } catch (Throwable failure) {
                    Log.e(TAG, "Batch " + batch.token + " threw", failure);
                } finally {
                    mCancelled.remove(batch.token);
                    if (!parked) settleAndFinish(batch, null);
                }
            }
        });
        return START_NOT_STICKY;
    }

    private FileOpEngine.ProgressCallback progressFor(final FileOpBatch batch) {
        return new FileOpEngine.ProgressCallback() {
            @Override
            public void onItemDone(int indexDone, int total, FileOpResult result) {
                mNotifications.notify(NOTIFICATION_ID,
                        buildNotification(batch.token, indexDone, total));
            }

            @Override
            public boolean isCancelled() {
                return Boolean.TRUE.equals(mCancelled.get(batch.token));
            }
        };
    }

    /**
     * Park the batch and show one system dialog covering every pending item.
     *
     * @return true when the batch is now parked awaiting ACTION_CONSENT_RESULT;
     *         false when no dialog could be shown and the caller must settle it
     */
    private boolean askForConsent(FileOpBatch batch, List<Uri> pending) {
        IntentSender sender = consentSenderFor(batch, pending);
        if (sender == null) return false;

        mAwaitingConsent.put(batch.token, new PendingConsent(batch, pending));
        Intent consent = ConsentActivity.intentFor(this, sender, batch.token);
        try {
            // Tapping the notification re-opens the dialog, for when the launch
            // below is blocked because the user has already left the app.
            PendingIntent reopen = PendingIntent.getActivity(this, batch.token.hashCode(),
                    consent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            mNotifications.notify(NOTIFICATION_ID, buildNotification(batch.token,
                    batch.results.size(), batch.items.size(), reopen));
            startActivity(consent);
            return true;
        } catch (RuntimeException failure) {
            Log.w(TAG, "Could not ask for consent for " + batch.token, failure);
            // Only settle here if nobody else (a cancel) got to it first.
            return mAwaitingConsent.remove(batch.token) == null;
        }
    }

    /**
     * One IntentSender for the whole list. A write grant is enough for every
     * gateway operation on the retry (update, IS_TRASHED, IS_FAVORITE, delete),
     * and unlike createTrashRequest/createDeleteRequest it does not perform the
     * operation itself, so the retry still records the "previous" fields Undo
     * needs and does not trip over an item the dialog already deleted.
     */
    private IntentSender consentSenderFor(FileOpBatch batch, List<Uri> pending) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return MediaStore.createWriteRequest(getContentResolver(), pending)
                        .getIntentSender();
            }
            // Android 10 has no batch request: all it offers is the per-item
            // RecoverableSecurityException. Ask about the first item; the retry
            // settles whatever that grant did not cover as a failure.
            for (FileOpResult result : batch.results) {
                if (result.status == FileOpResult.Status.CONSENT_REQUIRED
                        && result.consent != null) {
                    return result.consent;
                }
            }
            return null;
        } catch (RuntimeException failure) {
            // IllegalArgumentException for a uri MediaStore does not own, or a
            // SecurityException; either way the items simply fail.
            Log.w(TAG, "Could not build a consent request for " + batch.token, failure);
            return null;
        }
    }

    private void onConsentResult(String token, boolean granted) {
        final PendingConsent parked = token == null ? null : mAwaitingConsent.remove(token);
        if (parked == null) {
            // Already answered, cancelled, or from before a process restart.
            stopIfIdle();
            return;
        }
        if (!granted) {
            settleAndFinish(parked.batch, null);
            return;
        }
        final FileOpBatch retry = new FileOpBatch(parked.batch.token, parked.batch.kind,
                parked.uris, parked.batch.destRelativePath);
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    mEngine.runBatch(retry, progressFor(retry));
                } catch (Throwable failure) {
                    Log.e(TAG, "Consent retry of " + retry.token + " threw", failure);
                } finally {
                    mCancelled.remove(retry.token);
                    // The one and only retry: anything still blocked has failed.
                    settleAndFinish(parked.batch, retry.results);
                }
            }
        });
    }

    /**
     * Fold the retry (or nothing) into the batch so the counts and Undo describe
     * the whole operation, report it, and release this batch's unit of work.
     */
    private void settleAndFinish(FileOpBatch batch, List<FileOpResult> retry) {
        try {
            List<FileOpResult> combined = ConsentPolicy.combine(batch.results, retry);
            batch.results.clear();
            batch.results.addAll(combined);
            broadcastDone(batch);
        } catch (Throwable failure) {
            Log.e(TAG, "Finishing " + batch.token + " threw", failure);
        } finally {
            endWork();
        }
    }

    private void beginWork() {
        synchronized (mWorkLock) {
            mOutstanding++;
        }
    }

    private void endWork() {
        synchronized (mWorkLock) {
            if (mOutstanding > 0) mOutstanding--;
            stopIfIdleLocked();
        }
    }

    private void stopIfIdle() {
        synchronized (mWorkLock) {
            stopIfIdleLocked();
        }
    }

    /**
     * stopSelf(startId) rather than stopSelf(): if a new command has been
     * delivered since, the platform keeps the service alive for it.
     */
    private void stopIfIdleLocked() {
        if (mOutstanding > 0) return;
        stopForeground(true);
        stopSelf(mLatestStartId);
    }

    private void broadcastDone(FileOpBatch batch) {
        Intent done = new Intent(ACTION_BATCH_DONE);
        done.setPackage(getPackageName());
        done.putExtra(EXTRA_TOKEN, batch.token);
        done.putExtra(EXTRA_KIND, batch.kind.name());
        done.putExtra(EXTRA_OK_COUNT, batch.okCount());
        done.putExtra(EXTRA_FAIL_COUNT, batch.failureCount());
        sendBroadcast(done);
        UndoManager.getInstance().remember(batch);
    }

    private Notification buildNotification(String token, int done, int total) {
        return buildNotification(token, done, total, null);
    }

    private Notification buildNotification(String token, int done, int total,
            PendingIntent content) {
        PendingIntent cancel = PendingIntent.getService(this, 0,
                cancelIntent(this, token),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.file_op_in_progress))
                .setContentText(getString(R.string.file_op_progress_format, done, total))
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(total, done, false)
                .setOngoing(true)
                .setContentIntent(content)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                        getString(android.R.string.cancel), cancel).build())
                .build();
    }

    /**
     * Undo affordance for hosts that cannot show a Snackbar. Posts a short-lived
     * notification carrying the same undo intent, which expires by itself once
     * the undo window closes.
     */
    public static void showUndoNotification(Context context, String message) {
        NotificationManager notifications = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notifications == null) return;
        notifications.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, context.getString(R.string.file_op_channel_name),
                NotificationManager.IMPORTANCE_LOW));
        PendingIntent undo = PendingIntent.getService(context, 1, undoIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        notifications.notify(UNDO_NOTIFICATION_ID, new Notification.Builder(
                context, CHANNEL_ID)
                .setContentTitle(message)
                .setSmallIcon(android.R.drawable.ic_menu_revert)
                .setTimeoutAfter(UndoManager.UNDO_WINDOW_MILLIS)
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(context, android.R.drawable.ic_menu_revert),
                        context.getString(R.string.undo), undo).build())
                .build());
    }

    @Override
    public void onDestroy() {
        mExecutor.shutdown();
        super.onDestroy();
    }
}
