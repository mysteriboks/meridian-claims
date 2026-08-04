package com.meridian.claims.intake;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for X12Edi834Parser using inline 834 fixture strings, mirroring
 * X12Edi278ParserTest's fixture style.
 */
public class X12Edi834ParserTest {

    private X12Edi834Parser parser;

    private static final String MEMBER_NUMBER  = "M100042";
    private static final String ISA_CONTROL_NR = "000000001";

    @Before
    public void setUp() {
        parser = new X12Edi834Parser();
    }

    @Test
    public void addRecord_parsesAllFields() throws Exception {
        List<EnrollmentRecord> records = parser.parse(addRequest());

        assertEquals(1, records.size());
        EnrollmentRecord rec = records.get(0);
        assertEquals(ISA_CONTROL_NR, rec.getIsaControlNumber());
        assertEquals("1", rec.getGsControlNumber());
        assertEquals("0001", rec.getStControlNumber());
        assertEquals(EnrollmentRecord.MAINTENANCE_ADD, rec.getMaintenanceTypeCode());
        assertEquals(MEMBER_NUMBER, rec.getMemberNumber());
        assertEquals("DOE", rec.getLastName());
        assertEquals("JOHN", rec.getFirstName());
        assertEquals("19800101", rec.getDobString());
        assertEquals("Gold PPO", rec.getPlanName());
        assertEquals("20260101", rec.getEffectiveDateString());
        assertNull("no DTP*349 in this fixture", rec.getTerminationDateString());
    }

    @Test
    public void terminationRecord_parsesTerminationDate() throws Exception {
        List<EnrollmentRecord> records = parser.parse(terminationRequest());

        assertEquals(1, records.size());
        EnrollmentRecord rec = records.get(0);
        assertEquals(EnrollmentRecord.MAINTENANCE_TERM, rec.getMaintenanceTypeCode());
        assertEquals(MEMBER_NUMBER, rec.getMemberNumber());
        assertEquals("20260630", rec.getTerminationDateString());
    }

    @Test
    public void twoInsLoops_eachBecomesItsOwnRecord() throws Exception {
        List<EnrollmentRecord> records = parser.parse(twoMemberRequest());

        assertEquals(2, records.size());
        assertEquals("M100001", records.get(0).getMemberNumber());
        assertEquals(EnrollmentRecord.MAINTENANCE_ADD, records.get(0).getMaintenanceTypeCode());
        assertEquals("M100002", records.get(1).getMemberNumber());
        assertEquals(EnrollmentRecord.MAINTENANCE_CHANGE, records.get(1).getMaintenanceTypeCode());
    }

    @Test(expected = IntakeParseException.class)
    public void isaLevelFailure_throwsIntakeParseException() throws Exception {
        parser.parse("THIS IS NOT AN EDI FILE AND CANNOT BE PARSED AS X12 834");
    }

    @Test(expected = IntakeParseException.class)
    public void emptyContent_throwsIntakeParseException() throws Exception {
        parser.parse("");
    }

    @Test
    public void emptyFile_noTransactions_returnsEmptyList() throws Exception {
        String noTransactions =
            "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *260101*0900*^*00501*" + ISA_CONTROL_NR + "*0*T*:~"
            + "GS*BE*SENDER*RECEIVER*20260101*0900*1*X*005010X220~"
            + "GE*0*1~"
            + "IEA*1*" + ISA_CONTROL_NR + "~";
        List<EnrollmentRecord> records = parser.parse(noTransactions);
        assertTrue(records.isEmpty());
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    private String addRequest() {
        return "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *260101*0900*^*00501*" + ISA_CONTROL_NR + "*0*T*:~"
            + "GS*BE*SENDER*RECEIVER*20260101*0900*1*X*005010X220~"
            + "ST*834*0001*005010X220~"
            + "INS*Y*18*021*A*E~"
            + "NM1*IL*1*DOE*JOHN****MI*" + MEMBER_NUMBER + "~"
            + "DMG*D8*19800101*M~"
            + "HD*021**HLT*Gold PPO~"
            + "DTP*348*D8*20260101~"
            + "SE*8*0001~"
            + "GE*1*1~"
            + "IEA*1*" + ISA_CONTROL_NR + "~";
    }

    private String terminationRequest() {
        return "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *260101*0900*^*00501*" + ISA_CONTROL_NR + "*0*T*:~"
            + "GS*BE*SENDER*RECEIVER*20260101*0900*1*X*005010X220~"
            + "ST*834*0001*005010X220~"
            + "INS*Y*18*024*A*E~"
            + "NM1*IL*1*DOE*JOHN****MI*" + MEMBER_NUMBER + "~"
            + "DTP*349*D8*20260630~"
            + "SE*6*0001~"
            + "GE*1*1~"
            + "IEA*1*" + ISA_CONTROL_NR + "~";
    }

    private String twoMemberRequest() {
        return "ISA*00*          *00*          *ZZ*SENDER         *ZZ*RECEIVER       *260101*0900*^*00501*" + ISA_CONTROL_NR + "*0*T*:~"
            + "GS*BE*SENDER*RECEIVER*20260101*0900*1*X*005010X220~"
            + "ST*834*0001*005010X220~"
            + "INS*Y*18*021*A*E~"
            + "NM1*IL*1*SMITH*JANE****MI*M100001~"
            + "DMG*D8*19750505*F~"
            + "DTP*348*D8*20260101~"
            + "INS*Y*18*001*A*E~"
            + "NM1*IL*1*BROWN*ALEX****MI*M100002~"
            + "SE*10*0001~"
            + "GE*1*1~"
            + "IEA*1*" + ISA_CONTROL_NR + "~";
    }
}
