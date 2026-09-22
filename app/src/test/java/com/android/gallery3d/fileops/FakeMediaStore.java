package com.android.gallery3d.fileops;

import android.net.Uri;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * In-memory MediaStoreGateway for JVM unit tests.
 *
 * Rows are keyed by a synthetic uri string "media://<id>" so that no android.net.Uri
 * parsing is needed: tests build uris with FakeMediaStore.uriOf(id).
 */
public class FakeMediaStore implements MediaStoreGateway {

    /** Row state, mutable so the fake can model an in-place update. */
    public static final class Row {
        public String displayName;
        public String relativePath;
        public long dateTakenMillis;
        public String volumeName;
        public String mimeType;
        public long sizeBytes;
        public boolean trashed;
        public boolean favourite;
        public long trashExpiryMillis;
        public long dateAddedSeconds;
        public String bytes;
    }

    private final Map<String, Row> mRows = new LinkedHashMap<String, Row>();
    private final Map<String, android.content.IntentSender> mConsentGates =
            new HashMap<String, android.content.IntentSender>();
    private final Set<String> mFailingUris = new HashSet<String>();
    private int mNextId = 1000;

    public static Uri uriOf(int id) {
        return Uri.parse("media://" + id);
    }

    /** Insert a row and return its uri. */
    public Uri addItem(String relativePath, String displayName, long dateTakenMillis) {
        return addItem(relativePath, displayName, dateTakenMillis, "external_primary");
    }

    public Uri addItem(String relativePath, String displayName, long dateTakenMillis,
            String volumeName) {
        int id = mNextId++;
        Row row = new Row();
        row.displayName = displayName;
        row.relativePath = RelativePaths.normalise(relativePath);
        row.dateTakenMillis = dateTakenMillis;
        row.volumeName = volumeName;
        row.mimeType = "image/jpeg";
        row.sizeBytes = 2048L;
        row.dateAddedSeconds = dateTakenMillis / 1000L;
        row.bytes = "bytes-of-" + displayName;
        mRows.put(uriOf(id).toString(), row);
        return uriOf(id);
    }

    /** Make the next write to this uri demand user consent. */
    public void requireConsentFor(Uri item, android.content.IntentSender sender) {
        mConsentGates.put(item.toString(), sender);
    }

    /** Clear a consent gate, as if the user had just approved it. */
    public void grantConsentFor(Uri item) {
        mConsentGates.remove(item.toString());
    }

    /** Make every write to this uri fail with IOException. */
    public void failWritesFor(Uri item) {
        mFailingUris.add(item.toString());
    }

    public Row row(Uri item) {
        return mRows.get(item.toString());
    }

    public int rowCount() {
        return mRows.size();
    }

    private void gate(Uri item) throws PendingConsentException, IOException {
        if (mConsentGates.containsKey(item.toString())) {
            throw new PendingConsentException(mConsentGates.get(item.toString()));
        }
        if (mFailingUris.contains(item.toString())) {
            throw new IOException("simulated write failure for " + item);
        }
    }

    @Override
    public MediaItemInfo query(Uri item) {
        Row row = mRows.get(item.toString());
        if (row == null) return null;
        return new MediaItemInfo(item, row.displayName, row.relativePath,
                row.dateTakenMillis, row.volumeName, row.mimeType, row.sizeBytes,
                row.trashed, row.favourite);
    }

    @Override
    public void updateLocation(Uri item, String relativePath, String displayName)
            throws PendingConsentException, IOException {
        gate(item);
        Row row = mRows.get(item.toString());
        if (row == null) throw new IOException("no such row " + item);
        row.relativePath = RelativePaths.normalise(relativePath);
        row.displayName = displayName;
    }

    @Override
    public Uri copyTo(Uri source, String relativePath, String displayName)
            throws PendingConsentException, IOException {
        gate(source);
        Row original = mRows.get(source.toString());
        if (original == null) throw new IOException("no such row " + source);
        int id = mNextId++;
        Row copy = new Row();
        copy.displayName = displayName;
        copy.relativePath = RelativePaths.normalise(relativePath);
        copy.dateTakenMillis = original.dateTakenMillis;
        copy.volumeName = volumeOfPath(relativePath, original.volumeName);
        copy.mimeType = original.mimeType;
        copy.sizeBytes = original.sizeBytes;
        copy.dateAddedSeconds = original.dateAddedSeconds;
        copy.bytes = original.bytes;
        mRows.put(uriOf(id).toString(), copy);
        return uriOf(id);
    }

    /** Tests set this to model an SD card; default keeps the source volume. */
    public String destinationVolume = null;

    private String volumeOfPath(String relativePath, String fallback) {
        return destinationVolume != null ? destinationVolume : fallback;
    }

    @Override
    public void setTrashed(Uri item, boolean trashed)
            throws PendingConsentException, IOException {
        gate(item);
        Row row = mRows.get(item.toString());
        if (row == null) throw new IOException("no such row " + item);
        row.trashed = trashed;
        row.trashExpiryMillis = trashed ? now + 30L * 24L * 60L * 60L * 1000L : 0L;
    }

    /** Tests advance this to simulate the passage of time. */
    public long now = 0L;

    @Override
    public void setFavourite(Uri item, boolean favourite)
            throws PendingConsentException, IOException {
        gate(item);
        Row row = mRows.get(item.toString());
        if (row == null) throw new IOException("no such row " + item);
        row.favourite = favourite;
    }

    @Override
    public void deletePermanently(Uri item) throws PendingConsentException, IOException {
        gate(item);
        if (mRows.remove(item.toString()) == null) {
            throw new IOException("no such row " + item);
        }
    }

    @Override
    public List<String> displayNamesIn(String relativePath) {
        String target = RelativePaths.normalise(relativePath);
        List<String> names = new ArrayList<String>();
        for (Row row : mRows.values()) {
            if (row.trashed) continue;
            if (row.relativePath.equals(target)) names.add(row.displayName);
        }
        return names;
    }

    @Override
    public List<String> folderPathsUnder(String relativePathPrefix) {
        String prefix = RelativePaths.normalise(relativePathPrefix);
        TreeSet<String> paths = new TreeSet<String>();
        for (Row row : mRows.values()) {
            if (row.trashed) continue;
            if (prefix.isEmpty() || row.relativePath.startsWith(prefix)) {
                paths.add(row.relativePath);
            }
        }
        return new ArrayList<String>(paths);
    }

    @Override
    public List<Uri> itemsUnder(String relativePathPrefix) {
        String prefix = RelativePaths.normalise(relativePathPrefix);
        List<Uri> uris = new ArrayList<Uri>();
        for (Map.Entry<String, Row> entry : mRows.entrySet()) {
            if (entry.getValue().trashed) continue;
            if (entry.getValue().relativePath.startsWith(prefix)) {
                uris.add(Uri.parse(entry.getKey()));
            }
        }
        return uris;
    }

    @Override
    public List<Uri> trashedOlderThan(long cutoffMillis) {
        List<Uri> uris = new ArrayList<Uri>();
        for (Map.Entry<String, Row> entry : mRows.entrySet()) {
            Row row = entry.getValue();
            if (row.trashed && row.trashExpiryMillis <= cutoffMillis) {
                uris.add(Uri.parse(entry.getKey()));
            }
        }
        return uris;
    }

    @Override
    public List<Uri> itemsAddedSince(String relativePath, long sinceEpochSeconds) {
        String target = RelativePaths.normalise(relativePath);
        List<Uri> uris = new ArrayList<Uri>();
        for (Map.Entry<String, Row> entry : mRows.entrySet()) {
            Row row = entry.getValue();
            if (row.trashed) continue;
            if (row.relativePath.equals(target) && row.dateAddedSeconds >= sinceEpochSeconds) {
                uris.add(Uri.parse(entry.getKey()));
            }
        }
        return Collections.unmodifiableList(uris);
    }

    @Override
    public Uri createPlaceholder(String relativePath)
            throws PendingConsentException, IOException {
        return addItem(relativePath, ".nomedia_placeholder", 0L);
    }
}
