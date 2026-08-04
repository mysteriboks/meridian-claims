package com.meridian.claims.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Shared helpers for the file-level idempotency ledger pattern used by every
 * batch intake service ({@code IntakeService}, {@code EnrollmentIntakeService}):
 * a SHA-256 hash keyed for "have we seen this exact file before", and a
 * capped, semicolon-joined summary of per-record quarantine reasons for the
 * ledger's {@code error_message} column.
 */
public final class LedgerUtil {

    private LedgerUtil() {
        // static helpers only
    }

    public static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Joins reasons with "; ", truncated to maxLen. Returns null for an empty list. */
    public static String joinReasons(List<String> reasons, int maxLen) {
        if (reasons.isEmpty()) {
            return null;
        }
        String joined = String.join("; ", reasons);
        return joined.length() <= maxLen ? joined : joined.substring(0, maxLen);
    }
}
