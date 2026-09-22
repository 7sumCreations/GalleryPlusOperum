package com.android.gallery3d.fileops;

import android.content.IntentSender;

/**
 * Thrown when the platform will only perform a write after the user approves it
 * in a system dialog. The caller must launch {@link #intentSender}, wait for the
 * result, and then retry the operation.
 */
public class PendingConsentException extends Exception {

    private static final long serialVersionUID = 1L;

    public final IntentSender intentSender;

    public PendingConsentException(IntentSender intentSender) {
        super("User consent required before this media item can be written");
        this.intentSender = intentSender;
    }
}
