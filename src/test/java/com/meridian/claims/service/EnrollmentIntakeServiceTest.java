package com.meridian.claims.service;

import com.meridian.claims.dao.EnrollmentBatchDAO;
import com.meridian.claims.intake.EnrollmentRecord;
import com.meridian.claims.intake.IntakeParseException;
import com.meridian.claims.intake.X12Edi834Parser;
import com.meridian.claims.model.CoverageOrder;
import com.meridian.claims.model.EnrollmentBatch;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberCoverage;
import com.meridian.claims.model.Plan;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EnrollmentIntakeServiceTest {

    private EnrollmentIntakeService service;
    private EnrollmentBatchDAO batchDAO;
    private X12Edi834Parser parser;
    private MemberService memberService;
    private PlanService planService;
    private AuditService auditService;

    @Before
    public void setUp() {
        batchDAO = mock(EnrollmentBatchDAO.class);
        parser = mock(X12Edi834Parser.class);
        memberService = mock(MemberService.class);
        planService = mock(PlanService.class);
        auditService = mock(AuditService.class);

        service = new EnrollmentIntakeService(batchDAO, parser, memberService, planService, auditService);

        when(batchDAO.insert(any(EnrollmentBatch.class))).thenReturn(1);
    }

    private EnrollmentRecord addRecord() {
        EnrollmentRecord rec = new EnrollmentRecord();
        rec.setMaintenanceTypeCode(EnrollmentRecord.MAINTENANCE_ADD);
        rec.setMemberNumber("M100001");
        rec.setFirstName("JANE");
        rec.setLastName("SMITH");
        rec.setDobString("19800101");
        rec.setEffectiveDateString("20260101");
        return rec;
    }

    @Test
    public void addRecord_noPlan_createsMemberOnly() throws Exception {
        when(parser.parse(anyString())).thenReturn(Arrays.asList(addRecord()));
        when(memberService.findByMemberNumber("M100001")).thenReturn(null);
        Member created = new Member();
        created.setId(10);
        when(memberService.createMember(eq("M100001"), eq("JANE"), eq("SMITH"), any(Date.class),
            isNull(String.class), isNull(String.class), isNull(String.class))).thenReturn(created);

        IntakeService.IntakeSummary summary = service.processFile("enroll.834", "ISA*...");

        assertEquals(1, summary.getSucceeded());
        assertEquals(0, summary.getQuarantined());
        verify(memberService).createMember(eq("M100001"), eq("JANE"), eq("SMITH"), any(Date.class),
            isNull(String.class), isNull(String.class), isNull(String.class));
        verify(memberService, never()).addCoverage(anyInt(), anyInt(), anyString(), any(Date.class), any(Date.class));
    }

    @Test
    public void addRecord_withResolvablePlan_createsMemberAndCoverage() throws Exception {
        EnrollmentRecord rec = addRecord();
        rec.setPlanName("Gold PPO");
        when(parser.parse(anyString())).thenReturn(Arrays.asList(rec));
        when(memberService.findByMemberNumber("M100001")).thenReturn(null);
        Member created = new Member();
        created.setId(10);
        when(memberService.createMember(anyString(), anyString(), anyString(), any(Date.class),
            isNull(String.class), isNull(String.class), isNull(String.class))).thenReturn(created);
        Plan plan = new Plan();
        plan.setId(5);
        when(planService.findByPlanName("Gold PPO")).thenReturn(plan);

        IntakeService.IntakeSummary summary = service.processFile("enroll.834", "ISA*...");

        assertEquals(1, summary.getSucceeded());
        verify(memberService).addCoverage(eq(10), eq(5), eq("PRIMARY"), any(Date.class), isNull(Date.class));
    }

    @Test
    public void addRecord_planNotFound_quarantines() throws Exception {
        EnrollmentRecord rec = addRecord();
        rec.setPlanName("Nonexistent Plan");
        when(parser.parse(anyString())).thenReturn(Arrays.asList(rec));
        when(planService.findByPlanName("Nonexistent Plan")).thenReturn(null);

        IntakeService.IntakeSummary summary = service.processFile("enroll.834", "ISA*...");

        assertEquals(0, summary.getSucceeded());
        assertEquals(1, summary.getQuarantined());
        verify(memberService, never()).createMember(anyString(), anyString(), anyString(),
            any(Date.class), anyString(), anyString(), anyString());
    }

    @Test
    public void addRecord_memberAlreadyExists_quarantines() throws Exception {
        when(parser.parse(anyString())).thenReturn(Arrays.asList(addRecord()));
        Member existing = new Member();
        existing.setId(10);
        when(memberService.findByMemberNumber("M100001")).thenReturn(existing);

        IntakeService.IntakeSummary summary = service.processFile("enroll.834", "ISA*...");

        assertEquals(0, summary.getSucceeded());
        assertEquals(1, summary.getQuarantined());
        verify(memberService, never()).createMember(anyString(), anyString(), anyString(),
            any(Date.class), anyString(), anyString(), anyString());
    }

    @Test
    public void terminationRecord_activeCoverageFound_terminatesIt() throws Exception {
        EnrollmentRecord rec = new EnrollmentRecord();
        rec.setMaintenanceTypeCode(EnrollmentRecord.MAINTENANCE_TERM);
        rec.setMemberNumber("M100001");
        rec.setTerminationDateString("20260630");
        when(parser.parse(anyString())).thenReturn(Arrays.asList(rec));
        Member member = new Member();
        member.setId(10);
        when(memberService.findByMemberNumber("M100001")).thenReturn(member);

        MemberCoverage cov = new MemberCoverage();
        cov.setId(20);
        cov.setPlanId(5);
        cov.setCoverageOrder(CoverageOrder.PRIMARY);
        cov.setEffectiveDate(date("20260101"));
        when(memberService.getCoverageRecords(10)).thenReturn(Arrays.asList(cov));

        IntakeService.IntakeSummary summary = service.processFile("enroll.834", "ISA*...");

        assertEquals(1, summary.getSucceeded());
        ArgumentCaptor<Date> termCaptor = ArgumentCaptor.forClass(Date.class);
        verify(memberService).updateCoverage(eq(20), eq(5), eq("PRIMARY"), eq(cov.getEffectiveDate()), termCaptor.capture());
        assertEquals(date("20260630"), termCaptor.getValue());
    }

    @Test
    public void terminationRecord_noActiveCoverage_quarantines() throws Exception {
        EnrollmentRecord rec = new EnrollmentRecord();
        rec.setMaintenanceTypeCode(EnrollmentRecord.MAINTENANCE_TERM);
        rec.setMemberNumber("M100001");
        rec.setTerminationDateString("20260630");
        when(parser.parse(anyString())).thenReturn(Arrays.asList(rec));
        Member member = new Member();
        member.setId(10);
        when(memberService.findByMemberNumber("M100001")).thenReturn(member);
        when(memberService.getCoverageRecords(10)).thenReturn(new ArrayList<MemberCoverage>());

        IntakeService.IntakeSummary summary = service.processFile("enroll.834", "ISA*...");

        assertEquals(0, summary.getSucceeded());
        assertEquals(1, summary.getQuarantined());
    }

    @Test
    public void terminationRecord_memberNotFound_quarantines() throws Exception {
        EnrollmentRecord rec = new EnrollmentRecord();
        rec.setMaintenanceTypeCode(EnrollmentRecord.MAINTENANCE_TERM);
        rec.setMemberNumber("UNKNOWN");
        rec.setTerminationDateString("20260630");
        when(parser.parse(anyString())).thenReturn(Arrays.asList(rec));
        when(memberService.findByMemberNumber("UNKNOWN")).thenReturn(null);

        IntakeService.IntakeSummary summary = service.processFile("enroll.834", "ISA*...");

        assertEquals(1, summary.getQuarantined());
    }

    @Test
    public void changeRecord_updatesDemographicsPreservingExistingContactInfo() throws Exception {
        EnrollmentRecord rec = new EnrollmentRecord();
        rec.setMaintenanceTypeCode(EnrollmentRecord.MAINTENANCE_CHANGE);
        rec.setMemberNumber("M100001");
        rec.setFirstName("JANET");
        when(parser.parse(anyString())).thenReturn(Arrays.asList(rec));
        Member member = new Member();
        member.setId(10);
        member.setFirstName("JANE");
        member.setLastName("SMITH");
        member.setDob(date("19800101"));
        member.setAddress("123 Main St");
        member.setPhone("555-1234");
        member.setEmail("jane@example.com");
        when(memberService.findByMemberNumber("M100001")).thenReturn(member);

        IntakeService.IntakeSummary summary = service.processFile("enroll.834", "ISA*...");

        assertEquals(1, summary.getSucceeded());
        verify(memberService).updateMember(10, "JANET", "SMITH", member.getDob(),
            "123 Main St", "555-1234", "jane@example.com", null);
    }

    @Test
    public void changeRecord_memberNotFound_quarantines() throws Exception {
        EnrollmentRecord rec = new EnrollmentRecord();
        rec.setMaintenanceTypeCode(EnrollmentRecord.MAINTENANCE_CHANGE);
        rec.setMemberNumber("UNKNOWN");
        when(parser.parse(anyString())).thenReturn(Arrays.asList(rec));
        when(memberService.findByMemberNumber("UNKNOWN")).thenReturn(null);

        IntakeService.IntakeSummary summary = service.processFile("enroll.834", "ISA*...");

        assertEquals(1, summary.getQuarantined());
    }

    @Test
    public void unrecognizedMaintenanceType_quarantines() throws Exception {
        EnrollmentRecord rec = addRecord();
        rec.setMaintenanceTypeCode("999");
        when(parser.parse(anyString())).thenReturn(Arrays.asList(rec));

        IntakeService.IntakeSummary summary = service.processFile("enroll.834", "ISA*...");

        assertEquals(1, summary.getQuarantined());
    }

    @Test
    public void fileLevelParseFailure_returnsFailureSummary_noRecordsProcessed() throws Exception {
        when(parser.parse(anyString())).thenThrow(new IntakeParseException("garbled"));

        IntakeService.IntakeSummary summary = service.processFile("bad.834", "not edi");

        assertTrue(summary.shouldReject());
        verify(batchDAO).updateCompletion(eq(1), eq("FAILED"), eq(0), eq(0), eq(0), anyString());
    }

    @Test
    public void duplicateFile_skipsReprocessing() throws Exception {
        EnrollmentBatch existing = new EnrollmentBatch();
        existing.setId(7);
        existing.setStatus("COMPLETED");
        existing.setSucceeded(3);
        existing.setQuarantined(1);
        when(batchDAO.findByFileHash(anyString())).thenReturn(existing);

        IntakeService.IntakeSummary summary = service.processFile("enroll.834", "ISA*...");

        assertTrue(summary.isDuplicate());
        assertEquals(3, summary.getSucceeded());
        assertEquals(1, summary.getQuarantined());
        verify(parser, never()).parse(anyString());
        verify(batchDAO, never()).insert(any(EnrollmentBatch.class));
    }

    @Test
    public void staleProcessingBatch_isReclaimedNotDuplicated() throws Exception {
        EnrollmentBatch existing = new EnrollmentBatch();
        existing.setId(7);
        existing.setStatus("PROCESSING");
        when(batchDAO.findByFileHash(anyString())).thenReturn(existing);
        when(parser.parse(anyString())).thenReturn(new ArrayList<EnrollmentRecord>());

        IntakeService.IntakeSummary summary = service.processFile("enroll.834", "ISA*...");

        assertTrue(!summary.isDuplicate());
        verify(batchDAO, never()).insert(any(EnrollmentBatch.class));
        verify(batchDAO).updateCompletion(eq(7), anyString(), eq(0), eq(0), eq(0), isNull(String.class));
    }

    @Test
    public void findRecentBatches_delegatesToDao() {
        service.findRecentBatches(10);
        verify(batchDAO).findRecent(10);
    }

    private Date date(String yyyyMMdd) throws Exception {
        return new SimpleDateFormat("yyyyMMdd").parse(yyyyMMdd);
    }
}
