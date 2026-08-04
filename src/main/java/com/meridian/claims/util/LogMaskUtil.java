package com.meridian.claims.util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Masks PHI fields (DOB, member numbers) in strings destined for log output.
 *
 * Rules:
 *   - Member numbers: retain the last 4 characters, mask the rest with '*'.
 *   - DOB in ISO format (yyyy-MM-dd): replace with ***-**-** to hide birth year and month.
 *
 * Usage:
 *   LOG.info("Processing member " + LogMaskUtil.maskMemberNumber(member.getMemberNumber()));
 *   LOG.debug("DOB check: " + LogMaskUtil.maskDob(member.getDob()));
 */
public final class LogMaskUtil {

    // Matches ISO date patterns like 1985-04-23 anywhere in a string.
    private static final Pattern DOB_PATTERN =
            Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");

    private LogMaskUtil() {
        // utility class
    }

    /**
     * Masks a member number to show only the last 4 characters.
     * "MBR-00123456" → "****-****56"  (last 4 of the full string)
     * Returns "(null)" if the value is null.
     */
    public static String maskMemberNumber(String memberNumber) {
        if (memberNumber == null) return "(null)";
        int len = memberNumber.length();
        if (len <= 4) return memberNumber; // too short to mask meaningfully
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len - 4; i++) {
            char c = memberNumber.charAt(i);
            sb.append(Character.isLetterOrDigit(c) ? '*' : c);
        }
        sb.append(memberNumber, len - 4, len);
        return sb.toString();
    }

    /**
     * Masks a DOB string in ISO format (yyyy-MM-dd) to "****-**-**".
     * Returns "(null)" if the value is null.
     */
    public static String maskDob(String dob) {
        if (dob == null) return "(null)";
        return dob.replaceAll("\\d{4}-\\d{2}-\\d{2}", "****-**-**");
    }

    /**
     * Scans an arbitrary log message and replaces any ISO-format dates with "****-**-**".
     * Use when the full message may contain DOB values from concatenation.
     */
    public static String maskDobsInMessage(String message) {
        if (message == null) return null;
        Matcher m = DOB_PATTERN.matcher(message);
        return m.replaceAll("****-**-**");
    }

    /**
     * Combined mask for operator-facing intake/quarantine messages, applying both
     * DOB and member-number masking before the message reaches logs or audit —
     * the single home for a composition every batch intake service needs
     * ({@code IntakeService}, {@code EnrollmentIntakeService}).
     */
    public static String maskPhi(String message) {
        return maskMemberNumber(maskDobsInMessage(message));
    }
}
