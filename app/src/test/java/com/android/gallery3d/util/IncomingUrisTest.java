package com.android.gallery3d.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Which uris other apps may hand to the exported activities. */
public class IncomingUrisTest {
    private static final String OWN = "app.grapheneos.gallery.debug";

    @Test
    public void mediaStoreAndDocumentUrisAreForeign() {
        assertTrue(IncomingUris.isForeignContent("content", "media", OWN, null));
        assertTrue(IncomingUris.isForeignContent("content",
                "com.android.externalstorage.documents", OWN, "com.android.externalstorage"));
        assertTrue(IncomingUris.isForeignContent("CONTENT", "media", OWN, null));
    }

    @Test
    public void fileAndOtherSchemesAreRefused() {
        assertFalse(IncomingUris.isForeignContent("file", "", OWN, null));
        assertFalse(IncomingUris.isForeignContent("file", null, OWN, null));
        assertFalse(IncomingUris.isForeignContent("http", "example.com", OWN, null));
        assertFalse(IncomingUris.isForeignContent(null, "media", OWN, null));
        assertFalse(IncomingUris.isForeignContent("content", null, OWN, null));
        assertFalse(IncomingUris.isForeignContent("content", "", OWN, null));
    }

    @Test
    public void ownProviderAuthoritiesAreRefused() {
        assertFalse(IncomingUris.isForeignContent("content",
                OWN + ".filtershow.provider.SharedImageProvider", OWN, OWN));
        assertFalse(IncomingUris.isForeignContent("content", OWN + ".photoprovider", OWN, null));
        assertFalse(IncomingUris.isForeignContent("content", OWN, OWN, null));
        // Upper case and a user-id prefix do not get around the check.
        assertFalse(IncomingUris.isForeignContent("content",
                OWN.toUpperCase() + ".photoprovider", OWN, null));
        assertFalse(IncomingUris.isForeignContent("content", "0@" + OWN + ".photoprovider",
                OWN, null));
        // An authority this app serves under another name.
        assertFalse(IncomingUris.isForeignContent("content", "some.other.name", OWN, OWN));
    }

    @Test
    public void similarlyNamedPackagesAreStillForeign() {
        assertTrue(IncomingUris.isForeignContent("content", OWN + "x.provider", OWN, null));
    }

    @Test
    public void onlyMediaStoreCountsAsMediaStore() {
        assertTrue(IncomingUris.isMediaStore("content", "media"));
        assertTrue(IncomingUris.isMediaStore("content", "10@media"));
        assertFalse(IncomingUris.isMediaStore("file", "media"));
        assertFalse(IncomingUris.isMediaStore("content", "media.evil"));
        assertFalse(IncomingUris.isMediaStore("content", "com.android.providers.downloads.documents"));
        assertFalse(IncomingUris.isMediaStore("content", null));
    }

    @Test
    public void userIdIsStripped() {
        assertEquals("media", IncomingUris.stripUserId("0@media"));
        assertEquals("media", IncomingUris.stripUserId("media"));
    }
}
