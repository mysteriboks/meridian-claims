package com.meridian.claims.util;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CsvReaderTest {

    @Test
    public void parseAll_simpleRows() {
        List<String[]> rows = CsvReader.parseAll("code,description\nE11.9,Type 2 diabetes mellitus");
        assertEquals(2, rows.size());
        assertArrayEquals(new String[]{"code", "description"}, rows.get(0));
        assertArrayEquals(new String[]{"E11.9", "Type 2 diabetes mellitus"}, rows.get(1));
    }

    @Test
    public void parseAll_quotedFieldWithEmbeddedComma() {
        List<String[]> rows = CsvReader.parseAll(
            "code,description\nE11.9,\"Type 2 diabetes mellitus, without complications\"");
        assertEquals(2, rows.size());
        assertArrayEquals(new String[]{"E11.9", "Type 2 diabetes mellitus, without complications"}, rows.get(1));
    }

    @Test
    public void parseAll_doubledQuoteEscaping() {
        List<String[]> rows = CsvReader.parseAll("code,description\nX01,\"Say \"\"hello\"\"\"");
        assertEquals("Say \"hello\"", rows.get(1)[1]);
    }

    @Test
    public void parseAll_skipsBlankLines() {
        List<String[]> rows = CsvReader.parseAll("code,description\n\nE11.9,Diabetes\n\n");
        assertEquals(2, rows.size());
    }

    @Test
    public void parseAll_emptyContent_returnsEmptyList() {
        assertTrue(CsvReader.parseAll("").isEmpty());
        assertTrue(CsvReader.parseAll(null).isEmpty());
    }

    @Test
    public void parseAll_handlesCrlfLineEndings() {
        List<String[]> rows = CsvReader.parseAll("a,b\r\nc,d\r\n");
        assertEquals(2, rows.size());
        assertArrayEquals(new String[]{"c", "d"}, rows.get(1));
    }

    @Test
    public void parseAll_trailingEmptyCell() {
        List<String[]> rows = CsvReader.parseAll("a,b,\nc,d,");
        assertArrayEquals(new String[]{"a", "b", ""}, rows.get(0));
    }
}
