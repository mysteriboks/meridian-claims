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
 * Generates outbound X12 277CA (Claim Acknowledgment) files, reporting
 * claim-level acceptance for an inbound 837 file, keyed to the interchange's
 * ISA13 (the same value stored on {@code claims.external_reference}, Phase 10).
 *
 * Structural simplification (same spirit as {@link Edi835Generator} and
 * {@link Edi999Generator}): the full 277CA implementation guide nests
 * information-source / information-receiver / billing-provider / patient HL
 * loops above the claim-status (STC) segment. This generator emits one flat
 * claim-status entry per transaction under a single BHT — structurally valid
 * X12, correlatable by ST02 and, for accepted claims, our own claim number —
 * without the full HL hierarchy a real trading-partner conformance test would
 * require. No real partner exists yet (see PHASES.md Phase 13).
 */
@Service
public class Edi277CaGenerator {

    private static final Logger LOG = Logger.getLogger(Edi277CaGenerator.class);

    // Representative Health Care Claim Status Category/Status code pairs (STC01
    // composite). Chosen to be recognisable, not asserted as code-list-perfect —
    // no real trading partner validates this file yet.
    private static final String STC_ACCEPTED = "A2:19"; // Acknowledgement/Acceptance into adjudication system
    private static final String STC_REJECTED = "A3:21"; // Acknowledgement/Rejected for Invalid Information

    @Value("${claims.integration.edi.output.path:}")
    private String ediOutputPath;

    @Value("${claims.integration.edi.production:false}")
    private boolean productionMode;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generates a 277CA for one inbound 837 interchange.
     *
     * @param isaControlNumber the inbound file's ISA13 — matches claims.external_reference
     * @param claims           one entry per transaction (accepted or rejected)
     * @return the 277CA EDI string
     */
    public String generate277Ca(String isaControlNumber, List<ClaimAckStatus> claims) {
        try {
            String edi = buildEdi(isaControlNumber, claims);
            persistToDisk(EdiEnvelopeWriter.safeRef(isaControlNumber), edi);
            return edi;
        } catch (EDIStreamException e) {
            LOG.error("277CA generation failed isaControlNumber=" + isaControlNumber, e);
            throw new ServiceException("Failed to generate 277CA for ISA " + isaControlNumber, e);
        } catch (IOException e) {
            LOG.error("277CA disk write failed isaControlNumber=" + isaControlNumber, e);
            throw new ServiceException("Failed to write 277CA file for ISA " + isaControlNumber, e);
        }
    }

    // -------------------------------------------------------------------------
    // EDI construction
    // -------------------------------------------------------------------------

    private String buildEdi(String isaControlNumber, List<ClaimAckStatus> claims)
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
        writer.writeElement("HN");           // GS01 functional identifier code (HN = health care claim status notification)
        writer.writeElement("MERIDIAN");
        writer.writeElement("PAYER");
        writer.writeElement(dateYyyyMmDd);
        writer.writeElement(timeHhMm);
        writer.writeElement("1");
        writer.writeElement("X");
        writer.writeElement("005010X214");   // GS08 version/release identifier (277CA)
        writer.writeEndSegment();

        // ----- ST -----
        writer.writeStartSegment("ST");
        writer.writeElement("277");
        writer.writeElement("0001");
        writer.writeEndSegment();
        segmentCount++;

        // ----- BHT — beginning of hierarchical transaction -----
        writer.writeStartSegment("BHT");
        writer.writeElement("0010");         // BHT01 hierarchical structure code
        writer.writeElement("08");           // BHT02 transaction set purpose code (08 = status)
        writer.writeElement(isaRef);         // BHT03 reference identification
        writer.writeElement(dateYyyyMmDd);   // BHT04 date
        writer.writeElement(timeHhMm);       // BHT05 time
        writer.writeEndSegment();
        segmentCount++;

        // ----- one claim-status entry per transaction -----
        for (ClaimAckStatus claim : claims) {
            segmentCount += EdiEnvelopeWriter.writeTrnAndStc(writer, claim.getStControlNumber(),
                claim.isAccepted() ? STC_ACCEPTED : STC_REJECTED, dateYyyyMmDd);

            if (claim.isAccepted() && claim.getClaimNumber() != null) {
                // REF*1K — payer claim control number (our own claim number)
                writer.writeStartSegment("REF");
                writer.writeElement("1K");
                writer.writeElement(claim.getClaimNumber());
                writer.writeEndSegment();
                segmentCount++;
            } else if (!claim.isAccepted() && claim.getReason() != null) {
                // NTE — free-text rejection reason
                writer.writeStartSegment("NTE");
                writer.writeElement("ADD");
                writer.writeElement(truncate(claim.getReason(), 80));
                writer.writeEndSegment();
                segmentCount++;
            }
        }

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
    // Helpers
    // -------------------------------------------------------------------------

    private String truncate(String value, int maxLen) {
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private void persistToDisk(String reference, String edi) throws IOException {
        if (ediOutputPath == null || ediOutputPath.trim().isEmpty()) {
            return;
        }
        String filename = ediOutputPath + "/277ca-" + reference + ".edi";
        Files.write(Paths.get(filename), edi.getBytes("UTF-8"));
        LOG.info("277CA written to " + filename);
    }

    // -------------------------------------------------------------------------
    // Claim status carrier
    // -------------------------------------------------------------------------

    /** Accept/reject outcome for one claim transaction within the inbound interchange. */
    public static class ClaimAckStatus {
        private final String stControlNumber;
        private final boolean accepted;
        private final String claimNumber;
        private final String reason;

        /** Accepted claim: carries the claim number we assigned on submission. */
        public static ClaimAckStatus accepted(String stControlNumber, String claimNumber) {
            return new ClaimAckStatus(stControlNumber, true, claimNumber, null);
        }

        /** Rejected/quarantined record: carries the reason for the reviewer/submitter. */
        public static ClaimAckStatus rejected(String stControlNumber, String reason) {
            return new ClaimAckStatus(stControlNumber, false, null, reason);
        }

        private ClaimAckStatus(String stControlNumber, boolean accepted, String claimNumber, String reason) {
            this.stControlNumber = stControlNumber;
            this.accepted = accepted;
            this.claimNumber = claimNumber;
            this.reason = reason;
        }

        public String getStControlNumber() { return stControlNumber; }
        public boolean isAccepted() { return accepted; }
        public String getClaimNumber() { return claimNumber; }
        public String getReason() { return reason; }
    }
}
