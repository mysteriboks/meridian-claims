package com.meridian.claims.service;

import io.xlate.edi.stream.EDIOutputFactory;
import io.xlate.edi.stream.EDIStreamException;
import io.xlate.edi.stream.EDIStreamWriter;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

/**
 * Generates outbound X12 999 (Implementation Acknowledgment) and TA1
 * (Interchange Acknowledgment) files closing the loop on inbound EDI 837
 * intake (Phase 12), using the same StAEDI streaming writer style as
 * {@link Edi835Generator}.
 *
 * The 999 reports accept/reject per transaction (AK2/AK5) under one
 * functional-group response (AK1/AK9), referencing the inbound file's own
 * GS06/ST02 control numbers so the submitter can correlate the response.
 * TA1 is generated instead of a 999 when the interchange envelope itself
 * could not be parsed (structurally invalid ISA) — there is no functional
 * group to acknowledge in that case.
 *
 * As with {@link Edi835Generator}, this targets structural correctness for
 * an in-house-testable acknowledgment, not full trading-partner conformance
 * testing (no real partner exists yet — see PHASES.md Phase 13).
 */
@Service
public class Edi999Generator {

    private static final Logger LOG = Logger.getLogger(Edi999Generator.class);

    @Value("${claims.integration.edi.output.path:}")
    private String ediOutputPath;

    @Value("${claims.integration.edi.production:false}")
    private boolean productionMode;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generates a 999 functional acknowledgment for one inbound 837 interchange.
     *
     * @param isaControlNumber the inbound file's ISA13 (interchange control number)
     * @param gsControlNumber  the inbound file's GS06 (functional group control number)
     * @param transactions     one entry per ST..SE transaction in the inbound file,
     *                         each carrying its ST02 control number and accept/reject outcome
     * @return the 999 EDI string
     */
    public String generate999(String isaControlNumber, String gsControlNumber,
                              List<TransactionAckStatus> transactions) {
        try {
            String edi = buildEdi999(isaControlNumber, gsControlNumber, transactions);
            persistToDisk("999", EdiEnvelopeWriter.safeRef(isaControlNumber), edi);
            return edi;
        } catch (EDIStreamException e) {
            LOG.error("EDI 999 generation failed isaControlNumber=" + isaControlNumber, e);
            throw new ServiceException("Failed to generate EDI 999 for ISA " + isaControlNumber, e);
        } catch (IOException e) {
            LOG.error("EDI 999 disk write failed isaControlNumber=" + isaControlNumber, e);
            throw new ServiceException("Failed to write EDI 999 file for ISA " + isaControlNumber, e);
        }
    }

    /**
     * Generates a TA1 interchange acknowledgment for an inbound file whose ISA
     * envelope could not be parsed at all. Best-effort: the interchange
     * control number is echoed if known ("000000000" per X12 convention
     * otherwise); date/time reflect generation time since the original
     * ISA09/10 were not recoverable from a failed parse.
     *
     * @param isaControlNumber the inbound ISA13 if recoverable, else null
     * @param accepted         true for an "A" (accepted) TA1; false for "R" (rejected)
     * @param noteCode         TA105 interchange note code (e.g. "001" accepted, "029" unknown/other)
     * @return the TA1 EDI string
     */
    public String generateTa1(String isaControlNumber, boolean accepted, String noteCode) {
        try {
            String edi = buildTa1(isaControlNumber, accepted, noteCode);
            persistToDisk("TA1", EdiEnvelopeWriter.safeRef(isaControlNumber), edi);
            return edi;
        } catch (EDIStreamException e) {
            LOG.error("TA1 generation failed isaControlNumber=" + isaControlNumber, e);
            throw new ServiceException("Failed to generate TA1 for ISA " + isaControlNumber, e);
        } catch (IOException e) {
            LOG.error("TA1 disk write failed isaControlNumber=" + isaControlNumber, e);
            throw new ServiceException("Failed to write TA1 file for ISA " + isaControlNumber, e);
        }
    }

    // -------------------------------------------------------------------------
    // EDI construction — 999
    // -------------------------------------------------------------------------

    private String buildEdi999(String isaControlNumber, String gsControlNumber,
                               List<TransactionAckStatus> transactions)
            throws EDIStreamException, IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        EDIOutputFactory factory = EDIOutputFactory.newFactory();
        EDIStreamWriter writer = factory.createEDIStreamWriter(baos);

        Date now = new Date();
        String dateYyyyMmDd = EdiEnvelopeWriter.fmt(now, "yyyyMMdd");
        String dateYyMmDd   = EdiEnvelopeWriter.fmt(now, "yyMMdd");
        String timeHhMm     = EdiEnvelopeWriter.fmt(now, "HHmm");
        String isaRef = EdiEnvelopeWriter.safeRef(isaControlNumber);
        String isaPadded = String.format("%09d", Math.abs(isaRef.hashCode()) % 1000000000);

        int segmentCount = 0;

        writer.startInterchange();
        EdiEnvelopeWriter.writeIsa(writer, dateYyMmDd, timeHhMm, isaPadded, productionMode);

        // ----- GS -----
        writer.writeStartSegment("GS");
        writer.writeElement("FA");           // GS01 functional identifier code (FA = 999)
        writer.writeElement("MERIDIAN");     // GS02 application sender code
        writer.writeElement("PAYER");        // GS03 application receiver code
        writer.writeElement(dateYyyyMmDd);   // GS04 date
        writer.writeElement(timeHhMm);       // GS05 time
        writer.writeElement("1");            // GS06 group control number
        writer.writeElement("X");            // GS07 responsible agency code
        writer.writeElement("005010X231A1"); // GS08 version/release identifier (999)
        writer.writeEndSegment();

        // ----- ST -----
        writer.writeStartSegment("ST");
        writer.writeElement("999");
        writer.writeElement("0001");
        writer.writeEndSegment();
        segmentCount++;

        // ----- AK1 — functional group response header -----
        writer.writeStartSegment("AK1");
        writer.writeElement("HC");                              // AK101 functional ID code being acknowledged (HC = health care claim)
        writer.writeElement(EdiEnvelopeWriter.nz(gsControlNumber, "0"));          // AK102 group control number being acknowledged
        writer.writeEndSegment();
        segmentCount++;

        int accepted = 0;
        int rejected = 0;
        for (TransactionAckStatus tx : transactions) {
            // ----- AK2 — transaction set response header -----
            writer.writeStartSegment("AK2");
            writer.writeElement("837");                          // AK201 transaction set ID code
            writer.writeElement(EdiEnvelopeWriter.nz(tx.getStControlNumber(), "0")); // AK202 transaction set control number
            writer.writeEndSegment();
            segmentCount++;

            // ----- AK5 — transaction set response trailer -----
            writer.writeStartSegment("AK5");
            writer.writeElement(tx.isAccepted() ? "A" : "R");    // AK501 transaction set acknowledgment code
            writer.writeEndSegment();
            segmentCount++;

            if (tx.isAccepted()) { accepted++; } else { rejected++; }
        }

        // ----- AK9 — functional group response trailer -----
        String groupAckCode = rejected == 0 ? "A" : (accepted == 0 ? "R" : "P");
        writer.writeStartSegment("AK9");
        writer.writeElement(groupAckCode);                       // AK901 functional group acknowledge code
        writer.writeElement(String.valueOf(transactions.size())); // AK902 number of TS included
        writer.writeElement(String.valueOf(transactions.size())); // AK903 number of TS received
        writer.writeElement(String.valueOf(accepted));             // AK904 number of TS accepted
        writer.writeEndSegment();
        segmentCount++;

        // ----- SE -----
        segmentCount++;
        writer.writeStartSegment("SE");
        writer.writeElement(String.valueOf(segmentCount));
        writer.writeElement("0001");
        writer.writeEndSegment();

        EdiEnvelopeWriter.writeGeAndIea(writer, isaPadded);

        writer.endInterchange();
        writer.flush();
        writer.close();

        return baos.toString("UTF-8");
    }

    // -------------------------------------------------------------------------
    // EDI construction — TA1
    // -------------------------------------------------------------------------

    private String buildTa1(String isaControlNumber, boolean accepted, String noteCode)
            throws EDIStreamException, IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        EDIOutputFactory factory = EDIOutputFactory.newFactory();
        EDIStreamWriter writer = factory.createEDIStreamWriter(baos);

        Date now = new Date();
        String dateYyMmDd = EdiEnvelopeWriter.fmt(now, "yyMMdd");
        String timeHhMm   = EdiEnvelopeWriter.fmt(now, "HHmm");
        // Per X12 convention, an interchange control number that could not be recovered
        // is reported as all-nines/zeros; we use zeros to signal "unknown" unambiguously.
        String echoedControlNumber = (isaControlNumber != null && !isaControlNumber.trim().isEmpty())
            ? pad9(isaControlNumber.trim()) : "000000000";
        String ourControlNumber = String.format("%09d", Math.abs(echoedControlNumber.hashCode()) % 1000000000);

        writer.startInterchange();
        EdiEnvelopeWriter.writeIsa(writer, dateYyMmDd, timeHhMm, ourControlNumber, productionMode);

        // ----- TA1 — interchange acknowledgment (stands alone; no GS/ST wrapper) -----
        writer.writeStartSegment("TA1");
        writer.writeElement(echoedControlNumber);        // TA101 interchange control number being acknowledged
        writer.writeElement(dateYyMmDd);                 // TA102 interchange date
        writer.writeElement(timeHhMm);                   // TA103 interchange time
        writer.writeElement(accepted ? "A" : "R");        // TA104 interchange acknowledgment code
        writer.writeElement(noteCode != null ? noteCode : (accepted ? "000" : "029")); // TA105 interchange note code
        writer.writeEndSegment();

        EdiEnvelopeWriter.writeIeaNoGroup(writer, ourControlNumber);

        writer.endInterchange();
        writer.flush();
        writer.close();

        return baos.toString("UTF-8");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Left-pads/truncates a control number to X12's 9-character ISA13 width. */
    private String pad9(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) { digits = "0"; }
        if (digits.length() > 9) { return digits.substring(digits.length() - 9); }
        return String.format("%9s", digits).replace(' ', '0');
    }

    private void persistToDisk(String kind, String reference, String edi) throws IOException {
        if (ediOutputPath == null || ediOutputPath.trim().isEmpty()) {
            return;
        }
        String filename = ediOutputPath + "/" + kind.toLowerCase() + "-" + reference + ".edi";
        Files.write(Paths.get(filename), edi.getBytes("UTF-8"));
        LOG.info(kind + " written to " + filename);
    }

    // -------------------------------------------------------------------------
    // Ack status carrier
    // -------------------------------------------------------------------------

    /** Accept/reject outcome for one inbound ST..SE transaction, keyed by its ST02 control number. */
    public static class TransactionAckStatus {
        private final String stControlNumber;
        private final boolean accepted;

        public TransactionAckStatus(String stControlNumber, boolean accepted) {
            this.stControlNumber = stControlNumber;
            this.accepted = accepted;
        }

        public String getStControlNumber() { return stControlNumber; }
        public boolean isAccepted() { return accepted; }
    }
}
