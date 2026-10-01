package com.android.gallery3d.fileops;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.settings.GallerySettings;

import java.util.List;

/**
 * Tells the user what Auto-file did: "Filed N new photos" with Undo, and
 * "couldn't move N photos because Android needs your permission".
 *
 * Every public method swallows its failures. Notifications are a report on
 * work already done; failing to post one must never undo, abort or crash that
 * work. Without POST_NOTIFICATIONS (Android 13+) or with the channel blocked,
 * nothing shows and nothing breaks; the note in Settings still explains.
 */
public final class AutoFileNotifier {

    private static final String TAG = "AutoFileNotifier";

    /** Shared with FileOpService: one "File operations" channel, low importance. */
    static final String CHANNEL_ID = "gallery_file_ops";
    static final int FILED_NOTIFICATION_ID = 0x0F11;
    static final int SKIPPED_NOTIFICATION_ID = 0x0F12;

    /** How long the "Put N photos back" confirmation stays up. */
    static final long UNDO_RESULT_TIMEOUT_MILLIS = 60L * 1000L;

    private static final String PREFS_NAME = "auto_file_notices";
    private static final String KEY_WINDOW_START = "undo_window_start_millis";
    private static final String KEY_SKIP_TOLD_COUNT = "skip_told_count";
    private static final String KEY_SKIP_TOLD_AT = "skip_told_at_millis";

    private AutoFileNotifier() {
    }

    /** Called after every run. Never throws. */
    public static void afterRun(Context context, AutoFileReceiver.RunOutcome outcome,
            long nowMillis) {
        try {
            if (outcome.moved > 0) postFiled(context, nowMillis);
        } catch (RuntimeException failure) {
            Log.w(TAG, "could not post the filed notification", failure);
        }
        try {
            handleSkips(context, outcome.needPermission, nowMillis);
        } catch (RuntimeException failure) {
            Log.w(TAG, "could not post the permission notification", failure);
        }
    }

    // ---- "Filed N new photos" ------------------------------------------------

    private static void postFiled(Context context, long nowMillis) {
        SharedPreferences prefs = prefs(context);
        long windowStart = AutoFileNotices.undoWindowStartFor(
                readLong(prefs, KEY_WINDOW_START), nowMillis);
        prefs.edit().putLong(KEY_WINDOW_START, windowStart).apply();

        List<AutoFileLog.Entry> entries = AutoFileNotices.entriesInWindow(
                AutoFileLog.load(context).entries(), windowStart, nowMillis);
        if (entries.isEmpty()) return;
        AutoFileNotices.FiledSummary summary = AutoFileNotices.summarise(entries);

        NotificationManager notifications = manager(context);
        if (notifications == null) return;
        Resources res = context.getResources();
        String title = summary.singleFolder() != null
                ? res.getQuantityString(R.plurals.auto_file_filed_into_folder,
                        summary.count, summary.count, summary.singleFolder())
                : res.getQuantityString(R.plurals.auto_file_filed_into_folders,
                        summary.count, summary.count, summary.folders.size());

        PendingIntent undo = PendingIntent.getBroadcast(context, FILED_NOTIFICATION_ID,
                AutoFileReceiver.undoIntent(context, windowStart, nowMillis),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent dismissed = PendingIntent.getBroadcast(context, FILED_NOTIFICATION_ID,
                AutoFileReceiver.dismissedIntent(context, windowStart),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentTitle(title)
                .setContentText(res.getString(R.string.auto_file_filed_text))
                .setStyle(new Notification.BigTextStyle()
                        .bigText(res.getString(R.string.auto_file_filed_text)))
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setTimeoutAfter(AutoFileNotices.remainingUndoMillis(windowStart, nowMillis))
                .setDeleteIntent(dismissed)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(context, android.R.drawable.ic_menu_revert),
                        res.getString(R.string.auto_file_undo_action), undo).build());
        PendingIntent open = openAppIntent(context);
        if (open != null) builder.setContentIntent(open);
        notifications.notify(FILED_NOTIFICATION_ID, builder.build());
    }

    /** Called once an Undo has run. Never throws. */
    public static void afterUndo(Context context, AutoFileNotices.UndoOutcome outcome) {
        try {
            prefs(context).edit().putLong(KEY_WINDOW_START, AutoFileNotices.NONE).apply();
            NotificationManager notifications = manager(context);
            if (notifications == null) return;
            Resources res = context.getResources();
            String title = outcome.restored > 0
                    ? res.getQuantityString(R.plurals.auto_file_undo_done,
                            outcome.restored, outcome.restored)
                    : res.getString(R.string.auto_file_undo_nothing);
            Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_revert)
                    .setContentTitle(title)
                    .setAutoCancel(true)
                    .setTimeoutAfter(UNDO_RESULT_TIMEOUT_MILLIS);
            if (outcome.notRestored() > 0) {
                String text = res.getQuantityString(R.plurals.auto_file_undo_not_restored,
                        outcome.notRestored(), outcome.notRestored());
                builder.setContentText(text)
                        .setStyle(new Notification.BigTextStyle().bigText(text));
            }
            notifications.notify(FILED_NOTIFICATION_ID, builder.build());
        } catch (RuntimeException failure) {
            Log.w(TAG, "could not report the undo", failure);
        }
    }

    /** The user swiped "Filed N new photos" away: later runs start a fresh window. */
    public static void afterDismissed(Context context, long windowStart) {
        try {
            SharedPreferences prefs = prefs(context);
            if (readLong(prefs, KEY_WINDOW_START) == windowStart) {
                prefs.edit().putLong(KEY_WINDOW_START, AutoFileNotices.NONE).apply();
            }
        } catch (RuntimeException failure) {
            Log.w(TAG, "could not record the dismissal", failure);
        }
    }

    // ---- "Couldn't move N photos" -------------------------------------------

    private static void handleSkips(Context context, int needPermission, long nowMillis) {
        SharedPreferences prefs = prefs(context);
        int toldCount = readInt(prefs, KEY_SKIP_TOLD_COUNT);
        long toldAt = readLong(prefs, KEY_SKIP_TOLD_AT);

        if (needPermission <= 0) {
            // Nothing is waiting any more (permission granted, photos moved by
            // hand or deleted): take the notice down and forget we told them.
            if (toldCount != 0 || toldAt != AutoFileNotices.NONE) {
                prefs.edit().remove(KEY_SKIP_TOLD_COUNT).remove(KEY_SKIP_TOLD_AT).apply();
                NotificationManager notifications = manager(context);
                if (notifications != null) notifications.cancel(SKIPPED_NOTIFICATION_ID);
            }
            return;
        }
        if (!AutoFileNotices.shouldNotifyAboutSkips(needPermission, toldCount, toldAt,
                nowMillis)) {
            return;
        }
        NotificationManager notifications = manager(context);
        if (notifications == null) return;

        Resources res = context.getResources();
        String text = res.getQuantityString(
                GallerySettings.isMediaAccessSupported(Build.VERSION.SDK_INT)
                        ? R.plurals.auto_file_skipped_text
                        : R.plurals.auto_file_skipped_text_legacy,
                needPermission, needPermission);
        Intent settings = new Intent(context, GallerySettings.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivity(context, SKIPPED_NOTIFICATION_ID,
                settings, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        notifications.notify(SKIPPED_NOTIFICATION_ID, new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(res.getString(R.string.auto_file_skipped_title))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build());
        // Only remember "told" when it could actually be seen; otherwise the
        // user hears about it the first run after notifications are allowed.
        if (canShow(notifications)) {
            prefs.edit().putInt(KEY_SKIP_TOLD_COUNT, needPermission)
                    .putLong(KEY_SKIP_TOLD_AT, nowMillis).apply();
        }
    }

    // ---- plumbing -------------------------------------------------------------

    private static NotificationManager manager(Context context) {
        NotificationManager notifications = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notifications == null) return null;
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.file_op_channel_name),
                NotificationManager.IMPORTANCE_LOW));
        return notifications;
    }

    /** False when POST_NOTIFICATIONS is denied, the app is blocked, or the channel is off. */
    private static boolean canShow(NotificationManager notifications) {
        try {
            if (!notifications.areNotificationsEnabled()) return false;
            NotificationChannel channel = notifications.getNotificationChannel(CHANNEL_ID);
            return channel == null
                    || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent launch = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        if (launch == null) return null;
        return PendingIntent.getActivity(context, FILED_NOTIFICATION_ID, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static long readLong(SharedPreferences prefs, String key) {
        try {
            return prefs.getLong(key, AutoFileNotices.NONE);
        } catch (ClassCastException wrongType) {
            return AutoFileNotices.NONE;
        }
    }

    private static int readInt(SharedPreferences prefs, String key) {
        try {
            return prefs.getInt(key, 0);
        } catch (ClassCastException wrongType) {
            return 0;
        }
    }
}
