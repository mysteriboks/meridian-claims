package com.meridian.claims.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class CsvWriterTest {

    @Test
    public void header_and_rows_produce_correct_csv() {
        String csv = new CsvWriter()
            .header("Name", "Amount", "Date")
            .row("Jane Doe", "100.00", "2026-01-15")
            .row("Bob Smith", "250.50", "2026-02-01")
            .build();

        String[] lines = csv.split("\r\n");
        assertEquals("Name,Amount,Date", lines[0]);
        assertEquals("Jane Doe,100.00,2026-01-15", lines[1]);
        assertEquals("Bob Smith,250.50,2026-02-01", lines[2]);
    }

    @Test
    public void cell_with_comma_is_quoted() {
        String csv = new CsvWriter().row("Smith, John", "50.00").build();
        assertTrue(csv.startsWith("\"Smith, John\",50.00"));
    }

    @Test
    public void cell_with_quote_escapes_it() {
        String csv = new CsvWriter().row("He said \"hello\"", "x").build();
        assertTrue(csv.contains("\"He said \"\"hello\"\"\""));
    }

    @Test
    public void null_cell_becomes_empty_string() {
        String csv = new CsvWriter().row((String) null, "value").build();
        assertTrue(csv.startsWith(",value"));
    }
}
