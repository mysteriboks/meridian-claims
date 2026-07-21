package com.meridian.claims.util;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;

import org.junit.Test;

/**
 * Verifies the money convention: scale 2, HALF_UP rounding, null-safe
 * arithmetic. Also proves the JUnit 4 test harness is wired correctly
 * (Phase 1 foundation).
 */
public class MoneyTest {

    @Test
    public void ofAppliesScaleTwoAndHalfUp() {
        assertEquals(new BigDecimal("10.00"), Money.of("10"));
        assertEquals(new BigDecimal("1.24"), Money.of("1.235")); // HALF_UP rounds .005 up
        assertEquals(new BigDecimal("1.23"), Money.of("1.234"));
    }

    @Test
    public void addAndSubtractTreatNullAsZero() {
        assertEquals(new BigDecimal("15.50"), Money.add(Money.of("10.00"), Money.of("5.50")));
        assertEquals(new BigDecimal("10.00"), Money.add(Money.of("10.00"), null));
        assertEquals(new BigDecimal("7.25"), Money.subtract(Money.of("10.00"), Money.of("2.75")));
    }

    @Test
    public void percentOfComputesCoverageShare() {
        // 80% of 200.00 = 160.00
        assertEquals(new BigDecimal("160.00"), Money.percentOf(Money.of("200.00"), new BigDecimal("80")));
    }

    @Test
    public void minAndMaxReturnCanonicalScale() {
        assertEquals(new BigDecimal("2.75"), Money.min(Money.of("2.75"), Money.of("9.00")));
        assertEquals(new BigDecimal("9.00"), Money.max(Money.of("2.75"), Money.of("9.00")));
    }
}
