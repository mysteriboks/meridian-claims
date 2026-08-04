package com.meridian.claims.intake;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for X12Edi278Parser using inline 278 fixture strings, mirroring
 * X12Edi276ParserTest's fixture style.
 */
public class X12Edi278ParserTest {

    private X12Edi278Parser parser;

    private static final String MEMBER_NUMBER  = "MBR-001";
    private static final String PROVIDER_NPI   = "1234567890";
    private static final String ISA_CONTROL_NR = "000000001";

    @Before
    public void setUp() {
        parser = new X12Edi278Parser();
    }

    @Test
    public void validRequest_parsesAllFields() throws Exception {
        List<PriorAuthRequest> requests = parser.parse(validRequest());

        assertEquals(1, requests.size());
        PriorAuthRequest req = requests.get(0);
        assertEquals(ISA_CONTROL_NR, req.getIsaControlNumber());
        assertEquals("1", req.getGsControlNumber());
        assertEquals("0001", req.getStControlNumber());
        assertEquals(MEMBER_NUMBER, req.getMemberNumber());
        assertEquals(PROVIDER_NPI, req.getProviderNpi());
        assertEquals("99213", req.getProcedureCode());
        assertEquals("SPECIALIST_VISIT", req.getServiceType());
        assertEquals("20260101", req.getAuthorizedFromString());
        assertEquals("20260131", req.getAuthorizedToString());
        assertEquals(Integer.valueOf(3), req.getRequestedUnits());
    }

    @Test
    public void twoTransactionFile_eachKeepsItsOwnStControlNumber() throws Exception {
        List<PriorAuthRequest> requests = parser.parse(twoTransactionRequest());

        assertEquals(2, requests.size());
        assertEquals("0001", requests.get(0).getStControlNumber());
        assertEquals("99213", requests.get(0).getProcedureCode());
        assertEquals("0002", requests.get(1).getStControlNumber());
        assertEquals("99214", requests.get(1).getProcedureCode());
    }

    @Test
    public void missingFields_leavesThemNull() throws Exception {
        List<PriorAuthRequest> requests = parser.parse(bareRequest());

        assertEquals(1, requests.size());
        PriorAuthRequest req = requests.get(0);
        assertNull(req.getProcedureCode());
        assertNull(req.getAuthorizedFromString());
    }

    @Test(expected = IntakeParseException.class)
    public void isaLevelFailure_throwsIntakeParseException() throws Exception {
        parser.parse("THIS IS NOT AN EDI FILE AND CANNOT BE PARSED AS X12 278");
    }

    @Test(expected = IntakeParseException.class)
    public void emptyContent_throwsIntakeParseException() throws Exception {
        parser.parse("");
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    private String validRequest() {
        return "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *260101*0900*^*00501*" + ISA_CONTROL_NR + "*0*T*:~"
            + "GS*HI*SENDER*RECEIVER*20260101*0900*1*X*005010X217~"
            + "ST*278*0001*005010X217~"
            + "BHT*0007*13*RA0001*20260101*0900~"
            + "NM1*1P*2*PROVIDER NAME*****XX*" + PROVIDER_NPI + "~"
            + "NM1*IL*1*DOE*JOHN****MI*" + MEMBER_NUMBER + "~"
            + "UM*HS*I*SPECIALIST_VISIT~"
            + "DTP*291*RD8*20260101-20260131~"
            + "SV1*HC:99213***3~"
            + "SE*8*0001~"
            + "GE*1*1~"
            + "IEA*1*" + ISA_CONTROL_NR + "~";
    }

    private String bareRequest() {
        return "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *260101*0900*^*00501*" + ISA_CONTROL_NR + "*0*T*:~"
            + "GS*HI*SENDER*RECEIVER*20260101*0900*1*X*005010X217~"
            + "ST*278*0001*005010X217~"
            + "NM1*IL*1*DOE*JOHN****MI*" + MEMBER_NUMBER + "~"
            + "SE*3*0001~"
            + "GE*1*1~"
            + "IEA*1*" + ISA_CONTROL_NR + "~";
    }

    private String twoTransactionRequest() {
        return "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *260101*0900*^*00501*" + ISA_CONTROL_NR + "*0*T*:~"
            + "GS*HI*SENDER*RECEIVER*20260101*0900*1*X*005010X217~"
            + "ST*278*0001*005010X217~"
            + "NM1*1P*2*PROVIDER NAME*****XX*" + PROVIDER_NPI + "~"
            + "NM1*IL*1*DOE*JOHN****MI*" + MEMBER_NUMBER + "~"
            + "DTP*291*RD8*20260101-20260131~"
            + "SV1*HC:99213***1~"
            + "SE*7*0001~"
            + "ST*278*0002*005010X217~"
            + "NM1*1P*2*PROVIDER NAME*****XX*" + PROVIDER_NPI + "~"
            + "NM1*IL*1*DOE*JOHN****MI*" + MEMBER_NUMBER + "~"
            + "DTP*291*RD8*20260201-20260228~"
            + "SV1*HC:99214***2~"
            + "SE*7*0002~"
            + "GE*2*1~"
            + "IEA*1*" + ISA_CONTROL_NR + "~";
    }
}
