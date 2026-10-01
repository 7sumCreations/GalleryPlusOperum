package com.android.gallery3d.help;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * HELP.md at the repo root is the only copy of the help text. The build copies
 * it into a generated assets directory for each variant (app/build.gradle.kts),
 * and Gradle hands this test both paths. If the copy step ever stops running,
 * points somewhere else, or someone checks in a second copy, this fails.
 */
public class HelpAssetSourceTest {

    private static File property(String name) {
        String value = System.getProperty(name);
        assertNotNull(name + " is not set: run this test through Gradle"
                + " (:app:testDebugUnitTest), which wires the help asset task", value);
        return new File(value);
    }

    @Test
    public void generatedAssetIsByteForByteHelpMd() throws IOException {
        File source = property("gallery.help.source");
        File generatedDir = property("gallery.help.generatedDir");
        File generated = new File(generatedDir, HelpActivity.ASSET_NAME);

        assertEquals("HELP.md", source.getName());
        assertTrue("missing " + source, source.isFile());
        assertTrue("the build did not generate " + generated, generated.isFile());
        assertArrayEquals("in-app help differs from HELP.md",
                Files.readAllBytes(source.toPath()), Files.readAllBytes(generated.toPath()));
    }

    @Test
    public void generatedDirectoryHoldsOnlyTheHelpAsset() {
        File generatedDir = property("gallery.help.generatedDir");
        String[] names = generatedDir.list();
        assertNotNull(names);
        assertArrayEquals(new String[] {HelpActivity.ASSET_NAME}, names);
    }

    @Test
    public void noSecondCopyIsCheckedIn() {
        File repoRoot = property("gallery.help.source").getParentFile();
        // The app's own source sets: res/, src/, src_pd/ and any assets dir.
        for (String dir : new String[] {"assets", "res/raw", "app/src/main/assets"}) {
            assertFalse("a checked-in copy of the help would drift from HELP.md: " + dir,
                    new File(new File(repoRoot, dir), HelpActivity.ASSET_NAME).exists());
        }
    }

    @Test
    public void helpMdStartsWithATitleAndReadsAsUtf8() throws IOException {
        File source = property("gallery.help.source");
        String text;
        try (InputStream in = new FileInputStream(source)) {
            text = HelpActivity.readUtf8(in);
        }
        assertEquals(new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8),
                text);
        assertTrue(text.startsWith("# "));
        assertFalse("the in-app help must render offline: no WebView-only markup",
                text.contains("<a ") || text.contains("<img"));
        assertFalse(HelpMarkdown.parse(text).isEmpty());
    }
}
