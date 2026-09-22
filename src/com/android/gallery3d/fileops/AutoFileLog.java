package com.android.gallery3d.fileops;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * An append-only, capped record of every automatic move, so any single
 * auto-filed photo can be put back where it came from.
 *
 * Stored in SharedPreferences as a string set of tab-separated fields. A
 * database would be overkill for a capped 500-row log that is only ever read
 * whole.
 */
public final class AutoFileLog {

    public static final int MAX_ENTRIES = 500;

    private static final String PREFS_NAME = "auto_file_log";
    private static final String KEY_ENTRIES = "entries";
    private static final String SEPARATOR = "\t";

    public static final class Entry {
        public final String itemUri;
        public final String fromRelativePath;
        public final String toRelativePath;
        public final String displayName;
        public final long whenMillis;

        public Entry(String itemUri, String fromRelativePath, String toRelativePath,
                String displayName, long whenMillis) {
            this.itemUri = itemUri;
            this.fromRelativePath = fromRelativePath;
            this.toRelativePath = toRelativePath;
            this.displayName = displayName;
            this.whenMillis = whenMillis;
        }

        String serialise() {
            return itemUri + SEPARATOR + fromRelativePath + SEPARATOR + toRelativePath
                    + SEPARATOR + displayName + SEPARATOR + whenMillis;
        }

        static Entry parse(String line) {
            String[] parts = line.split(SEPARATOR, -1);
            if (parts.length != 5) return null;
            try {
                return new Entry(parts[0], parts[1], parts[2], parts[3],
                        Long.parseLong(parts[4]));
            } catch (NumberFormatException malformed) {
                return null;
            }
        }
    }

    private final List<Entry> mEntries;

    public AutoFileLog(List<Entry> existing) {
        mEntries = new ArrayList<Entry>(existing);
    }

    public static AutoFileLog load(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> lines = prefs.getStringSet(KEY_ENTRIES, Collections.<String>emptySet());
        List<Entry> entries = new ArrayList<Entry>();
        for (String line : lines) {
            Entry entry = Entry.parse(line);
            if (entry != null) entries.add(entry);
        }
        Collections.sort(entries, new java.util.Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return Long.compare(a.whenMillis, b.whenMillis);
            }
        });
        return new AutoFileLog(entries);
    }

    public void record(FileOpResult result, String destRelativePath, long whenMillis) {
        if (!result.isOk()) return;
        Uri resultUri = result.resultUri;
        mEntries.add(new Entry(
                resultUri == null ? result.sourceUri.toString() : resultUri.toString(),
                result.previousRelativePath,
                RelativePaths.normalise(destRelativePath),
                result.previousDisplayName,
                whenMillis));
        while (mEntries.size() > MAX_ENTRIES) {
            mEntries.remove(0);
        }
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(mEntries);
    }

    public Entry find(String itemUri) {
        for (Entry entry : mEntries) {
            if (entry.itemUri.equals(itemUri)) return entry;
        }
        return null;
    }

    public void remove(String itemUri) {
        for (int i = mEntries.size() - 1; i >= 0; i--) {
            if (mEntries.get(i).itemUri.equals(itemUri)) mEntries.remove(i);
        }
    }

    public void save(Context context) {
        Set<String> lines = new LinkedHashSet<String>();
        for (Entry entry : mEntries) {
            lines.add(entry.serialise());
        }
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_ENTRIES, lines).apply();
    }
}
