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
 * Generates outbound X12 278 (Health Care Services Review — Response) files
 * answering an inbound 278 request (Phase 15). Same structural-simplification
 * precedent as {@link Edi277Generator}/{@link Edi277CaGenerator} — a flat
 * UM-based response entry per transaction, not the full HL-loop hierarchy.
 *
 * This app's prior-auth domain ({@code PriorAuthStatus}: ACTIVE/EXPIRED/VOIDED)
 * has no "pended" state, so — unlike a real UM review that can also return
 * pended/contact-payer — a request is either certified (A1) or not (A3); there
 * is no third outcome to report.
 */
@Service
public class Edi278ResponseGenerator {

    private static final Logger LOG = Logger.getLogger(Edi278ResponseGenerator.class);

    private static final String UM_CERTIFIED = "A1";     // Certified in total
    private static final String UM_NOT_CERTIFIED = "A3"; // Not certified

    @Value("${claims.integration.edi.output.path:}")
    private String ediOutputPath;

    @Value("${claims.integration.edi.production:false}")
    private boolean productionMode;

    public String generate278Response(String isaControlNumber, List<PriorAuthResult> results) {
        try {
            String edi = buildEdi(isaControlNumber, results);
            persistToDisk(EdiEnvelopeWriter.safeRef(isaControlNumber), edi);
            return edi;
        } catch (EDIStreamException e) {
            LOG.error("EDI 278 response generation failed isaControlNumber=" + isaControlNumber, e);
            throw new ServiceException("Failed to generate EDI 278 response for ISA " + isaControlNumber, e);
        } catch (IOException e) {
            LOG.error("EDI 278 response disk write failed isaControlNumber=" + isaControlNumber, e);
            throw new ServiceException("Failed to write EDI 278 response file for ISA " + isaControlNumber, e);
        }
    }

    private String buildEdi(String isaControlNumber, List<PriorAuthResult> results)
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
        writer.writeElement("HI");           // GS01 functional identifier code (HI = health care services review)
        writer.writeElement("MERIDIAN");
        writer.writeElement("PAYER");
        writer.writeElement(dateYyyyMmDd);
        writer.writeElement(timeHhMm);
        writer.writeElement("1");
        writer.writeElement("X");
        writer.writeElement("005010X217");   // GS08 version/release identifier (278)
        writer.writeEndSegment();

        // ----- ST -----
        writer.writeStartSegment("ST");
        writer.writeElement("278");
        writer.writeElement("0001");
        writer.writeEndSegment();
        segmentCount++;

        // ----- BHT -----
        writer.writeStartSegment("BHT");
        writer.writeElement("0007");
        writer.writeElement("11");           // BHT02 transaction set purpose code (11 = response)
        writer.writeElement(isaRef);
        writer.writeElement(dateYyyyMmDd);
        writer.writeElement(timeHhMm);
        writer.writeEndSegment();
        segmentCount++;

        for (PriorAuthResult result : results) {
            segmentCount += EdiEnvelopeWriter.writeTrn(writer, result.getStControlNumber());

            // UM — certification decision
            writer.writeStartSegment("UM");
            writer.writeElement(result.isApproved() ? UM_CERTIFIED : UM_NOT_CERTIFIED);
            writer.writeEndSegment();
            segmentCount++;

            if (result.isApproved() && result.getAuthNumber() != null) {
                // REF*1K — our own authorization number
                writer.writeStartSegment("REF");
                writer.writeElement("1K");
                writer.writeElement(result.getAuthNumber());
                writer.writeEndSegment();
                segmentCount++;
            } else if (!result.isApproved() && result.getReason() != null) {
                // NTE — free-text denial reason
                writer.writeStartSegment("NTE");
                writer.writeElement("ADD");
                writer.writeElement(truncate(result.getReason(), 80));
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

    private String truncate(String value, int maxLen) {
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private void persistToDisk(String reference, String edi) throws IOException {
        if (ediOutputPath == null || ediOutputPath.trim().isEmpty()) {
            return;
        }
        String filename = ediOutputPath + "/278resp-" + reference + ".edi";
        Files.write(Paths.get(filename), edi.getBytes("UTF-8"));
        LOG.info("278 response written to " + filename);
    }

    /** Certification outcome for one prior-auth request transaction. */
    public static class PriorAuthResult {
        private final String stControlNumber;
        private final boolean approved;
        private final String authNumber;
        private final String reason;

        public static PriorAuthResult approved(String stControlNumber, String authNumber) {
            return new PriorAuthResult(stControlNumber, true, authNumber, null);
        }

        public static PriorAuthResult denied(String stControlNumber, String reason) {
            return new PriorAuthResult(stControlNumber, false, null, reason);
        }

        private PriorAuthResult(String stControlNumber, boolean approved, String authNumber, String reason) {
            this.stControlNumber = stControlNumber;
            this.approved = approved;
            this.authNumber = authNumber;
            this.reason = reason;
        }

        public String getStControlNumber() { return stControlNumber; }
        public boolean isApproved() { return approved; }
        public String getAuthNumber() { return authNumber; }
        public String getReason() { return reason; }
    }
}
