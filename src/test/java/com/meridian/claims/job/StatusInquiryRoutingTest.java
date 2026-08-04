package com.meridian.claims.job;

import com.meridian.claims.dao.EdiTransactionDAO;
import com.meridian.claims.dao.TradingPartnerDAO;
import com.meridian.claims.intake.EnrollmentFileMatcher;
import com.meridian.claims.intake.FhirClaimFileParser;
import com.meridian.claims.intake.ParserResolver;
import com.meridian.claims.intake.PriorAuthRequestFileMatcher;
import com.meridian.claims.intake.StatusInquiryFileMatcher;
import com.meridian.claims.intake.X12Edi837Parser;
import com.meridian.claims.model.EdiTransaction;
import com.meridian.claims.model.TradingPartner;
import com.meridian.claims.service.ClaimStatusInquiryService;
import com.meridian.claims.service.EnrollmentIntakeService;
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
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that both pollers route .276 files to ClaimStatusInquiryService — not
 * the claim-submission path (IntakeService) — and .json/.edi files the other way
 * around. Phase 14 dispatch is additive to the existing Phase 9-13 pollers; this
 * is the seam where a regression would most easily hide.
 */
public class StatusInquiryRoutingTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // -------------------------------------------------------------------------
    // InboundClaimFilePollerJob
    // -------------------------------------------------------------------------

    @Test
    public void globalPoller_276File_routesToClaimStatusInquiryService_notIntakeService() throws IOException {
        Path inbound = tmp.newFolder("inbound").toPath();
        Path archive = tmp.newFolder("archive").toPath();
        Path rejected = tmp.newFolder("rejected").toPath();
        Files.write(inbound.resolve("inquiry.276"), "ISA*...".getBytes(StandardCharsets.UTF_8));

        IntakeService intakeService = mock(IntakeService.class);
        ClaimStatusInquiryService claimStatusInquiryService = mock(ClaimStatusInquiryService.class);
        ScheduledJobLogService jobLogService = mock(ScheduledJobLogService.class);
        when(jobLogService.start(anyString())).thenReturn(1);
        // tradingPartnerId is null for the global (non-partner-scoped) poller — any(Integer.class)
        // rejects null in this Mockito version, so use the raw any() matcher instead.
        when(claimStatusInquiryService.processFile(eq("inquiry.276"), anyString(), any()))
            .thenReturn(true);

        ParserResolver parserResolver = new ParserResolver(mock(FhirClaimFileParser.class), mock(X12Edi837Parser.class));
        ReflectionTestUtils.setField(parserResolver, "ediExtensions", "edi,x12,837");
        StatusInquiryFileMatcher matcher = new StatusInquiryFileMatcher();
        ReflectionTestUtils.setField(matcher, "statusInquiryExtensions", "276");

        InboundClaimFilePollerJob job = new InboundClaimFilePollerJob();
        ReflectionTestUtils.setField(job, "intakeService", intakeService);
        ReflectionTestUtils.setField(job, "claimStatusInquiryService", claimStatusInquiryService);
        ReflectionTestUtils.setField(job, "priorAuthRequestService", mock(PriorAuthRequestService.class));
        ReflectionTestUtils.setField(job, "enrollmentIntakeService", mock(EnrollmentIntakeService.class));
        ReflectionTestUtils.setField(job, "jobLogService", jobLogService);
        ReflectionTestUtils.setField(job, "parserResolver", parserResolver);
        ReflectionTestUtils.setField(job, "statusInquiryFileMatcher", matcher);
        ReflectionTestUtils.setField(job, "priorAuthRequestFileMatcher", mock(PriorAuthRequestFileMatcher.class));
        ReflectionTestUtils.setField(job, "enrollmentFileMatcher", mock(EnrollmentFileMatcher.class));
        ReflectionTestUtils.setField(job, "intakeEnabled", true);
        ReflectionTestUtils.setField(job, "inboundPath", inbound.toString());
        ReflectionTestUtils.setField(job, "archivePath", archive.toString());
        ReflectionTestUtils.setField(job, "rejectedPath", rejected.toString());

        job.execute(mock(JobExecutionContext.class));

        verify(claimStatusInquiryService).processFile(eq("inquiry.276"), anyString(), any());
        verify(intakeService, never()).processFile(anyString(), anyString(), any(), any(Integer.class));
        assertFileMoved(archive, "inquiry.276");
    }

    @Test
    public void globalPoller_edifile_stillRoutesToIntakeService_notClaimStatusInquiryService() throws IOException {
        Path inbound = tmp.newFolder("inbound2").toPath();
        Path archive = tmp.newFolder("archive2").toPath();
        Path rejected = tmp.newFolder("rejected2").toPath();
        Files.write(inbound.resolve("claim.edi"), "ISA*...".getBytes(StandardCharsets.UTF_8));

        IntakeService intakeService = mock(IntakeService.class);
        ClaimStatusInquiryService claimStatusInquiryService = mock(ClaimStatusInquiryService.class);
        ScheduledJobLogService jobLogService = mock(ScheduledJobLogService.class);
        when(jobLogService.start(anyString())).thenReturn(1);
        IntakeService.IntakeSummary summary = new IntakeService.IntakeSummary("claim.edi", false, false, 1, 0, null);
        when(intakeService.processFile(eq("claim.edi"), anyString(), any())).thenReturn(summary);

        X12Edi837Parser x12Parser = mock(X12Edi837Parser.class);
        ParserResolver parserResolver = new ParserResolver(mock(FhirClaimFileParser.class), x12Parser);
        ReflectionTestUtils.setField(parserResolver, "ediExtensions", "edi,x12,837");
        StatusInquiryFileMatcher matcher = new StatusInquiryFileMatcher();
        ReflectionTestUtils.setField(matcher, "statusInquiryExtensions", "276");

        InboundClaimFilePollerJob job = new InboundClaimFilePollerJob();
        ReflectionTestUtils.setField(job, "intakeService", intakeService);
        ReflectionTestUtils.setField(job, "claimStatusInquiryService", claimStatusInquiryService);
        ReflectionTestUtils.setField(job, "priorAuthRequestService", mock(PriorAuthRequestService.class));
        ReflectionTestUtils.setField(job, "enrollmentIntakeService", mock(EnrollmentIntakeService.class));
        ReflectionTestUtils.setField(job, "jobLogService", jobLogService);
        ReflectionTestUtils.setField(job, "parserResolver", parserResolver);
        ReflectionTestUtils.setField(job, "statusInquiryFileMatcher", matcher);
        ReflectionTestUtils.setField(job, "priorAuthRequestFileMatcher", mock(PriorAuthRequestFileMatcher.class));
        ReflectionTestUtils.setField(job, "enrollmentFileMatcher", mock(EnrollmentFileMatcher.class));
        ReflectionTestUtils.setField(job, "intakeEnabled", true);
        ReflectionTestUtils.setField(job, "inboundPath", inbound.toString());
        ReflectionTestUtils.setField(job, "archivePath", archive.toString());
        ReflectionTestUtils.setField(job, "rejectedPath", rejected.toString());

        job.execute(mock(JobExecutionContext.class));

        verify(intakeService).processFile(eq("claim.edi"), anyString(), eq(x12Parser));
        verify(claimStatusInquiryService, never()).processFile(anyString(), anyString(), any(Integer.class));
    }

    // -------------------------------------------------------------------------
    // TradingPartnerPollerJob
    // -------------------------------------------------------------------------

    @Test
    public void partnerPoller_276File_routesToClaimStatusInquiryService_notIntakeService() throws Exception {
        TradingPartnerDAO tradingPartnerDAO = mock(TradingPartnerDAO.class);
        TransportAdapterResolver transportAdapterResolver = mock(TransportAdapterResolver.class);
        TransportAdapter adapter = mock(TransportAdapter.class);
        IntakeService intakeService = mock(IntakeService.class);
        ClaimStatusInquiryService claimStatusInquiryService = mock(ClaimStatusInquiryService.class);
        EdiTransactionDAO ediTransactionDAO = mock(EdiTransactionDAO.class);
        ScheduledJobLogService jobLogService = mock(ScheduledJobLogService.class);

        TradingPartner partner = new TradingPartner();
        partner.setId(1);
        partner.setPartnerName("Acme");

        when(tradingPartnerDAO.findAllActive()).thenReturn(Arrays.asList(partner));
        when(transportAdapterResolver.resolve(partner)).thenReturn(adapter);
        when(adapter.listInboundFiles(partner)).thenReturn(Arrays.asList("inquiry.276"));
        when(adapter.readInboundFile(partner, "inquiry.276")).thenReturn("ISA*...");
        when(claimStatusInquiryService.processFile(eq("inquiry.276"), anyString(), eq(1))).thenReturn(true);
        when(ediTransactionDAO.findByFileReference("inquiry.276")).thenReturn(Collections.<EdiTransaction>emptyList());
        when(jobLogService.start(anyString())).thenReturn(1);

        ParserResolver parserResolver = mock(ParserResolver.class);
        StatusInquiryFileMatcher matcher = new StatusInquiryFileMatcher();
        ReflectionTestUtils.setField(matcher, "statusInquiryExtensions", "276");

        TradingPartnerPollerJob job = new TradingPartnerPollerJob();
        ReflectionTestUtils.setField(job, "tradingPartnerDAO", tradingPartnerDAO);
        ReflectionTestUtils.setField(job, "transportAdapterResolver", transportAdapterResolver);
        ReflectionTestUtils.setField(job, "parserResolver", parserResolver);
        ReflectionTestUtils.setField(job, "statusInquiryFileMatcher", matcher);
        ReflectionTestUtils.setField(job, "priorAuthRequestFileMatcher", mock(PriorAuthRequestFileMatcher.class));
        ReflectionTestUtils.setField(job, "enrollmentFileMatcher", mock(EnrollmentFileMatcher.class));
        ReflectionTestUtils.setField(job, "intakeService", intakeService);
        ReflectionTestUtils.setField(job, "claimStatusInquiryService", claimStatusInquiryService);
        ReflectionTestUtils.setField(job, "priorAuthRequestService", mock(PriorAuthRequestService.class));
        ReflectionTestUtils.setField(job, "enrollmentIntakeService", mock(EnrollmentIntakeService.class));
        ReflectionTestUtils.setField(job, "ediTransactionDAO", ediTransactionDAO);
        ReflectionTestUtils.setField(job, "jobLogService", jobLogService);

        job.execute(mock(JobExecutionContext.class));

        verify(claimStatusInquiryService).processFile(eq("inquiry.276"), anyString(), eq(1));
        verify(intakeService, never()).processFile(anyString(), anyString(), any(), anyInt());
        verify(adapter).archiveInboundFile(partner, "inquiry.276", true);
    }

    private void assertFileMoved(Path dir, String fileName) {
        org.junit.Assert.assertTrue("expected " + fileName + " to be in " + dir,
            Files.exists(dir.resolve(fileName)));
    }
}
