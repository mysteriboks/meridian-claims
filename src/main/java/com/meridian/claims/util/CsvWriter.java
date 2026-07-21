package com.meridian.claims.util;

import java.util.List;

/**
 * Minimal RFC-4180-compliant CSV builder. Cells containing commas, quotes, or
 * newlines are double-quoted and internal quotes are escaped by doubling.
 * Usage: call header(cols), then one row(cells) per data row, then build().
 */
public class CsvWriter {

    private final StringBuilder sb = new StringBuilder();

    public CsvWriter header(String... columns) {
        return row(columns);
    }

    public CsvWriter row(String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cells[i]));
        }
        sb.append("\r\n");
        return this;
    }

    public CsvWriter row(List<String> cells) {
        return row(cells.toArray(new String[0]));
    }

    public String build() {
        return sb.toString();
    }

    private String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
