package com.android.gallery3d.fileops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ConsentPolicyTest {

    private static final Uri A = Uri.parse("content://media/external/images/media/1");
    private static final Uri B = Uri.parse("content://media/external/images/media/2");
    private static final Uri C = Uri.parse("content://media/external/images/media/3");
    private static final Uri D = Uri.parse("content://media/external/images/media/4");

    private static FileOpResult ok(Uri uri) {
        return FileOpResult.ok(uri, uri, "DCIM/Camera/", "x.jpg", false);
    }

    private static FileOpResult consent(Uri uri) {
        // The IntentSender is irrelevant to the policy; only the status matters.
        return FileOpResult.consentRequired(uri, null);
    }

    private static FileOpResult failed(Uri uri) {
        return FileOpResult.failed(uri, "boom");
    }

    @Test
    public void itemsNeedingConsentKeepsOrderAndCollapsesDuplicates() {
        List<FileOpResult> results = Arrays.asList(
                consent(C), consent(A), consent(C), consent(B));
        assertEquals(Arrays.asList(C, A, B), ConsentPolicy.itemsNeedingConsent(results));
    }

    @Test
    public void itemsNeedingConsentIgnoresOkFailedAndNulls() {
        List<FileOpResult> results = Arrays.asList(
                ok(A), null, failed(B), consent(C), FileOpResult.consentRequired(null, null));
        assertEquals(Collections.singletonList(C),
                ConsentPolicy.itemsNeedingConsent(results));
        assertTrue(ConsentPolicy.itemsNeedingConsent(null).isEmpty());
        assertTrue(ConsentPolicy.itemsNeedingConsent(
                Arrays.asList(ok(A), failed(B))).isEmpty());
    }

    @Test
    public void combineReplacesConsentEntriesWithRetryOutcomesInPlace() {
        List<FileOpResult> first = Arrays.asList(ok(A), consent(B), consent(C));
        FileOpResult retriedB = ok(B);
        FileOpResult retriedC = failed(C);
        List<FileOpResult> combined = ConsentPolicy.combine(first,
                Arrays.asList(retriedC, retriedB));

        assertEquals(3, combined.size());
        assertEquals(A, combined.get(0).sourceUri);
        assertTrue(combined.get(1) == retriedB);
        assertTrue(combined.get(2) == retriedC);
        assertEquals("boom", combined.get(2).failureReason);
    }

    @Test
    public void stillBlockedAfterRetryBecomesFailed() {
        List<FileOpResult> combined = ConsentPolicy.combine(
                Arrays.asList(consent(A)), Arrays.asList(consent(A)));
        assertEquals(1, combined.size());
        assertEquals(FileOpResult.Status.FAILED, combined.get(0).status);
        assertEquals(A, combined.get(0).sourceUri);
        assertEquals(ConsentPolicy.NOT_GRANTED, combined.get(0).failureReason);
    }

    @Test
    public void noRetrySettlesEveryConsentEntryAsFailed() {
        FileOpResult okA = ok(A);
        List<FileOpResult> combined = ConsentPolicy.combine(
                Arrays.asList(okA, consent(B), consent(C)), null);
        assertEquals(3, combined.size());
        assertTrue(combined.get(0) == okA);
        for (int i = 1; i < 3; i++) {
            assertEquals(FileOpResult.Status.FAILED, combined.get(i).status);
            assertEquals(ConsentPolicy.NOT_GRANTED, combined.get(i).failureReason);
            assertNull(combined.get(i).consent);
        }
        assertEquals(B, combined.get(1).sourceUri);
        assertEquals(C, combined.get(2).sourceUri);
    }

    @Test
    public void itemMissingFromRetryIsSettledAsFailed() {
        // The retry batch stops at its first failure, so later items never run.
        List<FileOpResult> combined = ConsentPolicy.combine(
                Arrays.asList(consent(A), consent(B)), Arrays.asList(failed(A)));
        assertEquals(FileOpResult.Status.FAILED, combined.get(0).status);
        assertEquals("boom", combined.get(0).failureReason);
        assertEquals(FileOpResult.Status.FAILED, combined.get(1).status);
        assertEquals(ConsentPolicy.NOT_GRANTED, combined.get(1).failureReason);
    }

    @Test
    public void combinedNeverContainsConsentRequired() {
        List<FileOpResult> combined = ConsentPolicy.combine(
                Arrays.asList(consent(A), ok(B), consent(C)),
                Arrays.asList(consent(C), consent(D)));
        for (FileOpResult result : combined) {
            assertTrue(result.status != FileOpResult.Status.CONSENT_REQUIRED);
        }
        assertTrue(ConsentPolicy.itemsNeedingConsent(combined).isEmpty());
        assertTrue(ConsentPolicy.combine(null, null).isEmpty());
    }

    @Test
    public void combinedCountsDescribeTheWholeBatch() {
        FileOpBatch batch = new FileOpBatch("t", FileOpBatch.Kind.MOVE,
                Arrays.asList(A, B, C, D), "Pictures/Holiday");
        batch.results.addAll(Arrays.asList(ok(A), consent(B), consent(C), consent(D)));
        assertEquals(1, batch.okCount());
        assertEquals(0, batch.failureCount());

        List<FileOpResult> retry = new ArrayList<FileOpResult>(
                Arrays.asList(ok(B), consent(C), ok(D)));
        List<FileOpResult> combined = ConsentPolicy.combine(batch.results, retry);
        batch.results.clear();
        batch.results.addAll(combined);

        assertEquals(3, batch.okCount());
        assertEquals(1, batch.failureCount());
        assertEquals(4, batch.results.size());
    }
}
