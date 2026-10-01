package com.android.gallery3d.fileops;

import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/**
 * Works out which items in the watched folder are due to be auto-filed, and
 * where each one goes. Pure logic: no Android context, no side effects, so the
 * whole rule is unit-testable.
 */
public final class AutoFileScheduler {

    /** One item and the folder it should end up in. */
    public static final class Plan {
        public final Uri item;
        public final String destRelativePath;

        Plan(Uri item, String destRelativePath) {
            this.item = item;
            this.destRelativePath = destRelativePath;
        }
    }

    private final MediaStoreGateway mGateway;
    private final AutoFileSettings mSettings;
    private final TimeZone mZone;
    private final Set<String> mNeverAgain;

    public AutoFileScheduler(MediaStoreGateway gateway, AutoFileSettings settings,
            TimeZone zone) {
        this(gateway, settings, zone, Collections.<String>emptySet());
    }

    /**
     * @param neverAgain uris of photos the user put back with Undo; they are
     *        never planned again (AutoFileLog.undoneUris()).
     */
    public AutoFileScheduler(MediaStoreGateway gateway, AutoFileSettings settings,
            TimeZone zone, Set<String> neverAgain) {
        mGateway = gateway;
        mSettings = settings;
        mZone = zone;
        mNeverAgain = neverAgain == null ? Collections.<String>emptySet() : neverAgain;
    }

    public List<Plan> planFor(long nowMillis) {
        List<Plan> plans = new ArrayList<Plan>();
        if (!mSettings.isEnabled()) return plans;

        // Only photos added since the rule was switched on are candidates: the
        // camera roll that was already there is never touched. A missing stamp
        // is initialised to now (and persisted), so on that run nothing is due.
        long nowSeconds = nowMillis / 1000L;
        long enabledSince = mSettings.ensureEnabledSinceSeconds(nowMillis);
        if (enabledSince >= nowSeconds) return plans;

        String watched = mSettings.watchedFolder();
        long cutoffSeconds = (nowMillis - mSettings.delayMillis()) / 1000L;

        for (Uri item : mGateway.itemsAddedSince(watched, enabledSince)) {
            // The user undid this one: their decision stands.
            if (mNeverAgain.contains(item.toString())) continue;
            MediaItemInfo info = mGateway.query(item);
            if (info == null) continue;
            // Only items in the watched folder itself, not its sub-folders.
            if (!info.relativePath.equals(watched)) continue;
            // Never touch the folder placeholder F-017 writes.
            if (FileOpEngine.PLACEHOLDER_NAME.equals(info.displayName)) continue;
            // A photo with no date taken has nowhere to go: leave it where it is.
            if (!info.hasDateTaken()) continue;

            String destination = RelativePaths.forDate(info.dateTakenMillis, mZone);
            // Never move anything twice.
            if (info.relativePath.equals(destination)) continue;

            if (addedAtOrBefore(item, cutoffSeconds)) {
                plans.add(new Plan(item, destination));
            }
        }
        return plans;
    }

    /**
     * True when the item has been sitting in the watched folder for at least the
     * configured delay. Implemented by asking the gateway for the items added
     * since the cutoff and checking this item is NOT among them.
     */
    private boolean addedAtOrBefore(Uri item, long cutoffSeconds) {
        return !mGateway.itemsAddedSince(mSettings.watchedFolder(), cutoffSeconds + 1)
                .contains(item);
    }
}
