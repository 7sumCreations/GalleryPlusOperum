package com.android.gallery3d.fileops;

import android.net.Uri;

import java.io.IOException;
import java.util.List;

/**
 * Puts auto-filed photos back where they came from, using the entries
 * AutoFileLog wrote. The same single RELATIVE_PATH update FileOpEngine's
 * move Undo uses: no bytes are copied and the row keeps its uri.
 *
 * Only touches a photo that is still exactly where Auto-file put it. One the
 * user has since deleted, trashed or moved somewhere else is left alone: their
 * later decision wins over an Undo of an earlier automatic one.
 *
 * No Android context, so it runs against FakeMediaStore on the JVM. Never
 * throws: it runs from a notification action.
 */
public final class AutoFileUndo {

    private AutoFileUndo() {
    }

    /** Undo every move the notification for [windowStart, windowEnd] counted. */
    public static AutoFileNotices.UndoOutcome undoWindow(MediaStoreGateway gateway,
            AutoFileLog log, long windowStart, long windowEnd, long nowMillis) {
        AutoFileNotices.UndoOutcome outcome = new AutoFileNotices.UndoOutcome();
        List<AutoFileLog.Entry> picked =
                AutoFileNotices.entriesInWindow(log.entries(), windowStart, windowEnd);
        for (AutoFileLog.Entry entry : picked) {
            switch (undoEntry(gateway, log, entry, nowMillis)) {
                case RESTORED:
                    outcome.restored++;
                    break;
                case GONE:
                    outcome.gone++;
                    break;
                default:
                    outcome.failed++;
                    break;
            }
        }
        return outcome;
    }

    enum Result { RESTORED, GONE, FAILED }

    /**
     * Undo one logged move. On success the entry leaves the log and the photo
     * is remembered as undone, so the next run does not file it again. A photo
     * that is gone or was moved since also leaves the log: there is nothing
     * left for that entry to undo.
     */
    static Result undoEntry(MediaStoreGateway gateway, AutoFileLog log,
            AutoFileLog.Entry entry, long nowMillis) {
        try {
            Uri item = Uri.parse(entry.itemUri);
            MediaItemInfo info = gateway.query(item);
            if (info == null || info.trashed
                    || !info.relativePath.equals(RelativePaths.normalise(entry.toRelativePath))) {
                log.remove(entry.itemUri);
                return Result.GONE;
            }
            String from = RelativePaths.normalise(entry.fromRelativePath);
            // Something new with the same name may have landed in the old folder.
            String name = UniqueNames.freeName(gateway.displayNamesIn(from), entry.displayName);
            gateway.updateLocation(item, from, name);
            log.remove(entry.itemUri);
            log.markUndone(entry.itemUri, nowMillis);
            return Result.RESTORED;
        } catch (PendingConsentException consent) {
            // MANAGE_MEDIA was revoked since. Keep the entry: nothing moved.
            return Result.FAILED;
        } catch (IOException failure) {
            return Result.FAILED;
        } catch (RuntimeException failure) {
            return Result.FAILED;
        }
    }
}
