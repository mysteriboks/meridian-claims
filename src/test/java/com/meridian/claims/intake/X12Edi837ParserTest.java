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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for X12Edi837Parser using inline 837P/837I fixture strings.
 *
 * Contract under test:
 *   - File-level failures (garbled content, missing ISA envelope) THROW IntakeParseException.
 *   - Per-record failures (bad CPT, unknown member) are collected as record errors and
 *     do NOT abort the rest of the file.
 *   - externalReference is set to the ISA13 interchange control number on every claim.
 *   - Money is parsed as BigDecimal (no double) with 2-decimal-place precision.
 *   - 837I SV2 segments produce line items the same way as 837P SV1 segments.
 */
public class X12Edi837ParserTest {

    private X12Edi837Parser parser;
    private MemberService memberService;
    private ProviderService providerService;

    private static final String MEMBER_NUMBER  = "MBR-001";
    private static final String PROVIDER_NPI   = "1234567890";
    private static final String ISA_CONTROL_NR = "000000001";

    @Before
    public void setUp() {
        memberService  = mock(MemberService.class);
        providerService = mock(ProviderService.class);
        parser = new X12Edi837Parser(memberService, providerService);

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
    // Happy path — valid 837P single transaction set
    // -------------------------------------------------------------------------

    @Test
    public void valid837P_parsesCorrectly() throws Exception {
        ClaimFileParseResult result = parser.parse(minimal837P());

        assertEquals(1, result.getClaims().size());
        assertTrue(result.getRecordErrors().isEmpty());

        SubmitClaimRequest req = result.getClaims().get(0);

        // externalReference must be set to the ISA13 interchange control number
        assertEquals("externalReference should equal ISA13",
            ISA_CONTROL_NR, req.getExternalReference());

        // member and provider resolved via service mocks
        assertEquals(10, req.getMemberId());
        assertEquals(5, req.getProviderId());

        // date of service from CLM/DTP segments
        assertNotNull("dateOfService must be populated", req.getDateOfService());

        // coverage order defaults to PRIMARY for an 837P without COB info
        assertEquals("PRIMARY", req.getCoverageOrder());

        // exactly one PRIMARY diagnosis
        assertEquals(1, req.getDiagnoses().size());
        // X12 sends "E119"; parser normalizes to "E11.9" to match ICD-10 format
        assertEquals("E11.9", req.getDiagnoses().get(0).getDiagnosisCode());
        assertEquals("PRIMARY", req.getDiagnoses().get(0).getDiagnosisType());

        // one line item with BigDecimal billed amount
        assertEquals(1, req.getLineItems().size());
        assertEquals("99213", req.getLineItems().get(0).getProcedureCode());
        assertEquals(new BigDecimal("150.00"),
            req.getLineItems().get(0).getBilledAmount());
    }

    // -------------------------------------------------------------------------
    // Per-record failure — one bad CPT in a two-transaction file
    // -------------------------------------------------------------------------

    @Test
    public void bundle_oneBadRecord_othersStillParsed() throws Exception {
        ClaimFileParseResult result = parser.parse(twoTransaction837P_secondBadCpt());

        assertEquals("good transaction must be preserved", 1, result.getClaims().size());
        assertEquals("bad transaction must be quarantined", 1, result.getRecordErrors().size());
        assertEquals(2, result.totalRecords());
        // File must NOT throw — bad record is per-record, not file-level
    }

    // -------------------------------------------------------------------------
    // Per-record failure — unknown member
    // -------------------------------------------------------------------------

    @Test
    public void missingMember_recordedAsError() throws Exception {
        when(memberService.findByMemberNumber(MEMBER_NUMBER)).thenReturn(null);

        ClaimFileParseResult result = parser.parse(minimal837P());

        assertTrue(result.getClaims().isEmpty());
        assertEquals(1, result.getRecordErrors().size());
        assertTrue("error reason should mention 'not found'",
            result.getRecordErrors().get(0).getReason().contains("not found"));
    }

    // -------------------------------------------------------------------------
    // File-level failure — completely garbled content
    // -------------------------------------------------------------------------

    @Test(expected = IntakeParseException.class)
    public void isaLevelFailure_throwsIntakeParseException() throws Exception {
        // Content is not EDI at all — no ISA envelope
        parser.parse("THIS IS NOT AN EDI FILE AND CANNOT BE PARSED AS X12 837");
    }

    // -------------------------------------------------------------------------
    // 837I — institutional claim maps SV2 to line item
    // -------------------------------------------------------------------------

    @Test
    public void i837I_parsesInstitutionalClaim() throws Exception {
        ClaimFileParseResult result = parser.parse(minimal837I());

        assertEquals(1, result.getClaims().size());
        assertTrue(result.getRecordErrors().isEmpty());

        SubmitClaimRequest req = result.getClaims().get(0);
        assertEquals(1, req.getLineItems().size());
        // SV2 revenue code maps to procedure code on the line item
        assertNotNull("procedureCode must be set from SV2", req.getLineItems().get(0).getProcedureCode());
        assertTrue("billedAmount must be positive",
            req.getLineItems().get(0).getBilledAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    // -------------------------------------------------------------------------
    // Per-record failure — provider not found (LOW-4)
    // -------------------------------------------------------------------------

    @Test
    public void x12_missingProvider_recordedAsError() throws Exception {
        when(providerService.findByNpi(PROVIDER_NPI)).thenReturn(null);

        ClaimFileParseResult result = parser.parse(minimal837P());

        assertTrue("no claims should be produced when provider is missing", result.getClaims().isEmpty());
        assertEquals("exactly one record error should be recorded", 1, result.getRecordErrors().size());
    }

    // =========================================================================
    // Fixtures — minimal valid inline 837 strings
    // =========================================================================

    /**
     * Minimal valid 837P (professional) EDI string — one ST/SE transaction set,
     * one CLM claim, one SV1 line item, one HI diagnosis.
     *
     * Element delimiter: * (asterisk)
     * Segment terminator: ~ (tilde)
     * Sub-element delimiter: : (colon)
     *
     * ISA13 = 000000001 (the interchange control number used as externalReference).
     */
    private String minimal837P() {
        return "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *260101*0900*^*00501*000000001*0*T*:~"
            + "GS*HC*SENDER*RECEIVER*20260101*0900*1*X*005010X222A2~"
            + "ST*837*0001*005010X222A2~"
            + "BPR*22*0*C*NON~"
            + "NM1*41*2*BILLING ORG*****46*123456789~"
            + "NM1*40*2*PAYER*****46*987654321~"
            + "HL*1**20*1~"
            + "NM1*85*2*PROVIDER NAME*****XX*" + PROVIDER_NPI + "~"
            + "HL*2*1*22*0~"
            + "SBR*P*18*******MC~"
            + "NM1*IL*1*DOE*JOHN****MI*" + MEMBER_NUMBER + "~"
            + "NM1*PR*2*PAYER NAME*****PI*PAYERID~"
            + "CLM*CLM-0001*150.00***11:B:1*Y*A*Y*I~"
            + "DTP*472*D8*20260115~"
            + "HI*ABK:E119~"
            + "LX*1~"
            + "SV1*HC:99213*150.00*UN*1***1~"
            + "DTP*472*D8*20260115~"
            + "SE*17*0001~"
            + "GE*1*1~"
            + "IEA*1*000000001~";
    }

    /**
     * Two-transaction 837P: first transaction is valid, second has an invalid
     * CPT code to trigger a per-record quarantine without aborting the file.
     */
    private String twoTransaction837P_secondBadCpt() {
        return "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *260101*0900*^*00501*000000001*0*T*:~"
            + "GS*HC*SENDER*RECEIVER*20260101*0900*1*X*005010X222A2~"
            // --- transaction 1: valid ---
            + "ST*837*0001*005010X222A2~"
            + "BPR*22*0*C*NON~"
            + "NM1*41*2*BILLING ORG*****46*123456789~"
            + "NM1*40*2*PAYER*****46*987654321~"
            + "HL*1**20*1~"
            + "NM1*85*2*PROVIDER NAME*****XX*" + PROVIDER_NPI + "~"
            + "HL*2*1*22*0~"
            + "SBR*P*18*******MC~"
            + "NM1*IL*1*DOE*JOHN****MI*" + MEMBER_NUMBER + "~"
            + "NM1*PR*2*PAYER NAME*****PI*PAYERID~"
            + "CLM*CLM-0001*150.00***11:B:1*Y*A*Y*I~"
            + "DTP*472*D8*20260115~"
            + "HI*ABK:E119~"
            + "LX*1~"
            + "SV1*HC:99213*150.00*UN*1***1~"
            + "DTP*472*D8*20260115~"
            + "SE*17*0001~"
            // --- transaction 2: bad CPT (too short) ---
            + "ST*837*0002*005010X222A2~"
            + "BPR*22*0*C*NON~"
            + "NM1*41*2*BILLING ORG*****46*123456789~"
            + "NM1*40*2*PAYER*****46*987654321~"
            + "HL*1**20*1~"
            + "NM1*85*2*PROVIDER NAME*****XX*" + PROVIDER_NPI + "~"
            + "HL*2*1*22*0~"
            + "SBR*P*18*******MC~"
            + "NM1*IL*1*DOE*JOHN****MI*" + MEMBER_NUMBER + "~"
            + "NM1*PR*2*PAYER NAME*****PI*PAYERID~"
            + "CLM*CLM-0002*200.00***11:B:1*Y*A*Y*I~"
            + "DTP*472*D8*20260115~"
            + "HI*ABK:E119~"
            + "LX*1~"
            + "SV1*HC:BAD*200.00*UN*1***1~"
            + "DTP*472*D8*20260115~"
            + "SE*17*0002~"
            + "GE*1*1~"
            + "IEA*1*000000001~";
    }

    /**
     * Minimal valid 837I (institutional) EDI string — uses SV2 (revenue code)
     * instead of SV1 (procedure code) to exercise the institutional line-item path.
     */
    private String minimal837I() {
        return "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *260101*0900*^*00501*000000001*0*T*:~"
            + "GS*HC*SENDER*RECEIVER*20260101*0900*1*X*005010X223A3~"
            + "ST*837*0001*005010X223A3~"
            + "BPR*22*0*C*NON~"
            + "NM1*41*2*BILLING ORG*****46*123456789~"
            + "NM1*40*2*PAYER*****46*987654321~"
            + "HL*1**20*1~"
            + "NM1*85*2*PROVIDER NAME*****XX*" + PROVIDER_NPI + "~"
            + "HL*2*1*22*0~"
            + "SBR*P*18*******MC~"
            + "NM1*IL*1*DOE*JOHN****MI*" + MEMBER_NUMBER + "~"
            + "NM1*PR*2*PAYER NAME*****PI*PAYERID~"
            + "CLM*CLM-I001*350.00***11:B:1*Y*A*Y*I~"
            + "DTP*472*D8*20260115~"
            + "HI*BK:E119~"
            + "LX*1~"
            + "SV2*00300*HC:99213*350.00*UN*1~"
            + "DTP*472*D8*20260115~"
            + "SE*16*0001~"
            + "GE*1*1~"
            + "IEA*1*000000001~";
    }
}
