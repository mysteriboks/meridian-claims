package com.meridian.claims.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Shared date-parsing helpers. Single home for the "flexible EDI date"
 * parser that had accumulated three near-identical private copies
 * ({@code ClaimStatusInquiryService}, {@code PriorAuthRequestService},
 * {@code EnrollmentIntakeService}) — each accepting the two date shapes X12
 * DTP segments and this app's own ISO fallback commonly carry.
 */
public final class DateUtil {

    private static final String[] FLEXIBLE_PATTERNS = {"yyyyMMdd", "yyyy-MM-dd"};

    private DateUtil() {
        // static helpers only
    }

    /**
     * Parses a date string trying, in order, {@code yyyyMMdd} (the X12 D8 qualifier shape)
     * then {@code yyyy-MM-dd}. Returns null for a null/blank input or one that matches
     * neither pattern — callers treat a null result as "missing or invalid date".
     */
    public static Date parseFlexible(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        String trimmed = dateStr.trim();
        for (String pattern : FLEXIBLE_PATTERNS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                sdf.setLenient(false);
                return sdf.parse(trimmed);
            } catch (ParseException e) {
                // try next pattern
            }
        }
        return null;
    }
}
