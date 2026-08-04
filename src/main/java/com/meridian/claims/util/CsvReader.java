package com.meridian.claims.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC-4180-compliant CSV reader — the counterpart to {@link CsvWriter}.
 * Handles double-quoted fields (embedded commas, doubled-quote escaping) and
 * plain unquoted fields. Blank lines are skipped. Not a streaming parser —
 * reference-data feed files (Phase 19) are small enough to hold in memory.
 */
public final class CsvReader {

    private CsvReader() {
        // static helpers only
    }

    /** Parses every line of the given content into a row of cells. */
    public static List<String[]> parseAll(String content) {
        List<String[]> rows = new ArrayList<String[]>();
        if (content == null) {
            return rows;
        }
        String[] lines = content.split("\r\n|\r|\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            rows.add(parseLine(line));
        }
        return rows;
    }

    /** Parses a single CSV line into cells, honoring double-quoted fields. */
    static String[] parseLine(String line) {
        List<String> cells = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    cells.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
            i++;
        }
        cells.add(current.toString());
        return cells.toArray(new String[0]);
    }
}
