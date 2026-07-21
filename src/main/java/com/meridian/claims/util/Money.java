package com.meridian.claims.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Central money convention for Meridian Claims (PHASES.md Phase 1:
 * "Currency convention established").
 *
 * NON-NEGOTIABLE RULES (CLAUDE.md / HIGH_LEVEL_DESIGN.md §10):
 *   * All money is {@link BigDecimal}; DB columns are NUMERIC(12,2).
 *   * Scale is always 2 decimal places.
 *   * Rounding is always {@link RoundingMode#HALF_UP}.
 *   * No double / float anywhere in the money path.
 *
 * Every monetary value entering the system should be normalised with
 * {@link #of} or {@link #scale}, and all arithmetic should go through the
 * helpers here so rounding behaviour is consistent.
 */
public final class Money {

    /** Number of decimal places for all monetary values. */
    public static final int SCALE = 2;

    /** The single rounding mode used across the money path. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** Zero, already at the canonical scale. */
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {
        // utility class — no instances
    }

    /** Build a money value from a string literal (preferred over double). */
    public static BigDecimal of(String value) {
        if (value == null) {
            return null;
        }
        return new BigDecimal(value).setScale(SCALE, ROUNDING);
    }

    /** Normalise an existing BigDecimal to scale 2, HALF_UP. */
    public static BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(SCALE, ROUNDING);
    }

    /** a + b, returned at canonical scale. Nulls treated as zero. */
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return scale(nz(a).add(nz(b)));
    }

    /** a - b, returned at canonical scale. Nulls treated as zero. */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return scale(nz(a).subtract(nz(b)));
    }

    /** a * b, returned at canonical scale. Nulls treated as zero. */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return scale(nz(a).multiply(nz(b)));
    }

    /**
     * amount * (percent / 100), e.g. applying a coverage percentage.
     * Percent is a whole-number-ish value (80 means 80%).
     */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        BigDecimal fraction = nz(percent).divide(new BigDecimal("100"), 10, ROUNDING);
        return scale(nz(amount).multiply(fraction));
    }

    /** Returns the smaller of two money values (nulls treated as zero). */
    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) <= 0 ? scale(a) : scale(b);
    }

    /** Returns the larger of two money values (nulls treated as zero). */
    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) >= 0 ? scale(a) : scale(b);
    }

    /** Null-to-zero helper. */
    private static BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
