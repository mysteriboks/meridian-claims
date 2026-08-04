package com.meridian.claims.intake;

import com.meridian.claims.controller.SubmitClaimRequest;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.Provider;
import com.meridian.claims.service.MemberService;
import com.meridian.claims.service.ProviderService;
import com.meridian.claims.util.ValidationUtil;
import io.xlate.edi.stream.EDIInputFactory;
import io.xlate.edi.stream.EDIStreamEvent;
import io.xlate.edi.stream.EDIStreamReader;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import com.meridian.claims.util.Money;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Parses X12 EDI 837P (professional) and 837I (institutional) files into
 * SubmitClaimRequest objects.
 *
 * One 837 interchange may contain multiple ST..SE transactions; each
 * transaction maps to one SubmitClaimRequest.  Per-transaction validation
 * failures are quarantined in ClaimFileParseResult so a single bad transaction
 * never aborts the rest of the file.  Only a structurally unparseable interchange
 * (ISA parse failure, completely garbled content) is a file-level failure and
 * throws IntakeParseException.
 *
 * Segment/element mappings (element positions are 1-based):
 *   ISA[13]           → externalReference (interchange control number); also captured on the
 *                       parse result (Phase 12: TA1/999 interchange-level acknowledgment)
 *   GS[6]             → functional group control number, captured on the parse result
 *                       (Phase 12: 999 AK1 group response header)
 *   ST[2]             → transaction set control number, captured per-record (Phase 12:
 *                       999 AK2/AK5 and 277CA STC correlator)
 *   NM1 qualifier IL  → member: NM1[9] = member number
 *   NM1 qualifier 82  → rendering provider: NM1[9] = NPI
 *   DTP qualifier 472 → date of service: DTP[3] (yyyyMMdd or yyyy-MM-dd)
 *   SBR[1] P/S        → coverage order PRIMARY/SECONDARY
 *   CLM[1]            → claim identifier (logged only)
 *   HI                → ICD-10 diagnoses (component 1 = qualifier:code)
 *   SV1               → professional service: SV1[1] sub-element 2 = CPT, SV1[2] = charge
 *   SV2               → institutional service: SV2[1] = revenue code (used as procedure), SV2[3] = charge
 */
@Component
public class X12Edi837Parser implements ClaimFileParser {

    private static final Logger LOG = Logger.getLogger(X12Edi837Parser.class);

    private final MemberService memberService;
    private final ProviderService providerService;

    @Autowired
    public X12Edi837Parser(MemberService memberService, ProviderService providerService) {
        this.memberService = memberService;
        this.providerService = providerService;
    }

    // -------------------------------------------------------------------------
    // ClaimFileParser implementation
    // -------------------------------------------------------------------------

    @Override
    public ClaimFileParseResult parse(String fileContent) throws IntakeParseException {
        if (fileContent == null || fileContent.trim().isEmpty()) {
            throw new IntakeParseException("File content is empty");
        }

        ClaimFileParseResult result = new ClaimFileParseResult();
        EDIInputFactory factory = EDIInputFactory.newFactory();
        EDIStreamReader reader = null;
        try {
            InputStream is = new ByteArrayInputStream(fileContent.getBytes("UTF-8"));
            reader = factory.createEDIStreamReader(is);
            processInterchange(reader, result);
        } catch (IntakeParseException e) {
            throw e;
        } catch (Exception e) {
            throw new IntakeParseException("EDI file could not be parsed: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                    // best-effort close
                }
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Interchange-level loop
    // -------------------------------------------------------------------------

    private void processInterchange(EDIStreamReader reader, ClaimFileParseResult result)
            throws Exception {
        // Mutable state across all transactions in this interchange.
        String isaControlNumber = null;
        String gsControlNumber = null;

        // Per-segment tracking.
        String currentSegment = null;
        int elementPosition = 0;      // 1-based position of current element within segment
        int componentPosition = 0;    // 1-based position within a composite element
        boolean insideComponent = false;

        // Per-transaction accumulator state.
        TransactionState tx = null;
        int txIndex = 0;              // 1-based counter for record error reporting

        while (reader.hasNext()) {
            EDIStreamEvent event;
            try {
                event = reader.next();
            } catch (Exception e) {
                if (isaControlNumber == null) {
                    // Failed before we even finished the ISA — file-level failure.
                    throw new IntakeParseException(
                        "EDI interchange header (ISA) could not be read: " + e.getMessage(), e);
                }
                // Failure mid-file: treat remaining content as unprocessable.
                LOG.error("EDI stream error after ISA " + isaControlNumber
                    + "; abandoning remaining transactions: " + e.getMessage());
                break;
            }

            switch (event) {

                case START_INTERCHANGE:
                    // Nothing to collect yet; ISA elements arrive as ELEMENT_DATA events
                    // after this event.
                    currentSegment = "ISA";
                    elementPosition = 0;
                    componentPosition = 0;
                    insideComponent = false;
                    break;

                case END_INTERCHANGE:
                    currentSegment = null;
                    break;

                case START_GROUP:
                case END_GROUP:
                    // GS/GE — nothing to map.
                    break;

                case START_TRANSACTION:
                    txIndex++;
                    tx = new TransactionState();
                    tx.isaControlNumber = isaControlNumber;
                    currentSegment = null;
                    elementPosition = 0;
                    componentPosition = 0;
                    insideComponent = false;
                    break;

                case END_TRANSACTION:
                    if (tx != null) {
                        try {
                            SubmitClaimRequest req = buildRequest(tx, txIndex);
                            result.addClaim(req, tx.stControlNumber);
                        } catch (IntakeParseException e) {
                            LOG.warn("Record " + txIndex + " quarantined: "
                                + com.meridian.claims.util.LogMaskUtil.maskMemberNumber(e.getMessage()));
                            result.addRecordError(txIndex, e.getMessage(), tx.stControlNumber);
                        }
                        tx = null;
                    }
                    currentSegment = null;
                    break;

                case START_SEGMENT:
                    currentSegment = reader.getText();
                    elementPosition = 0;
                    componentPosition = 0;
                    insideComponent = false;
                    break;

                case END_SEGMENT:
                    break;

                case START_COMPOSITE:
                    // A composite element counts as one element position.
                    elementPosition++;
                    insideComponent = true;
                    componentPosition = 0;
                    break;

                case END_COMPOSITE:
                    insideComponent = false;
                    break;

                case ELEMENT_DATA:
                    String value = reader.getText();
                    if (insideComponent) {
                        componentPosition++;
                    } else {
                        elementPosition++;
                        componentPosition = 0;
                    }
                    handleElementData(currentSegment, elementPosition,
                                      componentPosition, insideComponent,
                                      value, tx, isaControlNumber);

                    // Capture ISA13 while we are still at the interchange level
                    // (tx is null during ISA). Propagated onto the result for Phase 12
                    // TA1/999 acknowledgment generation.
                    if ("ISA".equals(currentSegment) && elementPosition == 13 && !insideComponent) {
                        isaControlNumber = value.trim();
                        result.setIsaControlNumber(isaControlNumber);
                    }
                    // Capture GS06 (functional group control number) — same rationale as ISA13.
                    if ("GS".equals(currentSegment) && elementPosition == 6 && !insideComponent) {
                        gsControlNumber = value.trim();
                        result.setGsControlNumber(gsControlNumber);
                    }
                    break;

                default:
                    // START_LOOP, END_LOOP, SEGMENT_ERROR, etc. — ignore.
                    break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Element data dispatch
    // -------------------------------------------------------------------------

    /**
     * Routes one element (or sub-element) data value into the appropriate
     * field of the current transaction accumulator.
     *
     * @param seg            current segment tag (may be null between segments)
     * @param elemPos        1-based element position within the segment
     * @param compPos        1-based sub-element position (only meaningful when inComp=true)
     * @param inComp         true when inside a composite element
     * @param value          the data value
     * @param tx             current transaction accumulator (null between transactions)
     * @param isaControlNum  interchange control number captured from ISA13
     */
    private void handleElementData(String seg, int elemPos, int compPos,
                                   boolean inComp, String value,
                                   TransactionState tx, String isaControlNum) {
        if (seg == null || value == null) return;
        value = value.trim();

        // ISA13 is captured directly in processInterchange; nothing else to do here.
        if ("ISA".equals(seg)) return;

        // All segment handling below requires an active transaction.
        if (tx == null) return;

        if ("ST".equals(seg)) {
            // ST02 = transaction set control number — the correlator for Phase 12
            // 999 AK2/AK5 and 277CA STC acknowledgment segments.
            if (elemPos == 2) {
                tx.stControlNumber = value;
            }

        } else if ("NM1".equals(seg)) {
            handleNm1(tx, elemPos, value);

        } else if ("DTP".equals(seg)) {
            handleDtp(tx, elemPos, value);

        } else if ("SBR".equals(seg)) {
            if (elemPos == 1) {
                if ("P".equalsIgnoreCase(value)) {
                    tx.coverageOrder = "PRIMARY";
                } else if ("S".equalsIgnoreCase(value)) {
                    tx.coverageOrder = "SECONDARY";
                } else {
                    tx.coverageOrder = "INVALID";
                }
            }

        } else if ("CLM".equals(seg)) {
            // CLM01 = claim identifier — not mapped to SubmitClaimRequest; ClaimService generates its own claim number.

        } else if ("HI".equals(seg)) {
            handleHi(tx, elemPos, compPos, inComp, value);

        } else if ("SV1".equals(seg)) {
            handleSv1(tx, elemPos, compPos, inComp, value);

        } else if ("SV2".equals(seg)) {
            handleSv2(tx, elemPos, value, inComp);

        } else if ("AMT".equals(seg)) {
            // AMT*D = prior payments (primary payer paid) — required for COB SECONDARY claims.
            if (elemPos == 1 && "D".equalsIgnoreCase(value)) {
                tx.pendingAmtQualifier = "D";
            } else if (elemPos == 2 && "D".equals(tx.pendingAmtQualifier)) {
                tx.cobPrimaryPaid = value;
                tx.pendingAmtQualifier = null;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Per-segment handlers
    // -------------------------------------------------------------------------

    /** NM1: name segment.  Element 1 = entity qualifier; element 9 = identifier. */
    private void handleNm1(TransactionState tx, int elemPos, String value) {
        if (elemPos == 1) {
            // Store the qualifier so we can use it when element 9 arrives.
            tx.currentNm1Qualifier = value;
        } else if (elemPos == 9) {
            String qualifier = tx.currentNm1Qualifier;
            if ("IL".equalsIgnoreCase(qualifier)) {
                // Insured / subscriber → member number
                tx.memberNumber = value;
            } else if ("82".equals(qualifier) || "85".equals(qualifier)
                    || "77".equals(qualifier)) {
                // 82 = rendering provider, 85 = billing provider, 77 = service facility
                // Accept any of these as the NPI source; last one wins if multiple present.
                tx.npi = value;
            }
        }
    }

    /** DTP: date/time segment.  Element 1 = qualifier; element 3 = date value. */
    private void handleDtp(TransactionState tx, int elemPos, String value) {
        if (elemPos == 1) {
            tx.currentDtpQualifier = value;
        } else if (elemPos == 3) {
            if ("472".equals(tx.currentDtpQualifier)) {
                tx.dateOfServiceString = value;
            }
        }
    }

    /**
     * HI: health care information (diagnosis) codes.
     *
     * Each element in HI is a composite: component 1 = qualifier + ":" + code
     * (e.g. "ABK:E119" — qualifier ABK = ICD-10-CM principal, ABF = ICD-10-CM
     * additional).  The qualifier prefix is stripped before recording the code.
     * The first HI code encountered becomes PRIMARY; the rest become SECONDARY.
     */
    private void handleHi(TransactionState tx, int elemPos,
                          int compPos, boolean inComp, String value) {
        String code = null;

        if (inComp) {
            // StAEDI splits "ABK:E119" into two sub-elements:
            //   compPos=1 → qualifier "ABK"  (skip)
            //   compPos=2 → code "E119"      (use this)
            if (compPos == 2) {
                code = value.trim();
            }
        } else {
            // Non-composite delivery: whole value is "ABK:E119" — strip qualifier.
            int colonIdx = value.indexOf(':');
            if (colonIdx >= 0) {
                code = value.substring(colonIdx + 1).trim();
            } else {
                code = value.trim();
            }
        }

        if (code == null || code.isEmpty()) return;
        // Only accept codes that start with a letter (ICD-10 style).
        if (!Character.isLetter(code.charAt(0))) return;
        // X12 EDI sends ICD-10 without the dot (e.g. "E119" not "E11.9").
        // Normalize: insert dot after 3rd char when code is 4+ chars and has no dot.
        if (code.length() > 3 && code.indexOf('.') < 0) {
            code = code.substring(0, 3) + "." + code.substring(3);
        }
        tx.diagnosisCodes.add(code);
    }

    /**
     * SV1: professional service line.
     *
     * Element 1 is a composite: sub-element 1 = qualifier (e.g. "HC"),
     * sub-element 2 = CPT code (e.g. "99213").
     * Element 2 = monetary amount (line charge).
     */
    private void handleSv1(TransactionState tx, int elemPos,
                           int compPos, boolean inComp, String value) {
        if (elemPos == 1) {
            if (inComp && compPos == 2) {
                // StAEDI splits "HC:99213" into compPos=1 (HC) and compPos=2 (99213).
                // compPos=2 is the actual CPT code.
                tx.pendingCptCode = value.trim();
            } else if (!inComp) {
                // Non-composite delivery: whole value is "HC:99213" or just "99213".
                int colonIdx = value.indexOf(':');
                if (colonIdx >= 0) {
                    tx.pendingCptCode = value.substring(colonIdx + 1).trim();
                } else {
                    tx.pendingCptCode = value.trim();
                }
            }
        } else if (elemPos == 2 && !inComp) {
            // Line charge — record the pending line item.
            String cpt = tx.pendingCptCode;
            tx.pendingCptCode = null;
            if (cpt != null && !cpt.isEmpty() && !value.isEmpty()) {
                TransactionState.LineItemAccumulator item =
                    new TransactionState.LineItemAccumulator();
                item.procedureCode = cpt;
                item.chargeString = value;
                tx.lineItems.add(item);
            }
        }
    }

    /**
     * SV2: institutional service line.
     *
     * Element 1 = revenue code (used as procedure code).
     * Element 3 = monetary amount (line charge).
     */
    private void handleSv2(TransactionState tx, int elemPos,
                           String value, boolean inComp) {
        if (inComp) return; // SV2 elements of interest are not composites
        if (elemPos == 1) {
            tx.pendingRevenueCode = value;
        } else if (elemPos == 3) {
            String revenueCode = tx.pendingRevenueCode;
            tx.pendingRevenueCode = null;
            if (revenueCode != null && !revenueCode.isEmpty() && !value.isEmpty()) {
                TransactionState.LineItemAccumulator item =
                    new TransactionState.LineItemAccumulator();
                item.procedureCode = revenueCode;
                item.chargeString = value;
                tx.lineItems.add(item);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Transaction assembly and validation
    // -------------------------------------------------------------------------

    /**
     * Convert the raw accumulated transaction state into a validated
     * SubmitClaimRequest.  Throws IntakeParseException for any per-record
     * validation failure.
     */
    private SubmitClaimRequest buildRequest(TransactionState tx, int txIndex)
            throws IntakeParseException {

        SubmitClaimRequest req = new SubmitClaimRequest();

        // ---- external reference (ISA control number) ----
        if (tx.isaControlNumber != null) {
            req.setExternalReference(tx.isaControlNumber);
        }

        // ---- coverage order + COB ----
        req.setCoverageOrder(tx.coverageOrder);
        if (!"PRIMARY".equals(tx.coverageOrder) && !"SECONDARY".equals(tx.coverageOrder)) {
            throw new IntakeParseException(
                "Record " + txIndex + ": unrecognised SBR coverage order '"
                + tx.coverageOrder + "' (expected P or S)");
        }
        if ("SECONDARY".equals(tx.coverageOrder)) {
            if (tx.cobPrimaryPaid == null || tx.cobPrimaryPaid.isEmpty()) {
                throw new IntakeParseException(
                    "Record " + txIndex + ": SECONDARY claim requires AMT*D (primary payer paid) segment");
            }
            req.setCobPrimaryPaid(parseCharge(tx.cobPrimaryPaid, txIndex, 0));
        }

        // ---- member resolution ----
        if (tx.memberNumber == null || tx.memberNumber.isEmpty()) {
            throw new IntakeParseException(
                "Record " + txIndex + ": member number is missing (no NM1*IL segment)");
        }
        Member member = memberService.findByMemberNumber(tx.memberNumber);
        if (member == null) {
            throw new IntakeParseException(
                "Record " + txIndex + ": member number '" + tx.memberNumber + "' not found");
        }
        req.setMemberId(member.getId());

        // ---- provider resolution ----
        String npiErr = ValidationUtil.validateNpi(tx.npi);
        if (npiErr != null) {
            throw new IntakeParseException("Record " + txIndex + ": " + npiErr);
        }
        Provider provider = providerService.findByNpi(tx.npi);
        if (provider == null) {
            throw new IntakeParseException(
                "Record " + txIndex + ": provider NPI '" + tx.npi + "' not found");
        }
        req.setProviderId(provider.getId());

        // ---- date of service ----
        req.setDateOfService(parseDateOfService(tx.dateOfServiceString, txIndex));

        // ---- diagnoses ----
        if (tx.diagnosisCodes.isEmpty()) {
            throw new IntakeParseException(
                "Record " + txIndex + ": no diagnosis codes found (no HI segment)");
        }
        List<SubmitClaimRequest.DiagnosisRow> diagnosisRows =
            new ArrayList<SubmitClaimRequest.DiagnosisRow>();
        for (int i = 0; i < tx.diagnosisCodes.size(); i++) {
            String code = tx.diagnosisCodes.get(i);
            String diagErr = ValidationUtil.validateDiagnosisCode(code);
            if (diagErr != null) {
                throw new IntakeParseException(
                    "Record " + txIndex + " diagnosis[" + (i + 1) + "]: " + diagErr);
            }
            SubmitClaimRequest.DiagnosisRow row = new SubmitClaimRequest.DiagnosisRow();
            row.setDiagnosisCode(code);
            row.setDiagnosisType(i == 0 ? "PRIMARY" : "SECONDARY");
            diagnosisRows.add(row);
        }
        req.setDiagnoses(diagnosisRows);

        // ---- line items ----
        if (tx.lineItems.isEmpty()) {
            throw new IntakeParseException(
                "Record " + txIndex + ": no line items found (no SV1/SV2 segment)");
        }
        List<SubmitClaimRequest.LineItemRow> lineItemRows =
            new ArrayList<SubmitClaimRequest.LineItemRow>();
        for (int i = 0; i < tx.lineItems.size(); i++) {
            TransactionState.LineItemAccumulator acc = tx.lineItems.get(i);
            String cptErr = ValidationUtil.validateProcedureCode(acc.procedureCode);
            if (cptErr != null) {
                throw new IntakeParseException(
                    "Record " + txIndex + " item[" + (i + 1) + "]: " + cptErr);
            }
            BigDecimal amount = parseCharge(acc.chargeString, txIndex, i + 1);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IntakeParseException(
                    "Record " + txIndex + " item[" + (i + 1) + "]: charge amount must be > 0");
            }
            SubmitClaimRequest.LineItemRow row = new SubmitClaimRequest.LineItemRow();
            row.setProcedureCode(acc.procedureCode);
            row.setBilledAmount(amount);
            lineItemRows.add(row);
        }
        req.setLineItems(lineItemRows);

        return req;
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    private Date parseDateOfService(String dateStr, int txIndex) throws IntakeParseException {
        if (dateStr == null || dateStr.isEmpty()) {
            throw new IntakeParseException(
                "Record " + txIndex + ": date of service is missing (no DTP*472 segment)");
        }
        // Accept yyyyMMdd (EDI compact format) and yyyy-MM-dd (ISO format).
        String[] patterns = {"yyyyMMdd", "yyyy-MM-dd"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                sdf.setLenient(false);
                return sdf.parse(dateStr);
            } catch (ParseException e) {
                // Try next pattern.
            }
        }
        throw new IntakeParseException(
            "Record " + txIndex + ": invalid date of service '" + dateStr
            + "' (expected yyyyMMdd or yyyy-MM-dd)");
    }

    private BigDecimal parseCharge(String chargeStr, int txIndex, int itemIndex)
            throws IntakeParseException {
        if (chargeStr == null || chargeStr.isEmpty()) {
            throw new IntakeParseException(
                "Record " + txIndex + " item[" + itemIndex + "]: charge amount is missing");
        }
        try {
            return Money.of(chargeStr);
        } catch (NumberFormatException e) {
            throw new IntakeParseException(
                "Record " + txIndex + " item[" + itemIndex + "]: charge amount '"
                + chargeStr + "' is not a valid number");
        }
    }

    // -------------------------------------------------------------------------
    // Transaction accumulator (mutable state for one ST..SE loop)
    // -------------------------------------------------------------------------

    /**
     * All mutable state collected while reading one ST..SE transaction.
     * Reset to a fresh instance at each START_TRANSACTION event.
     */
    private static class TransactionState {

        String isaControlNumber;

        // ST02 — transaction set control number (Phase 12 ack correlator)
        String stControlNumber;

        // NM1 tracking
        String currentNm1Qualifier;
        String memberNumber;
        String npi;

        // DTP tracking
        String currentDtpQualifier;
        String dateOfServiceString;

        // SBR
        String coverageOrder = "PRIMARY";

        // HI: ordered list of ICD-10 codes; index 0 = PRIMARY
        List<String> diagnosisCodes = new ArrayList<String>();

        // SV1 / SV2 pending state
        String pendingCptCode;
        String pendingRevenueCode;

        // AMT segment pending qualifier
        String pendingAmtQualifier;

        // COB: primary payer paid amount (AMT*D) — required for SECONDARY claims
        String cobPrimaryPaid;

        // Collected line items
        List<LineItemAccumulator> lineItems = new ArrayList<LineItemAccumulator>();

        static class LineItemAccumulator {
            String procedureCode;
            String chargeString;
        }
    }
}
