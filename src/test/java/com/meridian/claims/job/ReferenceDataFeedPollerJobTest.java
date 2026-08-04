package com.meridian.claims.job;

import com.meridian.claims.model.ReferenceDataImportBatch;
import com.meridian.claims.service.ReferenceDataImportService;
import com.meridian.claims.service.ScheduledJobLogService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.quartz.JobExecutionContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ReferenceDataFeedPollerJobTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private ReferenceDataFeedPollerJob job;
    private ReferenceDataImportService importService;
    private ScheduledJobLogService jobLogService;

    @Before
    public void setUp() {
        importService = mock(ReferenceDataImportService.class);
        jobLogService = mock(ScheduledJobLogService.class);
        when(jobLogService.start(anyString())).thenReturn(1);

        job = new ReferenceDataFeedPollerJob();
        ReflectionTestUtils.setField(job, "referenceDataImportService", importService);
        ReflectionTestUtils.setField(job, "jobLogService", jobLogService);
    }

    private Path dropFile(Path dir, String name, String content) throws IOException {
        Path f = dir.resolve(name);
        Files.write(f, content.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test
    public void stagesAndArchivesEachConfiguredFeedDirectory() throws IOException {
        Path diagnosisDir = tmp.newFolder("diagnosis").toPath();
        Path procedureDir = tmp.newFolder("procedure").toPath();
        dropFile(diagnosisDir, "icd10.csv", "code,description\nE11.9,Diabetes");
        dropFile(procedureDir, "cpt.csv", "code,description\n99213,Office visit");

        ReflectionTestUtils.setField(job, "diagnosisPath", diagnosisDir.toString());
        ReflectionTestUtils.setField(job, "procedurePath", procedureDir.toString());
        ReflectionTestUtils.setField(job, "carcRarcPath", "");
        ReflectionTestUtils.setField(job, "nppesPath", "");

        job.execute(mock(JobExecutionContext.class));

        verify(importService).stageImport(eq(ReferenceDataImportBatch.FEED_DIAGNOSIS_CODES), eq("icd10.csv"), anyString());
        verify(importService).stageImport(eq(ReferenceDataImportBatch.FEED_PROCEDURE_CODES), eq("cpt.csv"), anyString());
        assertTrue(Files.exists(diagnosisDir.resolve("archive").resolve("icd10.csv")));
        assertTrue(Files.exists(procedureDir.resolve("archive").resolve("cpt.csv")));
        assertFalse(Files.exists(diagnosisDir.resolve("icd10.csv")));
    }

    @Test
    public void unconfiguredPaths_areSkippedWithoutError() {
        ReflectionTestUtils.setField(job, "diagnosisPath", "");
        ReflectionTestUtils.setField(job, "procedurePath", "");
        ReflectionTestUtils.setField(job, "carcRarcPath", "");
        ReflectionTestUtils.setField(job, "nppesPath", "");

        job.execute(mock(JobExecutionContext.class));

        verify(importService, never()).stageImport(anyString(), anyString(), anyString());
        verify(jobLogService).complete(1, 0);
    }

    @Test
    public void nonExistentConfiguredPath_isSkippedWithoutError() {
        ReflectionTestUtils.setField(job, "diagnosisPath", tmp.getRoot().toPath().resolve("does-not-exist").toString());
        ReflectionTestUtils.setField(job, "procedurePath", "");
        ReflectionTestUtils.setField(job, "carcRarcPath", "");
        ReflectionTestUtils.setField(job, "nppesPath", "");

        job.execute(mock(JobExecutionContext.class));

        verify(importService, never()).stageImport(anyString(), anyString(), anyString());
    }

    @Test
    public void stageFailure_leavesFileInPlaceForRetry() throws IOException {
        Path nppesDir = tmp.newFolder("nppes").toPath();
        dropFile(nppesDir, "nppes.csv", "npi,status\n1234567893,ACTIVE");
        when(importService.stageImport(eq(ReferenceDataImportBatch.FEED_NPPES), anyString(), anyString()))
            .thenThrow(new RuntimeException("boom"));

        ReflectionTestUtils.setField(job, "diagnosisPath", "");
        ReflectionTestUtils.setField(job, "procedurePath", "");
        ReflectionTestUtils.setField(job, "carcRarcPath", "");
        ReflectionTestUtils.setField(job, "nppesPath", nppesDir.toString());

        job.execute(mock(JobExecutionContext.class));

        assertTrue(Files.exists(nppesDir.resolve("nppes.csv")));
        assertFalse(Files.exists(nppesDir.resolve("archive").resolve("nppes.csv")));
    }

    @Test
    public void onlyRegularFilesAreProcessed() throws IOException {
        Path diagnosisDir = tmp.newFolder("diagnosis2").toPath();
        Files.createDirectory(diagnosisDir.resolve("subdir"));
        dropFile(diagnosisDir, "icd10.csv", "code,description\nE11.9,Diabetes");

        ReflectionTestUtils.setField(job, "diagnosisPath", diagnosisDir.toString());
        ReflectionTestUtils.setField(job, "procedurePath", "");
        ReflectionTestUtils.setField(job, "carcRarcPath", "");
        ReflectionTestUtils.setField(job, "nppesPath", "");

        job.execute(mock(JobExecutionContext.class));

        verify(importService).stageImport(eq(ReferenceDataImportBatch.FEED_DIAGNOSIS_CODES), eq("icd10.csv"), anyString());
        verify(jobLogService).complete(1, 1);
    }
}
