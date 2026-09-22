package com.android.gallery3d.fileops;

import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** One user-initiated operation over one or more items. The unit Undo reverses. */
public final class FileOpBatch {

    public enum Kind { MOVE, COPY, TRASH, RESTORE, FAVOURITE, UNFAVOURITE, DELETE_FOREVER }

    private static final AtomicInteger sCounter = new AtomicInteger();

    public final String token;
    public final Kind kind;
    public final List<Uri> items;
    /** Canonical RELATIVE_PATH, or null for kinds that need no destination. */
    public final String destRelativePath;
    public final List<FileOpResult> results = new ArrayList<FileOpResult>();

    public FileOpBatch(String token, Kind kind, List<Uri> items, String destRelativePath) {
        this.token = token;
        this.kind = kind;
        this.items = Collections.unmodifiableList(new ArrayList<Uri>(items));
        this.destRelativePath = destRelativePath == null
                ? null : RelativePaths.normalise(destRelativePath);
    }

    public static String nextToken() {
        return "batch-" + sCounter.incrementAndGet();
    }

    public int okCount() {
        int count = 0;
        for (FileOpResult result : results) {
            if (result.isOk()) count++;
        }
        return count;
    }

    public int failureCount() {
        int count = 0;
        for (FileOpResult result : results) {
            if (result.status == FileOpResult.Status.FAILED) count++;
        }
        return count;
    }
}
