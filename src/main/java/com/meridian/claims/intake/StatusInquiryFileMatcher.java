package com.meridian.claims.intake;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Recognises X12 276 claim status inquiry files by extension, the single home
 * for that dispatch decision so both pollers (global directory and
 * trading-partner) agree on it — mirrors {@link ParserResolver}'s role for
 * claim-submission files, but a 276 is a read-only query, not a submission,
 * so it is deliberately a separate, smaller resolver rather than folded into
 * {@link ParserResolver}'s {@code ClaimFileParser}-returning contract.
 */
@Component
public class StatusInquiryFileMatcher {

    @Value("${claims.intake.status-inquiry.extensions:276}")
    private String statusInquiryExtensions;

    public boolean matches(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        String ext = fileName.substring(dot + 1).toLowerCase();
        String[] exts = statusInquiryExtensions.split(",");
        for (int i = 0; i < exts.length; i++) {
            if (exts[i].trim().toLowerCase().equals(ext)) {
                return true;
            }
        }
        return false;
    }
}
