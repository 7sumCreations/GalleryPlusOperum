package com.android.gallery3d.fileops;

/** Outcome of a folder-level operation: rename, move, or delete. */
public final class FolderOpResult {

    public final boolean ok;
    public final String fromRelativePath;
    public final String toRelativePath;
    public final int itemsChanged;
    public final String failureReason;

    private FolderOpResult(boolean ok, String fromRelativePath, String toRelativePath,
            int itemsChanged, String failureReason) {
        this.ok = ok;
        this.fromRelativePath = fromRelativePath;
        this.toRelativePath = toRelativePath;
        this.itemsChanged = itemsChanged;
        this.failureReason = failureReason;
    }

    public static FolderOpResult ok(String from, String to, int itemsChanged) {
        return new FolderOpResult(true, from, to, itemsChanged, null);
    }

    public static FolderOpResult failed(String from, String reason) {
        return new FolderOpResult(false, from, null, 0, reason);
    }
}
