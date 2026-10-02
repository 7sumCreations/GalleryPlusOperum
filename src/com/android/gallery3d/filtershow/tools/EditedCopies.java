package com.android.gallery3d.filtershow.tools;

import com.android.gallery3d.fileops.RelativePaths;
import com.android.gallery3d.fileops.UniqueNames;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Where an edited copy goes and what it is called. Pure string logic, no
 * Android types, so it is covered by JVM tests.
 *
 * The editor never modifies the photo it was given: every save is a new
 * image. It goes next to the source when the source sits in a folder the
 * Images collection accepts (DCIM/ or Pictures/), otherwise into
 * {@link #FALLBACK_FOLDER}. It is named "<source name>_edited.<ext>", with
 * the app's usual "keep both" suffix (" (1)", " (2)", ...) on a clash.
 */
public final class EditedCopies {

    /** Used when the source is outside DCIM/ and Pictures/, or not in MediaStore. */
    public static final String FALLBACK_FOLDER = "Pictures/Edited/";

    static final String EDITED_SUFFIX = "_edited";

    /** "_edited", "_edited (2)" at the end of a stem: an earlier copy being re-edited. */
    private static final Pattern EARLIER_SUFFIX =
            Pattern.compile("(?:_edited(?: \\(\\d+\\))?)+$");

    private EditedCopies() {
    }

    /** The RELATIVE_PATH the copy is inserted at. */
    public static String targetFolder(String sourceRelativePath) {
        String normalised = RelativePaths.normalise(sourceRelativePath);
        if (RelativePaths.isUnderMediaRoot(normalised) && !hasHiddenSegment(normalised)) {
            return normalised;
        }
        return FALLBACK_FOLDER;
    }

    /**
     * The DISPLAY_NAME for the copy.
     *
     * @param sourceDisplayName the source's DISPLAY_NAME, or null when unknown
     * @param extension the copy's extension without the dot ("jpg", "png")
     * @param taken display names already in the target folder
     * @param nowMillis used for a fallback name when the source has none
     */
    public static String copyName(String sourceDisplayName, String extension,
            Collection<String> taken, long nowMillis) {
        String stem = stemOf(sourceDisplayName);
        stem = EARLIER_SUFFIX.matcher(stem).replaceFirst("");
        stem = stem.trim();
        if (stem.isEmpty() || stem.startsWith(".")) {
            stem = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date(nowMillis));
        }
        String desired = stem + EDITED_SUFFIX + "." + extension;
        return UniqueNames.freeName(taken, desired);
    }

    /** "IMG_1.jpg" -> "IMG_1"; null -> "". */
    private static String stemOf(String displayName) {
        if (displayName == null) return "";
        String name = displayName.trim();
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name;
    }

    /** A copy in a folder like "DCIM/.aux/" would be invisible, so avoid it. */
    private static boolean hasHiddenSegment(String normalised) {
        for (String segment : normalised.split("/")) {
            if (segment.startsWith(".")) return true;
        }
        return false;
    }
}
