package com.meridian.claims.intake;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Recognises X12 834 enrollment files by extension — the third small
 * per-transaction-type matcher, mirroring {@link StatusInquiryFileMatcher}
 * (276) and {@link PriorAuthRequestFileMatcher} (278). Still simple enough
 * that a shared enum-based resolver would cost more (re-touching two
 * already-tested pollers and their tests) than it saves; the standing
 * decision from the Phase 15 sweep was to revisit at a fourth type.
 */
@Component
public class EnrollmentFileMatcher {

    @Value("${claims.intake.enrollment.extensions:834}")
    private String enrollmentExtensions;

    public boolean matches(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        String ext = fileName.substring(dot + 1).toLowerCase();
        String[] exts = enrollmentExtensions.split(",");
        for (int i = 0; i < exts.length; i++) {
            if (exts[i].trim().toLowerCase().equals(ext)) {
                return true;
            }
        }
        return false;
    }
}
