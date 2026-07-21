package com.meridian.claims.job;

import com.meridian.claims.intake.ClaimFileParser;
import com.meridian.claims.intake.FhirClaimFileParser;
import com.meridian.claims.intake.X12Edi837Parser;
import com.meridian.claims.service.IntakeService;
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
    @Autowired private ScheduledJobLogService jobLogService;
    @Autowired private FhirClaimFileParser fhirParser;
    @Autowired private X12Edi837Parser x12Parser;

    @Value("${claims.intake.enabled:false}")
    private boolean intakeEnabled;

    @Value("${claims.intake.path:}")
    private String inboundPath;

    @Value("${claims.intake.archive.path:}")
    private String archivePath;

    @Value("${claims.intake.rejected.path:}")
    private String rejectedPath;

    @Value("${claims.intake.edi.extensions:edi,x12,837}")
    private String ediExtensions;

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

                    ClaimFileParser parser = parserFor(fileName);
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
     * Return the parser appropriate for the given file name, or null if the
     * extension is not recognised (caller should skip the file).
     *
     * - .json files are handled by FhirClaimFileParser.
     * - Files whose extension (case-insensitive) matches any token in
     *   claims.intake.edi.extensions are handled by X12Edi837Parser.
     */
    private ClaimFileParser parserFor(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        String ext = fileName.substring(dot + 1).toLowerCase();
        if ("json".equals(ext)) {
            return fhirParser;
        }
        String[] ediExts = ediExtensions.split(",");
        for (int i = 0; i < ediExts.length; i++) {
            if (ediExts[i].trim().toLowerCase().equals(ext)) {
                return x12Parser;
            }
        }
        return null;
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
