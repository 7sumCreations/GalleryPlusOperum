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
    public static final String PLACEHOLDER_NAME = ".nomedia_placeholder.jpg";

    /**
     * Cleanup matches on the stem, not the whole name: MediaStore is free to
     * append a dedup suffix ("... (1).jpg") when it accepts the insert, and a
     * placeholder we failed to recognise would linger in the grid forever.
     */
    static final String PLACEHOLDER_PREFIX = ".nomedia_placeholder";

    private final MediaStoreGateway mGateway;

    public FileOpEngine(MediaStoreGateway gateway) {
        mGateway = gateway;
    }

    /**
     * Move one item into destRelativePath.
     *
     * Always one RELATIVE_PATH update on the item's own row: no bytes are
     * copied, and the row keeps its uri, its DATE_TAKEN and its volume.
     *
     * A destination is a RELATIVE_PATH only (FileOpBatch, the folder picker and
     * auto-file never carry a volume), so a move is by definition within the
     * item's own volume. An item on an SD card stays on that SD card.
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
            mGateway.updateLocation(item, destination, name);
            return FileOpResult.ok(item, item, info.relativePath, info.displayName,
                    info.favourite);
        } catch (PendingConsentException consent) {
            return FileOpResult.consentRequired(item, consent.intentSender);
        } catch (IOException failure) {
            return FileOpResult.failed(item, failure.getMessage());
        }
    }

    /** Create a real second file at the destination. The source is not touched. */
    public FileOpResult copy(Uri item, String destRelativePath) {
        String destination = RelativePaths.normalise(destRelativePath);
        if (!RelativePaths.isUnderMediaRoot(destination)) {
            return FileOpResult.failed(item,
                    "Destination must be under Pictures/ or DCIM/: " + destRelativePath);
        }
        MediaItemInfo info = mGateway.query(item);
        if (info == null) return FileOpResult.failed(item, "Item no longer exists");

        String name = UniqueNames.freeName(
                mGateway.displayNamesIn(destination), info.displayName);
        try {
            Uri copy = mGateway.copyTo(item, destination, name);
            return FileOpResult.ok(item, copy, info.relativePath, info.displayName,
                    info.favourite);
        } catch (PendingConsentException consent) {
            return FileOpResult.consentRequired(item, consent.intentSender);
        } catch (IOException failure) {
            return FileOpResult.failed(item, failure.getMessage());
        }
    }

    /**
     * Rewrite the RELATIVE_PATH of every item at or under fromRelativePath so
     * that the folder appears under a new name in the same parent.
     *
     * Refuses rather than merges when the new name is already taken: merging is
     * out of scope for Epic 1.
     */
    public FolderOpResult renameFolder(String fromRelativePath, String newName) {
        String from = RelativePaths.normalise(fromRelativePath);
        String nameError = RelativePaths.validateFolderName(newName);
        if (nameError != null) return FolderOpResult.failed(from, nameError);

        String to = RelativePaths.join(RelativePaths.parentOf(from), newName);
        if (from.equals(to)) return FolderOpResult.ok(from, to, 0);
        if (mGateway.folderPathsUnder(to).contains(to)) {
            return FolderOpResult.failed(from,
                    "A folder called " + newName + " already exists here");
        }
        return relocateTree(from, to);
    }

    /**
     * Re-parent a folder: Pictures/Lisbon under Pictures/Trips becomes
     * Pictures/Trips/Lisbon, contents and sub-folders included.
     */
    public FolderOpResult moveFolder(String fromRelativePath, String newParentRelativePath) {
        String from = RelativePaths.normalise(fromRelativePath);
        String newParent = RelativePaths.normalise(newParentRelativePath);

        if (!RelativePaths.isUnderMediaRoot(newParent)) {
            return FolderOpResult.failed(from,
                    "Folders can only be moved under Pictures or DCIM");
        }
        if (newParent.startsWith(from)) {
            return FolderOpResult.failed(from, "A folder cannot be moved inside itself");
        }

        String to = RelativePaths.join(newParent, RelativePaths.lastSegment(from));
        if (from.equals(to)) return FolderOpResult.ok(from, to, 0);
        if (mGateway.folderPathsUnder(to).contains(to)) {
            return FolderOpResult.failed(from, "A folder called "
                    + RelativePaths.lastSegment(from) + " already exists there");
        }
        return relocateTree(from, to);
    }

    /**
     * Move every item under {@code from} so that it sits under {@code to},
     * preserving the sub-folder structure beneath it.
     *
     * Shared by renameFolder (F-018) and moveFolder (F-020).
     */
    FolderOpResult relocateTree(String from, String to) {
        int changed = 0;
        for (Uri item : mGateway.itemsUnder(from)) {
            MediaItemInfo info = mGateway.query(item);
            if (info == null) continue;
            String suffix = info.relativePath.substring(from.length());
            String destination = RelativePaths.normalise(to + suffix);
            String name = UniqueNames.freeName(
                    mGateway.displayNamesIn(destination), info.displayName);
            try {
                mGateway.updateLocation(item, destination, name);
                changed++;
            } catch (PendingConsentException consent) {
                return FolderOpResult.failed(from,
                        "Permission is needed to move these photos");
            } catch (IOException failure) {
                return FolderOpResult.failed(from, failure.getMessage());
            }
        }
        return FolderOpResult.ok(from, to, changed);
    }

    /** The camera's own folder. Deleting it breaks the camera app, so we refuse. */
    public static final String CAMERA_PATH = "DCIM/Camera/";

    /**
     * Send every item at or under a folder to the trash. The folder then
     * disappears from the grid, because a folder with no visible items does not
     * exist as far as MediaStore is concerned.
     */
    public FolderOpResult trashFolder(String relativePath) {
        String folder = RelativePaths.normalise(relativePath);
        if (folder.equalsIgnoreCase(CAMERA_PATH)) {
            return FolderOpResult.failed(folder,
                    "The camera folder cannot be deleted");
        }
        if (!RelativePaths.isUnderMediaRoot(folder)) {
            return FolderOpResult.failed(folder,
                    "Only folders under Pictures or DCIM can be deleted");
        }
        int changed = 0;
        for (Uri item : mGateway.itemsUnder(folder)) {
            FileOpResult result = trash(item);
            if (!result.isOk()) {
                return FolderOpResult.failed(folder, result.failureReason == null
                        ? "Permission is needed to delete these photos"
                        : result.failureReason);
            }
            changed++;
        }
        return FolderOpResult.ok(folder, null, changed);
    }

    /** Set or clear the MediaStore favourite flag. The file never moves. */
    public FileOpResult setFavourite(Uri item, boolean favourite) {
        MediaItemInfo info = mGateway.query(item);
        if (info == null) return FileOpResult.failed(item, "Item no longer exists");
        try {
            mGateway.setFavourite(item, favourite);
            return FileOpResult.ok(item, item, info.relativePath, info.displayName,
                    info.favourite);
        } catch (PendingConsentException consent) {
            return FileOpResult.consentRequired(item, consent.intentSender);
        } catch (IOException failure) {
            return FileOpResult.failed(item, failure.getMessage());
        }
    }

    /** How long a trashed item survives before purgeExpiredTrash removes it. */
    public static final long TRASH_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1000L;

    /** Move to the system trash. The file stays on disk with IS_TRASHED set. */
    public FileOpResult trash(Uri item) {
        return setTrashedFlag(item, true);
    }

    /** Bring an item back out of the trash, to exactly where it was. */
    public FileOpResult restore(Uri item) {
        return setTrashedFlag(item, false);
    }

    private FileOpResult setTrashedFlag(Uri item, boolean trashed) {
        MediaItemInfo info = mGateway.query(item);
        if (info == null) return FileOpResult.failed(item, "Item no longer exists");
        try {
            mGateway.setTrashed(item, trashed);
            return FileOpResult.ok(item, item, info.relativePath, info.displayName,
                    info.favourite);
        } catch (PendingConsentException consent) {
            return FileOpResult.consentRequired(item, consent.intentSender);
        } catch (IOException failure) {
            return FileOpResult.failed(item, failure.getMessage());
        }
    }

    /** Destroy the file. There is no coming back from this one. */
    public FileOpResult deleteForever(Uri item) {
        MediaItemInfo info = mGateway.query(item);
        if (info == null) return FileOpResult.failed(item, "Item no longer exists");
        try {
            mGateway.deletePermanently(item);
            return FileOpResult.ok(item, null, info.relativePath, info.displayName,
                    info.favourite);
        } catch (PendingConsentException consent) {
            return FileOpResult.consentRequired(item, consent.intentSender);
        } catch (IOException failure) {
            return FileOpResult.failed(item, failure.getMessage());
        }
    }

    /**
     * Permanently remove every trashed item whose retention window has closed.
     *
     * @return how many items were purged.
     */
    public int purgeExpiredTrash(long nowMillis) {
        int purged = 0;
        for (Uri item : mGateway.trashedOlderThan(nowMillis)) {
            try {
                mGateway.deletePermanently(item);
                purged++;
            } catch (PendingConsentException consent) {
                // A background purge must never pop a dialog: skip and retry tomorrow.
            } catch (IOException failure) {
                // Same: skip and retry tomorrow.
            }
        }
        return purged;
    }

    /**
     * Permanently remove every trashed item, regardless of age.
     *
     * @return how many items were destroyed.
     */
    public int emptyTrash() {
        int destroyed = 0;
        for (Uri item : mGateway.trashedItems()) {
            if (deleteForever(item).isOk()) destroyed++;
        }
        return destroyed;
    }

    /**
     * Reverse a completed batch, item by item, using the previous location each
     * FileOpResult recorded when the batch ran.
     *
     * Only items that actually succeeded are reversed: an item that failed never
     * moved, so there is nothing to put back.
     *
     * @return the reversal batch, so callers can report how much came back.
     */
    public FileOpBatch reverseBatch(FileOpBatch batch, ProgressCallback callback) {
        java.util.List<FileOpResult> reversible = new java.util.ArrayList<FileOpResult>();
        for (FileOpResult result : batch.results) {
            if (result.isOk()) reversible.add(result);
        }

        java.util.List<Uri> items = new java.util.ArrayList<Uri>(reversible.size());
        for (FileOpResult result : reversible) {
            items.add(result.resultUri != null ? result.resultUri : result.sourceUri);
        }
        FileOpBatch reversal = new FileOpBatch(
                batch.token + "-undo", batch.kind, items, null);

        int total = reversible.size();
        int index = 0;
        for (FileOpResult original : reversible) {
            if (callback.isCancelled()) return reversal;
            FileOpResult undone = reverseOne(batch.kind, original);
            reversal.results.add(undone);
            index++;
            callback.onItemDone(index, total, undone);
        }
        return reversal;
    }

    private FileOpResult reverseOne(FileOpBatch.Kind kind, FileOpResult original) {
        Uri current = original.resultUri != null ? original.resultUri : original.sourceUri;
        switch (kind) {
            case MOVE: {
                // Put it back where it came from, under the name it came with.
                MediaItemInfo info = mGateway.query(current);
                if (info == null) return FileOpResult.failed(current, "Item no longer exists");
                try {
                    mGateway.updateLocation(current, original.previousRelativePath,
                            original.previousDisplayName);
                    return FileOpResult.ok(current, current, info.relativePath,
                            info.displayName, info.favourite);
                } catch (PendingConsentException consent) {
                    return FileOpResult.consentRequired(current, consent.intentSender);
                } catch (IOException failure) {
                    return FileOpResult.failed(current, failure.getMessage());
                }
            }
            case COPY:
                // Undoing a copy means removing the copy. The original never moved.
                return deleteForever(current);
            case TRASH:
                return restore(current);
            case RESTORE:
                return trash(current);
            case FAVOURITE:
            case UNFAVOURITE:
                return setFavourite(current, original.previousFavourite);
            case DELETE_FOREVER:
            default:
                return FileOpResult.failed(current, "This cannot be undone");
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
            case COPY:
                return copy(item, destRelativePath);
            case FAVOURITE:
                return setFavourite(item, true);
            case UNFAVOURITE:
                return setFavourite(item, false);
            case TRASH:
                return trash(item);
            case RESTORE:
                return restore(item);
            case DELETE_FOREVER:
                return deleteForever(item);
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
        boolean present = false;
        for (String name : mGateway.displayNamesIn(relativePath)) {
            if (name != null && name.startsWith(PLACEHOLDER_PREFIX)) {
                present = true;
                break;
            }
        }
        if (!present) return;
        for (Uri candidate : mGateway.itemsUnder(relativePath)) {
            MediaItemInfo info = mGateway.query(candidate);
            if (info == null) continue;
            if (!info.relativePath.equals(RelativePaths.normalise(relativePath))) continue;
            if (info.displayName == null
                    || !info.displayName.startsWith(PLACEHOLDER_PREFIX)) continue;
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
