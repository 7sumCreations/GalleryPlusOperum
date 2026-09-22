package com.android.gallery3d.fileops;

import android.content.IntentSender;
import android.net.Uri;

/**
 * The outcome of one operation on one item.
 *
 * The "previous" fields are what Undo (F-023) replays: they always describe
 * where the item was immediately before this operation ran.
 */
public final class FileOpResult {

    public enum Status { OK, CONSENT_REQUIRED, FAILED }

    public final Status status;
    public final Uri sourceUri;
    public final Uri resultUri;
    public final String previousRelativePath;
    public final String previousDisplayName;
    public final boolean previousFavourite;
    public final IntentSender consent;
    public final String failureReason;

    private FileOpResult(Status status, Uri sourceUri, Uri resultUri,
            String previousRelativePath, String previousDisplayName,
            boolean previousFavourite, IntentSender consent, String failureReason) {
        this.status = status;
        this.sourceUri = sourceUri;
        this.resultUri = resultUri;
        this.previousRelativePath = previousRelativePath;
        this.previousDisplayName = previousDisplayName;
        this.previousFavourite = previousFavourite;
        this.consent = consent;
        this.failureReason = failureReason;
    }

    public static FileOpResult ok(Uri source, Uri result, String prevPath,
            String prevName, boolean prevFavourite) {
        return new FileOpResult(Status.OK, source, result, prevPath, prevName,
                prevFavourite, null, null);
    }

    public static FileOpResult consentRequired(Uri source, IntentSender sender) {
        return new FileOpResult(Status.CONSENT_REQUIRED, source, null, null, null,
                false, sender, null);
    }

    public static FileOpResult failed(Uri source, String reason) {
        return new FileOpResult(Status.FAILED, source, null, null, null, false,
                null, reason);
    }

    public boolean isOk() {
        return status == Status.OK;
    }

    @Override
    public String toString() {
        return "FileOpResult{" + status + " " + sourceUri + " -> " + resultUri
                + (failureReason == null ? "" : " (" + failureReason + ")") + "}";
    }
}
