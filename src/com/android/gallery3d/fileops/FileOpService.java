package com.android.gallery3d.fileops;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import com.android.gallery3d.R;

import java.util.ArrayList;
import java.util.Collections;
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

    public static final String ACTION_RUN = "com.android.gallery3d.fileops.RUN";
    public static final String ACTION_CANCEL = "com.android.gallery3d.fileops.CANCEL";
    public static final String ACTION_BATCH_DONE = "com.android.gallery3d.fileops.BATCH_DONE";
    public static final String EXTRA_TOKEN = "com.android.gallery3d.fileops.TOKEN";
    public static final String EXTRA_KIND = "com.android.gallery3d.fileops.KIND";
    public static final String EXTRA_ITEMS = "com.android.gallery3d.fileops.ITEMS";
    public static final String EXTRA_DEST = "com.android.gallery3d.fileops.DEST";
    public static final String EXTRA_OK_COUNT = "com.android.gallery3d.fileops.OK_COUNT";
    public static final String EXTRA_FAIL_COUNT = "com.android.gallery3d.fileops.FAIL_COUNT";

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, Boolean> mCancelled = new ConcurrentHashMap<String, Boolean>();
    private NotificationManager mNotifications;
    private FileOpEngine mEngine;

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
        if (intent == null) return START_NOT_STICKY;
        final String token = intent.getStringExtra(EXTRA_TOKEN);

        if (ACTION_CANCEL.equals(intent.getAction())) {
            mCancelled.put(token, Boolean.TRUE);
            return START_NOT_STICKY;
        }

        final FileOpBatch.Kind kind =
                FileOpBatch.Kind.valueOf(intent.getStringExtra(EXTRA_KIND));
        ArrayList<Uri> items = intent.getParcelableArrayListExtra(EXTRA_ITEMS);
        if (items == null) items = new ArrayList<Uri>();
        final String destination = intent.getStringExtra(EXTRA_DEST);
        final FileOpBatch batch = new FileOpBatch(token, kind, items, destination);

        startForeground(NOTIFICATION_ID, buildNotification(token, 0, items.size()));

        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    mEngine.runBatch(batch, new FileOpEngine.ProgressCallback() {
                        @Override
                        public void onItemDone(int indexDone, int total, FileOpResult result) {
                            if (result.status == FileOpResult.Status.CONSENT_REQUIRED
                                    && result.consent != null) {
                                startActivity(ConsentActivity.intentFor(
                                        FileOpService.this, result.consent, batch.token));
                            }
                            mNotifications.notify(NOTIFICATION_ID,
                                    buildNotification(batch.token, indexDone, total));
                        }

                        @Override
                        public boolean isCancelled() {
                            return Boolean.TRUE.equals(mCancelled.get(batch.token));
                        }
                    });
                } catch (Throwable failure) {
                    Log.e(TAG, "Batch " + batch.token + " threw", failure);
                } finally {
                    mCancelled.remove(batch.token);
                    broadcastDone(batch);
                    stopForeground(true);
                    stopSelf();
                }
            }
        });
        return START_NOT_STICKY;
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
        PendingIntent cancel = PendingIntent.getService(this, 0,
                cancelIntent(this, token),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.file_op_in_progress))
                .setContentText(getString(R.string.file_op_progress_format, done, total))
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(total, done, false)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null,
                        getString(android.R.string.cancel), cancel).build())
                .build();
    }

    @Override
    public void onDestroy() {
        mExecutor.shutdown();
        super.onDestroy();
    }
}
