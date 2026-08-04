package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.EdiTransactionDAO;
import com.meridian.claims.intake.X12Edi276Parser;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.EdiTransaction;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.Provider;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClaimStatusInquiryServiceTest {

    private ClaimStatusInquiryService service;
    private X12Edi276Parser parser;
    private ClaimDAO claimDAO;
    private MemberService memberService;
    private ProviderService providerService;
    private Edi277Generator edi277Generator;
    private EdiTransactionDAO ediTransactionDAO;
    private AuditService auditService;

    private static final String MEMBER_NUMBER = "MBR-001";
    private static final String PROVIDER_NPI = "1234567890";

    @Before
    public void setUp() {
        parser = mock(X12Edi276Parser.class);
        claimDAO = mock(ClaimDAO.class);
        memberService = mock(MemberService.class);
        providerService = mock(ProviderService.class);
        edi277Generator = mock(Edi277Generator.class);
        ediTransactionDAO = mock(EdiTransactionDAO.class);
        auditService = mock(AuditService.class);

        service = new ClaimStatusInquiryService(parser, claimDAO, memberService, providerService,
            edi277Generator, ediTransactionDAO, auditService);

        when(ediTransactionDAO.insert(any(EdiTransaction.class))).thenReturn(1);
        when(edi277Generator.generate277(anyString(), any())).thenReturn("277-EDI-CONTENT");
    }

    private com.meridian.claims.intake.ClaimStatusInquiry inquiryByClaimNumber(String claimNumber) {
        com.meridian.claims.intake.ClaimStatusInquiry inquiry = new com.meridian.claims.intake.ClaimStatusInquiry();
        inquiry.setIsaControlNumber("ISA-1");
        inquiry.setGsControlNumber("1");
        inquiry.setStControlNumber("0001");
        inquiry.setClaimControlNumber(claimNumber);
        return inquiry;
    }

    private com.meridian.claims.intake.ClaimStatusInquiry inquiryByMemberProviderDos() {
        com.meridian.claims.intake.ClaimStatusInquiry inquiry = new com.meridian.claims.intake.ClaimStatusInquiry();
        inquiry.setIsaControlNumber("ISA-1");
        inquiry.setGsControlNumber("1");
        inquiry.setStControlNumber("0001");
        inquiry.setMemberNumber(MEMBER_NUMBER);
        inquiry.setProviderNpi(PROVIDER_NPI);
        inquiry.setDateOfServiceString("20260115");
        return inquiry;
    }

    private Date date(int y, int m, int d) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(y, m - 1, d);
        return cal.getTime();
    }

    @Test
    public void inquiryByClaimNumber_found_resolvesDirectly() throws Exception {
        when(parser.parse(anyString())).thenReturn(Arrays.asList(inquiryByClaimNumber("CLM-0001")));
        Claim claim = new Claim();
        claim.setClaimNumber("CLM-0001");
        claim.setStatus(ClaimStatus.PAID);
        when(claimDAO.findByClaimNumber("CLM-0001")).thenReturn(claim);

        boolean parsed = service.processFile("inquiry.276", "ISA*...", null);

        assertTrue(parsed);
        ArgumentCaptor<java.util.List> captor = ArgumentCaptor.forClass(java.util.List.class);
        verify(edi277Generator).generate277(anyString(), captor.capture());
        @SuppressWarnings("unchecked")
        java.util.List<Edi277Generator.ClaimStatusResult> results = captor.getValue();
        assertEquals(1, results.size());
        assertTrue(results.get(0).isFound());
        assertEquals("CLM-0001", results.get(0).getClaimNumber());
        assertEquals(ClaimStatus.PAID, results.get(0).getStatus());
        verify(claimDAO, never()).findByMemberAndDOS(anyInt(), anyInt(), any(Date.class));
    }

    @Test
    public void inquiryByMemberProviderDos_found_fallsBackWhenNoClaimNumber() throws Exception {
        when(parser.parse(anyString())).thenReturn(Arrays.asList(inquiryByMemberProviderDos()));
        Member member = new Member();
        member.setId(10);
        when(memberService.findByMemberNumber(MEMBER_NUMBER)).thenReturn(member);
        Provider provider = new Provider();
        provider.setId(5);
        when(providerService.findByNpi(PROVIDER_NPI)).thenReturn(provider);
        Claim claim = new Claim();
        claim.setClaimNumber("CLM-0099");
        claim.setStatus(ClaimStatus.IN_REVIEW);
        when(claimDAO.findByMemberAndDOS(10, 5, date(2026, 1, 15))).thenReturn(claim);

        service.processFile("inquiry.276", "ISA*...", null);

        ArgumentCaptor<java.util.List> captor = ArgumentCaptor.forClass(java.util.List.class);
        verify(edi277Generator).generate277(anyString(), captor.capture());
        @SuppressWarnings("unchecked")
        java.util.List<Edi277Generator.ClaimStatusResult> results = captor.getValue();
        assertTrue(results.get(0).isFound());
        assertEquals("CLM-0099", results.get(0).getClaimNumber());
    }

    @Test
    public void inquiryWithUnknownClaimNumber_reportsNotFound() throws Exception {
        when(parser.parse(anyString())).thenReturn(Arrays.asList(inquiryByClaimNumber("CLM-BOGUS")));
        when(claimDAO.findByClaimNumber("CLM-BOGUS")).thenReturn(null);

        service.processFile("inquiry.276", "ISA*...", null);

        ArgumentCaptor<java.util.List> captor = ArgumentCaptor.forClass(java.util.List.class);
        verify(edi277Generator).generate277(anyString(), captor.capture());
        @SuppressWarnings("unchecked")
        java.util.List<Edi277Generator.ClaimStatusResult> results = captor.getValue();
        assertEquals(false, results.get(0).isFound());
        assertNull(results.get(0).getClaimNumber());
    }

    @Test
    public void inquiryWithNoIdentifyingFields_reportsNotFound() throws Exception {
        com.meridian.claims.intake.ClaimStatusInquiry bare = new com.meridian.claims.intake.ClaimStatusInquiry();
        bare.setStControlNumber("0001");
        when(parser.parse(anyString())).thenReturn(Arrays.asList(bare));

        service.processFile("inquiry.276", "ISA*...", null);

        // This inquiry has no ISA control number either (bare fixture), so the first
        // generate277 argument is null here — anyString() (Mockito 2) rejects null, hence any().
        ArgumentCaptor<java.util.List> captor = ArgumentCaptor.forClass(java.util.List.class);
        verify(edi277Generator).generate277(any(), captor.capture());
        @SuppressWarnings("unchecked")
        java.util.List<Edi277Generator.ClaimStatusResult> results = captor.getValue();
        assertEquals(false, results.get(0).isFound());
        verify(claimDAO, never()).findByMemberAndDOS(anyInt(), anyInt(), any(Date.class));
    }

    @Test
    public void logsInboundAndOutboundEdiTransactions_withTradingPartnerId() throws Exception {
        when(parser.parse(anyString())).thenReturn(Arrays.asList(inquiryByClaimNumber("CLM-0001")));
        when(claimDAO.findByClaimNumber("CLM-0001")).thenReturn(null);

        service.processFile("partner-inquiry.276", "ISA*...", 42);

        ArgumentCaptor<EdiTransaction> captor = ArgumentCaptor.forClass(EdiTransaction.class);
        verify(ediTransactionDAO, times(2)).insert(captor.capture());
        EdiTransaction inbound = captor.getAllValues().get(0);
        EdiTransaction outbound = captor.getAllValues().get(1);
        assertEquals("INBOUND", inbound.getDirection());
        assertEquals("276", inbound.getTransactionType());
        assertEquals(Integer.valueOf(42), inbound.getTradingPartnerId());
        assertEquals("OUTBOUND", outbound.getDirection());
        assertEquals("277", outbound.getTransactionType());
        assertEquals(Integer.valueOf(42), outbound.getTradingPartnerId());
        assertEquals("277-EDI-CONTENT", outbound.getDetail());
    }

    @Test
    public void fileLevelParseFailure_returnsFalse_noGeneratorCall() throws Exception {
        when(parser.parse(anyString())).thenThrow(new com.meridian.claims.intake.IntakeParseException("garbled"));

        boolean parsed = service.processFile("bad.276", "not edi", null);

        assertEquals(false, parsed);
        verify(edi277Generator, never()).generate277(anyString(), any());
        verify(ediTransactionDAO, never()).insert(any(EdiTransaction.class));
    }
}
