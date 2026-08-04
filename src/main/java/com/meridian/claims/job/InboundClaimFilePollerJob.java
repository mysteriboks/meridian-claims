package com.meridian.claims.job;

import com.meridian.claims.intake.ClaimFileParser;
import com.meridian.claims.intake.EnrollmentFileMatcher;
import com.meridian.claims.intake.ParserResolver;
import com.meridian.claims.intake.PriorAuthRequestFileMatcher;
import com.meridian.claims.intake.StatusInquiryFileMatcher;
import com.meridian.claims.service.ClaimStatusInquiryService;
import com.meridian.claims.service.EnrollmentIntakeService;
import com.meridian.claims.service.IntakeService;
import com.meridian.claims.service.PriorAuthRequestService;
import com.meridian.claims.service.ScheduledJobLogService;
import org.apache.log4j.Logger;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Quartz job that polls the configured inbound directory for FHIR R4 Claim
 * JSON files and submits them through the batch intake pipeline.
 *
 * For each *.json file found:
 *   - Reads the file content and delegates to IntakeService.processFile().
 *   - On success: moves the file to the archive directory.
 *   - On file-level failure: moves the file to the rejected directory.
 *
 * A file that has already been processed (duplicate hash) is also moved to
 * archive so the poller does not re-process it on the next run.
 *
 * The job is inert when claims.intake.enabled=false or when the inbound
 * directory is not configured.
 *
 * @DisallowConcurrentExecution prevents two poller runs from scanning the same
 * directory at once if a run overruns the cron interval.
 */
@DisallowConcurrentExecution
public class InboundClaimFilePollerJob implements Job {

    private static final Logger LOG = Logger.getLogger(InboundClaimFilePollerJob.class);

    @Autowired private IntakeService intakeService;
    @Autowired private ClaimStatusInquiryService claimStatusInquiryService;
    @Autowired private PriorAuthRequestService priorAuthRequestService;
    @Autowired private ScheduledJobLogService jobLogService;
    @Autowired private ParserResolver parserResolver;
    @Autowired private StatusInquiryFileMatcher statusInquiryFileMatcher;
    @Autowired private PriorAuthRequestFileMatcher priorAuthRequestFileMatcher;
    @Autowired private EnrollmentFileMatcher enrollmentFileMatcher;
    @Autowired private EnrollmentIntakeService enrollmentIntakeService;

    @Value("${claims.intake.enabled:false}")
    private boolean intakeEnabled;

    @Value("${claims.intake.path:}")
    private String inboundPath;

    @Value("${claims.intake.archive.path:}")
    private String archivePath;

    @Value("${claims.intake.rejected.path:}")
    private String rejectedPath;

    @Override
    public void execute(JobExecutionContext context) {
        int logId = jobLogService.start("InboundClaimFilePollerJob");
        int filesProcessed = 0;

        try {
            if (!intakeEnabled) {
                LOG.info("InboundClaimFilePollerJob: intake disabled (claims.intake.enabled=false) — skipping");
                jobLogService.complete(logId, 0);
                return;
            }
            if (inboundPath == null || inboundPath.trim().isEmpty()) {
                LOG.warn("InboundClaimFilePollerJob: claims.intake.path not configured — skipping");
                jobLogService.complete(logId, 0);
                return;
            }

            Path inbound  = Paths.get(inboundPath.trim());
            Path archive  = resolveDir(archivePath,  inbound, "archive");
            Path rejected = resolveDir(rejectedPath, inbound, "rejected");

            ensureDir(archive);
            ensureDir(rejected);

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(inbound, "*")) {
                for (Path file : stream) {
                    if (!Files.isRegularFile(file)) continue;
                    String fileName = file.getFileName().toString();

                    if (statusInquiryFileMatcher.matches(fileName)) {
                        if (processStatusInquiryFile(file, fileName, archive, rejected)) {
                            filesProcessed++;
                        }
                        continue;
                    }

                    if (priorAuthRequestFileMatcher.matches(fileName)) {
                        if (processPriorAuthRequestFile(file, fileName, archive, rejected)) {
                            filesProcessed++;
                        }
                        continue;
                    }

                    if (enrollmentFileMatcher.matches(fileName)) {
                        if (processEnrollmentFile(file, fileName, archive, rejected)) {
                            filesProcessed++;
                        }
                        continue;
                    }

                    ClaimFileParser parser = parserResolver.resolve(fileName);
                    if (parser == null) {
                        LOG.warn("InboundClaimFilePollerJob: skipping " + fileName
                            + " — unrecognised file extension");
                        continue;
                    }

                    LOG.info("InboundClaimFilePollerJob: processing " + fileName);

                    try {
                        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                        IntakeService.IntakeSummary summary = intakeService.processFile(fileName, content, parser);

                        // Route on outcome: a file that could not be accepted at all
                        // (file-level parse failure, or every record rejected) goes to
                        // rejected/; otherwise archive/ (incl. duplicates and partial-
                        // quarantine successes), which prevents re-processing.
                        Path dest = summary.shouldReject() ? rejected.resolve(fileName)
                                                           : archive.resolve(fileName);
                        if (moveFile(file, dest)) {
                            filesProcessed++;
                            LOG.info("InboundClaimFilePollerJob: moved " + fileName + " to "
                                + (summary.shouldReject() ? "rejected/" : "archive/")
                                + " succeeded=" + summary.getSucceeded()
                                + " quarantined=" + summary.getQuarantined()
                                + " duplicate=" + summary.isDuplicate());
                        } else {
                            LOG.error("InboundClaimFilePollerJob: " + fileName
                                + " processed but could not be moved — left in inbound for retry");
                        }

                    } catch (Exception e) {
                        LOG.error("InboundClaimFilePollerJob: failed to process " + fileName
                            + " — moving to rejected: " + e.getMessage(), e);
                        if (moveFile(file, rejected.resolve(fileName))) {
                            filesProcessed++;
                        }
                    }
                }
            }

            jobLogService.complete(logId, filesProcessed);
            LOG.info("InboundClaimFilePollerJob: done — " + filesProcessed + " file(s) handled");

        } catch (Exception e) {
            LOG.error("InboundClaimFilePollerJob failed", e);
            jobLogService.fail(logId, e.getMessage());
        }
    }

    /**
     * Processes one X12 276 claim status inquiry file: delegates to
     * ClaimStatusInquiryService (which generates and logs the 277 response — written to disk
     * via claims.integration.edi.output.path if configured, same as the 837 ack flow), then
     * archives (structurally parseable) or rejects (file-level parse failure) the file.
     * Returns true if the file was handled (moved) either way.
     */
    private boolean processStatusInquiryFile(Path file, String fileName, Path archive, Path rejected) {
        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            boolean parsed = claimStatusInquiryService.processFile(fileName, content, null);
            return archiveOrReject(file, fileName, parsed, archive, rejected, "status inquiry");
        } catch (Exception e) {
            LOG.error("InboundClaimFilePollerJob: failed to process status inquiry " + fileName
                + " — moving to rejected: " + e.getMessage(), e);
            return moveFile(file, rejected.resolve(fileName));
        }
    }

    /**
     * Processes one X12 278 prior-authorization request file: delegates to
     * PriorAuthRequestService (which creates the authorization when certified and generates +
     * logs the 278 response), then archives or rejects the file — same shape as the 276 handler
     * above.
     */
    private boolean processPriorAuthRequestFile(Path file, String fileName, Path archive, Path rejected) {
        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            boolean parsed = priorAuthRequestService.processFile(fileName, content, null);
            return archiveOrReject(file, fileName, parsed, archive, rejected, "prior auth request");
        } catch (Exception e) {
            LOG.error("InboundClaimFilePollerJob: failed to process prior auth request " + fileName
                + " — moving to rejected: " + e.getMessage(), e);
            return moveFile(file, rejected.resolve(fileName));
        }
    }

    /**
     * Processes one X12 834 enrollment file: delegates to EnrollmentIntakeService (file-level
     * idempotency + per-record add/change/termination dispatch against member/coverage data),
     * then archives (at least one record succeeded) or rejects (file-level parse failure, or
     * every record quarantined) the file — same shape as the 276/278 handlers above.
     */
    private boolean processEnrollmentFile(Path file, String fileName, Path archive, Path rejected) {
        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            IntakeService.IntakeSummary summary = enrollmentIntakeService.processFile(fileName, content);
            return archiveOrReject(file, fileName, !summary.shouldReject(), archive, rejected, "enrollment file");
        } catch (Exception e) {
            LOG.error("InboundClaimFilePollerJob: failed to process enrollment file " + fileName
                + " — moving to rejected: " + e.getMessage(), e);
            return moveFile(file, rejected.resolve(fileName));
        }
    }

    /** Shared archive-vs-rejected move + logging for the ancillary (non-submission) file handlers above. */
    private boolean archiveOrReject(Path file, String fileName, boolean accepted, Path archive, Path rejected, String logLabel) {
        Path dest = accepted ? archive.resolve(fileName) : rejected.resolve(fileName);
        if (moveFile(file, dest)) {
            LOG.info("InboundClaimFilePollerJob: moved " + logLabel + " " + fileName + " to "
                + (accepted ? "archive/" : "rejected/"));
            return true;
        }
        LOG.error("InboundClaimFilePollerJob: " + fileName
            + " processed but could not be moved — left in inbound for retry");
        return false;
    }

    /** Returns the configured path if non-empty, else a subdirectory of inbound. */
    private Path resolveDir(String configured, Path inbound, String defaultSubdir) {
        if (configured != null && !configured.trim().isEmpty()) {
            return Paths.get(configured.trim());
        }
        return inbound.resolve(defaultSubdir);
    }

    private void ensureDir(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    /** Returns true if the move succeeded; false (and logs) if it failed. */
    private boolean moveFile(Path source, Path target) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            LOG.error("InboundClaimFilePollerJob: could not move " + source + " to " + target
                + ": " + e.getMessage());
            return false;
        }
    }
}
