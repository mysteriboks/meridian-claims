package com.meridian.claims.service;

import com.meridian.claims.model.ClaimStatus;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates outbound X12 277 (Health Care Claim Status Response) files answering
 * an inbound 276 inquiry (Phase 14). Same structural-simplification precedent as
 * {@link Edi277CaGenerator} (flat STC entry per transaction, no full HL-loop
 * hierarchy) and reuses {@link EdiEnvelopeWriter} for the envelope.
 */
@Service
public class Edi277Generator {

    private static final Logger LOG = Logger.getLogger(Edi277Generator.class);

    // Representative Health Care Claim Status Category/Status code pairs (STC01 composite).
    // Chosen to be recognisable and internally consistent with ClaimStatus, not asserted as
    // code-list-perfect — no real trading partner validates this file yet (same precedent as
    // Edi277CaGenerator's STC_ACCEPTED/STC_REJECTED).
    private static final String STC_NOT_FOUND = "A4:1"; // Acknowledgement/Not Found

    private static final Map<ClaimStatus, String> STC_BY_STATUS = new HashMap<ClaimStatus, String>();
    static {
        STC_BY_STATUS.put(ClaimStatus.SUBMITTED,       "A1:1");  // Acknowledgement/Receipt
        STC_BY_STATUS.put(ClaimStatus.IN_REVIEW,       "A6:75"); // Acknowledgement/Pended — under review
        STC_BY_STATUS.put(ClaimStatus.PENDING_INFO,    "A6:35"); // Acknowledgement/Pended — additional info required
        STC_BY_STATUS.put(ClaimStatus.APPROVED,        "F1:1");  // Finalized/Payment — approved, processing
        STC_BY_STATUS.put(ClaimStatus.PENDING_PAYMENT, "F1:1");  // Finalized/Payment — approved, awaiting batch
        STC_BY_STATUS.put(ClaimStatus.IN_BATCH,        "F1:1");  // Finalized/Payment — in payment batch
        STC_BY_STATUS.put(ClaimStatus.PAID,            "F1:2");  // Finalized/Payment — paid
        STC_BY_STATUS.put(ClaimStatus.DENIED,          "F2:1");  // Finalized/Denial
        STC_BY_STATUS.put(ClaimStatus.VOIDED,          "F3:1");  // Finalized/Revised — voided
        STC_BY_STATUS.put(ClaimStatus.REPLACED,        "F3:2");  // Finalized/Revised — replaced by corrected claim
        STC_BY_STATUS.put(ClaimStatus.ABANDONED,       "A3:21"); // Acknowledgement/Rejected — abandoned
    }

    @Value("${claims.integration.edi.output.path:}")
    private String ediOutputPath;

    @Value("${claims.integration.edi.production:false}")
    private boolean productionMode;

    /** Returns the STC category:status pair for a claim status; never null. */
    public static String stcFor(ClaimStatus status) {
        String stc = STC_BY_STATUS.get(status);
        return stc != null ? stc : STC_NOT_FOUND;
    }

    /**
     * Generates a 277 response for one inbound 276 interchange.
     *
     * @param isaControlNumber the inbound file's ISA13
     * @param results          one entry per inquiry transaction in the inbound file
     * @return the 277 EDI string
     */
    public String generate277(String isaControlNumber, List<ClaimStatusResult> results) {
        try {
            String edi = buildEdi(isaControlNumber, results);
            persistToDisk(EdiEnvelopeWriter.safeRef(isaControlNumber), edi);
            return edi;
        } catch (EDIStreamException e) {
            LOG.error("EDI 277 generation failed isaControlNumber=" + isaControlNumber, e);
            throw new ServiceException("Failed to generate EDI 277 for ISA " + isaControlNumber, e);
        } catch (IOException e) {
            LOG.error("EDI 277 disk write failed isaControlNumber=" + isaControlNumber, e);
            throw new ServiceException("Failed to write EDI 277 file for ISA " + isaControlNumber, e);
        }
    }

    private String buildEdi(String isaControlNumber, List<ClaimStatusResult> results)
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
        writer.writeElement("005010X212");   // GS08 version/release identifier (276/277)
        writer.writeEndSegment();

        // ----- ST -----
        writer.writeStartSegment("ST");
        writer.writeElement("277");
        writer.writeElement("0001");
        writer.writeEndSegment();
        segmentCount++;

        // ----- BHT -----
        writer.writeStartSegment("BHT");
        writer.writeElement("0010");
        writer.writeElement("08");           // BHT02 transaction set purpose code (08 = status)
        writer.writeElement(isaRef);
        writer.writeElement(dateYyyyMmDd);
        writer.writeElement(timeHhMm);
        writer.writeEndSegment();
        segmentCount++;

        for (ClaimStatusResult result : results) {
            segmentCount += EdiEnvelopeWriter.writeTrnAndStc(writer, result.getStControlNumber(),
                result.isFound() ? stcFor(result.getStatus()) : STC_NOT_FOUND, dateYyyyMmDd);

            if (result.isFound() && result.getClaimNumber() != null) {
                // REF*1K — payer claim control number (our own claim number)
                writer.writeStartSegment("REF");
                writer.writeElement("1K");
                writer.writeElement(result.getClaimNumber());
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

    private void persistToDisk(String reference, String edi) throws IOException {
        if (ediOutputPath == null || ediOutputPath.trim().isEmpty()) {
            return;
        }
        String filename = ediOutputPath + "/277-" + reference + ".edi";
        Files.write(Paths.get(filename), edi.getBytes("UTF-8"));
        LOG.info("277 written to " + filename);
    }

    /** Result of resolving one claim status inquiry against the claims table. */
    public static class ClaimStatusResult {
        private final String stControlNumber;
        private final boolean found;
        private final String claimNumber;
        private final ClaimStatus status;

        public static ClaimStatusResult found(String stControlNumber, String claimNumber, ClaimStatus status) {
            return new ClaimStatusResult(stControlNumber, true, claimNumber, status);
        }

        public static ClaimStatusResult notFound(String stControlNumber) {
            return new ClaimStatusResult(stControlNumber, false, null, null);
        }

        private ClaimStatusResult(String stControlNumber, boolean found, String claimNumber, ClaimStatus status) {
            this.stControlNumber = stControlNumber;
            this.found = found;
            this.claimNumber = claimNumber;
            this.status = status;
        }

        public String getStControlNumber() { return stControlNumber; }
        public boolean isFound() { return found; }
        public String getClaimNumber() { return claimNumber; }
        public ClaimStatus getStatus() { return status; }
    }
}
