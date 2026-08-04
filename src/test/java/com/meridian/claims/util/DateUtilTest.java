package com.meridian.claims.util;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DateUtilTest {

    @Test
    public void parsesYyyyMMdd() throws Exception {
        Date parsed = DateUtil.parseFlexible("20260115");
        assertEquals(new SimpleDateFormat("yyyy-MM-dd").parse("2026-01-15"), parsed);
    }

    @Test
    public void parsesIsoDashedFormat() throws Exception {
        Date parsed = DateUtil.parseFlexible("2026-01-15");
        assertEquals(new SimpleDateFormat("yyyy-MM-dd").parse("2026-01-15"), parsed);
    }

    @Test
    public void trimsWhitespace() throws Exception {
        Date parsed = DateUtil.parseFlexible("  20260115  ");
        assertEquals(new SimpleDateFormat("yyyy-MM-dd").parse("2026-01-15"), parsed);
    }

    @Test
    public void nullInput_returnsNull() {
        assertNull(DateUtil.parseFlexible(null));
    }

    @Test
    public void blankInput_returnsNull() {
        assertNull(DateUtil.parseFlexible("   "));
    }

    @Test
    public void unrecognizedFormat_returnsNull() {
        assertNull(DateUtil.parseFlexible("not-a-date"));
    }

    @Test
    public void invalidCalendarDate_returnsNull() {
        assertNull(DateUtil.parseFlexible("20261332"));
    }
}
