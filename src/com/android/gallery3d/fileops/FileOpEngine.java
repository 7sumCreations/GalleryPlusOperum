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
}
