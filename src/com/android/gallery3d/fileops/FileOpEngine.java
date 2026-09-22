package com.android.gallery3d.fileops;

import android.net.Uri;

import java.io.IOException;

/**
 * The one engine every write in Epic 1 goes through.
 *
 * It holds no Android context and no ContentResolver: everything it touches is
 * behind MediaStoreGateway, which is what makes it unit-testable on the JVM and
 * what would let a future vault plug in as an alternative gateway.
 */
public class FileOpEngine {

    /** Zero-byte file that keeps a newly created empty folder visible. */
    public static final String PLACEHOLDER_NAME = ".nomedia_placeholder";

    private final MediaStoreGateway mGateway;

    public FileOpEngine(MediaStoreGateway gateway) {
        mGateway = gateway;
    }

    /**
     * Move one item into destRelativePath.
     *
     * Same volume: one RELATIVE_PATH update, which preserves the file, its name
     * and its DATE_TAKEN. Different volume: copy first, verify the copy landed,
     * and only then delete the source.
     */
    public FileOpResult move(Uri item, String destRelativePath) {
        String destination = RelativePaths.normalise(destRelativePath);
        if (!RelativePaths.isUnderMediaRoot(destination)) {
            return FileOpResult.failed(item,
                    "Destination must be under Pictures/ or DCIM/: " + destRelativePath);
        }

        MediaItemInfo info = mGateway.query(item);
        if (info == null) {
            return FileOpResult.failed(item, "Item no longer exists");
        }
        if (info.relativePath.equals(destination)) {
            return FileOpResult.ok(item, item, info.relativePath, info.displayName,
                    info.favourite);
        }

        String name = UniqueNames.freeName(
                mGateway.displayNamesIn(destination), info.displayName);

        try {
            Uri probe = mGateway.copyTo(item, destination, name);
            MediaItemInfo copied = mGateway.query(probe);
            boolean sameVolume = copied != null && copied.volumeName.equals(info.volumeName);
            if (sameVolume) {
                // Same volume: the copy was unnecessary. Throw it away and do the
                // cheap in-place rewrite that preserves the original row identity.
                mGateway.deletePermanently(probe);
                mGateway.updateLocation(item, destination, name);
                return FileOpResult.ok(item, item, info.relativePath, info.displayName,
                        info.favourite);
            }
            // Cross-volume: the copy IS the move. Verify before deleting the source.
            if (copied == null) {
                return FileOpResult.failed(item, "Copy to " + destination + " did not land");
            }
            mGateway.deletePermanently(item);
            return FileOpResult.ok(item, probe, info.relativePath, info.displayName,
                    info.favourite);
        } catch (PendingConsentException consent) {
            return FileOpResult.consentRequired(item, consent.intentSender);
        } catch (IOException failure) {
            return FileOpResult.failed(item, failure.getMessage());
        }
    }

    /** How a long-running batch reports progress and learns it has been cancelled. */
    public interface ProgressCallback {
        /** @param indexDone 1-based count of items finished so far. */
        void onItemDone(int indexDone, int total, FileOpResult result);

        boolean isCancelled();
    }

    /**
     * Apply one operation to one item. Every kind in Epic 1 routes through here,
     * so there is exactly one place that decides what "move" or "trash" means.
     */
    public FileOpResult applyOne(FileOpBatch.Kind kind, Uri item, String destRelativePath) {
        switch (kind) {
            case MOVE:
                return move(item, destRelativePath);
            default:
                return FileOpResult.failed(item, "Unsupported operation: " + kind);
        }
    }

    /**
     * Run every item in the batch in order.
     *
     * Epic 1 policy: stop at the first failure and report (no resume, no rollback
     * of the items that already succeeded). Cancel takes effect between items, so
     * the file currently being written is always finished.
     */
    public void runBatch(FileOpBatch batch, ProgressCallback callback) {
        int total = batch.items.size();
        int index = 0;
        boolean everythingSucceeded = true;
        for (Uri item : batch.items) {
            if (callback.isCancelled()) {
                everythingSucceeded = false;
                break;
            }
            FileOpResult result = applyOne(batch.kind, item, batch.destRelativePath);
            batch.results.add(result);
            index++;
            callback.onItemDone(index, total, result);
            if (result.status == FileOpResult.Status.FAILED) {
                everythingSucceeded = false;
                break;
            }
        }
        if (everythingSucceeded && batch.destRelativePath != null) {
            removePlaceholderIn(batch.destRelativePath);
        }
    }

    /**
     * A folder that now holds real photos no longer needs its placeholder.
     * Failures are deliberately swallowed: a stray placeholder is harmless,
     * and losing the whole batch's result over one is not.
     */
    private void removePlaceholderIn(String relativePath) {
        if (!mGateway.displayNamesIn(relativePath).contains(PLACEHOLDER_NAME)) return;
        for (Uri candidate : mGateway.itemsUnder(relativePath)) {
            MediaItemInfo info = mGateway.query(candidate);
            if (info == null) continue;
            if (!info.relativePath.equals(RelativePaths.normalise(relativePath))) continue;
            if (!PLACEHOLDER_NAME.equals(info.displayName)) continue;
            try {
                mGateway.deletePermanently(candidate);
            } catch (PendingConsentException consent) {
                return;
            } catch (IOException failure) {
                return;
            }
        }
    }
}
