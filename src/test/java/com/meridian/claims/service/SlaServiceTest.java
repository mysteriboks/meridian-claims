package com.meridian.claims.service;

import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimStatus;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Calendar;
import java.util.Date;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SlaServiceTest {

    private SlaService slaService;

    @Before
    public void setUp() {
        slaService = new SlaService();
        ReflectionTestUtils.setField(slaService, "submittedHours", 24);
        ReflectionTestUtils.setField(slaService, "inReviewHours", 48);
        ReflectionTestUtils.setField(slaService, "pendingInfoHours", 72);
    }

    @Test
    public void computeExpectedBy_submittedStatus_addsHours() {
        Date enteredAt = hoursAgo(0);
        Date expected = slaService.computeExpectedBy("SUBMITTED", enteredAt);
        assertNotNull(expected);
        // Should be roughly 24h from enteredAt
        long diff = expected.getTime() - enteredAt.getTime();
        assertTrue(Math.abs(diff - 24 * 3600 * 1000L) < 1000);
    }

    @Test
    public void computeExpectedBy_unknownStatus_returnsNull() {
        assertNull(slaService.computeExpectedBy("PAID", new Date()));
    }

    @Test
    public void computeExpectedBy_nullEnteredAt_returnsNull() {
        assertNull(slaService.computeExpectedBy("SUBMITTED", null));
    }

    @Test
    public void isBreached_enteredAtOverThreshold_returnsTrue() {
        Claim claim = new Claim();
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setStatusEnteredAt(hoursAgo(25)); // 25 hours ago, threshold is 24
        assertTrue(slaService.isBreached(claim));
    }

    @Test
    public void isBreached_enteredAtUnderThreshold_returnsFalse() {
        Claim claim = new Claim();
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setStatusEnteredAt(hoursAgo(10)); // 10 hours ago, threshold is 24
        assertFalse(slaService.isBreached(claim));
    }

    @Test
    public void isBreached_noSlaStatus_returnsFalse() {
        Claim claim = new Claim();
        claim.setStatus(ClaimStatus.APPROVED);
        claim.setStatusEnteredAt(hoursAgo(999));
        assertFalse(slaService.isBreached(claim));
    }

    @Test
    public void isBreached_nullStatusEnteredAt_returnsFalse() {
        Claim claim = new Claim();
        claim.setStatus(ClaimStatus.IN_REVIEW);
        claim.setStatusEnteredAt(null);
        assertFalse(slaService.isBreached(claim));
    }

    private Date hoursAgo(int hours) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -hours);
        return cal.getTime();
    }
}
