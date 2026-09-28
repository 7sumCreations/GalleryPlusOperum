package com.android.gallery3d.fileops;

import android.net.Uri;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The batch-consent decisions, pulled out of FileOpService so they can be
 * unit-tested on the JVM.
 *
 * FileOpService itself is not testable without a device, but "which items still
 * need the user's permission" and "what do the final counts look like after the
 * retry" are pure functions over FileOpResults, and those are exactly the two
 * things that were wrong before F-026.
 */
public final class ConsentPolicy {

    /** Shown for an item the user did not (or could not) authorise. */
    public static final String NOT_GRANTED =
            "Permission to modify this photo was not granted";

    private ConsentPolicy() {
    }

    /**
     * The source uris of every result still waiting on the user, in the order
     * they were attempted and with duplicates collapsed.
     *
     * One dialog covers the whole list, which is why the caller needs the list
     * rather than a per-item IntentSender.
     */
    public static List<Uri> itemsNeedingConsent(List<FileOpResult> results) {
        LinkedHashSet<Uri> pending = new LinkedHashSet<Uri>();
        if (results == null) return new ArrayList<Uri>();
        for (FileOpResult result : results) {
            if (result == null) continue;
            if (result.status != FileOpResult.Status.CONSENT_REQUIRED) continue;
            if (result.sourceUri == null) continue;
            pending.add(result.sourceUri);
        }
        return new ArrayList<Uri>(pending);
    }

    /**
     * Fold the retry pass back into the first pass so the completion counts the
     * user sees describe the whole operation.
     *
     * Every first-pass entry keeps its position. A CONSENT_REQUIRED entry is
     * replaced by whatever the retry made of the same item; if there was no
     * retry for it, or the retry still came back CONSENT_REQUIRED, it is demoted
     * to a plain failure. Nothing in the returned list is ever
     * CONSENT_REQUIRED, because the retry happens at most once and an item that
     * is still blocked after it has genuinely failed.
     *
     * @param first the results of the original run; may be null or empty
     * @param retry the results of the single consent retry; may be null
     */
    public static List<FileOpResult> combine(List<FileOpResult> first,
            List<FileOpResult> retry) {
        // Insertion-ordered so leftovers below come out in the order they were retried.
        Map<String, FileOpResult> byUri = new LinkedHashMap<String, FileOpResult>();
        List<FileOpResult> extras = new ArrayList<FileOpResult>();
        if (retry != null) {
            for (FileOpResult result : retry) {
                if (result == null) continue;
                if (result.sourceUri == null) {
                    extras.add(settle(result));
                    continue;
                }
                byUri.put(result.sourceUri.toString(), result);
            }
        }

        List<FileOpResult> combined = new ArrayList<FileOpResult>();
        if (first != null) {
            for (FileOpResult result : first) {
                if (result == null) continue;
                if (result.status != FileOpResult.Status.CONSENT_REQUIRED) {
                    combined.add(result);
                    continue;
                }
                FileOpResult retried = result.sourceUri == null
                        ? null : byUri.remove(result.sourceUri.toString());
                combined.add(settle(retried == null ? result : retried));
            }
        }
        // A retry result for an item the first pass never reported still counts.
        for (FileOpResult leftover : byUri.values()) {
            combined.add(settle(leftover));
        }
        combined.addAll(extras);
        return combined;
    }

    /** Turn a still-pending result into the failure it has become. */
    private static FileOpResult settle(FileOpResult result) {
        if (result.status != FileOpResult.Status.CONSENT_REQUIRED) return result;
        return FileOpResult.failed(result.sourceUri, NOT_GRANTED);
    }
}
