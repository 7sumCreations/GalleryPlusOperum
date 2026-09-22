package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BucketPathResolverTest {

    private FakeMediaStore store;

    @Before
    public void setUp() {
        store = new FakeMediaStore();
    }

    @Test
    public void resolveFindsTheFolderWhoseHashMatches() {
        store.addItem("Pictures/Lisbon", "a.jpg", 1L);
        store.addItem("Pictures/Other", "b.jpg", 2L);
        int bucketId = BucketPathResolver.bucketIdOf("Pictures/Lisbon/");

        assertEquals("Pictures/Lisbon/", BucketPathResolver.resolve(store, bucketId));
    }

    @Test
    public void resolveReturnsEmptyForAnUnknownBucket() {
        store.addItem("Pictures/Lisbon", "a.jpg", 1L);

        assertEquals("", BucketPathResolver.resolve(store, 12345678));
    }

    @Test
    public void bucketIdIsStableAndCaseInsensitive() {
        assertEquals(BucketPathResolver.bucketIdOf("Pictures/Lisbon/"),
                BucketPathResolver.bucketIdOf("pictures/lisbon/"));
        assertEquals(BucketPathResolver.bucketIdOf("Pictures/Lisbon"),
                BucketPathResolver.bucketIdOf("Pictures/Lisbon/"));
    }

    @Test
    public void differentFoldersGetDifferentBucketIds() {
        int lisbon = BucketPathResolver.bucketIdOf("Pictures/Lisbon/");
        int porto = BucketPathResolver.bucketIdOf("Pictures/Porto/");

        org.junit.Assert.assertNotEquals(lisbon, porto);
    }
}
