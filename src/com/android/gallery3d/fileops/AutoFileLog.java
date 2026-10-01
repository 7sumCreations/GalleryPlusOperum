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
    /**
     * Photos the user put back with Undo. Auto-file must never file these
     * again, or Undo would be reversed by the very next run: a put-back photo
     * keeps its DATE_ADDED, so it still looks "new" to the scheduler.
     */
    private static final String KEY_UNDONE = "undone";
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
    /** Uri -> when it was undone; insertion-ordered so the oldest drop first. */
    private final java.util.LinkedHashMap<String, Long> mUndone =
            new java.util.LinkedHashMap<String, Long>();

    public AutoFileLog(List<Entry> existing) {
        mEntries = new ArrayList<Entry>(existing);
    }

    /** Never throws: a log that cannot be read is treated as empty. */
    public static AutoFileLog load(Context context) {
        try {
            return loadOrThrow(context);
        } catch (RuntimeException unreadable) {
            android.util.Log.w("AutoFileLog", "auto-file log unreadable", unreadable);
            return new AutoFileLog(Collections.<Entry>emptyList());
        }
    }

    private static AutoFileLog loadOrThrow(Context context) {
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
        AutoFileLog log = new AutoFileLog(entries);
        List<String[]> undone = new ArrayList<String[]>();
        for (String line : prefs.getStringSet(KEY_UNDONE, Collections.<String>emptySet())) {
            String[] parts = line.split(SEPARATOR, -1);
            if (parts.length == 2) undone.add(parts);
        }
        Collections.sort(undone, new java.util.Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                return Long.compare(parseLong(a[1]), parseLong(b[1]));
            }
        });
        for (String[] parts : undone) {
            log.markUndone(parts[0], parseLong(parts[1]));
        }
        return log;
    }

    private static long parseLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException malformed) {
            return 0L;
        }
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

    /**
     * Remember that the user put this photo back, so it is never auto-filed
     * again. Capped like the log itself; the oldest are forgotten first.
     */
    public void markUndone(String itemUri, long whenMillis) {
        mUndone.remove(itemUri);
        mUndone.put(itemUri, whenMillis);
        java.util.Iterator<String> oldest = mUndone.keySet().iterator();
        while (mUndone.size() > MAX_ENTRIES && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    public boolean isUndone(String itemUri) {
        return mUndone.containsKey(itemUri);
    }

    /** Uris auto-file must leave alone because the user undid their filing. */
    public Set<String> undoneUris() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(mUndone.keySet()));
    }

    /** Never throws: failing to persist the log must not abort a run or an Undo. */
    public void save(Context context) {
        try {
            Set<String> lines = new LinkedHashSet<String>();
            for (Entry entry : mEntries) {
                lines.add(entry.serialise());
            }
            Set<String> undone = new LinkedHashSet<String>();
            for (java.util.Map.Entry<String, Long> item : mUndone.entrySet()) {
                undone.add(item.getKey() + SEPARATOR + item.getValue());
            }
            context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putStringSet(KEY_ENTRIES, lines)
                    .putStringSet(KEY_UNDONE, undone).apply();
        } catch (RuntimeException couldNotPersist) {
            android.util.Log.w("AutoFileLog", "could not save auto-file log", couldNotPersist);
        }
    }
}
