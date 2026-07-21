package com.meridian.claims.service;

import com.meridian.claims.model.Claim;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;

/**
 * SLA threshold computation for claim status transitions.
 * Thresholds are configured in application.properties as hours.
 * A threshold of 0 means "no SLA applies" for that status.
 */
@Service
public class SlaService {

    @Value("${claims.sla.SUBMITTED.hours:24}")
    private int submittedHours;

    @Value("${claims.sla.IN_REVIEW.hours:48}")
    private int inReviewHours;

    @Value("${claims.sla.PENDING_INFO.hours:72}")
    private int pendingInfoHours;

    /** Returns when the SLA expires for a claim currently in the given status, or null if no SLA. */
    public Date computeExpectedBy(String status, Date statusEnteredAt) {
        if (statusEnteredAt == null) {
            return null;
        }
        int hours = hoursForStatus(status);
        if (hours <= 0) {
            return null;
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(statusEnteredAt);
        cal.add(Calendar.HOUR_OF_DAY, hours);
        return cal.getTime();
    }

    /** Returns true if the claim's current status has exceeded its SLA threshold. */
    public boolean isBreached(Claim claim) {
        if (claim.getStatusEnteredAt() == null) {
            return false;
        }
        Date expectedBy = computeExpectedBy(claim.getStatus().name(), claim.getStatusEnteredAt());
        if (expectedBy == null) {
            return false;
        }
        return new Date().after(expectedBy);
    }

    private int hoursForStatus(String status) {
        if ("SUBMITTED".equals(status))    return submittedHours;
        if ("IN_REVIEW".equals(status))    return inReviewHours;
        if ("PENDING_INFO".equals(status)) return pendingInfoHours;
        return 0;
    }
}
