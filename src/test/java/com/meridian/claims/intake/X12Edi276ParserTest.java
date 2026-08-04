package com.meridian.claims.intake;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for X12Edi276Parser using inline 276 fixture strings, mirroring
 * X12Edi837ParserTest's fixture style. Same structural-simplification precedent
 * as the 837 parser — enough segments for StAEDI tokenization and our own
 * flat segment extraction, not full 276 implementation-guide conformance.
 */
public class X12Edi276ParserTest {

    private X12Edi276Parser parser;

    private static final String MEMBER_NUMBER  = "MBR-001";
    private static final String PROVIDER_NPI   = "1234567890";
    private static final String ISA_CONTROL_NR = "000000001";

    @Before
    public void setUp() {
        parser = new X12Edi276Parser();
    }

    @Test
    public void inquiryWithClaimControlNumber_parsesAllFields() throws Exception {
        List<ClaimStatusInquiry> inquiries = parser.parse(inquiryWithRef());

        assertEquals(1, inquiries.size());
        ClaimStatusInquiry inquiry = inquiries.get(0);
        assertEquals(ISA_CONTROL_NR, inquiry.getIsaControlNumber());
        assertEquals("1", inquiry.getGsControlNumber());
        assertEquals("0001", inquiry.getStControlNumber());
        assertEquals("CLM-0001", inquiry.getClaimControlNumber());
        assertEquals(MEMBER_NUMBER, inquiry.getMemberNumber());
        assertEquals(PROVIDER_NPI, inquiry.getProviderNpi());
        assertEquals("20260115", inquiry.getDateOfServiceString());
    }

    @Test
    public void inquiryWithoutClaimControlNumber_fallsBackToMemberProviderDos() throws Exception {
        List<ClaimStatusInquiry> inquiries = parser.parse(inquiryWithoutRef());

        assertEquals(1, inquiries.size());
        ClaimStatusInquiry inquiry = inquiries.get(0);
        assertNull("no REF*1K in this fixture", inquiry.getClaimControlNumber());
        assertEquals(MEMBER_NUMBER, inquiry.getMemberNumber());
        assertEquals(PROVIDER_NPI, inquiry.getProviderNpi());
        assertEquals("20260115", inquiry.getDateOfServiceString());
    }

    @Test
    public void twoTransactionFile_eachKeepsItsOwnStControlNumber() throws Exception {
        List<ClaimStatusInquiry> inquiries = parser.parse(twoTransactionInquiry());

        assertEquals(2, inquiries.size());
        assertEquals("0001", inquiries.get(0).getStControlNumber());
        assertEquals("CLM-0001", inquiries.get(0).getClaimControlNumber());
        assertEquals("0002", inquiries.get(1).getStControlNumber());
        assertEquals("CLM-0002", inquiries.get(1).getClaimControlNumber());
    }

    @Test(expected = IntakeParseException.class)
    public void isaLevelFailure_throwsIntakeParseException() throws Exception {
        parser.parse("THIS IS NOT AN EDI FILE AND CANNOT BE PARSED AS X12 276");
    }

    @Test(expected = IntakeParseException.class)
    public void emptyContent_throwsIntakeParseException() throws Exception {
        parser.parse("");
    }

    @Test
    public void emptyFile_noTransactions_returnsEmptyList() throws Exception {
        // Well-formed envelope with zero ST..SE transactions inside it.
        String noTransactions =
            "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *260101*0900*^*00501*" + ISA_CONTROL_NR + "*0*T*:~"
            + "GS*HR*SENDER*RECEIVER*20260101*0900*1*X*005010X212~"
            + "GE*0*1~"
            + "IEA*1*" + ISA_CONTROL_NR + "~";
        List<ClaimStatusInquiry> inquiries = parser.parse(noTransactions);
        assertTrue(inquiries.isEmpty());
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    private String inquiryWithRef() {
        return "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *260101*0900*^*00501*" + ISA_CONTROL_NR + "*0*T*:~"
            + "GS*HR*SENDER*RECEIVER*20260101*0900*1*X*005010X212~"
            + "ST*276*0001*005010X212~"
            + "BHT*0010*13*RA0001*20260101*0900~"
            + "NM1*1P*2*PROVIDER NAME*****XX*" + PROVIDER_NPI + "~"
            + "NM1*IL*1*DOE*JOHN****MI*" + MEMBER_NUMBER + "~"
            + "TRN*1*TRACE0001~"
            + "REF*1K*CLM-0001~"
            + "DTP*472*D8*20260115~"
            + "SE*8*0001~"
            + "GE*1*1~"
            + "IEA*1*" + ISA_CONTROL_NR + "~";
    }

    private String inquiryWithoutRef() {
        return "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *260101*0900*^*00501*" + ISA_CONTROL_NR + "*0*T*:~"
            + "GS*HR*SENDER*RECEIVER*20260101*0900*1*X*005010X212~"
            + "ST*276*0001*005010X212~"
            + "BHT*0010*13*RA0001*20260101*0900~"
            + "NM1*1P*2*PROVIDER NAME*****XX*" + PROVIDER_NPI + "~"
            + "NM1*IL*1*DOE*JOHN****MI*" + MEMBER_NUMBER + "~"
            + "TRN*1*TRACE0001~"
            + "DTP*472*D8*20260115~"
            + "SE*7*0001~"
            + "GE*1*1~"
            + "IEA*1*" + ISA_CONTROL_NR + "~";
    }

    private String twoTransactionInquiry() {
        return "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *260101*0900*^*00501*" + ISA_CONTROL_NR + "*0*T*:~"
            + "GS*HR*SENDER*RECEIVER*20260101*0900*1*X*005010X212~"
            + "ST*276*0001*005010X212~"
            + "REF*1K*CLM-0001~"
            + "DTP*472*D8*20260115~"
            + "SE*3*0001~"
            + "ST*276*0002*005010X212~"
            + "REF*1K*CLM-0002~"
            + "DTP*472*D8*20260116~"
            + "SE*3*0002~"
            + "GE*2*1~"
            + "IEA*1*" + ISA_CONTROL_NR + "~";
    }
}
