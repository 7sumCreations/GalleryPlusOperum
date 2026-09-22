package com.android.gallery3d.fileops;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Epic 1's collision policy is "keep both": never overwrite, never skip.
 * A clash yields "name (1).ext", "name (2).ext", and so on.
 */
public final class UniqueNames {

    private UniqueNames() {
    }

    public static String freeName(Collection<String> taken, String desired) {
        Set<String> lowered = new HashSet<String>();
        for (String name : taken) {
            if (name != null) lowered.add(name.toLowerCase(Locale.US));
        }
        if (!lowered.contains(desired.toLowerCase(Locale.US))) {
            return desired;
        }
        String stem = stemOf(desired);
        String extension = extensionOf(desired);
        for (int n = 1; ; n++) {
            String candidate = stem + " (" + n + ")" + extension;
            if (!lowered.contains(candidate.toLowerCase(Locale.US))) {
                return candidate;
            }
        }
    }

    /** "IMG_0001.jpg" -> "IMG_0001"; ".hidden" -> ".hidden"; "photo" -> "photo". */
    private static String stemOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) return fileName;
        return fileName.substring(0, dot);
    }

    /** "IMG_0001.jpg" -> ".jpg"; ".hidden" -> ""; "photo" -> "". */
    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) return "";
        return fileName.substring(dot);
    }
}
