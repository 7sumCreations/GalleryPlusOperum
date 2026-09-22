package com.android.gallery3d.fileops;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Pure string arithmetic on MediaStore RELATIVE_PATH values.
 *
 * The canonical form used throughout the fileops package is:
 * no leading slash, exactly one trailing slash, no empty segments.
 * "Pictures/Trips/Lisbon/" is canonical; "/Pictures//Trips/Lisbon" is not.
 */
public final class RelativePaths {

    /** Media roots this app is allowed to create folders under. */
    private static final String[] MEDIA_ROOTS = {"Pictures", "DCIM"};

    /** Characters Android's media provider rejects in a path segment. */
    private static final String ILLEGAL_CHARS = "/\\:*?\"<>|\u0000";

    private static final int MAX_NAME_LENGTH = 127;

    private RelativePaths() {
    }

    public static String normalise(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder();
        for (String segment : raw.trim().split("/")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) continue;
            out.append(trimmed).append('/');
        }
        return out.toString();
    }

    public static boolean isUnderMediaRoot(String relativePath) {
        String normalised = normalise(relativePath);
        if (normalised.isEmpty()) return false;
        int slash = normalised.indexOf('/');
        String root = normalised.substring(0, slash);
        for (String allowed : MEDIA_ROOTS) {
            if (allowed.equals(root)) return true;
        }
        return false;
    }

    public static String parentOf(String relativePath) {
        String normalised = normalise(relativePath);
        if (normalised.isEmpty()) return "";
        String withoutTrailing = normalised.substring(0, normalised.length() - 1);
        int lastSlash = withoutTrailing.lastIndexOf('/');
        if (lastSlash < 0) return "";
        return withoutTrailing.substring(0, lastSlash + 1);
    }

    public static String lastSegment(String relativePath) {
        String normalised = normalise(relativePath);
        if (normalised.isEmpty()) return "";
        String withoutTrailing = normalised.substring(0, normalised.length() - 1);
        int lastSlash = withoutTrailing.lastIndexOf('/');
        return withoutTrailing.substring(lastSlash + 1);
    }

    public static String join(String parent, String child) {
        return normalise(normalise(parent) + normalise(child));
    }

    public static String forDate(long dateTakenMillis, TimeZone zone) {
        Calendar calendar = Calendar.getInstance(zone, Locale.US);
        calendar.setTimeInMillis(dateTakenMillis);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        return String.format(Locale.US, "Pictures/%04d/%02d/", year, month);
    }

    /** @return null when the name is usable, otherwise a human-readable reason. */
    public static String validateFolderName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Folder name cannot be empty";
        }
        if (!name.equals(name.trim())) {
            return "Folder name cannot start or end with a space";
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return "Folder name is too long";
        }
        if (name.equals(".") || name.equals("..")) {
            return "Folder name cannot be \".\" or \"..\"";
        }
        if (name.startsWith(".")) {
            return "Folder name cannot start with a dot";
        }
        for (int i = 0; i < name.length(); i++) {
            if (ILLEGAL_CHARS.indexOf(name.charAt(i)) >= 0) {
                return "Folder name cannot contain " + name.charAt(i);
            }
        }
        return null;
    }
}
