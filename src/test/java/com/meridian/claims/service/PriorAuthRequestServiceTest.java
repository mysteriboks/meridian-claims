package com.meridian.claims.service;

import com.meridian.claims.dao.EdiTransactionDAO;
import com.meridian.claims.intake.IntakeParseException;
import com.meridian.claims.intake.PriorAuthRequest;
import com.meridian.claims.intake.X12Edi278Parser;
import com.meridian.claims.model.EdiTransaction;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.PriorAuthorization;
import com.meridian.claims.model.Provider;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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

public class PriorAuthRequestServiceTest {

    private PriorAuthRequestService service;
    private X12Edi278Parser parser;
    private MemberService memberService;
    private ProviderService providerService;
    private PriorAuthorizationService priorAuthorizationService;
    private Edi278ResponseGenerator edi278ResponseGenerator;
    private EdiTransactionDAO ediTransactionDAO;
    private AuditService auditService;

    private static final String MEMBER_NUMBER = "MBR-001";
    private static final String PROVIDER_NPI = "1234567890";

    @Before
    public void setUp() {
        parser = mock(X12Edi278Parser.class);
        memberService = mock(MemberService.class);
        providerService = mock(ProviderService.class);
        priorAuthorizationService = mock(PriorAuthorizationService.class);
        edi278ResponseGenerator = mock(Edi278ResponseGenerator.class);
        ediTransactionDAO = mock(EdiTransactionDAO.class);
        auditService = mock(AuditService.class);

        service = new PriorAuthRequestService(parser, memberService, providerService,
            priorAuthorizationService, edi278ResponseGenerator, ediTransactionDAO, auditService);

        when(ediTransactionDAO.insert(any(EdiTransaction.class))).thenReturn(1);
        when(edi278ResponseGenerator.generate278Response(anyString(), any())).thenReturn("278-RESPONSE-EDI");
    }

    private PriorAuthRequest validRequest() {
        PriorAuthRequest req = new PriorAuthRequest();
        req.setIsaControlNumber("ISA-1");
        req.setGsControlNumber("1");
        req.setStControlNumber("0001");
        req.setMemberNumber(MEMBER_NUMBER);
        req.setProviderNpi(PROVIDER_NPI);
        req.setProcedureCode("99213");
        req.setServiceType("SPECIALIST_VISIT");
        req.setAuthorizedFromString("20260101");
        req.setAuthorizedToString("20260131");
        req.setRequestedUnits(3);
        return req;
    }

    @Test
    public void validRequest_certifiesAndCreatesAuthorization() throws Exception {
        when(parser.parse(anyString())).thenReturn(Arrays.asList(validRequest()));
        Member member = new Member();
        member.setId(10);
        when(memberService.findByMemberNumber(MEMBER_NUMBER)).thenReturn(member);
        Provider provider = new Provider();
        provider.setId(5);
        when(providerService.findByNpi(PROVIDER_NPI)).thenReturn(provider);
        PriorAuthorization created = new PriorAuthorization();
        created.setAuthNumber("PA-ABCD1234");
        when(priorAuthorizationService.createAuthorization(eq(10), eq(5), eq("99213"),
            eq("SPECIALIST_VISIT"), any(), any(), eq(3), anyString())).thenReturn(created);

        boolean parsed = service.processFile("request.278", "ISA*...", null);

        assertTrue(parsed);
        ArgumentCaptor<java.util.List> captor = ArgumentCaptor.forClass(java.util.List.class);
        verify(edi278ResponseGenerator).generate278Response(anyString(), captor.capture());
        @SuppressWarnings("unchecked")
        java.util.List<Edi278ResponseGenerator.PriorAuthResult> results = captor.getValue();
        assertEquals(1, results.size());
        assertTrue(results.get(0).isApproved());
        assertEquals("PA-ABCD1234", results.get(0).getAuthNumber());
        verify(priorAuthorizationService).createAuthorization(eq(10), eq(5), eq("99213"),
            eq("SPECIALIST_VISIT"), any(), any(), eq(3), anyString());
    }

    @Test
    public void unknownMember_deniesWithoutCreatingAuthorization() throws Exception {
        when(parser.parse(anyString())).thenReturn(Arrays.asList(validRequest()));
        when(memberService.findByMemberNumber(MEMBER_NUMBER)).thenReturn(null);

        service.processFile("request.278", "ISA*...", null);

        ArgumentCaptor<java.util.List> captor = ArgumentCaptor.forClass(java.util.List.class);
        verify(edi278ResponseGenerator).generate278Response(anyString(), captor.capture());
        @SuppressWarnings("unchecked")
        java.util.List<Edi278ResponseGenerator.PriorAuthResult> results = captor.getValue();
        assertEquals(false, results.get(0).isApproved());
        assertEquals("member not found", results.get(0).getReason());
        verify(priorAuthorizationService, never()).createAuthorization(
            anyInt(), anyInt(), anyString(), anyString(), any(), any(), anyInt(), anyString());
    }

    @Test
    public void unknownProvider_deniesWithoutCreatingAuthorization() throws Exception {
        when(parser.parse(anyString())).thenReturn(Arrays.asList(validRequest()));
        Member member = new Member();
        member.setId(10);
        when(memberService.findByMemberNumber(MEMBER_NUMBER)).thenReturn(member);
        when(providerService.findByNpi(PROVIDER_NPI)).thenReturn(null);

        service.processFile("request.278", "ISA*...", null);

        ArgumentCaptor<java.util.List> captor = ArgumentCaptor.forClass(java.util.List.class);
        verify(edi278ResponseGenerator).generate278Response(anyString(), captor.capture());
        @SuppressWarnings("unchecked")
        java.util.List<Edi278ResponseGenerator.PriorAuthResult> results = captor.getValue();
        assertEquals(false, results.get(0).isApproved());
        assertEquals("provider not found", results.get(0).getReason());
    }

    @Test
    public void invalidProcedureCode_denies() throws Exception {
        PriorAuthRequest req = validRequest();
        req.setProcedureCode("BAD");
        when(parser.parse(anyString())).thenReturn(Arrays.asList(req));

        service.processFile("request.278", "ISA*...", null);

        ArgumentCaptor<java.util.List> captor = ArgumentCaptor.forClass(java.util.List.class);
        verify(edi278ResponseGenerator).generate278Response(anyString(), captor.capture());
        @SuppressWarnings("unchecked")
        java.util.List<Edi278ResponseGenerator.PriorAuthResult> results = captor.getValue();
        assertEquals(false, results.get(0).isApproved());
        verify(memberService, never()).findByMemberNumber(anyString());
    }

    @Test
    public void missingAuthorizedPeriod_denies() throws Exception {
        PriorAuthRequest req = validRequest();
        req.setAuthorizedFromString(null);
        req.setAuthorizedToString(null);
        when(parser.parse(anyString())).thenReturn(Arrays.asList(req));
        Member member = new Member();
        member.setId(10);
        when(memberService.findByMemberNumber(MEMBER_NUMBER)).thenReturn(member);
        Provider provider = new Provider();
        provider.setId(5);
        when(providerService.findByNpi(PROVIDER_NPI)).thenReturn(provider);

        service.processFile("request.278", "ISA*...", null);

        ArgumentCaptor<java.util.List> captor = ArgumentCaptor.forClass(java.util.List.class);
        verify(edi278ResponseGenerator).generate278Response(anyString(), captor.capture());
        @SuppressWarnings("unchecked")
        java.util.List<Edi278ResponseGenerator.PriorAuthResult> results = captor.getValue();
        assertEquals(false, results.get(0).isApproved());
        assertTrue(results.get(0).getReason().contains("certification period"));
    }

    @Test
    public void logsInboundAndOutboundEdiTransactions_withTradingPartnerId() throws Exception {
        when(parser.parse(anyString())).thenReturn(Arrays.asList(validRequest()));
        when(memberService.findByMemberNumber(MEMBER_NUMBER)).thenReturn(null);

        service.processFile("partner-request.278", "ISA*...", 42);

        ArgumentCaptor<EdiTransaction> captor = ArgumentCaptor.forClass(EdiTransaction.class);
        verify(ediTransactionDAO, times(2)).insert(captor.capture());
        EdiTransaction inbound = captor.getAllValues().get(0);
        EdiTransaction outbound = captor.getAllValues().get(1);
        assertEquals("INBOUND", inbound.getDirection());
        assertEquals("278", inbound.getTransactionType());
        assertEquals(Integer.valueOf(42), inbound.getTradingPartnerId());
        assertEquals("OUTBOUND", outbound.getDirection());
        assertEquals("278", outbound.getTransactionType());
        assertEquals(Integer.valueOf(42), outbound.getTradingPartnerId());
        assertEquals("278-RESPONSE-EDI", outbound.getDetail());
    }

    @Test
    public void fileLevelParseFailure_returnsFalse_noResponseGenerated() throws Exception {
        when(parser.parse(anyString())).thenThrow(new IntakeParseException("garbled"));

        boolean parsed = service.processFile("bad.278", "not edi", null);

        assertEquals(false, parsed);
        verify(edi278ResponseGenerator, never()).generate278Response(anyString(), any());
        verify(ediTransactionDAO, never()).insert(any(EdiTransaction.class));
    }
}
