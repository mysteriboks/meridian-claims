package com.meridian.claims.job;

import com.meridian.claims.dao.EdiTransactionDAO;
import com.meridian.claims.dao.TradingPartnerDAO;
import com.meridian.claims.intake.FhirClaimFileParser;
import com.meridian.claims.intake.ParserResolver;
import com.meridian.claims.intake.PriorAuthRequestFileMatcher;
import com.meridian.claims.intake.StatusInquiryFileMatcher;
import com.meridian.claims.intake.X12Edi837Parser;
import com.meridian.claims.model.EdiTransaction;
import com.meridian.claims.model.TradingPartner;
import com.meridian.claims.service.ClaimStatusInquiryService;
import com.meridian.claims.service.IntakeService;
import com.meridian.claims.service.PriorAuthRequestService;
import com.meridian.claims.service.ScheduledJobLogService;
import com.meridian.claims.transport.TransportAdapter;
import com.meridian.claims.transport.TransportAdapterResolver;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.quartz.JobExecutionContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that both pollers route .278 files to PriorAuthRequestService — not
 * the claim-submission path (IntakeService) or the status-inquiry path
 * (ClaimStatusInquiryService). Mirrors StatusInquiryRoutingTest for the same
 * reason: this dispatch seam is additive and easy to silently regress.
 */
public class PriorAuthRoutingTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void globalPoller_278File_routesToPriorAuthRequestService_notOtherPaths() throws IOException {
        Path inbound = tmp.newFolder("inbound").toPath();
        Path archive = tmp.newFolder("archive").toPath();
        Path rejected = tmp.newFolder("rejected").toPath();
        Files.write(inbound.resolve("request.278"), "ISA*...".getBytes(StandardCharsets.UTF_8));

        IntakeService intakeService = mock(IntakeService.class);
        ClaimStatusInquiryService claimStatusInquiryService = mock(ClaimStatusInquiryService.class);
        PriorAuthRequestService priorAuthRequestService = mock(PriorAuthRequestService.class);
        ScheduledJobLogService jobLogService = mock(ScheduledJobLogService.class);
        when(jobLogService.start(anyString())).thenReturn(1);
        when(priorAuthRequestService.processFile(eq("request.278"), anyString(), any())).thenReturn(true);

        ParserResolver parserResolver = new ParserResolver(mock(FhirClaimFileParser.class), mock(X12Edi837Parser.class));
        ReflectionTestUtils.setField(parserResolver, "ediExtensions", "edi,x12,837");
        StatusInquiryFileMatcher statusMatcher = mock(StatusInquiryFileMatcher.class);
        PriorAuthRequestFileMatcher priorAuthMatcher = new PriorAuthRequestFileMatcher();
        ReflectionTestUtils.setField(priorAuthMatcher, "priorAuthExtensions", "278");

        InboundClaimFilePollerJob job = new InboundClaimFilePollerJob();
        ReflectionTestUtils.setField(job, "intakeService", intakeService);
        ReflectionTestUtils.setField(job, "claimStatusInquiryService", claimStatusInquiryService);
        ReflectionTestUtils.setField(job, "priorAuthRequestService", priorAuthRequestService);
        ReflectionTestUtils.setField(job, "jobLogService", jobLogService);
        ReflectionTestUtils.setField(job, "parserResolver", parserResolver);
        ReflectionTestUtils.setField(job, "statusInquiryFileMatcher", statusMatcher);
        ReflectionTestUtils.setField(job, "priorAuthRequestFileMatcher", priorAuthMatcher);
        ReflectionTestUtils.setField(job, "intakeEnabled", true);
        ReflectionTestUtils.setField(job, "inboundPath", inbound.toString());
        ReflectionTestUtils.setField(job, "archivePath", archive.toString());
        ReflectionTestUtils.setField(job, "rejectedPath", rejected.toString());

        job.execute(mock(JobExecutionContext.class));

        verify(priorAuthRequestService).processFile(eq("request.278"), anyString(), any());
        verify(intakeService, never()).processFile(anyString(), anyString(), any(), any(Integer.class));
        verify(claimStatusInquiryService, never()).processFile(anyString(), anyString(), any(Integer.class));
        org.junit.Assert.assertTrue(Files.exists(archive.resolve("request.278")));
    }

    @Test
    public void partnerPoller_278File_routesToPriorAuthRequestService_notOtherPaths() throws Exception {
        TradingPartnerDAO tradingPartnerDAO = mock(TradingPartnerDAO.class);
        TransportAdapterResolver transportAdapterResolver = mock(TransportAdapterResolver.class);
        TransportAdapter adapter = mock(TransportAdapter.class);
        IntakeService intakeService = mock(IntakeService.class);
        ClaimStatusInquiryService claimStatusInquiryService = mock(ClaimStatusInquiryService.class);
        PriorAuthRequestService priorAuthRequestService = mock(PriorAuthRequestService.class);
        EdiTransactionDAO ediTransactionDAO = mock(EdiTransactionDAO.class);
        ScheduledJobLogService jobLogService = mock(ScheduledJobLogService.class);

        TradingPartner partner = new TradingPartner();
        partner.setId(1);
        partner.setPartnerName("Acme");

        when(tradingPartnerDAO.findAllActive()).thenReturn(Arrays.asList(partner));
        when(transportAdapterResolver.resolve(partner)).thenReturn(adapter);
        when(adapter.listInboundFiles(partner)).thenReturn(Arrays.asList("request.278"));
        when(adapter.readInboundFile(partner, "request.278")).thenReturn("ISA*...");
        when(priorAuthRequestService.processFile(eq("request.278"), anyString(), eq(1))).thenReturn(true);
        when(ediTransactionDAO.findByFileReference("request.278")).thenReturn(Collections.<EdiTransaction>emptyList());
        when(jobLogService.start(anyString())).thenReturn(1);

        ParserResolver parserResolver = mock(ParserResolver.class);
        StatusInquiryFileMatcher statusMatcher = mock(StatusInquiryFileMatcher.class);
        PriorAuthRequestFileMatcher priorAuthMatcher = new PriorAuthRequestFileMatcher();
        ReflectionTestUtils.setField(priorAuthMatcher, "priorAuthExtensions", "278");

        TradingPartnerPollerJob job = new TradingPartnerPollerJob();
        ReflectionTestUtils.setField(job, "tradingPartnerDAO", tradingPartnerDAO);
        ReflectionTestUtils.setField(job, "transportAdapterResolver", transportAdapterResolver);
        ReflectionTestUtils.setField(job, "parserResolver", parserResolver);
        ReflectionTestUtils.setField(job, "statusInquiryFileMatcher", statusMatcher);
        ReflectionTestUtils.setField(job, "priorAuthRequestFileMatcher", priorAuthMatcher);
        ReflectionTestUtils.setField(job, "intakeService", intakeService);
        ReflectionTestUtils.setField(job, "claimStatusInquiryService", claimStatusInquiryService);
        ReflectionTestUtils.setField(job, "priorAuthRequestService", priorAuthRequestService);
        ReflectionTestUtils.setField(job, "ediTransactionDAO", ediTransactionDAO);
        ReflectionTestUtils.setField(job, "jobLogService", jobLogService);

        job.execute(mock(JobExecutionContext.class));

        verify(priorAuthRequestService).processFile(eq("request.278"), anyString(), eq(1));
        verify(intakeService, never()).processFile(anyString(), anyString(), any(), org.mockito.Matchers.anyInt());
        verify(claimStatusInquiryService, never()).processFile(anyString(), anyString(), any(Integer.class));
        verify(adapter).archiveInboundFile(partner, "request.278", true);
    }
}
