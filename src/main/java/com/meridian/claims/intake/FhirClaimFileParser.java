package com.meridian.claims.intake;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.claims.controller.SubmitClaimRequest;
import com.meridian.claims.intake.fhir.FhirBundle;
import com.meridian.claims.intake.fhir.FhirClaim;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.Provider;
import com.meridian.claims.service.MemberService;
import com.meridian.claims.service.ProviderService;
import com.meridian.claims.util.ValidationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Parses FHIR R4 Claim JSON into SubmitClaimRequest objects.
 *
 * Accepts either a single FHIR Claim resource or a FHIR Bundle containing
 * Claim resources. Only a documented subset of the FHIR Claim fields is
 * mapped; all other fields are ignored.
 *
 * Mapped FHIR Claim → SubmitClaimRequest:
 *   patient.identifier.value      → member number → memberId
 *   provider.identifier.value     → NPI           → providerId
 *   billablePeriod.start          → dateOfService
 *   insurance[focal=true].sequence → coverage order (1=PRIMARY, else SECONDARY)
 *   diagnosis[].diagnosisCodeableConcept → diagnoses (ICD-10)
 *   item[].productOrService + item[].net → line items (CPT + billedAmount)
 */
@Component
public class FhirClaimFileParser implements ClaimFileParser {

    private final ObjectMapper objectMapper;
    private final MemberService memberService;
    private final ProviderService providerService;

    @Autowired
    public FhirClaimFileParser(MemberService memberService, ProviderService providerService) {
        this.memberService = memberService;
        this.providerService = providerService;
        this.objectMapper = new ObjectMapper();
        // Money is BigDecimal end-to-end: parse JSON numbers as BigDecimal (not double)
        // so monetary values never pass through a lossy IEEE-754 representation.
        this.objectMapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    @Override
    public ClaimFileParseResult parse(String fileContent) throws IntakeParseException {
        if (fileContent == null || fileContent.trim().isEmpty()) {
            throw new IntakeParseException("File content is empty");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(fileContent);
        } catch (Exception e) {
            throw new IntakeParseException("File is not valid JSON: " + e.getMessage(), e);
        }

        String resourceType = root.path("resourceType").asText(null);
        if (resourceType == null) {
            throw new IntakeParseException("File is missing 'resourceType' — not a FHIR resource");
        }

        ClaimFileParseResult result = new ClaimFileParseResult();
        if ("Bundle".equals(resourceType)) {
            parseBundle(root, result);
        } else if ("Claim".equals(resourceType)) {
            FhirClaim claim = deserializeClaim(root);
            mapClaimInto(claim, 1, result);
        } else {
            throw new IntakeParseException(
                "Unsupported FHIR resourceType '" + resourceType + "'; expected 'Claim' or 'Bundle'");
        }
        return result;
    }

    private void parseBundle(JsonNode bundleRoot, ClaimFileParseResult result)
            throws IntakeParseException {
        FhirBundle bundle;
        try {
            bundle = objectMapper.treeToValue(bundleRoot, FhirBundle.class);
        } catch (Exception e) {
            throw new IntakeParseException("Could not deserialize FHIR Bundle: " + e.getMessage(), e);
        }

        if (bundle.getEntry() == null || bundle.getEntry().isEmpty()) {
            return;
        }

        int index = 1;
        for (FhirBundle.Entry entry : bundle.getEntry()) {
            FhirClaim claim = entry.getResource();
            if (claim == null) {
                result.addRecordError(index, "Bundle entry has no resource");
                index++;
                continue;
            }
            if (!"Claim".equals(claim.getResourceType())) {
                result.addRecordError(index, "Bundle entry resourceType '"
                    + claim.getResourceType() + "' is not Claim");
                index++;
                continue;
            }
            mapClaimInto(claim, index, result);
            index++;
        }
    }

    /**
     * Map one FHIR Claim into the result: on success add the claim, on a
     * per-record validation failure record the error (do NOT throw — one bad
     * record must not abort the whole file).
     */
    private void mapClaimInto(FhirClaim claim, int index, ClaimFileParseResult result) {
        try {
            result.addClaim(mapClaim(claim, index));
        } catch (IntakeParseException e) {
            result.addRecordError(index, e.getMessage());
        }
    }

    private FhirClaim deserializeClaim(JsonNode node) throws IntakeParseException {
        try {
            return objectMapper.treeToValue(node, FhirClaim.class);
        } catch (Exception e) {
            throw new IntakeParseException("Could not deserialize FHIR Claim: " + e.getMessage(), e);
        }
    }

    private SubmitClaimRequest mapClaim(FhirClaim fhir, int recordIndex) throws IntakeParseException {
        SubmitClaimRequest req = new SubmitClaimRequest();

        // ---- member resolution ----
        String memberNumber = extractIdentifierValue(fhir.getPatient(), "patient", recordIndex);
        Member member = memberService.findByMemberNumber(memberNumber);
        if (member == null) {
            throw new IntakeParseException(
                "Record " + recordIndex + ": member number '" + memberNumber + "' not found");
        }
        req.setMemberId(member.getId());

        // ---- provider resolution ----
        String npi = extractIdentifierValue(fhir.getProvider(), "provider", recordIndex);
        String npiErr = ValidationUtil.validateNpi(npi);
        if (npiErr != null) {
            throw new IntakeParseException("Record " + recordIndex + ": " + npiErr);
        }
        Provider provider = providerService.findByNpi(npi);
        if (provider == null) {
            throw new IntakeParseException(
                "Record " + recordIndex + ": provider NPI '" + npi + "' not found");
        }
        req.setProviderId(provider.getId());

        // ---- date of service ----
        req.setDateOfService(extractDateOfService(fhir, recordIndex));

        // ---- coverage order ----
        req.setCoverageOrder(extractCoverageOrder(fhir));

        // ---- diagnoses ----
        List<SubmitClaimRequest.DiagnosisRow> diagnoses = extractDiagnoses(fhir, recordIndex);
        if (diagnoses.isEmpty()) {
            throw new IntakeParseException("Record " + recordIndex + ": no diagnosis codes found");
        }
        req.setDiagnoses(diagnoses);

        // ---- line items ----
        List<SubmitClaimRequest.LineItemRow> lineItems = extractLineItems(fhir, recordIndex);
        if (lineItems.isEmpty()) {
            throw new IntakeParseException("Record " + recordIndex + ": no line items found");
        }
        req.setLineItems(lineItems);

        return req;
    }

    private String extractIdentifierValue(com.meridian.claims.intake.fhir.FhirReference ref,
                                          String fieldName, int recordIndex)
            throws IntakeParseException {
        if (ref == null || ref.getIdentifier() == null
                || ref.getIdentifier().getValue() == null
                || ref.getIdentifier().getValue().trim().isEmpty()) {
            throw new IntakeParseException(
                "Record " + recordIndex + ": missing " + fieldName + ".identifier.value");
        }
        return ref.getIdentifier().getValue().trim();
    }

    private Date extractDateOfService(FhirClaim fhir, int recordIndex) throws IntakeParseException {
        String dateStr = null;
        if (fhir.getBillablePeriod() != null && fhir.getBillablePeriod().getStart() != null) {
            dateStr = fhir.getBillablePeriod().getStart().trim();
        }
        // Fall back to servicedDate on the first item if billablePeriod absent
        if (dateStr == null && fhir.getItem() != null && !fhir.getItem().isEmpty()) {
            dateStr = fhir.getItem().get(0).getServicedDate();
        }
        if (dateStr == null || dateStr.isEmpty()) {
            throw new IntakeParseException(
                "Record " + recordIndex + ": missing billablePeriod.start or item[0].servicedDate");
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(dateStr);
        } catch (ParseException e) {
            throw new IntakeParseException(
                "Record " + recordIndex + ": invalid date '" + dateStr + "' (expected yyyy-MM-dd)");
        }
    }

    private String extractCoverageOrder(FhirClaim fhir) {
        if (fhir.getInsurance() != null) {
            for (FhirClaim.Insurance ins : fhir.getInsurance()) {
                if (ins.isFocal()) {
                    return ins.getSequence() == 1 ? "PRIMARY" : "SECONDARY";
                }
            }
        }
        return "PRIMARY"; // default
    }

    private List<SubmitClaimRequest.DiagnosisRow> extractDiagnoses(FhirClaim fhir, int recordIndex)
            throws IntakeParseException {
        List<SubmitClaimRequest.DiagnosisRow> rows = new ArrayList<SubmitClaimRequest.DiagnosisRow>();
        if (fhir.getDiagnosis() == null) return rows;

        // First pass: build all rows as SECONDARY, tracking which FHIR entries were
        // flagged principal. The domain requires exactly one PRIMARY diagnosis, so we
        // normalise here rather than trust the file to flag exactly one.
        int firstPrimaryIdx = -1;
        for (FhirClaim.Diagnosis d : fhir.getDiagnosis()) {
            if (d.getDiagnosisCodeableConcept() == null) continue;
            String code = d.getDiagnosisCodeableConcept().firstCode();
            String err = ValidationUtil.validateDiagnosisCode(code);
            if (err != null) {
                throw new IntakeParseException(
                    "Record " + recordIndex + " diagnosis[" + d.getSequence() + "]: " + err);
            }
            SubmitClaimRequest.DiagnosisRow row = new SubmitClaimRequest.DiagnosisRow();
            row.setDiagnosisCode(code);
            row.setDiagnosisType("SECONDARY");
            if (firstPrimaryIdx == -1 && d.isPrimary()) {
                firstPrimaryIdx = rows.size();
            }
            rows.add(row);
        }
        // Promote exactly one row to PRIMARY: the first flagged principal, or — if the
        // file flagged none (FHIR does not require a 'principal' type) — the first row.
        if (!rows.isEmpty()) {
            int primaryIdx = (firstPrimaryIdx != -1) ? firstPrimaryIdx : 0;
            rows.get(primaryIdx).setDiagnosisType("PRIMARY");
        }
        return rows;
    }

    private List<SubmitClaimRequest.LineItemRow> extractLineItems(FhirClaim fhir, int recordIndex)
            throws IntakeParseException {
        List<SubmitClaimRequest.LineItemRow> rows = new ArrayList<SubmitClaimRequest.LineItemRow>();
        if (fhir.getItem() == null) return rows;

        for (FhirClaim.Item item : fhir.getItem()) {
            if (item.getProductOrService() == null) continue;
            String cpt = item.getProductOrService().firstCode();
            String err = ValidationUtil.validateProcedureCode(cpt);
            if (err != null) {
                throw new IntakeParseException(
                    "Record " + recordIndex + " item[" + item.getSequence() + "]: " + err);
            }
            if (item.getNet() == null || item.getNet().getValue() == null) {
                throw new IntakeParseException(
                    "Record " + recordIndex + " item[" + item.getSequence() + "]: missing net.value");
            }
            BigDecimal amount = item.getNet().getValue();
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IntakeParseException(
                    "Record " + recordIndex + " item[" + item.getSequence() + "]: net.value must be > 0");
            }
            SubmitClaimRequest.LineItemRow row = new SubmitClaimRequest.LineItemRow();
            row.setProcedureCode(cpt);
            row.setBilledAmount(amount.setScale(2, RoundingMode.HALF_UP));
            if (item.getProductOrService().getText() != null) {
                row.setDescription(item.getProductOrService().getText());
            }
            rows.add(row);
        }
        return rows;
    }
}
