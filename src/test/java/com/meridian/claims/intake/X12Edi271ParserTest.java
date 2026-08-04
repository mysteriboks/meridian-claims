package com.meridian.claims.intake;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for X12Edi271Parser using inline 271 fixture strings, mirroring
 * X12Edi276ParserTest's fixture style. Meridian is the requester for 270/271
 * (Phase 16), so this parses a response we received.
 */
public class X12Edi271ParserTest {

    private X12Edi271Parser parser;

    private static final String MEMBER_NUMBER  = "M100042";
    private static final String ISA_CONTROL_NR = "000000001";

    @Before
    public void setUp() {
        parser = new X12Edi271Parser();
    }

    @Test
    public void activeCoverage_parsesEb01AndPlanDescription() throws Exception {
        List<EligibilityResponse> responses = parser.parse(activeResponse());

        assertEquals(1, responses.size());
        EligibilityResponse resp = responses.get(0);
        assertEquals(ISA_CONTROL_NR, resp.getIsaControlNumber());
        assertEquals("1", resp.getGsControlNumber());
        assertEquals("0001", resp.getStControlNumber());
        assertEquals(MEMBER_NUMBER, resp.getMemberNumber());
        assertEquals("1", resp.getEb01Code());
        assertEquals("Gold PPO", resp.getPlanDescription());
    }

    @Test
    public void inactiveCoverage_parsesEb01AsSix() throws Exception {
        List<EligibilityResponse> responses = parser.parse(inactiveResponse());

        assertEquals(1, responses.size());
        assertEquals("6", responses.get(0).getEb01Code());
    }

    @Test
    public void noEbDescription_fallsBackToMsgSegment() throws Exception {
        List<EligibilityResponse> responses = parser.parse(msgFallbackResponse());

        assertEquals("Coverage not active", responses.get(0).getPlanDescription());
    }

    @Test(expected = IntakeParseException.class)
    public void isaLevelFailure_throwsIntakeParseException() throws Exception {
        parser.parse("THIS IS NOT AN EDI FILE AND CANNOT BE PARSED AS X12 271");
    }

    @Test(expected = IntakeParseException.class)
    public void emptyContent_throwsIntakeParseException() throws Exception {
        parser.parse("");
    }

    @Test
    public void emptyFile_noTransactions_returnsEmptyList() throws Exception {
        String noTransactions =
            "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *260101*0900*^*00501*" + ISA_CONTROL_NR + "*0*T*:~"
            + "GS*HB*SENDER*RECEIVER*20260101*0900*1*X*005010X279A1~"
            + "GE*0*1~"
            + "IEA*1*" + ISA_CONTROL_NR + "~";
        List<EligibilityResponse> responses = parser.parse(noTransactions);
        assertTrue(responses.isEmpty());
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    private String activeResponse() {
        return "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *260101*0900*^*00501*" + ISA_CONTROL_NR + "*0*T*:~"
            + "GS*HB*SENDER*RECEIVER*20260101*0900*1*X*005010X279A1~"
            + "ST*271*0001~"
            + "BHT*0022*11*42*20260101*0900~"
            + "NM1*IL*1*DOE*JOHN****MI*" + MEMBER_NUMBER + "~"
            + "EB*1*IND*30**Gold PPO~"
            + "SE*6*0001~"
            + "GE*1*1~"
            + "IEA*1*" + ISA_CONTROL_NR + "~";
    }

    private String inactiveResponse() {
        return "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *260101*0900*^*00501*" + ISA_CONTROL_NR + "*0*T*:~"
            + "GS*HB*SENDER*RECEIVER*20260101*0900*1*X*005010X279A1~"
            + "ST*271*0001~"
            + "NM1*IL*1*DOE*JOHN****MI*" + MEMBER_NUMBER + "~"
            + "EB*6*IND*30~"
            + "SE*5*0001~"
            + "GE*1*1~"
            + "IEA*1*" + ISA_CONTROL_NR + "~";
    }

    private String msgFallbackResponse() {
        return "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *260101*0900*^*00501*" + ISA_CONTROL_NR + "*0*T*:~"
            + "GS*HB*SENDER*RECEIVER*20260101*0900*1*X*005010X279A1~"
            + "ST*271*0001~"
            + "NM1*IL*1*DOE*JOHN****MI*" + MEMBER_NUMBER + "~"
            + "EB*6*IND*30~"
            + "MSG*Coverage not active~"
            + "SE*6*0001~"
            + "GE*1*1~"
            + "IEA*1*" + ISA_CONTROL_NR + "~";
    }
}
