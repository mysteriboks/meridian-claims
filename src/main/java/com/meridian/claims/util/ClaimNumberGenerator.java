package com.meridian.claims.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

public class ClaimNumberGenerator {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    /**
     * Generates a claim number of the form CLM-YYYYMMDD-NNNNNN.
     * The 6-digit suffix mixes the millisecond timestamp with a process-local counter
     * to make same-millisecond collisions vanishingly rare without changing the format.
     */
    public static String generate(Date submissionDate) {
        String dateStr;
        synchronized (DATE_FORMAT) {
            dateStr = DATE_FORMAT.format(submissionDate);
        }
        int counter = COUNTER.incrementAndGet() % 1000;
        long milliPart = (System.currentTimeMillis() % 1000) * 1000;
        long suffix = (milliPart + counter) % 1_000_000L;
        return String.format("CLM-%s-%06d", dateStr, suffix);
    }

    private ClaimNumberGenerator() {}
}
