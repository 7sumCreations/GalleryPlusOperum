package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class UniqueNamesTest {

    @Test
    public void afreeNameIsReturnedUnchanged() {
        assertEquals("IMG_0001.jpg",
                UniqueNames.freeName(Collections.<String>emptyList(), "IMG_0001.jpg"));
    }

    @Test
    public void firstCollisionGetsSuffixOne() {
        assertEquals("IMG_0001 (1).jpg",
                UniqueNames.freeName(Arrays.asList("IMG_0001.jpg"), "IMG_0001.jpg"));
    }

    @Test
    public void suffixIncrementsUntilFree() {
        assertEquals("IMG_0001 (3).jpg",
                UniqueNames.freeName(
                        Arrays.asList("IMG_0001.jpg", "IMG_0001 (1).jpg", "IMG_0001 (2).jpg"),
                        "IMG_0001.jpg"));
    }

    @Test
    public void extensionlessNamesGetTheSuffixAtTheEnd() {
        assertEquals("photo (1)",
                UniqueNames.freeName(Arrays.asList("photo"), "photo"));
    }

    @Test
    public void dotFilesAreNotTreatedAsExtensions() {
        assertEquals(".hidden (1)",
                UniqueNames.freeName(Arrays.asList(".hidden"), ".hidden"));
    }

    @Test
    public void comparisonIsCaseInsensitive() {
        assertEquals("IMG_0001 (1).jpg",
                UniqueNames.freeName(Arrays.asList("img_0001.JPG"), "IMG_0001.jpg"));
    }
}
