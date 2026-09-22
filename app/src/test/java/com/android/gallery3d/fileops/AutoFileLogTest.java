package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
public class AutoFileLogTest {

    private FakeMediaStore store;
    private FileOpEngine engine;
    private AutoFileLog log;

    @Before
    public void setUp() {
        store = new FakeMediaStore();
        engine = new FileOpEngine(store);
        log = new AutoFileLog(new ArrayList<AutoFileLog.Entry>());
    }

    @Test
    public void recordKeepsWhereTheItemCameFromAndWentTo() {
        Uri item = store.addItem("DCIM/Camera", "a.jpg", 1790157600000L);
        FileOpResult result = engine.move(item, "Pictures/2026/09");

        log.record(result, "Pictures/2026/09/", 5_000L);

        assertEquals(1, log.entries().size());
        AutoFileLog.Entry entry = log.entries().get(0);
        assertEquals("DCIM/Camera/", entry.fromRelativePath);
        assertEquals("Pictures/2026/09/", entry.toRelativePath);
        assertEquals("a.jpg", entry.displayName);
        assertEquals(5_000L, entry.whenMillis);
    }

    @Test
    public void aFailedMoveIsNotLogged() {
        Uri item = store.addItem("DCIM/Camera", "a.jpg", 1L);
        FileOpResult result = engine.move(item, "Download/Nope");

        log.record(result, "Download/Nope/", 5_000L);

        assertEquals(0, log.entries().size());
    }

    @Test
    public void findLocatesAnEntryByItsUri() {
        Uri item = store.addItem("DCIM/Camera", "a.jpg", 1790157600000L);
        FileOpResult result = engine.move(item, "Pictures/2026/09");
        log.record(result, "Pictures/2026/09/", 5_000L);

        assertNotNull(log.find(result.resultUri.toString()));
        assertNull(log.find("media://does-not-exist"));
    }

    @Test
    public void removeDropsTheEntry() {
        Uri item = store.addItem("DCIM/Camera", "a.jpg", 1790157600000L);
        FileOpResult result = engine.move(item, "Pictures/2026/09");
        log.record(result, "Pictures/2026/09/", 5_000L);

        log.remove(result.resultUri.toString());

        assertEquals(0, log.entries().size());
    }

    @Test
    public void theLogIsCappedSoItCannotGrowForever() {
        for (int i = 0; i < AutoFileLog.MAX_ENTRIES + 20; i++) {
            Uri item = store.addItem("DCIM/Camera", "a" + i + ".jpg", 1790157600000L);
            FileOpResult result = engine.move(item, "Pictures/2026/09");
            log.record(result, "Pictures/2026/09/", i);
        }

        assertEquals(AutoFileLog.MAX_ENTRIES, log.entries().size());
        assertEquals("the oldest entries are dropped first",
                20L, log.entries().get(0).whenMillis);
    }
}
