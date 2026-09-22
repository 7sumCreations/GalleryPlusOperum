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

    /** Test seam: remember with an explicit clock reading. */
    public synchronized void rememberAt(FileOpBatch batch, long nowMillis) {
        mBatch = batch;
        mRememberedAtMillis = nowMillis;
    }

    public synchronized boolean hasUndoable(long nowMillis) {
        if (mBatch == null) return false;
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
