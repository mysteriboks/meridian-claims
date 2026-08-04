package com.meridian.claims.intake;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Recognises X12 278 prior-authorization request files by extension — mirrors
 * {@link StatusInquiryFileMatcher}'s role for 276 files. A separate, small
 * matcher per ancillary transaction type rather than one generalised
 * dispatcher: each is independently simple, and the alternative (a shared
 * enum-based resolver) would mean touching the already-tested pollers and
 * their tests again for marginal benefit. Revisit if a fourth type makes the
 * pattern genuinely unwieldy.
 */
@Component
public class PriorAuthRequestFileMatcher {

    @Value("${claims.intake.prior-auth.extensions:278}")
    private String priorAuthExtensions;

    public boolean matches(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        String ext = fileName.substring(dot + 1).toLowerCase();
        String[] exts = priorAuthExtensions.split(",");
        for (int i = 0; i < exts.length; i++) {
            if (exts[i].trim().toLowerCase().equals(ext)) {
                return true;
            }
        }
        return false;
    }
}
