package com.meridian.claims.service;

import com.meridian.claims.controller.SubmitClaimRequest;
import com.meridian.claims.dao.ClaimIntakeBatchDAO;
import com.meridian.claims.dao.EdiTransactionDAO;
import com.meridian.claims.dao.UserDAO;
import com.meridian.claims.intake.ClaimFileParseResult;
import com.meridian.claims.intake.ClaimFileParser;
import com.meridian.claims.intake.IntakeParseException;
import com.meridian.claims.intake.X12Edi837Parser;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimIntakeBatch;
import com.meridian.claims.model.EdiTransaction;
import com.meridian.claims.model.User;
import com.meridian.claims.util.LedgerUtil;
import com.meridian.claims.util.LogMaskUtil;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates inbound claim file processing for the batch intake pipeline.
 *
 * Responsibilities:
 *   1. File-level idempotency — SHA-256 hash keyed in claim_intake_batches;
 *      re-submitting the same file is a no-op.
 *   2. Parsing via the injected ClaimFileParser (FHIR R4 in Phase 9).
 *   3. Per-record fault isolation — one bad record is quarantined; the rest proceed.
 *   4. Submitting clean records through the existing ClaimService.submit pipeline.
 *   5. Updating the ledger row to COMPLETED / FAILED with counts.
 */
@Service
public class IntakeService {

    private static final Logger LOG = Logger.getLogger(IntakeService.class);

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED  = "COMPLETED";
    private static final String STATUS_FAILED     = "FAILED";

    /** Cap on the error_message ledger field (TEXT column; this is a sanity bound). */
    private static final int MAX_ERROR_MESSAGE_LEN = 2000;

    private final ClaimIntakeBatchDAO batchDAO;
    private final ClaimFileParser claimFileParser;
    private final ClaimService claimService;
    private final AuditService auditService;
    private final UserDAO userDAO;
    private final EdiTransactionDAO ediTransactionDAO;
    private final Edi999Generator edi999Generator;
    private final Edi277CaGenerator edi277CaGenerator;

    @Value("${claims.intake.system-user-id:0}")
    private int configuredSystemUserId;

    private volatile int resolvedSystemUserId = -1;

    @Autowired
    public IntakeService(ClaimIntakeBatchDAO batchDAO,
                         ClaimFileParser claimFileParser,
                         ClaimService claimService,
                         AuditService auditService,
                         UserDAO userDAO,
                         EdiTransactionDAO ediTransactionDAO,
                         Edi999Generator edi999Generator,
                         Edi277CaGenerator edi277CaGenerator) {
        this.batchDAO = batchDAO;
        this.claimFileParser = claimFileParser;
        this.claimService = claimService;
        this.auditService = auditService;
        this.userDAO = userDAO;
        this.ediTransactionDAO = ediTransactionDAO;
        this.edi999Generator = edi999Generator;
        this.edi277CaGenerator = edi277CaGenerator;
    }

    /**
     * Return the most recent intake batch ledger rows, newest first.
     * Used by IntakeBatchController to populate the admin batch list.
     *
     * @param limit maximum number of rows to return (caller is responsible for clamping to a sane range)
     */
    public List<ClaimIntakeBatch> findRecentBatches(int limit) {
        return batchDAO.findRecent(limit);
    }

    /**
     * Return the most recent EDI transaction log rows (Phase 12 acknowledgments),
     * newest first. Used by IntegrationController to populate the admin monitor.
     *
     * @param limit maximum number of rows to return (caller is responsible for clamping to a sane range)
     */
    public List<EdiTransaction> findRecentEdiTransactions(int limit) {
        return ediTransactionDAO.findRecent(limit);
    }

    /**
     * Process a single inbound claim file using the injected default parser.
     * Delegates to {@link #processFile(String, String, ClaimFileParser)}.
     *
     * @param fileName    the original file name (for logging and the ledger row)
     * @param fileContent full UTF-8 text content of the file
     * @return a summary of the processing run
     */
    public IntakeSummary processFile(String fileName, String fileContent) {
        return processFile(fileName, fileContent, claimFileParser);
    }

    /**
     * Process a single inbound claim file using the supplied parser.
     * The poller calls this overload to pass the format-specific parser
     * (FHIR or X12 EDI 837) selected per file extension.
     *
     * @param fileName    the original file name (for logging and the ledger row)
     * @param fileContent full UTF-8 text content of the file
     * @param parser      the parser to use for this file
     * @return a summary of the processing run
     */
    public IntakeSummary processFile(String fileName, String fileContent, ClaimFileParser parser) {
        return processFile(fileName, fileContent, parser, null);
    }

    /**
     * Process a single inbound claim file, attributing any EDI acknowledgment
     * transactions to a specific trading partner. Used by TradingPartnerPollerJob
     * (Phase 13) so the Integrations monitor can filter by partner and the caller
     * can retrieve the just-generated ack(s) via {@link com.meridian.claims.dao.EdiTransactionDAO#findByFileReference}
     * to push them back through that partner's own transport.
     *
     * @param fileName         the original file name (for logging and the ledger row)
     * @param fileContent      full UTF-8 text content of the file
     * @param parser           the parser to use for this file
     * @param tradingPartnerId the trading partner this file came from, or null for the
     *                         global (non-partner-scoped) intake directory
     * @return a summary of the processing run
     */
    public IntakeSummary processFile(String fileName, String fileContent, ClaimFileParser parser,
                                      Integer tradingPartnerId) {
        String fileHash = LedgerUtil.sha256(fileContent);

        // Idempotency check — only a terminal (COMPLETED/FAILED) row is a true no-op.
        // A stale PROCESSING row means a prior run died mid-file; re-process it.
        ClaimIntakeBatch existing = batchDAO.findByFileHash(fileHash);
        if (existing != null && !STATUS_PROCESSING.equals(existing.getStatus())) {
            LOG.info("IntakeService: file already processed (hash=" + fileHash
                + " status=" + existing.getStatus() + ") — skipping fileName=" + fileName);
            return IntakeSummary.duplicate(fileName, existing.getSucceeded(), existing.getQuarantined());
        }

        // Insert (or reuse) the ledger row.
        int batchId;
        if (existing != null) {
            batchId = existing.getId();
            LOG.warn("IntakeService: reclaiming stale PROCESSING batch id=" + batchId
                + " for fileName=" + fileName);
        } else {
            ClaimIntakeBatch batch = new ClaimIntakeBatch();
            batch.setFileName(fileName);
            batch.setFileHash(fileHash);
            batch.setStatus(STATUS_PROCESSING);
            batchId = batchDAO.insert(batch);
        }

        int systemUserId = resolveSystemUserId();
        // Phase 12: X12 837 files get EDI acknowledgments (TA1/999/277CA); FHIR files don't.
        boolean isX12 = parser instanceof X12Edi837Parser;

        // Parse. A file-level failure (bad JSON / wrong resource type) marks the whole
        // batch FAILED and signals the caller to route the file to rejected/.
        ClaimFileParseResult parseResult;
        try {
            parseResult = parser.parse(fileContent);
        } catch (IntakeParseException e) {
            String msg = LogMaskUtil.maskPhi("File-level parse failure: " + e.getMessage());
            LOG.error("IntakeService: " + msg + " fileName=" + fileName);
            batchDAO.updateCompletion(batchId, STATUS_FAILED, 0, 0, 0, msg);
            auditService.record("INTAKE_FILE_FAILED", "ClaimIntakeBatch", (long) batchId, msg);
            if (isX12) {
                generateTa1ForFileLevelFailure(fileName, fileContent, msg, tradingPartnerId);
            }
            return IntakeSummary.fileLevelFailure(fileName, msg);
        }

        int succeeded = 0;
        int quarantined = 0;
        List<String> quarantineReasons = new ArrayList<String>();
        List<Edi999Generator.TransactionAckStatus> ack999 = new ArrayList<Edi999Generator.TransactionAckStatus>();
        List<Edi277CaGenerator.ClaimAckStatus> ack277 = new ArrayList<Edi277CaGenerator.ClaimAckStatus>();

        // Per-record parse failures (bad data within an otherwise valid file) are
        // quarantined individually — they do not abort the file.
        for (ClaimFileParseResult.RecordError pe : parseResult.getRecordErrors()) {
            quarantined++;
            recordQuarantine(batchId, fileName, "Record " + pe.getRecordIndex() + ": " + pe.getReason(),
                quarantineReasons);
            if (isX12) {
                ack999.add(new Edi999Generator.TransactionAckStatus(pe.getStControlNumber(), false));
                ack277.add(Edi277CaGenerator.ClaimAckStatus.rejected(pe.getStControlNumber(), pe.getReason()));
            }
        }

        // Submit each successfully-parsed claim; a submission failure quarantines
        // that record without affecting the others.
        List<SubmitClaimRequest> claims = parseResult.getClaims();
        for (int i = 0; i < claims.size(); i++) {
            String stCtrl = isX12 ? parseResult.getClaimStControlNumber(i) : null;
            try {
                Claim submitted = claimService.submit(claims.get(i), systemUserId);
                succeeded++;
                if (isX12) {
                    ack999.add(new Edi999Generator.TransactionAckStatus(stCtrl, true));
                    ack277.add(Edi277CaGenerator.ClaimAckStatus.accepted(stCtrl, submitted.getClaimNumber()));
                }
            } catch (Exception e) {
                quarantined++;
                recordQuarantine(batchId, fileName, "Claim " + (i + 1) + ": " + e.getMessage(),
                    quarantineReasons);
                if (isX12) {
                    ack999.add(new Edi999Generator.TransactionAckStatus(stCtrl, false));
                    ack277.add(Edi277CaGenerator.ClaimAckStatus.rejected(stCtrl, e.getMessage()));
                }
            }
        }

        int total = parseResult.totalRecords();
        // The whole file "failed" (route to rejected/) when nothing was accepted.
        boolean nothingSucceeded = succeeded == 0 && total > 0;
        String finalStatus = nothingSucceeded ? STATUS_FAILED : STATUS_COMPLETED;
        batchDAO.updateCompletion(batchId, finalStatus, total, succeeded, quarantined,
            LedgerUtil.joinReasons(quarantineReasons, MAX_ERROR_MESSAGE_LEN));

        if (isX12) {
            String ediStatus = nothingSucceeded ? EdiTransaction.STATUS_REJECTED
                : (quarantined > 0 ? EdiTransaction.STATUS_PARTIAL : EdiTransaction.STATUS_ACCEPTED);
            generateAcksForX12(fileName, parseResult.getIsaControlNumber(), parseResult.getGsControlNumber(),
                ediStatus, ack999, ack277, tradingPartnerId);
        }

        LOG.info("IntakeService: fileName=" + fileName + " batchId=" + batchId
            + " total=" + total + " succeeded=" + succeeded
            + " quarantined=" + quarantined + " status=" + finalStatus);

        return new IntakeSummary(fileName, false, finalStatus.equals(STATUS_FAILED) && nothingSucceeded,
            succeeded, quarantined, null);
    }

    // -------------------------------------------------------------------------
    // Phase 12 — EDI acknowledgments (TA1 / 999 / 277CA)
    // -------------------------------------------------------------------------

    /**
     * The X12 parser failed before returning a result at all, so there is no
     * captured ISA13 to correlate a TA1 against. Best-effort recover it directly
     * from the raw file text (the ISA segment is fixed-position even when the
     * body that follows is unparseable) so the TA1 still references the right
     * interchange when possible; falls back to "unknown" otherwise.
     */
    private void generateTa1ForFileLevelFailure(String fileName, String fileContent, String reason,
                                                Integer tradingPartnerId) {
        String isaControlNumber = extractIsaControlNumberBestEffort(fileContent);
        EdiTransaction inbound = new EdiTransaction();
        inbound.setDirection(EdiTransaction.DIRECTION_INBOUND);
        inbound.setTransactionType("837");
        inbound.setIsaControlNumber(isaControlNumber);
        inbound.setStatus(EdiTransaction.STATUS_REJECTED);
        inbound.setFileReference(fileName);
        inbound.setDetail(reason);
        inbound.setTradingPartnerId(tradingPartnerId);
        int inboundId = ediTransactionDAO.insert(inbound);

        String ta1 = edi999Generator.generateTa1(isaControlNumber, false, "029");
        EdiTransaction outbound = new EdiTransaction();
        outbound.setDirection(EdiTransaction.DIRECTION_OUTBOUND);
        outbound.setTransactionType("TA1");
        outbound.setIsaControlNumber(isaControlNumber);
        outbound.setStatus(EdiTransaction.STATUS_REJECTED);
        outbound.setRelatedTransactionId(inboundId);
        outbound.setFileReference(fileName);
        outbound.setDetail(ta1);
        outbound.setTradingPartnerId(tradingPartnerId);
        ediTransactionDAO.insert(outbound);

        LOG.info("IntakeService: TA1 generated for unparseable X12 file fileName=" + fileName
            + " isaControlNumber=" + isaControlNumber);
    }

    /**
     * Logs the inbound 837 and generates + logs the 999 and 277CA acknowledgments
     * for a file that was at least parseable (whether fully, partially, or not at
     * all accepted at the record level).
     */
    private void generateAcksForX12(String fileName, String isaControlNumber, String gsControlNumber,
                                    String ediStatus, List<Edi999Generator.TransactionAckStatus> ack999,
                                    List<Edi277CaGenerator.ClaimAckStatus> ack277, Integer tradingPartnerId) {
        EdiTransaction inbound = new EdiTransaction();
        inbound.setDirection(EdiTransaction.DIRECTION_INBOUND);
        inbound.setTransactionType("837");
        inbound.setIsaControlNumber(isaControlNumber);
        inbound.setGsControlNumber(gsControlNumber);
        inbound.setStatus(ediStatus);
        inbound.setFileReference(fileName);
        inbound.setTradingPartnerId(tradingPartnerId);
        int inboundId = ediTransactionDAO.insert(inbound);

        String edi999 = edi999Generator.generate999(isaControlNumber, gsControlNumber, ack999);
        EdiTransaction outbound999 = new EdiTransaction();
        outbound999.setDirection(EdiTransaction.DIRECTION_OUTBOUND);
        outbound999.setTransactionType("999");
        outbound999.setIsaControlNumber(isaControlNumber);
        outbound999.setGsControlNumber(gsControlNumber);
        outbound999.setStatus(ediStatus);
        outbound999.setRelatedTransactionId(inboundId);
        outbound999.setFileReference(fileName);
        outbound999.setDetail(edi999);
        outbound999.setTradingPartnerId(tradingPartnerId);
        ediTransactionDAO.insert(outbound999);

        String edi277 = edi277CaGenerator.generate277Ca(isaControlNumber, ack277);
        EdiTransaction outbound277 = new EdiTransaction();
        outbound277.setDirection(EdiTransaction.DIRECTION_OUTBOUND);
        outbound277.setTransactionType("277CA");
        outbound277.setIsaControlNumber(isaControlNumber);
        outbound277.setGsControlNumber(gsControlNumber);
        outbound277.setStatus(ediStatus);
        outbound277.setRelatedTransactionId(inboundId);
        outbound277.setFileReference(fileName);
        outbound277.setDetail(edi277);
        outbound277.setTradingPartnerId(tradingPartnerId);
        ediTransactionDAO.insert(outbound277);

        LOG.info("IntakeService: 999/277CA generated fileName=" + fileName
            + " isaControlNumber=" + isaControlNumber + " status=" + ediStatus);
    }

    /**
     * Best-effort extraction of ISA13 (interchange control number) directly from
     * raw EDI text, for use only when the parser failed before returning a
     * result. Returns null if the content does not look like a well-formed ISA
     * segment — callers must tolerate a null (the TA1 generator falls back to
     * the X12 "unknown control number" convention).
     */
    private String extractIsaControlNumberBestEffort(String rawContent) {
        if (rawContent == null || rawContent.length() < 4 || !rawContent.startsWith("ISA")) {
            return null;
        }
        char elementSeparator = rawContent.charAt(3);
        String[] elements = rawContent.split(java.util.regex.Pattern.quote(String.valueOf(elementSeparator)));
        if (elements.length <= 13) {
            return null;
        }
        String raw = elements[13];
        int cut = raw.length();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\n' || c == '\r' || c == '~') { cut = i; break; }
        }
        String value = raw.substring(0, cut).trim();
        return value.isEmpty() ? null : value;
    }

    private void recordQuarantine(int batchId, String fileName, String rawReason,
                                  List<String> reasons) {
        String reason = LogMaskUtil.maskPhi(rawReason);
        reasons.add(reason);
        LOG.warn("IntakeService: quarantined " + reason + " fileName=" + fileName);
        auditService.record("INTAKE_RECORD_QUARANTINED", "ClaimIntakeBatch", (long) batchId, reason);
    }

    /** Resolve the system user id to attribute batch-submitted claims. */
    private int resolveSystemUserId() {
        if (resolvedSystemUserId > 0) return resolvedSystemUserId;
        if (configuredSystemUserId > 0) {
            resolvedSystemUserId = configuredSystemUserId;
            return resolvedSystemUserId;
        }
        // Fall back to looking up by username
        User system = userDAO.findByUsername("system");
        if (system != null) {
            resolvedSystemUserId = system.getId();
            LOG.info("IntakeService: resolved system user id=" + resolvedSystemUserId);
            return resolvedSystemUserId;
        }
        LOG.warn("IntakeService: 'system' user not found — claims will have no creator");
        return 0;
    }

    /** Summary returned by processFile(). */
    public static class IntakeSummary {
        private final String fileName;
        private final boolean duplicate;
        private final boolean reject;
        private final int succeeded;
        private final int quarantined;
        private final String errorMessage;

        public IntakeSummary(String fileName, boolean duplicate, boolean reject,
                             int succeeded, int quarantined, String errorMessage) {
            this.fileName = fileName;
            this.duplicate = duplicate;
            this.reject = reject;
            this.succeeded = succeeded;
            this.quarantined = quarantined;
            this.errorMessage = errorMessage;
        }

        /** Already-processed file — caller archives it (no re-processing). */
        public static IntakeSummary duplicate(String fileName, int succeeded, int quarantined) {
            return new IntakeSummary(fileName, true, false, succeeded, quarantined, null);
        }

        /** File could not be parsed at all — caller routes it to rejected/. */
        public static IntakeSummary fileLevelFailure(String fileName, String errorMessage) {
            return new IntakeSummary(fileName, false, true, 0, 0, errorMessage);
        }

        public String getFileName() { return fileName; }
        public boolean isDuplicate() { return duplicate; }
        /** True if the file should be moved to rejected/ rather than archive/. */
        public boolean shouldReject() { return reject; }
        public int getSucceeded() { return succeeded; }
        public int getQuarantined() { return quarantined; }
        public String getErrorMessage() { return errorMessage; }
    }
}
