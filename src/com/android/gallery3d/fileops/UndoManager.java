package com.android.gallery3d.fileops;

/**
 * Remembers exactly one completed batch, for ten seconds.
 *
 * Deliberately not a history stack: the backlog says "not multi-step history".
 * A second batch replaces the first, and taking a batch consumes it.
 */
public final class UndoManager {

    public static final long UNDO_WINDOW_MILLIS = 10_000L;

    private static final UndoManager INSTANCE = new UndoManager();

    private FileOpBatch mBatch;
    private long mRememberedAtMillis;

    private UndoManager() {
    }

    public static UndoManager getInstance() {
        return INSTANCE;
    }

    public void remember(FileOpBatch batch) {
        rememberAt(batch, System.currentTimeMillis());
    }

    /**
     * Whether a batch of this kind can be reversed at all. A permanent delete
     * cannot: the file is gone, so offering Undo would only fail.
     */
    public static boolean isUndoable(FileOpBatch.Kind kind) {
        return kind != FileOpBatch.Kind.DELETE_FOREVER;
    }

    /**
     * Test seam: remember with an explicit clock reading.
     *
     * A batch that cannot be undone still replaces the previous one, so the
     * Undo shown after a permanent delete can never reach back and reverse
     * whatever ran before it.
     */
    public synchronized void rememberAt(FileOpBatch batch, long nowMillis) {
        if (batch == null || !isUndoable(batch.kind)) {
            mBatch = null;
            mRememberedAtMillis = 0L;
            return;
        }
        mBatch = batch;
        mRememberedAtMillis = nowMillis;
    }

    public synchronized boolean hasUndoable(long nowMillis) {
        if (mBatch == null) return false;
        if (!isUndoable(mBatch.kind)) return false;
        if (mBatch.okCount() == 0) return false;
        return nowMillis - mRememberedAtMillis <= UNDO_WINDOW_MILLIS;
    }

    /** @return the batch to reverse, consuming it; null when there is nothing to undo. */
    public synchronized FileOpBatch takeUndoable(long nowMillis) {
        if (!hasUndoable(nowMillis)) {
            mBatch = null;
            return null;
        }
        FileOpBatch batch = mBatch;
        mBatch = null;
        return batch;
    }

    public synchronized void clear() {
        mBatch = null;
        mRememberedAtMillis = 0L;
    }
}
