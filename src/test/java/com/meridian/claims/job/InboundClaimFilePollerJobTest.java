package com.meridian.claims.job;

import com.meridian.claims.intake.ClaimFileParser;
import com.meridian.claims.intake.FhirClaimFileParser;
import com.meridian.claims.intake.X12Edi837Parser;
import com.meridian.claims.service.IntakeService;
import com.meridian.claims.service.ScheduledJobLogService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the InboundClaimFilePollerJob file-handling behaviour with a real
 * temporary directory and a mocked IntakeService. Verifies the enabled/path
 * guards, file-extension routing (JSON → FHIR parser, EDI → X12 parser,
 * unknown → skip), and the archive-vs-rejected routing.
 */
public class InboundClaimFilePollerJobTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private InboundClaimFilePollerJob job;
    private IntakeService intakeService;
    private ScheduledJobLogService jobLogService;
    private FhirClaimFileParser fhirParser;
    private X12Edi837Parser x12Parser;

    private Path inbound;
    private Path archive;
    private Path rejected;

    @Before
    public void setUp() throws IOException {
        intakeService  = mock(IntakeService.class);
        jobLogService  = mock(ScheduledJobLogService.class);
        fhirParser     = mock(FhirClaimFileParser.class);
        x12Parser      = mock(X12Edi837Parser.class);
        when(jobLogService.start(anyString())).thenReturn(1);

        inbound  = tmp.newFolder("inbound").toPath();
        archive  = tmp.newFolder("archive").toPath();
        rejected = tmp.newFolder("rejected").toPath();

        job = new InboundClaimFilePollerJob();
        ReflectionTestUtils.setField(job, "intakeService",  intakeService);
        ReflectionTestUtils.setField(job, "jobLogService",  jobLogService);
        ReflectionTestUtils.setField(job, "fhirParser",     fhirParser);
        ReflectionTestUtils.setField(job, "x12Parser",      x12Parser);
        ReflectionTestUtils.setField(job, "intakeEnabled",  true);
        ReflectionTestUtils.setField(job, "inboundPath",    inbound.toString());
        ReflectionTestUtils.setField(job, "archivePath",    archive.toString());
        ReflectionTestUtils.setField(job, "rejectedPath",   rejected.toString());
        ReflectionTestUtils.setField(job, "ediExtensions",  "edi,x12,837");
    }

    private Path dropFile(String name, String content) throws IOException {
        Path f = inbound.resolve(name);
        Files.write(f, content.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    // -------------------------------------------------------------------------
    // Guards
    // -------------------------------------------------------------------------

    @Test
    public void disabled_doesNothing() throws Exception {
        ReflectionTestUtils.setField(job, "intakeEnabled", false);
        dropFile("a.json", "{}");

        job.execute(null);

        verify(intakeService, never()).processFile(anyString(), anyString(), any(ClaimFileParser.class));
        assertTrue("file untouched", Files.exists(inbound.resolve("a.json")));
        verify(jobLogService).complete(1, 0);
    }

    @Test
    public void unconfiguredPath_doesNothing() throws Exception {
        ReflectionTestUtils.setField(job, "inboundPath", "");

        job.execute(null);

        verify(intakeService, never()).processFile(anyString(), anyString(), any(ClaimFileParser.class));
        verify(jobLogService).complete(1, 0);
    }

    // -------------------------------------------------------------------------
    // Routing
    // -------------------------------------------------------------------------

    @Test
    public void successfulFile_movedToArchive() throws Exception {
        dropFile("good.json", "{\"resourceType\":\"Claim\"}");
        when(intakeService.processFile(eq("good.json"), anyString(), any(ClaimFileParser.class)))
            .thenReturn(new IntakeService.IntakeSummary("good.json", false, false, 2, 0, null));

        job.execute(null);

        assertTrue("moved to archive", Files.exists(archive.resolve("good.json")));
        assertFalse("removed from inbound", Files.exists(inbound.resolve("good.json")));
        assertFalse("not in rejected", Files.exists(rejected.resolve("good.json")));
    }

    @Test
    public void rejectedFile_movedToRejected() throws Exception {
        dropFile("bad.json", "not valid");
        when(intakeService.processFile(eq("bad.json"), anyString(), any(ClaimFileParser.class)))
            .thenReturn(IntakeService.IntakeSummary.fileLevelFailure("bad.json", "parse error"));

        job.execute(null);

        assertTrue("moved to rejected", Files.exists(rejected.resolve("bad.json")));
        assertFalse("removed from inbound", Files.exists(inbound.resolve("bad.json")));
        assertFalse("not in archive", Files.exists(archive.resolve("bad.json")));
    }

    @Test
    public void duplicateFile_movedToArchive() throws Exception {
        dropFile("dup.json", "{\"resourceType\":\"Claim\"}");
        when(intakeService.processFile(eq("dup.json"), anyString(), any(ClaimFileParser.class)))
            .thenReturn(IntakeService.IntakeSummary.duplicate("dup.json", 5, 0));

        job.execute(null);

        assertTrue("duplicate archived (not re-processed)", Files.exists(archive.resolve("dup.json")));
        assertFalse(Files.exists(inbound.resolve("dup.json")));
    }

    @Test
    public void exceptionDuringProcessing_movedToRejected() throws Exception {
        dropFile("boom.json", "{}");
        when(intakeService.processFile(eq("boom.json"), anyString(), any(ClaimFileParser.class)))
            .thenThrow(new RuntimeException("unexpected"));

        job.execute(null);

        assertTrue("exception routes to rejected", Files.exists(rejected.resolve("boom.json")));
        assertFalse(Files.exists(inbound.resolve("boom.json")));
    }

    @Test
    public void onlyRecognisedExtensionsProcessed() throws Exception {
        dropFile("claim.json", "{\"resourceType\":\"Claim\"}");
        dropFile("notes.txt", "ignore me");
        when(intakeService.processFile(anyString(), anyString(), any(ClaimFileParser.class)))
            .thenReturn(new IntakeService.IntakeSummary("claim.json", false, false, 1, 0, null));

        job.execute(null);

        verify(intakeService).processFile(eq("claim.json"), anyString(), any(ClaimFileParser.class));
        verify(intakeService, never()).processFile(eq("notes.txt"), anyString(), any(ClaimFileParser.class));
        assertTrue("non-recognised extension left in place", Files.exists(inbound.resolve("notes.txt")));
    }

    @Test
    public void multipleFiles_mixedOutcomes() throws Exception {
        dropFile("ok.json", "{\"resourceType\":\"Claim\"}");
        dropFile("fail.json", "{\"resourceType\":\"Claim\"}");
        when(intakeService.processFile(eq("ok.json"), anyString(), any(ClaimFileParser.class)))
            .thenReturn(new IntakeService.IntakeSummary("ok.json", false, false, 1, 0, null));
        when(intakeService.processFile(eq("fail.json"), anyString(), any(ClaimFileParser.class)))
            .thenReturn(IntakeService.IntakeSummary.fileLevelFailure("fail.json", "bad"));

        job.execute(null);

        assertTrue(Files.exists(archive.resolve("ok.json")));
        assertTrue(Files.exists(rejected.resolve("fail.json")));
        verify(jobLogService).complete(1, 2);
    }

    @Test
    public void ediFile_usesX12Parser() throws Exception {
        dropFile("claims.edi", "ISA*00*...");
        when(intakeService.processFile(eq("claims.edi"), anyString(), eq(x12Parser)))
            .thenReturn(new IntakeService.IntakeSummary("claims.edi", false, false, 1, 0, null));

        job.execute(null);

        assertTrue("EDI file archived", Files.exists(archive.resolve("claims.edi")));
        verify(intakeService).processFile(eq("claims.edi"), anyString(), eq(x12Parser));
    }
}
