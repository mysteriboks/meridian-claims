package com.meridian.claims.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LogMaskUtilTest {

    // --- maskMemberNumber ---

    @Test
    public void maskMemberNumber_showsLastFour() {
        String masked = LogMaskUtil.maskMemberNumber("MBR-00123456");
        assertTrue("should end with last 4 chars", masked.endsWith("3456"));
        assertFalse("should not contain full number", masked.contains("00123456"));
    }

    @Test
    public void maskMemberNumber_shortValue_returnsAsIs() {
        assertEquals("123", LogMaskUtil.maskMemberNumber("123"));
    }

    @Test
    public void maskMemberNumber_null_returnsNullLabel() {
        assertEquals("(null)", LogMaskUtil.maskMemberNumber(null));
    }

    @Test
    public void maskMemberNumber_preservesNonAlphanumericSeparators() {
        String masked = LogMaskUtil.maskMemberNumber("MBR-00123456");
        assertTrue("hyphen should survive", masked.contains("-"));
    }

    // --- maskDob ---

    @Test
    public void maskDob_isoDate_isHidden() {
        assertEquals("****-**-**", LogMaskUtil.maskDob("1985-04-23"));
    }

    @Test
    public void maskDob_null_returnsNullLabel() {
        assertEquals("(null)", LogMaskUtil.maskDob(null));
    }

    @Test
    public void maskDob_nonDate_returnsUnchanged() {
        assertEquals("no-date-here", LogMaskUtil.maskDob("no-date-here"));
    }

    // --- maskDobsInMessage ---

    @Test
    public void maskDobsInMessage_replacesEmbeddedDate() {
        String msg = "member dob=1985-04-23 was checked";
        String masked = LogMaskUtil.maskDobsInMessage(msg);
        assertFalse(masked.contains("1985"));
        assertTrue(masked.contains("****-**-**"));
    }

    @Test
    public void maskDobsInMessage_noDate_unchanged() {
        String msg = "no phi here";
        assertEquals(msg, LogMaskUtil.maskDobsInMessage(msg));
    }

    @Test
    public void maskDobsInMessage_null_returnsNull() {
        assertEquals(null, LogMaskUtil.maskDobsInMessage(null));
    }
}
