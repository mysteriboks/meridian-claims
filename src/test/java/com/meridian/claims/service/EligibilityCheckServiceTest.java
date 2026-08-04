package com.meridian.claims.service;

import com.meridian.claims.dao.EligibilityCheckDAO;
import com.meridian.claims.intake.EligibilityResponse;
import com.meridian.claims.intake.IntakeParseException;
import com.meridian.claims.intake.X12Edi271Parser;
import com.meridian.claims.model.EligibilityCheck;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.Provider;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EligibilityCheckServiceTest {

    private EligibilityCheckService service;
    private Edi270Generator edi270Generator;
    private X12Edi271Parser edi271Parser;
    private EligibilityClient eligibilityClient;
    private EligibilityCheckDAO eligibilityCheckDAO;
    private MemberService memberService;
    private ProviderService providerService;
    private AuditService auditService;

    @Before
    public void setUp() {
        edi270Generator = mock(Edi270Generator.class);
        edi271Parser = mock(X12Edi271Parser.class);
        eligibilityClient = mock(EligibilityClient.class);
        eligibilityCheckDAO = mock(EligibilityCheckDAO.class);
        memberService = mock(MemberService.class);
        providerService = mock(ProviderService.class);
        auditService = mock(AuditService.class);

        service = new EligibilityCheckService(edi270Generator, edi271Parser, eligibilityClient,
            eligibilityCheckDAO, memberService, providerService, auditService);

        when(edi270Generator.generate270(any(Member.class), any(), anyString())).thenReturn("270-EDI");
    }

    @Test
    public void memberNotFound_throwsServiceException() {
        when(memberService.findById(99)).thenReturn(null);
        try {
            service.checkEligibility(99, null, "30", 1);
            org.junit.Assert.fail("Expected ServiceException");
        } catch (ServiceException expected) {
            assertEquals("Member not found: 99", expected.getMessage());
        }
        verify(eligibilityCheckDAO, never()).insert(any(EligibilityCheck.class));
    }

    @Test
    public void activeCoverage_mapsEb01OneToActive() throws Exception {
        Member member = new Member();
        member.setId(10);
        when(memberService.findById(10)).thenReturn(member);
        when(eligibilityClient.checkEligibility(any(Member.class), isNull(Provider.class), anyString(), anyString()))
            .thenReturn("271-EDI");
        EligibilityResponse resp = new EligibilityResponse();
        resp.setEb01Code("1");
        resp.setPlanDescription("Gold PPO");
        when(edi271Parser.parse("271-EDI")).thenReturn(Arrays.asList(resp));

        EligibilityCheck check = service.checkEligibility(10, null, "30", 5);

        assertEquals(EligibilityCheck.STATUS_ACTIVE, check.getResultStatus());
        assertEquals("Gold PPO", check.getCoverageSnapshot());
        ArgumentCaptor<EligibilityCheck> captor = ArgumentCaptor.forClass(EligibilityCheck.class);
        verify(eligibilityCheckDAO).insert(captor.capture());
        assertEquals(10, captor.getValue().getMemberId());
        assertEquals(Integer.valueOf(5), captor.getValue().getCheckedByUserId());
    }

    @Test
    public void inactiveCoverage_mapsEb01SixToInactive() throws Exception {
        Member member = new Member();
        member.setId(11);
        when(memberService.findById(11)).thenReturn(member);
        when(eligibilityClient.checkEligibility(any(Member.class), isNull(Provider.class), anyString(), anyString()))
            .thenReturn("271-EDI");
        EligibilityResponse resp = new EligibilityResponse();
        resp.setEb01Code("6");
        when(edi271Parser.parse("271-EDI")).thenReturn(Arrays.asList(resp));

        EligibilityCheck check = service.checkEligibility(11, null, "30", null);

        assertEquals(EligibilityCheck.STATUS_INACTIVE, check.getResultStatus());
    }

    @Test
    public void unrecognizedEb01Code_mapsToError() throws Exception {
        Member member = new Member();
        member.setId(12);
        when(memberService.findById(12)).thenReturn(member);
        when(eligibilityClient.checkEligibility(any(Member.class), isNull(Provider.class), anyString(), anyString()))
            .thenReturn("271-EDI");
        EligibilityResponse resp = new EligibilityResponse();
        resp.setEb01Code("Z");
        when(edi271Parser.parse("271-EDI")).thenReturn(Arrays.asList(resp));

        EligibilityCheck check = service.checkEligibility(12, null, "30", null);

        assertEquals(EligibilityCheck.STATUS_ERROR, check.getResultStatus());
    }

    @Test
    public void emptyResponseTransactionList_mapsToError() throws Exception {
        Member member = new Member();
        member.setId(13);
        when(memberService.findById(13)).thenReturn(member);
        when(eligibilityClient.checkEligibility(any(Member.class), isNull(Provider.class), anyString(), anyString()))
            .thenReturn("271-EDI");
        when(edi271Parser.parse("271-EDI")).thenReturn(Collections.<EligibilityResponse>emptyList());

        EligibilityCheck check = service.checkEligibility(13, null, "30", null);

        assertEquals(EligibilityCheck.STATUS_ERROR, check.getResultStatus());
    }

    @Test
    public void clientException_isCaughtAndMappedToError() throws Exception {
        Member member = new Member();
        member.setId(14);
        when(memberService.findById(14)).thenReturn(member);
        when(eligibilityClient.checkEligibility(any(Member.class), isNull(Provider.class), anyString(), anyString()))
            .thenThrow(new EligibilityClientException("clearinghouse unreachable"));

        EligibilityCheck check = service.checkEligibility(14, null, "30", null);

        assertEquals(EligibilityCheck.STATUS_ERROR, check.getResultStatus());
        verify(eligibilityCheckDAO).insert(any(EligibilityCheck.class));
    }

    @Test
    public void parseException_isCaughtAndMappedToError() throws Exception {
        Member member = new Member();
        member.setId(15);
        when(memberService.findById(15)).thenReturn(member);
        when(eligibilityClient.checkEligibility(any(Member.class), isNull(Provider.class), anyString(), anyString()))
            .thenReturn("garbled");
        when(edi271Parser.parse("garbled")).thenThrow(new IntakeParseException("bad edi"));

        EligibilityCheck check = service.checkEligibility(15, null, "30", null);

        assertEquals(EligibilityCheck.STATUS_ERROR, check.getResultStatus());
    }

    @Test
    public void providerId_resolvesProviderAndPassesToClient() throws Exception {
        Member member = new Member();
        member.setId(16);
        when(memberService.findById(16)).thenReturn(member);
        Provider provider = new Provider();
        provider.setId(7);
        when(providerService.findById(7)).thenReturn(provider);
        when(eligibilityClient.checkEligibility(any(Member.class), eq(provider), anyString(), anyString()))
            .thenReturn("271-EDI");
        EligibilityResponse resp = new EligibilityResponse();
        resp.setEb01Code("1");
        when(edi271Parser.parse("271-EDI")).thenReturn(Arrays.asList(resp));

        EligibilityCheck check = service.checkEligibility(16, 7, "30", null);

        assertEquals(EligibilityCheck.STATUS_ACTIVE, check.getResultStatus());
        verify(eligibilityClient).checkEligibility(any(Member.class), eq(provider), anyString(), anyString());
    }

    @Test
    public void findRecentByMember_delegatesToDao() {
        service.findRecentByMember(10, 5);
        verify(eligibilityCheckDAO).findByMemberId(10, 5);
    }
}
