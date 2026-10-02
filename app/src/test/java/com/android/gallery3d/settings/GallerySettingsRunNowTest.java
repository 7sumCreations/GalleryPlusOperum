package com.android.gallery3d.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * The "File now" Toast texts and the Settings entry. The JVM tests run without
 * the app's merged resources, so this reads res/values/strings.xml and
 * res/xml/auto_file_preferences.xml straight from the repo.
 */
public class GallerySettingsRunNowTest {

    private static File sRepo;
    private static final Map<String, String> STRINGS = new HashMap<String, String>();
    private static final Map<String, Map<String, String>> PLURALS =
            new HashMap<String, Map<String, String>>();

    @BeforeClass
    public static void read() throws Exception {
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (dir != null && !new File(dir, "settings.gradle.kts").isFile()) {
            dir = dir.getParentFile();
        }
        if (dir == null) fail("could not find the repo root");
        sRepo = dir;
        Document doc = parse(new File(sRepo, "res/values/strings.xml"));
        NodeList strings = doc.getElementsByTagName("string");
        for (int i = 0; i < strings.getLength(); i++) {
            Element e = (Element) strings.item(i);
            STRINGS.put(e.getAttribute("name"), e.getTextContent());
        }
        NodeList plurals = doc.getElementsByTagName("plurals");
        for (int i = 0; i < plurals.getLength(); i++) {
            Element e = (Element) plurals.item(i);
            Map<String, String> items = new HashMap<String, String>();
            NodeList children = e.getElementsByTagName("item");
            for (int j = 0; j < children.getLength(); j++) {
                Element item = (Element) children.item(j);
                items.put(item.getAttribute("quantity"), item.getTextContent());
            }
            PLURALS.put(e.getAttribute("name"), items);
        }
    }

    private static Document parse(File f) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(f);
    }

    private static String other(String plural) {
        Map<String, String> items = PLURALS.get(plural);
        assertNotNull(plural, items);
        assertTrue(plural + " needs a 'one' form", items.containsKey("one"));
        return items.get("other");
    }

    @Test
    public void filedSaysHowMany() {
        assertEquals("Filed %1$d photos", other("auto_file_run_now_filed"));
    }

    @Test
    public void needPermissionPointsAtManageMedia() {
        String text = other("auto_file_run_now_need_permission");
        assertTrue(text, text.startsWith("%1$d photos need permission"));
        assertTrue(text, text.contains(STRINGS.get("media_access_manage_title")));
    }

    @Test
    public void nothingYetNamesTheDelay() {
        assertEquals("Nothing to file yet. Photos are filed once they\\'re older than"
                + " %1$d minutes.", other("auto_file_run_now_nothing"));
    }

    @Test
    public void offAndFailedHaveText() {
        assertEquals("Auto-file is off", STRINGS.get("auto_file_run_now_off"));
        assertNotNull(STRINGS.get("auto_file_run_now_failed"));
        assertEquals("File now", STRINGS.get("auto_file_run_now_title"));
    }

    @Test
    public void fileNowSitsInTheAutoFileSectionAndFollowsTheSwitch() throws Exception {
        Document prefs = parse(new File(sRepo, "res/xml/auto_file_preferences.xml"));
        NodeList nodes = prefs.getElementsByTagName("Preference");
        String ns = "http://schemas.android.com/apk/res/android";
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            if (!GallerySettings.KEY_AUTO_FILE_RUN_NOW.equals(e.getAttributeNS(ns, "key"))) {
                continue;
            }
            assertEquals("auto_file_enabled", e.getAttributeNS(ns, "dependency"));
            assertEquals("false", e.getAttributeNS(ns, "persistent"));
            assertEquals(GallerySettings.KEY_AUTO_FILE_CATEGORY,
                    ((Element) e.getParentNode()).getAttributeNS(ns, "key"));
            return;
        }
        fail("no File now entry in auto_file_preferences.xml");
    }
}
