package com.meridian.claims.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HtmlUtilTest {

    @Test
    public void escape_null_returnsEmptyString() {
        assertEquals("", HtmlUtil.escape(null));
    }

    @Test
    public void escape_ampersand_entitized() {
        assertEquals("&amp;", HtmlUtil.escape("&"));
    }

    @Test
    public void escape_lessThan_entitized() {
        assertEquals("&lt;", HtmlUtil.escape("<"));
    }

    @Test
    public void escape_greaterThan_entitized() {
        assertEquals("&gt;", HtmlUtil.escape(">"));
    }

    @Test
    public void escape_xssPayload_fullyEscaped() {
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;",
            HtmlUtil.escape("<script>alert(1)</script>"));
    }

    @Test
    public void escape_noSpecialChars_unchanged() {
        assertEquals("hello world", HtmlUtil.escape("hello world"));
    }
}
