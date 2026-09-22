package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.TimeZone;

public class RelativePathsTest {

    @Test
    public void normaliseAddsOneTrailingSlash() {
        assertEquals("Pictures/Test/", RelativePaths.normalise("Pictures/Test"));
        assertEquals("Pictures/Test/", RelativePaths.normalise("Pictures/Test/"));
        assertEquals("Pictures/Test/", RelativePaths.normalise("/Pictures//Test//"));
        assertEquals("Pictures/Test/", RelativePaths.normalise("  Pictures/Test  "));
    }

    @Test
    public void normaliseOfEmptyIsEmpty() {
        assertEquals("", RelativePaths.normalise(null));
        assertEquals("", RelativePaths.normalise("   "));
        assertEquals("", RelativePaths.normalise("/"));
    }

    @Test
    public void onlyPicturesAndDcimAreMediaRoots() {
        assertTrue(RelativePaths.isUnderMediaRoot("Pictures/Test/"));
        assertTrue(RelativePaths.isUnderMediaRoot("DCIM/Camera/"));
        assertTrue(RelativePaths.isUnderMediaRoot("Pictures/"));
        assertFalse(RelativePaths.isUnderMediaRoot("Download/Test/"));
        assertFalse(RelativePaths.isUnderMediaRoot("Music/"));
        assertFalse(RelativePaths.isUnderMediaRoot(""));
    }

    @Test
    public void parentOfStripsTheLastSegment() {
        assertEquals("Pictures/Trips/", RelativePaths.parentOf("Pictures/Trips/Lisbon/"));
        assertEquals("Pictures/", RelativePaths.parentOf("Pictures/Trips/"));
        assertEquals("", RelativePaths.parentOf("Pictures/"));
        assertEquals("", RelativePaths.parentOf(""));
    }

    @Test
    public void lastSegmentIsTheFolderName() {
        assertEquals("Lisbon", RelativePaths.lastSegment("Pictures/Trips/Lisbon/"));
        assertEquals("Pictures", RelativePaths.lastSegment("Pictures/"));
        assertEquals("", RelativePaths.lastSegment(""));
    }

    @Test
    public void joinNormalisesBothSides() {
        assertEquals("Pictures/Trips/Lisbon/", RelativePaths.join("Pictures/Trips", "Lisbon"));
        assertEquals("Pictures/Trips/Lisbon/", RelativePaths.join("Pictures/Trips/", "/Lisbon/"));
        assertEquals("Lisbon/", RelativePaths.join("", "Lisbon"));
    }

    @Test
    public void forDateBuildsPicturesYearMonth() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        // 2026-09-22T10:00:00Z
        long instant = 1790157600000L;
        assertEquals("Pictures/2026/09/", RelativePaths.forDate(instant, utc));
    }

    @Test
    public void forDateZeroPadsSingleDigitMonths() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        // 2026-01-05T00:00:00Z
        long instant = 1767571200000L;
        assertEquals("Pictures/2026/01/", RelativePaths.forDate(instant, utc));
    }

    @Test
    public void validateFolderNameAcceptsOrdinaryNames() {
        assertNull(RelativePaths.validateFolderName("Trip 2026"));
        assertNull(RelativePaths.validateFolderName("Lisbon"));
        assertNull(RelativePaths.validateFolderName("holiday-01"));
    }

    @Test
    public void validateFolderNameRejectsBadNames() {
        assertNotNull(RelativePaths.validateFolderName(null));
        assertNotNull(RelativePaths.validateFolderName(""));
        assertNotNull(RelativePaths.validateFolderName("   "));
        assertNotNull(RelativePaths.validateFolderName("."));
        assertNotNull(RelativePaths.validateFolderName(".."));
        assertNotNull(RelativePaths.validateFolderName(".hidden"));
        assertNotNull(RelativePaths.validateFolderName("a/b"));
        assertNotNull(RelativePaths.validateFolderName("bad:name"));
        assertNotNull(RelativePaths.validateFolderName("trailing "));
    }
}
