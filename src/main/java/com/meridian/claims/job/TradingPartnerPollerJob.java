package com.meridian.claims.job;

import com.meridian.claims.dao.EdiTransactionDAO;
import com.meridian.claims.dao.TradingPartnerDAO;
import com.meridian.claims.intake.ClaimFileParser;
import com.meridian.claims.intake.EnrollmentFileMatcher;
import com.meridian.claims.intake.ParserResolver;
import com.meridian.claims.intake.PriorAuthRequestFileMatcher;
import com.meridian.claims.intake.StatusInquiryFileMatcher;
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
import org.apache.log4j.Logger;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Quartz job that polls every active trading partner's configured transport
 * (local directory or SFTP — see {@link TransportAdapterResolver}) for inbound
 * files. Claim files (837/FHIR) submit through the same {@link IntakeService}
 * pipeline the global directory poller ({@code InboundClaimFilePollerJob}) uses;
 * claim status inquiries (276, Phase 14) route read-only through
 * {@link ClaimStatusInquiryService}; prior-authorization requests (278, Phase 15)
 * route through {@link PriorAuthRequestService}, which creates a real
 * authorization when certified. Each ancillary type is recognised by its own
 * small matcher ({@link StatusInquiryFileMatcher}, {@link PriorAuthRequestFileMatcher})
 * before falling through to the claim-submission path. Whichever path handles a
 * file, any generated EDI response is pushed back through that partner's own
 * outbound transport — closing the loop for a real trading-partner exchange
 * rather than the single shared directory Phase 9-12 poll.
 *
 * A partner is entirely independent of the others: one partner's transport
 * failure does not stop the run for the rest.
 *
 * @DisallowConcurrentExecution mirrors InboundClaimFilePollerJob — prevents two
 * runs from processing the same partner's files concurrently if a run overruns
 * the cron interval.
 */
@DisallowConcurrentExecution
public class TradingPartnerPollerJob implements Job {

    private static final Logger LOG = Logger.getLogger(TradingPartnerPollerJob.class);

    @Autowired private TradingPartnerDAO tradingPartnerDAO;
    @Autowired private TransportAdapterResolver transportAdapterResolver;
    @Autowired private ParserResolver parserResolver;
    @Autowired private StatusInquiryFileMatcher statusInquiryFileMatcher;
    @Autowired private PriorAuthRequestFileMatcher priorAuthRequestFileMatcher;
    @Autowired private EnrollmentFileMatcher enrollmentFileMatcher;
    @Autowired private IntakeService intakeService;
    @Autowired private ClaimStatusInquiryService claimStatusInquiryService;
    @Autowired private PriorAuthRequestService priorAuthRequestService;
    @Autowired private EnrollmentIntakeService enrollmentIntakeService;
    @Autowired private EdiTransactionDAO ediTransactionDAO;
    @Autowired private ScheduledJobLogService jobLogService;

    @Override
    public void execute(JobExecutionContext context) {
        int logId = jobLogService.start("TradingPartnerPollerJob");
        int filesProcessed = 0;

        try {
            List<TradingPartner> partners = tradingPartnerDAO.findAllActive();
            for (TradingPartner partner : partners) {
                filesProcessed += pollPartner(partner);
            }
            jobLogService.complete(logId, filesProcessed);
            LOG.info("TradingPartnerPollerJob: done — " + filesProcessed
                + " file(s) handled across " + partners.size() + " active partner(s)");
        } catch (Exception e) {
            LOG.error("TradingPartnerPollerJob failed", e);
            jobLogService.fail(logId, e.getMessage());
        }
    }

    /** Polls one partner; returns the number of files handled. Never throws — a partner's
     *  transport/processing failure is logged and skipped so the rest of the run proceeds. */
    private int pollPartner(TradingPartner partner) {
        int handled = 0;
        try {
            TransportAdapter adapter = transportAdapterResolver.resolve(partner);
            List<String> fileNames = adapter.listInboundFiles(partner);

            for (String fileName : fileNames) {
                if (statusInquiryFileMatcher.matches(fileName)) {
                    if (processStatusInquiryFile(partner, adapter, fileName)) {
                        handled++;
                    }
                    continue;
                }

                if (priorAuthRequestFileMatcher.matches(fileName)) {
                    if (processPriorAuthRequestFile(partner, adapter, fileName)) {
                        handled++;
                    }
                    continue;
                }

                if (enrollmentFileMatcher.matches(fileName)) {
                    if (processEnrollmentFile(partner, adapter, fileName)) {
                        handled++;
                    }
                    continue;
                }

                ClaimFileParser parser = parserResolver.resolve(fileName);
                if (parser == null) {
                    LOG.warn("TradingPartnerPollerJob: partner=" + partner.getPartnerName()
                        + " skipping " + fileName + " — unrecognised file extension");
                    continue;
                }
                if (!processOneFile(partner, adapter, fileName, parser)) {
                    continue;
                }
                handled++;
            }
        } catch (TransportException e) {
            LOG.error("TradingPartnerPollerJob: transport failure for partner="
                + partner.getPartnerName() + ": " + e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("TradingPartnerPollerJob: unexpected failure for partner="
                + partner.getPartnerName(), e);
        }
        return handled;
    }

    private boolean processOneFile(TradingPartner partner, TransportAdapter adapter,
                                    String fileName, ClaimFileParser parser) {
        try {
            String content = adapter.readInboundFile(partner, fileName);
            IntakeService.IntakeSummary summary =
                intakeService.processFile(fileName, content, parser, partner.getId());

            adapter.archiveInboundFile(partner, fileName, !summary.shouldReject());
            LOG.info("TradingPartnerPollerJob: partner=" + partner.getPartnerName()
                + " processed " + fileName + " succeeded=" + summary.getSucceeded()
                + " quarantined=" + summary.getQuarantined() + " reject=" + summary.shouldReject());

            deliverAcknowledgments(partner, adapter, fileName);
            return true;
        } catch (TransportException e) {
            LOG.error("TradingPartnerPollerJob: partner=" + partner.getPartnerName()
                + " transport failure on " + fileName + ": " + e.getMessage(), e);
            return false;
        } catch (Exception e) {
            LOG.error("TradingPartnerPollerJob: partner=" + partner.getPartnerName()
                + " failed to process " + fileName + ": " + e.getMessage(), e);
            // Leave the file in place for retry on the next poll rather than losing it —
            // matches the "left in inbound for retry" behavior of the failed-move case in
            // InboundClaimFilePollerJob.
            return false;
        }
    }

    private boolean processStatusInquiryFile(TradingPartner partner, TransportAdapter adapter, String fileName) {
        try {
            String content = adapter.readInboundFile(partner, fileName);
            boolean parsed = claimStatusInquiryService.processFile(fileName, content, partner.getId());

            adapter.archiveInboundFile(partner, fileName, parsed);
            LOG.info("TradingPartnerPollerJob: partner=" + partner.getPartnerName()
                + " processed status inquiry " + fileName + " parsed=" + parsed);

            deliverAcknowledgments(partner, adapter, fileName);
            return true;
        } catch (TransportException e) {
            LOG.error("TradingPartnerPollerJob: partner=" + partner.getPartnerName()
                + " transport failure on status inquiry " + fileName + ": " + e.getMessage(), e);
            return false;
        } catch (Exception e) {
            LOG.error("TradingPartnerPollerJob: partner=" + partner.getPartnerName()
                + " failed to process status inquiry " + fileName + ": " + e.getMessage(), e);
            return false;
        }
    }

    private boolean processPriorAuthRequestFile(TradingPartner partner, TransportAdapter adapter, String fileName) {
        try {
            String content = adapter.readInboundFile(partner, fileName);
            boolean parsed = priorAuthRequestService.processFile(fileName, content, partner.getId());

            adapter.archiveInboundFile(partner, fileName, parsed);
            LOG.info("TradingPartnerPollerJob: partner=" + partner.getPartnerName()
                + " processed prior auth request " + fileName + " parsed=" + parsed);

            deliverAcknowledgments(partner, adapter, fileName);
            return true;
        } catch (TransportException e) {
            LOG.error("TradingPartnerPollerJob: partner=" + partner.getPartnerName()
                + " transport failure on prior auth request " + fileName + ": " + e.getMessage(), e);
            return false;
        } catch (Exception e) {
            LOG.error("TradingPartnerPollerJob: partner=" + partner.getPartnerName()
                + " failed to process prior auth request " + fileName + ": " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Processes one X12 834 enrollment file: delegates to EnrollmentIntakeService (file-level
     * idempotency + per-record add/change/termination dispatch). Unlike the other ancillary
     * types, an 834 has no response document to deliver back — enrollment is one-way, so this
     * does not call {@link #deliverAcknowledgments}.
     */
    private boolean processEnrollmentFile(TradingPartner partner, TransportAdapter adapter, String fileName) {
        try {
            String content = adapter.readInboundFile(partner, fileName);
            IntakeService.IntakeSummary summary = enrollmentIntakeService.processFile(fileName, content);

            adapter.archiveInboundFile(partner, fileName, !summary.shouldReject());
            LOG.info("TradingPartnerPollerJob: partner=" + partner.getPartnerName()
                + " processed enrollment file " + fileName + " succeeded=" + summary.getSucceeded()
                + " quarantined=" + summary.getQuarantined() + " reject=" + summary.shouldReject());
            return true;
        } catch (TransportException e) {
            LOG.error("TradingPartnerPollerJob: partner=" + partner.getPartnerName()
                + " transport failure on enrollment file " + fileName + ": " + e.getMessage(), e);
            return false;
        } catch (Exception e) {
            LOG.error("TradingPartnerPollerJob: partner=" + partner.getPartnerName()
                + " failed to process enrollment file " + fileName + ": " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * IntakeService already generated and logged any 999/277CA/TA1 for this file
     * (attributed to this partner via tradingPartnerId). Retrieve the outbound rows
     * just written and push their content through the partner's own transport so
     * the acknowledgment actually reaches them, not just the global disk path.
     */
    private void deliverAcknowledgments(TradingPartner partner, TransportAdapter adapter, String fileName) {
        List<EdiTransaction> related = ediTransactionDAO.findByFileReference(fileName);
        for (EdiTransaction txn : related) {
            if (!EdiTransaction.DIRECTION_OUTBOUND.equals(txn.getDirection()) || txn.getDetail() == null) {
                continue;
            }
            String ackFileName = fileName + "." + txn.getTransactionType().toLowerCase();
            try {
                adapter.writeOutboundFile(partner, ackFileName, txn.getDetail());
            } catch (TransportException e) {
                LOG.error("TradingPartnerPollerJob: could not deliver " + txn.getTransactionType()
                    + " ack for " + fileName + " to partner=" + partner.getPartnerName()
                    + ": " + e.getMessage(), e);
            }
        }
    }
}
