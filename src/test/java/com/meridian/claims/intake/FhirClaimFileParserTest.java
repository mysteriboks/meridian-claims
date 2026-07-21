package com.meridian.claims.intake;

import com.meridian.claims.controller.SubmitClaimRequest;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.Provider;
import com.meridian.claims.service.MemberService;
import com.meridian.claims.service.ProviderService;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FhirClaimFileParser using real FHIR R4 Claim JSON fixtures.
 *
 * Contract under test (post-review):
 *   - File-level failures (bad JSON, wrong resourceType, empty content) THROW IntakeParseException.
 *   - Per-record failures (bad code, unknown member, missing fields) are collected as
 *     record errors in ClaimFileParseResult — they do NOT throw and do NOT abort the file.
 *   - Money is parsed as BigDecimal end-to-end (no double).
 *   - Exactly one PRIMARY diagnosis is produced per claim.
 */
public class FhirClaimFileParserTest {

    private FhirClaimFileParser parser;
    private MemberService memberService;
    private ProviderService providerService;

    private static final String MEMBER_NUMBER = "MBR-001";
    private static final String PROVIDER_NPI  = "1234567890";

    @Before
    public void setUp() {
        memberService = mock(MemberService.class);
        providerService = mock(ProviderService.class);
        parser = new FhirClaimFileParser(memberService, providerService);

        Member member = new Member();
        member.setId(10);
        member.setMemberNumber(MEMBER_NUMBER);
        when(memberService.findByMemberNumber(MEMBER_NUMBER)).thenReturn(member);

        Provider provider = new Provider();
        provider.setId(5);
        provider.setNpi(PROVIDER_NPI);
        when(providerService.findByNpi(PROVIDER_NPI)).thenReturn(provider);
    }

    // -------------------------------------------------------------------------
    // Happy path — single Claim resource
    // -------------------------------------------------------------------------

    @Test
    public void singleClaimResource_parsesCorrectly() throws Exception {
        ClaimFileParseResult result = parser.parse(singleClaimJson());

        assertEquals(1, result.getClaims().size());
        assertTrue(result.getRecordErrors().isEmpty());
        SubmitClaimRequest req = result.getClaims().get(0);
        assertEquals(10, req.getMemberId());
        assertEquals(5, req.getProviderId());
        assertNotNull(req.getDateOfService());
        assertEquals("PRIMARY", req.getCoverageOrder());
        assertEquals(1, req.getDiagnoses().size());
        assertEquals("E11.9", req.getDiagnoses().get(0).getDiagnosisCode());
        assertEquals("PRIMARY", req.getDiagnoses().get(0).getDiagnosisType());
        assertEquals(1, req.getLineItems().size());
        assertEquals("99213", req.getLineItems().get(0).getProcedureCode());
        assertEquals(new BigDecimal("150.00"), req.getLineItems().get(0).getBilledAmount());
    }

    // -------------------------------------------------------------------------
    // Happy path — Bundle of Claim resources
    // -------------------------------------------------------------------------

    @Test
    public void bundleWithTwoClaims_parsesBoth() throws Exception {
        ClaimFileParseResult result = parser.parse(bundleJson(2));
        assertEquals(2, result.getClaims().size());
        assertTrue(result.getRecordErrors().isEmpty());
    }

    @Test
    public void emptyBundle_returnsEmptyResult() throws Exception {
        ClaimFileParseResult result = parser.parse("{\"resourceType\":\"Bundle\",\"entry\":[]}");
        assertTrue(result.getClaims().isEmpty());
        assertTrue(result.getRecordErrors().isEmpty());
        assertEquals(0, result.totalRecords());
    }

    // -------------------------------------------------------------------------
    // File-level failures — THROW
    // -------------------------------------------------------------------------

    @Test(expected = IntakeParseException.class)
    public void malformedJson_throwsIntakeParseException() throws Exception {
        parser.parse("{ not valid json }");
    }

    @Test(expected = IntakeParseException.class)
    public void emptyContent_throwsIntakeParseException() throws Exception {
        parser.parse("   ");
    }

    @Test(expected = IntakeParseException.class)
    public void missingResourceType_throwsIntakeParseException() throws Exception {
        parser.parse("{\"patient\":{\"identifier\":{\"value\":\"MBR-001\"}}}");
    }

    @Test(expected = IntakeParseException.class)
    public void unsupportedResourceType_throwsIntakeParseException() throws Exception {
        parser.parse("{\"resourceType\":\"Patient\"}");
    }

    // -------------------------------------------------------------------------
    // Per-record failures — collected as record errors, NOT thrown
    // -------------------------------------------------------------------------

    @Test
    public void singleClaim_missingPatientIdentifier_recordedAsError() throws Exception {
        // Valid JSON, valid resourceType, but no patient → per-record error (not file-level)
        String json = "{\n"
            + "  \"resourceType\": \"Claim\",\n"
            + "  \"provider\": {\"identifier\": {\"value\": \"" + PROVIDER_NPI + "\"}},\n"
            + "  \"billablePeriod\": {\"start\": \"2026-01-15\"},\n"
            + "  \"diagnosis\": [{\"diagnosisCodeableConcept\": {\"coding\": [{\"code\": \"E11.9\"}]}}],\n"
            + "  \"item\": [{\"productOrService\": {\"coding\": [{\"code\": \"99213\"}]}, \"net\": {\"value\": 150.00}}]\n"
            + "}";
        ClaimFileParseResult result = parser.parse(json);
        assertTrue(result.getClaims().isEmpty());
        assertEquals(1, result.getRecordErrors().size());
    }

    @Test
    public void singleClaim_memberNotFound_recordedAsError() throws Exception {
        when(memberService.findByMemberNumber(MEMBER_NUMBER)).thenReturn(null);
        ClaimFileParseResult result = parser.parse(singleClaimJson());
        assertTrue(result.getClaims().isEmpty());
        assertEquals(1, result.getRecordErrors().size());
        assertTrue(result.getRecordErrors().get(0).getReason().contains("not found"));
    }

    @Test
    public void singleClaim_invalidNpi_recordedAsError() throws Exception {
        ClaimFileParseResult result = parser.parse(claimWithNpi("1234")); // too short
        assertTrue(result.getClaims().isEmpty());
        assertEquals(1, result.getRecordErrors().size());
    }

    @Test
    public void singleClaim_providerNotFound_recordedAsError() throws Exception {
        when(providerService.findByNpi(PROVIDER_NPI)).thenReturn(null);
        ClaimFileParseResult result = parser.parse(singleClaimJson());
        assertEquals(1, result.getRecordErrors().size());
    }

    @Test
    public void singleClaim_invalidDiagnosisCode_recordedAsError() throws Exception {
        ClaimFileParseResult result = parser.parse(claimWithDiagnosisCode("NOTVALID"));
        assertEquals(1, result.getRecordErrors().size());
    }

    @Test
    public void singleClaim_invalidCptCode_recordedAsError() throws Exception {
        ClaimFileParseResult result = parser.parse(claimWithCptCode("999")); // too short
        assertEquals(1, result.getRecordErrors().size());
    }

    @Test
    public void singleClaim_missingNetValue_recordedAsError() throws Exception {
        ClaimFileParseResult result = parser.parse(claimWithoutNet());
        assertEquals(1, result.getRecordErrors().size());
    }

    @Test
    public void singleClaim_zeroNetValue_recordedAsError() throws Exception {
        ClaimFileParseResult result = parser.parse(claimWithNetValue("0.00"));
        assertEquals(1, result.getRecordErrors().size());
    }

    // -------------------------------------------------------------------------
    // CRITICAL: one bad record in a Bundle must NOT abort the others
    // -------------------------------------------------------------------------

    @Test
    public void bundle_oneBadRecord_othersStillParsed() throws Exception {
        // 3 entries: good, bad-CPT, good
        String good = singleClaimJson();
        String bad = claimWithCptCode("BADCODE");
        String json = "{\"resourceType\":\"Bundle\",\"entry\":["
            + "{\"resource\":" + good + "},"
            + "{\"resource\":" + bad + "},"
            + "{\"resource\":" + good + "}"
            + "]}";

        ClaimFileParseResult result = parser.parse(json);

        assertEquals("two good records survive", 2, result.getClaims().size());
        assertEquals("one record quarantined", 1, result.getRecordErrors().size());
        assertEquals(2, result.getRecordErrors().get(0).getRecordIndex());
        assertEquals(3, result.totalRecords());
    }

    // -------------------------------------------------------------------------
    // Diagnosis normalization — exactly one PRIMARY
    // -------------------------------------------------------------------------

    @Test
    public void multipleDiagnoses_noneFlaggedPrincipal_firstBecomesPrimary() throws Exception {
        ClaimFileParseResult result = parser.parse(claimWithTwoPlainDiagnoses());
        SubmitClaimRequest req = result.getClaims().get(0);
        assertEquals(2, req.getDiagnoses().size());
        assertEquals("PRIMARY", req.getDiagnoses().get(0).getDiagnosisType());
        assertEquals("SECONDARY", req.getDiagnoses().get(1).getDiagnosisType());
    }

    @Test
    public void multipleDiagnoses_twoFlaggedPrincipal_onlyOnePrimary() throws Exception {
        ClaimFileParseResult result = parser.parse(claimWithTwoPrincipalDiagnoses());
        SubmitClaimRequest req = result.getClaims().get(0);
        int primaryCount = 0;
        for (SubmitClaimRequest.DiagnosisRow d : req.getDiagnoses()) {
            if ("PRIMARY".equals(d.getDiagnosisType())) primaryCount++;
        }
        assertEquals("exactly one PRIMARY despite two flagged principal", 1, primaryCount);
    }

    // -------------------------------------------------------------------------
    // Money precision — BigDecimal, no double
    // -------------------------------------------------------------------------

    @Test
    public void moneyRoundedToTwoDecimalPlaces() throws Exception {
        ClaimFileParseResult result = parser.parse(claimWithNetValue("123.456"));
        assertEquals(new BigDecimal("123.46"), result.getClaims().get(0).getLineItems().get(0).getBilledAmount());
    }

    @Test
    public void moneyExactCentsPreserved() throws Exception {
        // 0.10 is not exactly representable in double; verify it survives as 0.10
        ClaimFileParseResult result = parser.parse(claimWithNetValue("1234567.89"));
        assertEquals(new BigDecimal("1234567.89"),
            result.getClaims().get(0).getLineItems().get(0).getBilledAmount());
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private String singleClaimJson() {
        return "{\n"
            + "  \"resourceType\": \"Claim\",\n"
            + "  \"patient\": {\"identifier\": {\"value\": \"" + MEMBER_NUMBER + "\"}},\n"
            + "  \"provider\": {\"identifier\": {\"value\": \"" + PROVIDER_NPI + "\"}},\n"
            + "  \"billablePeriod\": {\"start\": \"2026-01-15\"},\n"
            + "  \"insurance\": [{\"sequence\": 1, \"focal\": true}],\n"
            + "  \"diagnosis\": [{\n"
            + "    \"sequence\": 1,\n"
            + "    \"type\": [{\"coding\": [{\"code\": \"principal\"}]}],\n"
            + "    \"diagnosisCodeableConcept\": {\"coding\": [{\"code\": \"E11.9\"}]}\n"
            + "  }],\n"
            + "  \"item\": [{\n"
            + "    \"sequence\": 1,\n"
            + "    \"productOrService\": {\"coding\": [{\"code\": \"99213\"}]},\n"
            + "    \"net\": {\"value\": 150.00, \"currency\": \"USD\"}\n"
            + "  }]\n"
            + "}";
    }

    private String bundleJson(int count) {
        StringBuilder sb = new StringBuilder("{\"resourceType\":\"Bundle\",\"entry\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"resource\":").append(singleClaimJson()).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }


    private String claimWithNpi(String npi) {
        return singleClaimJson().replace("\"" + PROVIDER_NPI + "\"", "\"" + npi + "\"");
    }

    private String claimWithDiagnosisCode(String code) {
        return singleClaimJson().replace("\"E11.9\"", "\"" + code + "\"");
    }

    private String claimWithCptCode(String code) {
        return singleClaimJson().replace("\"99213\"", "\"" + code + "\"");
    }

    private String claimWithoutNet() {
        return singleClaimJson().replace(",\n    \"net\": {\"value\": 150.00, \"currency\": \"USD\"}", "");
    }

    private String claimWithNetValue(String value) {
        return singleClaimJson().replace("150.00", value);
    }

    private String claimWithTwoPlainDiagnoses() {
        return "{\n"
            + "  \"resourceType\": \"Claim\",\n"
            + "  \"patient\": {\"identifier\": {\"value\": \"" + MEMBER_NUMBER + "\"}},\n"
            + "  \"provider\": {\"identifier\": {\"value\": \"" + PROVIDER_NPI + "\"}},\n"
            + "  \"billablePeriod\": {\"start\": \"2026-01-15\"},\n"
            + "  \"diagnosis\": [\n"
            + "    {\"sequence\": 1, \"diagnosisCodeableConcept\": {\"coding\": [{\"code\": \"E11.9\"}]}},\n"
            + "    {\"sequence\": 2, \"diagnosisCodeableConcept\": {\"coding\": [{\"code\": \"I10\"}]}}\n"
            + "  ],\n"
            + "  \"item\": [{\"sequence\": 1, \"productOrService\": {\"coding\": [{\"code\": \"99213\"}]}, \"net\": {\"value\": 150.00}}]\n"
            + "}";
    }

    private String claimWithTwoPrincipalDiagnoses() {
        return "{\n"
            + "  \"resourceType\": \"Claim\",\n"
            + "  \"patient\": {\"identifier\": {\"value\": \"" + MEMBER_NUMBER + "\"}},\n"
            + "  \"provider\": {\"identifier\": {\"value\": \"" + PROVIDER_NPI + "\"}},\n"
            + "  \"billablePeriod\": {\"start\": \"2026-01-15\"},\n"
            + "  \"diagnosis\": [\n"
            + "    {\"sequence\": 1, \"type\": [{\"coding\": [{\"code\": \"principal\"}]}], \"diagnosisCodeableConcept\": {\"coding\": [{\"code\": \"E11.9\"}]}},\n"
            + "    {\"sequence\": 2, \"type\": [{\"coding\": [{\"code\": \"principal\"}]}], \"diagnosisCodeableConcept\": {\"coding\": [{\"code\": \"I10\"}]}}\n"
            + "  ],\n"
            + "  \"item\": [{\"sequence\": 1, \"productOrService\": {\"coding\": [{\"code\": \"99213\"}]}, \"net\": {\"value\": 150.00}}]\n"
            + "}";
    }
}
