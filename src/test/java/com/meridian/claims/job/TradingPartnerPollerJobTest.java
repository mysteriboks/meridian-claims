package com.meridian.claims.job;

import com.meridian.claims.dao.EdiTransactionDAO;
import com.meridian.claims.dao.TradingPartnerDAO;
import com.meridian.claims.intake.ClaimFileParser;
import com.meridian.claims.intake.EnrollmentFileMatcher;
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
import com.meridian.claims.transport.TransportException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.quartz.JobExecutionContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TradingPartnerPollerJobTest {

    private TradingPartnerPollerJob job;
    private TradingPartnerDAO tradingPartnerDAO;
    private TransportAdapterResolver transportAdapterResolver;
    private ParserResolver parserResolver;
    private IntakeService intakeService;
    private EdiTransactionDAO ediTransactionDAO;
    private ScheduledJobLogService jobLogService;
    private TransportAdapter adapter;
    private X12Edi837Parser x12Parser;

    @Before
    public void setUp() {
        tradingPartnerDAO = mock(TradingPartnerDAO.class);
        transportAdapterResolver = mock(TransportAdapterResolver.class);
        parserResolver = mock(ParserResolver.class);
        intakeService = mock(IntakeService.class);
        ediTransactionDAO = mock(EdiTransactionDAO.class);
        jobLogService = mock(ScheduledJobLogService.class);
        adapter = mock(TransportAdapter.class);
        x12Parser = mock(X12Edi837Parser.class);

        when(jobLogService.start(anyString())).thenReturn(1);
        when(ediTransactionDAO.findByFileReference(anyString())).thenReturn(Collections.<EdiTransaction>emptyList());

        // Not under test here (see StatusInquiryRoutingTest / PriorAuthRoutingTest) — a mock's
        // unstubbed matches(...) returns false, so every file in this test falls through to the
        // pre-existing claim-submission dispatch, matching prior behaviour exactly.
        StatusInquiryFileMatcher statusInquiryFileMatcher = mock(StatusInquiryFileMatcher.class);
        ClaimStatusInquiryService claimStatusInquiryService = mock(ClaimStatusInquiryService.class);
        PriorAuthRequestFileMatcher priorAuthRequestFileMatcher = mock(PriorAuthRequestFileMatcher.class);
        PriorAuthRequestService priorAuthRequestService = mock(PriorAuthRequestService.class);
        EnrollmentFileMatcher enrollmentFileMatcher = mock(EnrollmentFileMatcher.class);
        EnrollmentIntakeService enrollmentIntakeService = mock(EnrollmentIntakeService.class);

        job = new TradingPartnerPollerJob();
        ReflectionTestUtils.setField(job, "tradingPartnerDAO", tradingPartnerDAO);
        ReflectionTestUtils.setField(job, "transportAdapterResolver", transportAdapterResolver);
        ReflectionTestUtils.setField(job, "parserResolver", parserResolver);
        ReflectionTestUtils.setField(job, "statusInquiryFileMatcher", statusInquiryFileMatcher);
        ReflectionTestUtils.setField(job, "priorAuthRequestFileMatcher", priorAuthRequestFileMatcher);
        ReflectionTestUtils.setField(job, "enrollmentFileMatcher", enrollmentFileMatcher);
        ReflectionTestUtils.setField(job, "intakeService", intakeService);
        ReflectionTestUtils.setField(job, "claimStatusInquiryService", claimStatusInquiryService);
        ReflectionTestUtils.setField(job, "priorAuthRequestService", priorAuthRequestService);
        ReflectionTestUtils.setField(job, "enrollmentIntakeService", enrollmentIntakeService);
        ReflectionTestUtils.setField(job, "ediTransactionDAO", ediTransactionDAO);
        ReflectionTestUtils.setField(job, "jobLogService", jobLogService);
    }

    private TradingPartner partner(int id, String name) {
        TradingPartner p = new TradingPartner();
        p.setId(id);
        p.setPartnerName(name);
        return p;
    }

    @Test
    public void noActivePartners_completesWithZero() {
        when(tradingPartnerDAO.findAllActive()).thenReturn(Collections.<TradingPartner>emptyList());

        job.execute(mock(JobExecutionContext.class));

        verify(jobLogService).complete(1, 0);
        Mockito.verifyZeroInteractions(intakeService);
    }

    @Test
    public void onePartner_processesAndArchivesEachFile() throws TransportException {
        TradingPartner p = partner(10, "Acme Health");
        when(tradingPartnerDAO.findAllActive()).thenReturn(Arrays.asList(p));
        when(transportAdapterResolver.resolve(p)).thenReturn(adapter);
        when(adapter.listInboundFiles(p)).thenReturn(Arrays.asList("claim1.edi", "claim2.edi"));
        when(adapter.readInboundFile(p, "claim1.edi")).thenReturn("ISA*content1");
        when(adapter.readInboundFile(p, "claim2.edi")).thenReturn("ISA*content2");
        when(parserResolver.resolve("claim1.edi")).thenReturn(x12Parser);
        when(parserResolver.resolve("claim2.edi")).thenReturn(x12Parser);

        IntakeService.IntakeSummary accepted = new IntakeService.IntakeSummary("claim1.edi", false, false, 1, 0, null);
        IntakeService.IntakeSummary rejected = IntakeService.IntakeSummary.fileLevelFailure("claim2.edi", "bad file");
        when(intakeService.processFile(eq("claim1.edi"), eq("ISA*content1"), eq(x12Parser), eq(10)))
            .thenReturn(accepted);
        when(intakeService.processFile(eq("claim2.edi"), eq("ISA*content2"), eq(x12Parser), eq(10)))
            .thenReturn(rejected);

        job.execute(mock(JobExecutionContext.class));

        verify(adapter).archiveInboundFile(p, "claim1.edi", true);
        verify(adapter).archiveInboundFile(p, "claim2.edi", false);
        verify(jobLogService).complete(1, 2);
    }

    @Test
    public void unrecognisedExtension_isSkippedNotProcessed() throws TransportException {
        TradingPartner p = partner(10, "Acme Health");
        when(tradingPartnerDAO.findAllActive()).thenReturn(Arrays.asList(p));
        when(transportAdapterResolver.resolve(p)).thenReturn(adapter);
        when(adapter.listInboundFiles(p)).thenReturn(Arrays.asList("readme.txt"));
        when(parserResolver.resolve("readme.txt")).thenReturn(null);

        job.execute(mock(JobExecutionContext.class));

        Mockito.verifyZeroInteractions(intakeService);
        verify(jobLogService).complete(1, 0);
    }

    @Test
    public void transportFailureOnOnePartner_doesNotStopOtherPartners() throws TransportException {
        TradingPartner broken = partner(10, "Broken Partner");
        TradingPartner healthy = partner(20, "Healthy Partner");
        when(tradingPartnerDAO.findAllActive()).thenReturn(Arrays.asList(broken, healthy));

        TransportAdapter brokenAdapter = mock(TransportAdapter.class);
        when(transportAdapterResolver.resolve(broken)).thenReturn(brokenAdapter);
        when(brokenAdapter.listInboundFiles(broken)).thenThrow(new TransportException("connection refused"));

        when(transportAdapterResolver.resolve(healthy)).thenReturn(adapter);
        when(adapter.listInboundFiles(healthy)).thenReturn(Arrays.asList("claim1.edi"));
        when(adapter.readInboundFile(healthy, "claim1.edi")).thenReturn("ISA*content");
        when(parserResolver.resolve("claim1.edi")).thenReturn(x12Parser);
        IntakeService.IntakeSummary accepted = new IntakeService.IntakeSummary("claim1.edi", false, false, 1, 0, null);
        when(intakeService.processFile(eq("claim1.edi"), anyString(), eq(x12Parser), eq(20))).thenReturn(accepted);

        job.execute(mock(JobExecutionContext.class));

        verify(adapter).archiveInboundFile(healthy, "claim1.edi", true);
        verify(jobLogService).complete(1, 1);
    }

    @Test
    public void deliversAcknowledgmentsThroughPartnerTransport() throws TransportException {
        TradingPartner p = partner(10, "Acme Health");
        when(tradingPartnerDAO.findAllActive()).thenReturn(Arrays.asList(p));
        when(transportAdapterResolver.resolve(p)).thenReturn(adapter);
        when(adapter.listInboundFiles(p)).thenReturn(Arrays.asList("claim1.edi"));
        when(adapter.readInboundFile(p, "claim1.edi")).thenReturn("ISA*content1");
        when(parserResolver.resolve("claim1.edi")).thenReturn(x12Parser);
        IntakeService.IntakeSummary accepted = new IntakeService.IntakeSummary("claim1.edi", false, false, 1, 0, null);
        when(intakeService.processFile(eq("claim1.edi"), anyString(), eq(x12Parser), eq(10))).thenReturn(accepted);

        EdiTransaction inboundRow = new EdiTransaction();
        inboundRow.setDirection(EdiTransaction.DIRECTION_INBOUND);
        inboundRow.setTransactionType("837");

        EdiTransaction ack999 = new EdiTransaction();
        ack999.setDirection(EdiTransaction.DIRECTION_OUTBOUND);
        ack999.setTransactionType("999");
        ack999.setDetail("999-ack-content");

        EdiTransaction ack277 = new EdiTransaction();
        ack277.setDirection(EdiTransaction.DIRECTION_OUTBOUND);
        ack277.setTransactionType("277CA");
        ack277.setDetail("277-ack-content");

        when(ediTransactionDAO.findByFileReference("claim1.edi"))
            .thenReturn(Arrays.asList(inboundRow, ack999, ack277));

        job.execute(mock(JobExecutionContext.class));

        verify(adapter).writeOutboundFile(p, "claim1.edi.999", "999-ack-content");
        verify(adapter).writeOutboundFile(p, "claim1.edi.277ca", "277-ack-content");
        // The inbound row itself is not an ack — must not be "delivered".
        verify(adapter, never()).writeOutboundFile(eq(p), eq("claim1.edi.837"), anyString());
    }

    @Test
    public void ackDeliveryFailure_doesNotFailTheFile() throws TransportException {
        TradingPartner p = partner(10, "Acme Health");
        when(tradingPartnerDAO.findAllActive()).thenReturn(Arrays.asList(p));
        when(transportAdapterResolver.resolve(p)).thenReturn(adapter);
        when(adapter.listInboundFiles(p)).thenReturn(Arrays.asList("claim1.edi"));
        when(adapter.readInboundFile(p, "claim1.edi")).thenReturn("ISA*content1");
        when(parserResolver.resolve("claim1.edi")).thenReturn(x12Parser);
        IntakeService.IntakeSummary accepted = new IntakeService.IntakeSummary("claim1.edi", false, false, 1, 0, null);
        when(intakeService.processFile(eq("claim1.edi"), anyString(), eq(x12Parser), eq(10))).thenReturn(accepted);

        EdiTransaction ack999 = new EdiTransaction();
        ack999.setDirection(EdiTransaction.DIRECTION_OUTBOUND);
        ack999.setTransactionType("999");
        ack999.setDetail("999-ack-content");
        when(ediTransactionDAO.findByFileReference("claim1.edi")).thenReturn(Arrays.asList(ack999));
        Mockito.doThrow(new TransportException("outbound dir unreachable"))
            .when(adapter).writeOutboundFile(eq(p), anyString(), anyString());

        job.execute(mock(JobExecutionContext.class));

        // The file itself is still counted as handled (archived) even though ack delivery failed.
        verify(adapter).archiveInboundFile(p, "claim1.edi", true);
        verify(jobLogService).complete(1, 1);
    }
}
