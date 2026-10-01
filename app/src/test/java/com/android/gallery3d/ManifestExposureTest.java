package com.android.gallery3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Pins what other apps can reach. Reads the shipped AndroidManifest.xml (the
 * one at the repo root that app/build.gradle.kts points at) so that exporting
 * a component, adding a web/file intent filter or requesting INTERNET or
 * CAMERA fails here instead of slipping through review.
 *
 * Changing the allow-list is fine when it is deliberate: say why in the
 * commit and in the comment next to the entry.
 */
public class ManifestExposureTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    /** Every component other apps may start, and why. */
    private static final List<String> EXPORTED_ALLOW_LIST = Arrays.asList(
            // Launcher icon, Pick a photo (GET_CONTENT/PICK), View an image.
            "com.android.gallery3d.app.GalleryActivity",
            // Play a local video handed over by another app.
            "com.android.gallery3d.app.MovieActivity",
            // Old launcher shortcuts (MAIN only).
            "com.android.gallery3d.app.Gallery",
            "com.cooliris.media.Gallery",
            "com.android.camera.CameraLauncher",
            // "Wallpapers" entry in the launcher's SET_WALLPAPER chooser.
            "com.android.gallery3d.app.Wallpaper",
            // "Edit with Gallery" (EDIT) for other apps' images.
            "com.android.gallery3d.filtershow.FilterShowActivity");

    private static final String[] COMPONENT_TAGS = {
            "activity", "activity-alias", "service", "receiver", "provider"};

    private static Document sManifest;

    @BeforeClass
    public static void readManifest() throws Exception {
        File manifest = findShippedManifest();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        sManifest = factory.newDocumentBuilder().parse(manifest);
    }

    private static File findShippedManifest() {
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (dir != null) {
            File manifest = new File(dir, "AndroidManifest.xml");
            if (manifest.isFile() && new File(dir, "settings.gradle.kts").isFile()) {
                return manifest;
            }
            dir = dir.getParentFile();
        }
        fail("could not find the repo-root AndroidManifest.xml from "
                + System.getProperty("user.dir"));
        return null;
    }

    private static List<Element> elements(String tag) {
        NodeList nodes = sManifest.getElementsByTagName(tag);
        List<Element> out = new ArrayList<Element>();
        for (int i = 0; i < nodes.getLength(); i++) out.add((Element) nodes.item(i));
        return out;
    }

    private static String attr(Element e, String name) {
        return e.hasAttributeNS(ANDROID, name) ? e.getAttributeNS(ANDROID, name) : null;
    }

    private static boolean hasIntentFilter(Element component) {
        return component.getElementsByTagName("intent-filter").getLength() > 0;
    }

    /**
     * The platform rule: an explicit android:exported wins; otherwise a
     * component is exported when it has an intent filter (providers default
     * to not exported for targetSdk 17+).
     */
    private static boolean isExported(Element component) {
        String exported = attr(component, "exported");
        if (exported != null) return Boolean.parseBoolean(exported);
        if ("provider".equals(component.getTagName())) return false;
        return hasIntentFilter(component);
    }

    @Test
    public void onlyAllowListedComponentsAreExported() {
        TreeSet<String> exported = new TreeSet<String>();
        for (String tag : COMPONENT_TAGS) {
            for (Element component : elements(tag)) {
                if (isExported(component)) exported.add(attr(component, "name"));
            }
        }
        assertEquals("exported components changed; update the allow-list only on purpose",
                new TreeSet<String>(EXPORTED_ALLOW_LIST), exported);
    }

    @Test
    public void everyComponentWithAFilterSaysWhetherItIsExported() {
        // Required for targetSdk 31+, and it keeps the intent explicit.
        for (String tag : COMPONENT_TAGS) {
            for (Element component : elements(tag)) {
                if (hasIntentFilter(component)) {
                    assertNotNull(attr(component, "name") + " needs android:exported",
                            attr(component, "exported"));
                }
            }
        }
    }

    @Test
    public void everyProviderIsUnexported() {
        for (Element provider : elements("provider")) {
            assertEquals(attr(provider, "name") + " must declare exported=false",
                    "false", attr(provider, "exported"));
        }
    }

    @Test
    public void noWebOrFileIntentFilters() {
        for (Element category : elements("category")) {
            assertFalse("BROWSABLE lets web pages launch the app",
                    "android.intent.category.BROWSABLE".equals(attr(category, "name")));
        }
        for (Element data : elements("data")) {
            String scheme = attr(data, "scheme");
            if (scheme == null) continue;
            assertTrue("intent filters may only take content: (or no) uris, found "
                    + scheme, scheme.isEmpty() || "content".equals(scheme));
        }
    }

    @Test
    public void noNetworkOrCameraPermission() {
        for (Element permission : elements("uses-permission")) {
            String name = attr(permission, "name");
            assertFalse("the app must not request INTERNET",
                    "android.permission.INTERNET".equals(name));
            assertFalse("the app opens no camera itself",
                    "android.permission.CAMERA".equals(name));
        }
    }

    @Test
    public void declaredPermissionsAreSignatureLevel() {
        List<Element> permissions = elements("permission");
        assertFalse("SharedImageProvider's permissions must be declared", permissions.isEmpty());
        for (Element permission : permissions) {
            assertEquals(attr(permission, "name"), "signature",
                    attr(permission, "protectionLevel"));
        }
    }

    @Test
    public void appDataStaysOnThePhone() {
        Element application = elements("application").get(0);
        assertEquals("false", attr(application, "allowBackup"));
        assertEquals("@xml/data_extraction_rules", attr(application, "dataExtractionRules"));
    }

    @Test
    public void extractionRulesExcludeEveryDomainFromBothPaths() throws Exception {
        File rules = new File(findShippedManifest().getParentFile(),
                "res/xml/data_extraction_rules.xml");
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(rules);
        List<String> domains = Arrays.asList("root", "file", "database", "sharedpref",
                "external", "device_root", "device_file", "device_database",
                "device_sharedpref");
        for (String section : new String[] {"cloud-backup", "device-transfer"}) {
            NodeList sections = doc.getElementsByTagName(section);
            assertEquals(section, 1, sections.getLength());
            Element el = (Element) sections.item(0);
            assertEquals(section + " must not include anything", 0,
                    el.getElementsByTagName("include").getLength());
            TreeSet<String> excluded = new TreeSet<String>();
            NodeList excludes = el.getElementsByTagName("exclude");
            for (int i = 0; i < excludes.getLength(); i++) {
                Element ex = (Element) excludes.item(i);
                if (".".equals(ex.getAttribute("path"))) excluded.add(ex.getAttribute("domain"));
            }
            assertEquals(section, new TreeSet<String>(domains), excluded);
        }
    }
}
