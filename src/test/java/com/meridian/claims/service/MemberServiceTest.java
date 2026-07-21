package com.meridian.claims.service;

import com.meridian.claims.dao.MemberCoverageDAO;
import com.meridian.claims.dao.MemberDAO;
import com.meridian.claims.model.CoverageOrder;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberCoverage;
import com.meridian.claims.model.MemberStatus;
import com.meridian.claims.util.Page;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MemberServiceTest {

    private MemberDAO memberDAO;
    private MemberCoverageDAO memberCoverageDAO;
    private AuditService auditService;
    private MemberService service;

    @Before
    public void setUp() {
        memberDAO = mock(MemberDAO.class);
        memberCoverageDAO = mock(MemberCoverageDAO.class);
        auditService = mock(AuditService.class);
        service = new MemberService(memberDAO, memberCoverageDAO, auditService);
    }

    @Test
    public void createMemberInsertsAndReturns() {
        when(memberDAO.findByMemberNumber("M001")).thenReturn(null);
        Member created = service.createMember("M001", "Jane", "Doe", new Date(), null, null, null);
        verify(memberDAO).insert(any(Member.class));
        assertNotNull(created);
        assertEquals("M001", created.getMemberNumber());
        assertEquals(MemberStatus.ACTIVE, created.getStatus());
    }

    @Test(expected = ServiceException.class)
    public void createMemberDuplicateNumberThrows() {
        when(memberDAO.findByMemberNumber("M001")).thenReturn(new Member());
        service.createMember("M001", "Jane", "Doe", new Date(), null, null, null);
    }

    @Test(expected = ServiceException.class)
    public void createMemberMissingFirstNameThrows() {
        service.createMember("M002", "", "Doe", new Date(), null, null, null);
    }

    @Test(expected = ServiceException.class)
    public void createMemberNullDobThrows() {
        service.createMember("M003", "Jane", "Doe", null, null, null, null);
    }

    @Test
    public void searchDelegatesToDAO() {
        Page<Member> fakePage = new Page<Member>(new ArrayList<Member>(), 1, 20, 0);
        when(memberDAO.search("Jane", 1, 20)).thenReturn(fakePage);
        Page<Member> result = service.search("Jane", 1, 20);
        assertEquals(fakePage, result);
    }

    @Test
    public void findByIdThrowsWhenNotFound() {
        when(memberDAO.findById(99)).thenReturn(null);
        try {
            service.findById(99);
        } catch (ServiceException e) {
            return;
        }
        throw new AssertionError("Expected ServiceException");
    }

    @Test
    public void deactivateCallsSoftDelete() {
        Member m = new Member();
        m.setId(5);
        when(memberDAO.findById(5)).thenReturn(m);
        service.deactivateMember(5);
        verify(memberDAO).softDelete(5);
    }

    // --- COB enforcement ---

    private MemberCoverage coverage(int id, CoverageOrder order, String effective, String termination) {
        MemberCoverage c = new MemberCoverage();
        c.setId(id);
        c.setMemberId(10);
        c.setCoverageOrder(order);
        c.setEffectiveDate(date(effective));
        c.setTerminationDate(termination == null ? null : date(termination));
        return c;
    }

    private Date date(String yyyyMmDd) {
        String[] p = yyyyMmDd.split("-");
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.clear();
        cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
        return cal.getTime();
    }

    @Test
    public void addFirstPrimaryCoverageSucceeds() {
        Member m = new Member();
        m.setId(10);
        when(memberDAO.findById(10)).thenReturn(m);
        when(memberCoverageDAO.findByMemberId(10)).thenReturn(new ArrayList<MemberCoverage>());

        service.addCoverage(10, 1, "PRIMARY", date("2026-01-01"), null);

        verify(memberCoverageDAO).insert(any(MemberCoverage.class));
    }

    @Test
    public void secondOverlappingPrimaryRejected() {
        Member m = new Member();
        m.setId(10);
        when(memberDAO.findById(10)).thenReturn(m);
        List<MemberCoverage> existing = new ArrayList<MemberCoverage>();
        existing.add(coverage(1, CoverageOrder.PRIMARY, "2026-01-01", null));
        when(memberCoverageDAO.findByMemberId(10)).thenReturn(existing);

        try {
            service.addCoverage(10, 2, "PRIMARY", date("2026-06-01"), null);
            throw new AssertionError("Expected ServiceException");
        } catch (ServiceException e) {
            // expected
        }
        verify(memberCoverageDAO, never()).insert(any(MemberCoverage.class));
    }

    @Test
    public void secondaryWithoutPrimaryRejected() {
        Member m = new Member();
        m.setId(10);
        when(memberDAO.findById(10)).thenReturn(m);
        when(memberCoverageDAO.findByMemberId(10)).thenReturn(new ArrayList<MemberCoverage>());

        try {
            service.addCoverage(10, 2, "SECONDARY", date("2026-01-01"), null);
            throw new AssertionError("Expected ServiceException");
        } catch (ServiceException e) {
            // expected
        }
        verify(memberCoverageDAO, never()).insert(any(MemberCoverage.class));
    }

    @Test
    public void secondaryWithOverlappingPrimaryAccepted() {
        Member m = new Member();
        m.setId(10);
        when(memberDAO.findById(10)).thenReturn(m);
        List<MemberCoverage> existing = new ArrayList<MemberCoverage>();
        existing.add(coverage(1, CoverageOrder.PRIMARY, "2026-01-01", null));
        when(memberCoverageDAO.findByMemberId(10)).thenReturn(existing);

        service.addCoverage(10, 2, "SECONDARY", date("2026-02-01"), null);

        verify(memberCoverageDAO).insert(any(MemberCoverage.class));
    }

    @Test
    public void nonOverlappingSecondPrimaryAccepted() {
        Member m = new Member();
        m.setId(10);
        when(memberDAO.findById(10)).thenReturn(m);
        List<MemberCoverage> existing = new ArrayList<MemberCoverage>();
        existing.add(coverage(1, CoverageOrder.PRIMARY, "2025-01-01", "2025-12-31"));
        when(memberCoverageDAO.findByMemberId(10)).thenReturn(existing);

        // new primary starts after the prior one terminated
        service.addCoverage(10, 2, "PRIMARY", date("2026-01-01"), null);

        verify(memberCoverageDAO).insert(any(MemberCoverage.class));
    }

    @Test
    public void terminationBeforeEffectiveRejected() {
        Member m = new Member();
        m.setId(10);
        when(memberDAO.findById(10)).thenReturn(m);
        when(memberCoverageDAO.findByMemberId(10)).thenReturn(new ArrayList<MemberCoverage>());

        try {
            service.addCoverage(10, 1, "PRIMARY", date("2026-06-01"), date("2026-01-01"));
            throw new AssertionError("Expected ServiceException");
        } catch (ServiceException e) {
            // expected
        }
        verify(memberCoverageDAO, never()).insert(any(MemberCoverage.class));
    }
}
