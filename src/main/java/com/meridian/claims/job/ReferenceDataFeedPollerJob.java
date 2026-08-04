package com.meridian.claims.job;

import com.meridian.claims.model.ReferenceDataImportBatch;
import com.meridian.claims.service.ReferenceDataImportService;
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
 * Quartz job that polls up to four independently configured directories —
 * one per reference-data feed type — for new extract files (Phase 19).
 * Each new file is staged via {@link ReferenceDataImportService#stageImport}
 * (parsed, diffed against current data, and recorded as a reviewable batch)
 * and archived. Staging never applies anything to a live table — an Admin
 * must explicitly approve each batch on the Reference Data Imports screen.
 *
 * Any feed whose path is left blank is simply skipped, the same
 * inert-when-unconfigured convention {@code InboundClaimFilePollerJob} uses.
 *
 * @DisallowConcurrentExecution mirrors every other poller in this codebase.
 */
@DisallowConcurrentExecution
public class ReferenceDataFeedPollerJob implements Job {

    private static final Logger LOG = Logger.getLogger(ReferenceDataFeedPollerJob.class);

    @Autowired private ReferenceDataImportService referenceDataImportService;
    @Autowired private ScheduledJobLogService jobLogService;

    @Value("${claims.referencedata.diagnosis.path:}")
    private String diagnosisPath;

    @Value("${claims.referencedata.procedure.path:}")
    private String procedurePath;

    @Value("${claims.referencedata.carcrarc.path:}")
    private String carcRarcPath;

    @Value("${claims.referencedata.nppes.path:}")
    private String nppesPath;

    @Override
    public void execute(JobExecutionContext context) {
        int logId = jobLogService.start("ReferenceDataFeedPollerJob");
        int filesProcessed = 0;
        try {
            filesProcessed += pollDirectory(diagnosisPath, ReferenceDataImportBatch.FEED_DIAGNOSIS_CODES);
            filesProcessed += pollDirectory(procedurePath, ReferenceDataImportBatch.FEED_PROCEDURE_CODES);
            filesProcessed += pollDirectory(carcRarcPath, ReferenceDataImportBatch.FEED_CARC_RARC);
            filesProcessed += pollDirectory(nppesPath, ReferenceDataImportBatch.FEED_NPPES);
            jobLogService.complete(logId, filesProcessed);
            LOG.info("ReferenceDataFeedPollerJob: done — " + filesProcessed + " file(s) staged");
        } catch (Exception e) {
            LOG.error("ReferenceDataFeedPollerJob failed", e);
            jobLogService.fail(logId, e.getMessage());
        }
    }

    private int pollDirectory(String configuredPath, String feedType) {
        if (configuredPath == null || configuredPath.trim().isEmpty()) {
            return 0;
        }
        Path dir = Paths.get(configuredPath.trim());
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            LOG.warn("ReferenceDataFeedPollerJob: " + feedType + " path does not exist — skipping: " + dir);
            return 0;
        }

        int handled = 0;
        try {
            Path archive = dir.resolve("archive");
            if (!Files.exists(archive)) {
                Files.createDirectories(archive);
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*")) {
                for (Path file : stream) {
                    if (!Files.isRegularFile(file)) continue;
                    String fileName = file.getFileName().toString();
                    try {
                        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                        referenceDataImportService.stageImport(feedType, fileName, content);
                        Files.move(file, archive.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                        handled++;
                        LOG.info("ReferenceDataFeedPollerJob: staged " + feedType + " file " + fileName);
                    } catch (Exception e) {
                        LOG.error("ReferenceDataFeedPollerJob: failed to stage " + feedType + " file "
                            + fileName + " — left in place for retry: " + e.getMessage(), e);
                    }
                }
            }
        } catch (IOException e) {
            LOG.error("ReferenceDataFeedPollerJob: I/O error polling " + feedType + " directory " + dir, e);
        }
        return handled;
    }
}
