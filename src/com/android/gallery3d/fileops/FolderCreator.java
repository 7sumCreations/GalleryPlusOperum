package com.android.gallery3d.fileops;

import java.io.IOException;

/**
 * Creates one folder, one level at a time, under a media root.
 *
 * "One level at a time" is a deliberate Epic 1 limit: "a/b/c" is rejected
 * rather than silently creating three folders.
 */
public final class FolderCreator {

    public static final class Outcome {
        public final boolean created;
        public final String relativePath;
        public final String errorMessage;

        private Outcome(boolean created, String relativePath, String errorMessage) {
            this.created = created;
            this.relativePath = relativePath;
            this.errorMessage = errorMessage;
        }

        static Outcome ok(String relativePath) {
            return new Outcome(true, relativePath, null);
        }

        static Outcome error(String message) {
            return new Outcome(false, null, message);
        }
    }

    private FolderCreator() {
    }

    public static Outcome create(MediaStoreGateway gateway, String parentRelativePath,
            String name) {
        String nameError = RelativePaths.validateFolderName(name);
        if (nameError != null) return Outcome.error(nameError);

        String parent = RelativePaths.normalise(parentRelativePath);
        if (!RelativePaths.isUnderMediaRoot(parent)) {
            return Outcome.error("Folders can only be created under Pictures or DCIM");
        }

        String target = RelativePaths.join(parent, name);
        if (gateway.folderPathsUnder(target).contains(target)) {
            // Already has content: nothing to create, and nothing to clean up later.
            return Outcome.ok(target);
        }

        try {
            gateway.createPlaceholder(target);
            return Outcome.ok(target);
        } catch (PendingConsentException consent) {
            return Outcome.error("Permission is needed to create that folder");
        } catch (IOException failure) {
            return Outcome.error(failure.getMessage());
        }
    }
}
