package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.RemittanceBatch;
import com.meridian.claims.model.RemittanceBatchItem;
import io.xlate.edi.stream.EDIOutputFactory;
import io.xlate.edi.stream.EDIStreamException;
import io.xlate.edi.stream.EDIStreamWriter;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

/**
 * Generates outbound X12 835 (Health Care Claim Payment/Advice) EDI files
 * from a RemittanceBatch and its RemittanceBatchItems.
 *
 * The 835 is written using the StAEDI streaming writer.  A segment count is
 * accumulated across the ST..SE envelope so that SE01 contains the correct
 * count (ST and SE are both included in the count per X12 spec).
 *
 * If {@code claims.remittance.edi.output.path} is configured, the generated
 * file is also written to disk as {@code <path>/remittance-<batchId>.835}.
 */
@Service
public class Edi835Generator {

    private static final Logger LOG = Logger.getLogger(Edi835Generator.class);

    @Autowired
    private ClaimDAO claimDAO;

    @Value("${claims.remittance.edi.output.path:}")
    private String ediOutputPath;

    /**
     * ISA15 usage indicator: false = 'T' (test), true = 'P' (production).
     * Default false — safe for dev/staging. Set claims.remittance.edi.production=true
     * in the prod property overlay so live clearinghouses receive production-flagged files.
     */
    @Value("${claims.remittance.edi.production:false}")
    private boolean productionMode;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generates an X12 835 EDI string for the given batch and its items.
     *
     * @param batch the remittance batch (id, paymentDate, totalPaid)
     * @param items the line items belonging to this batch
     * @return the 835 EDI string
     * @throws ServiceException if EDI generation fails
     */
    public String generate(RemittanceBatch batch, List<RemittanceBatchItem> items) {
        return generate(batch, items, null);
    }

    /**
     * Generates an X12 835 EDI string, stamping TRN02 with {@code trnReassociationNumber}
     * when provided (Phase 18) — the same value written to the paired NACHA ACH file's
     * addenda record via {@link com.meridian.claims.util.AchFileWriter}, letting a
     * provider match the electronic deposit back to this remittance advice. Falls back
     * to the plain batch id (prior behavior) when null, e.g. for a batch that was paid
     * by check rather than ACH.
     */
    public String generate(RemittanceBatch batch, List<RemittanceBatchItem> items, String trnReassociationNumber) {
        try {
            String edi = buildEdi(batch, items, trnReassociationNumber);
            persistToDisk(batch.getId(), edi);
            return edi;
        } catch (EDIStreamException e) {
            LOG.error("EDI 835 generation failed for batchId=" + batch.getId(), e);
            throw new ServiceException("Failed to generate EDI 835 for batch " + batch.getId(), e);
        } catch (IOException e) {
            LOG.error("EDI 835 disk write failed for batchId=" + batch.getId(), e);
            throw new ServiceException("Failed to write EDI 835 file for batch " + batch.getId(), e);
        }
    }

    // -------------------------------------------------------------------------
    // EDI construction
    // -------------------------------------------------------------------------

    private String buildEdi(RemittanceBatch batch, List<RemittanceBatchItem> items, String trnReassociationNumber)
            throws EDIStreamException, IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        EDIOutputFactory factory = EDIOutputFactory.newFactory();
        EDIStreamWriter writer = factory.createEDIStreamWriter(baos);

        Date now = new Date();
        String dateYyyyMmDd = EdiEnvelopeWriter.fmt(now, "yyyyMMdd");
        String dateYyMmDd   = EdiEnvelopeWriter.fmt(now, "yyMMdd");
        String timeHhMm     = EdiEnvelopeWriter.fmt(now, "HHmm");
        String batchIdPadded = String.format("%09d", batch.getId());
        String batchIdStr    = String.valueOf(batch.getId());

        // The segment count begins at ST (inclusive) and ends at SE (inclusive).
        // We count every segment written inside the ST..SE envelope.
        int segmentCount = 0;

        writer.startInterchange();
        EdiEnvelopeWriter.writeIsa(writer, dateYyMmDd, timeHhMm, batchIdPadded, productionMode);

        // ----- GS -----
        writer.writeStartSegment("GS");
        writer.writeElement("HP");           // GS01 functional identifier code (HP = 835)
        writer.writeElement("MERIDIAN");     // GS02 application sender code
        writer.writeElement("PAYER");        // GS03 application receiver code
        writer.writeElement(dateYyyyMmDd);   // GS04 date (yyyyMMdd)
        writer.writeElement(timeHhMm);       // GS05 time (HHmm)
        writer.writeElement("1");            // GS06 group control number
        writer.writeElement("X");            // GS07 responsible agency code (X = ASC X12)
        writer.writeElement("005010X221A1"); // GS08 version/release identifier
        writer.writeEndSegment();

        // ----- ST -----
        writer.writeStartSegment("ST");
        writer.writeElement("835");          // ST01 transaction set identifier code
        writer.writeElement("0001");         // ST02 transaction set control number
        writer.writeEndSegment();
        segmentCount++;

        // ----- BPR -----
        BigDecimal totalPaid = sumPlanPaid(items);
        String paymentDateStr = batch.getPaymentDate() != null
            ? EdiEnvelopeWriter.fmt(batch.getPaymentDate(), "yyyyMMdd")
            : dateYyyyMmDd;

        boolean isAch = trnReassociationNumber != null && !trnReassociationNumber.trim().isEmpty();

        writer.writeStartSegment("BPR");
        writer.writeElement("I");                          // BPR01 transaction handling code
        writer.writeElement(totalPaid.toPlainString());    // BPR02 monetary amount (total paid)
        writer.writeElement("C");                          // BPR03 credit/debit flag
        writer.writeElement(isAch ? "ACH" : "CHK");         // BPR04 payment method code
        writer.writeEmptyElement();                        // BPR05
        writer.writeEmptyElement();                        // BPR06
        writer.writeEmptyElement();                        // BPR07
        writer.writeEmptyElement();                        // BPR08
        writer.writeEmptyElement();                        // BPR09
        writer.writeEmptyElement();                        // BPR10
        writer.writeEmptyElement();                        // BPR11
        writer.writeEmptyElement();                        // BPR12
        writer.writeEmptyElement();                        // BPR13
        writer.writeEmptyElement();                        // BPR14
        writer.writeEmptyElement();                        // BPR15
        writer.writeElement(paymentDateStr);               // BPR16 effective date
        writer.writeEndSegment();
        segmentCount++;

        // ----- TRN -----
        writer.writeStartSegment("TRN");
        writer.writeElement("1");            // TRN01 trace type code
        // TRN02: the EFT reassociation number (Phase 18) when this batch was paid via ACH —
        // the same value stamped on the NACHA addenda record — else the plain batch id.
        writer.writeElement(isAch ? trnReassociationNumber : batchIdStr);
        writer.writeElement("1234567890");   // TRN03 originating company identifier
        writer.writeEndSegment();
        segmentCount++;

        // ----- DTM*405 -----
        writer.writeStartSegment("DTM");
        writer.writeElement("405");          // DTM01 date/time qualifier (405 = production date)
        writer.writeElement(dateYyyyMmDd);   // DTM02 date (today)
        writer.writeEndSegment();
        segmentCount++;

        // ----- N1*PR (payer) -----
        writer.writeStartSegment("N1");
        writer.writeElement("PR");                  // N101 entity identifier code (PR = payer)
        writer.writeElement("MERIDIAN CLAIMS");     // N102 name
        writer.writeEndSegment();
        segmentCount++;

        // ----- N1*PE (payee) -----
        writer.writeStartSegment("N1");
        writer.writeElement("PE");           // N101 entity identifier code (PE = payee)
        writer.writeElement("PROVIDER");     // N102 name
        writer.writeEndSegment();
        segmentCount++;

        // ----- Claim-level loops -----
        for (RemittanceBatchItem item : items) {
            String claimNumber = resolveClaimNumber(item);
            String billed   = item.getBilled().toPlainString();
            String planPaid = item.getPlanPaid().toPlainString();

            // CLP — claim-level payment information
            writer.writeStartSegment("CLP");
            writer.writeElement(claimNumber);  // CLP01 patient control number
            writer.writeElement("1");          // CLP02 claim status code (1 = processed as primary)
            writer.writeElement(billed);       // CLP03 total claim charge amount
            writer.writeElement(planPaid);     // CLP04 claim payment amount
            writer.writeElement("");           // CLP05 patient responsibility amount (blank)
            writer.writeElement("MC");         // CLP06 claim filing indicator code (MC = Medicaid)
            writer.writeElement(claimNumber);  // CLP07 payer claim control number
            writer.writeEndSegment();
            segmentCount++;

            // SVC — service-level payment information
            // SVC01 is a composite: qualifier component + procedure code component
            writer.writeStartSegment("SVC");
            writer.writeStartElement();        // SVC01 composite: procedure code
            writer.writeComponent("HC");       // SVC01-1 product/service qualifier
            String procCode = item.getProcedureCode() != null ? item.getProcedureCode() : "00000";
            writer.writeComponent(procCode);   // SVC01-2 procedure code
            writer.endElement();
            writer.writeElement(billed);       // SVC02 line item charge amount
            writer.writeElement(planPaid);     // SVC03 line item provider payment amount
            writer.writeEndSegment();
            segmentCount++;

            // CAS — claim adjustment (only when an adjustment reason code is present)
            String carc = item.getAdjustmentReasonCode();
            if (carc != null && !carc.trim().isEmpty()) {
                BigDecimal adjustmentAmount = item.getBilled()
                    .subtract(item.getPlanPaid())
                    .setScale(2, RoundingMode.HALF_UP);
                writer.writeStartSegment("CAS");
                writer.writeElement("CO");                              // CAS01 claim adjustment group code
                writer.writeElement(carc);                              // CAS02 claim adjustment reason code
                writer.writeElement(adjustmentAmount.toPlainString()); // CAS03 adjustment amount
                writer.writeEndSegment();
                segmentCount++;
            }

            // AMT — service supplemental amount
            writer.writeStartSegment("AMT");
            writer.writeElement("AU");         // AMT01 amount qualifier code (AU = coverage amount)
            writer.writeElement(planPaid);     // AMT02 monetary amount
            writer.writeEndSegment();
            segmentCount++;
        }

        // ----- SE -----
        segmentCount++;  // SE itself counts as one segment
        writer.writeStartSegment("SE");
        writer.writeElement(String.valueOf(segmentCount)); // SE01 number of included segments
        writer.writeElement("0001");                        // SE02 transaction set control number
        writer.writeEndSegment();

        EdiEnvelopeWriter.writeGeAndIea(writer, batchIdPadded);

        writer.endInterchange();
        writer.flush();
        writer.close();

        return baos.toString("UTF-8");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Sums planPaid across all items; returns ZERO when the list is empty. */
    private BigDecimal sumPlanPaid(List<RemittanceBatchItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (RemittanceBatchItem item : items) {
            if (item.getPlanPaid() != null) {
                total = total.add(item.getPlanPaid());
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Looks up the claim number for a batch item.  Falls back to the item id
     * as a string if the claim cannot be found.
     */
    private String resolveClaimNumber(RemittanceBatchItem item) {
        try {
            Claim claim = claimDAO.findById(item.getClaimId());
            if (claim != null && claim.getClaimNumber() != null) {
                return claim.getClaimNumber();
            }
        } catch (Exception e) {
            LOG.warn("resolveClaimNumber: could not load claim id=" + item.getClaimId()
                + "; using item id as fallback. " + e.getMessage());
        }
        return String.valueOf(item.getId());
    }

    /** Writes the generated EDI string to disk if {@code ediOutputPath} is configured. */
    private void persistToDisk(int batchId, String edi) throws IOException {
        if (ediOutputPath == null || ediOutputPath.trim().isEmpty()) {
            return;
        }
        String filename = ediOutputPath + "/remittance-" + batchId + ".835";
        Files.write(Paths.get(filename), edi.getBytes("UTF-8"));
        LOG.info("EDI 835 written to " + filename);
    }
}
