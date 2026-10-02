package com.android.gallery3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * The app must never show up as "Gallery", next to the stock Gallery, in the
 * launcher, the share sheet or an "Open with" / "Edit with" chooser. Pins the
 * name, that no translation brings "Gallery" back, that the debug build is
 * told apart, and that every label another app can see is the app name.
 */
public class AppNameTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String APP_NAME = "@string/app_name";

    private static File repoRoot() {
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (dir != null) {
            if (new File(dir, "AndroidManifest.xml").isFile()
                    && new File(dir, "settings.gradle.kts").isFile()) {
                return dir;
            }
            dir = dir.getParentFile();
        }
        fail("could not find the repo root from " + System.getProperty("user.dir"));
        return null;
    }

    private static Document parse(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(file);
    }

    private static Element appNameIn(File strings) throws Exception {
        NodeList nodes = parse(strings).getElementsByTagName("string");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            if ("app_name".equals(e.getAttribute("name"))) return e;
        }
        return null;
    }

    @Test
    public void appNameIsGalleryPlusOperumAndUntranslatable() throws Exception {
        Element name = appNameIn(new File(repoRoot(), "res/values/strings.xml"));
        assertNotNull(name);
        assertEquals("Gallery Plus Operum", name.getTextContent());
        assertEquals("false", name.getAttribute("translatable"));
    }

    @Test
    public void noTranslationOverridesTheName() throws Exception {
        File[] dirs = new File(repoRoot(), "res").listFiles();
        assertNotNull(dirs);
        for (File dir : dirs) {
            if (!dir.getName().startsWith("values-")) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (!f.getName().endsWith(".xml")) continue;
                String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                assertFalse("translated app_name would show the old name: " + f,
                        text.contains("name=\"app_name\""));
            }
        }
    }

    @Test
    public void debugBuildIsToldApart() throws Exception {
        Element name = appNameIn(new File(repoRoot(), "app/src/debug/res/values/strings.xml"));
        assertNotNull(name);
        assertEquals("Gallery Plus Operum (debug)", name.getTextContent());
    }

    @Test
    public void everyLabelOtherAppsSeeIsTheAppName() throws Exception {
        Document manifest = parse(new File(repoRoot(), "AndroidManifest.xml"));
        Element application = (Element) manifest.getElementsByTagName("application").item(0);
        assertEquals(APP_NAME, application.getAttributeNS(ANDROID, "label"));

        int checked = 0;
        for (String tag : new String[] {"activity", "activity-alias"}) {
            NodeList nodes = manifest.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element e = (Element) nodes.item(i);
                String component = e.getAttributeNS(ANDROID, "name");
                if (!"true".equals(e.getAttributeNS(ANDROID, "exported"))) continue;
                // The camera trampoline hands over to the camera app; it is
                // not this app's name.
                if (component.startsWith("com.android.camera.")) continue;
                String label = e.getAttributeNS(ANDROID, "label");
                assertTrue(component + " is labelled " + label,
                        label.isEmpty() || APP_NAME.equals(label));
                NodeList filters = e.getElementsByTagName("intent-filter");
                for (int j = 0; j < filters.getLength(); j++) {
                    String filterLabel = ((Element) filters.item(j)).getAttributeNS(ANDROID, "label");
                    assertTrue(component + " intent filter is labelled " + filterLabel,
                            filterLabel.isEmpty() || APP_NAME.equals(filterLabel));
                }
                checked++;
            }
        }
        assertTrue("expected the exported gallery components", checked >= 5);
    }
}
