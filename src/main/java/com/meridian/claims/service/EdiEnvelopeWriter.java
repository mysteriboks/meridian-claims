package com.meridian.claims.service;

import io.xlate.edi.stream.EDIStreamException;
import io.xlate.edi.stream.EDIStreamWriter;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Shared ISA/GE/IEA interchange envelope construction for Meridian's outbound
 * X12 generators ({@link Edi835Generator}, {@link Edi999Generator},
 * {@link Edi277CaGenerator}). Single home for the envelope boilerplate that is
 * otherwise identical across every outbound file — CLAUDE.md "Lean Codebase":
 * no hand-copied duplication. Segment/element content that differs per
 * transaction type (GS, ST..SE detail loops) stays in each generator.
 */
final class EdiEnvelopeWriter {

    private EdiEnvelopeWriter() {
        // static helpers only
    }

    static final String SENDER_ID   = "MERIDIAN       ";
    static final String RECEIVER_ID = "PAYER          ";

    /** Writes the ISA interchange control header shared by every outbound X12 file. */
    static void writeIsa(EDIStreamWriter writer, String dateYyMmDd, String timeHhMm,
                         String controlNumber, boolean productionMode) throws EDIStreamException {
        writer.writeStartSegment("ISA");
        writer.writeElement("00");           // ISA01 authorization info qualifier
        writer.writeElement("          ");   // ISA02 authorization info (10 spaces)
        writer.writeElement("00");           // ISA03 security info qualifier
        writer.writeElement("          ");   // ISA04 security info (10 spaces)
        writer.writeElement("ZZ");           // ISA05 interchange sender ID qualifier
        writer.writeElement(SENDER_ID);      // ISA06 interchange sender ID (15 chars)
        writer.writeElement("ZZ");           // ISA07 interchange receiver ID qualifier
        writer.writeElement(RECEIVER_ID);    // ISA08 interchange receiver ID (15 chars)
        writer.writeElement(dateYyMmDd);     // ISA09 interchange date (yyMMdd)
        writer.writeElement(timeHhMm);       // ISA10 interchange time (HHmm)
        writer.writeElement("^");            // ISA11 repetition separator
        writer.writeElement("00501");        // ISA12 interchange control version number
        writer.writeElement(controlNumber);  // ISA13 interchange control number
        writer.writeElement("0");            // ISA14 acknowledgment requested
        writer.writeElement(productionMode ? "P" : "T"); // ISA15: P=production, T=test
        writer.writeElement(":");            // ISA16 component element separator
        writer.writeEndSegment();
    }

    /** Writes GE (functional group trailer) + IEA (interchange trailer) for a single-group file. */
    static void writeGeAndIea(EDIStreamWriter writer, String isaControlNumber) throws EDIStreamException {
        writer.writeStartSegment("GE");
        writer.writeElement("1");            // GE01 number of transaction sets included
        writer.writeElement("1");            // GE02 group control number
        writer.writeEndSegment();

        writer.writeStartSegment("IEA");
        writer.writeElement("1");            // IEA01 number of included functional groups
        writer.writeElement(isaControlNumber); // IEA02 interchange control number
        writer.writeEndSegment();
    }

    /** Writes IEA only — for TA1, which stands alone with no functional group to close. */
    static void writeIeaNoGroup(EDIStreamWriter writer, String isaControlNumber) throws EDIStreamException {
        writer.writeStartSegment("IEA");
        writer.writeElement("0");            // IEA01 — no functional groups (TA1 has none)
        writer.writeElement(isaControlNumber); // IEA02 interchange control number
        writer.writeEndSegment();
    }

    /**
     * Writes the TRN (trace) segment that correlates a response back to the submitter's own
     * ST02 — shared by every response-family generator ({@link Edi277CaGenerator},
     * {@link Edi277Generator}, {@link Edi278ResponseGenerator}). Returns the segment count (1).
     */
    static int writeTrn(EDIStreamWriter writer, String stControlNumber) throws EDIStreamException {
        writer.writeStartSegment("TRN");
        writer.writeElement("2");
        writer.writeElement(nz(stControlNumber, "0"));
        writer.writeEndSegment();
        return 1;
    }

    /**
     * Writes the STC (claim-level status, a category:status composite) segment — shared by
     * {@link Edi277CaGenerator} and {@link Edi277Generator}. Returns the segment count (1).
     */
    static int writeStc(EDIStreamWriter writer, String stcCode, String dateYyyyMmDd) throws EDIStreamException {
        writer.writeStartSegment("STC");
        writer.writeStartElement();
        String[] parts = stcCode.split(":");
        writer.writeComponent(parts[0]);
        writer.writeComponent(parts[1]);
        writer.endElement();
        writer.writeElement(dateYyyyMmDd);
        writer.writeEndSegment();
        return 1;
    }

    /** Writes TRN + STC together (the 277-family pair). Returns the segment count (2). */
    static int writeTrnAndStc(EDIStreamWriter writer, String stControlNumber, String stcCode,
                              String dateYyyyMmDd) throws EDIStreamException {
        return writeTrn(writer, stControlNumber) + writeStc(writer, stcCode, dateYyyyMmDd);
    }

    /** Formats a Date using the given pattern. Thread-safe via per-call instantiation. */
    static String fmt(Date date, String pattern) {
        return new SimpleDateFormat(pattern).format(date);
    }

    /** Returns value trimmed, or fallback if value is null/blank. */
    static String nz(String value, String fallback) {
        return (value != null && !value.trim().isEmpty()) ? value.trim() : fallback;
    }

    /** Returns value trimmed, or "unknown" if value is null/blank — for use in file/log references. */
    static String safeRef(String value) {
        return nz(value, "unknown");
    }
}
