package com.meridian.claims.service;

import com.meridian.claims.controller.SubmitClaimRequest;
import com.meridian.claims.dao.ClaimIntakeBatchDAO;
import com.meridian.claims.dao.UserDAO;
import com.meridian.claims.intake.ClaimFileParseResult;
import com.meridian.claims.intake.ClaimFileParser;
import com.meridian.claims.intake.IntakeParseException;
import com.meridian.claims.model.ClaimIntakeBatch;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.User;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IntakeServiceTest {

    private IntakeService intakeService;
    private ClaimIntakeBatchDAO batchDAO;
    private ClaimFileParser claimFileParser;
    private ClaimService claimService;
    private AuditService auditService;
    private UserDAO userDAO;

    private static final String FILE_CONTENT = "{\"resourceType\":\"Claim\"}";
    private static final String FILE_NAME = "claims.json";

    @Before
    public void setUp() {
        batchDAO = mock(ClaimIntakeBatchDAO.class);
        claimFileParser = mock(ClaimFileParser.class);
        claimService = mock(ClaimService.class);
        auditService = mock(AuditService.class);
        userDAO = mock(UserDAO.class);

        intakeService = new IntakeService(batchDAO, claimFileParser, claimService, auditService, userDAO);

        when(batchDAO.insert(any(ClaimIntakeBatch.class))).thenReturn(42);
        when(batchDAO.findByFileHash(anyString())).thenReturn(null); // default: not a duplicate

        User systemUser = new User();
        systemUser.setId(99);
        when(userDAO.findByUsername("system")).thenReturn(systemUser);
    }

    private ClaimFileParseResult resultWithClaims(int n) {
        ClaimFileParseResult r = new ClaimFileParseResult();
        for (int i = 0; i < n; i++) r.addClaim(new SubmitClaimRequest());
        return r;
    }

    private Claim okClaim() {
        Claim c = new Claim();
        c.setId(1);
        c.setClaimNumber("CLM-001");
        return c;
    }

    // -------------------------------------------------------------------------
    // Idempotency
    // -------------------------------------------------------------------------

    @Test
    public void duplicateFile_completed_isNoOp() throws Exception {
        ClaimIntakeBatch existing = new ClaimIntakeBatch();
        existing.setStatus("COMPLETED");
        existing.setSucceeded(3);
        existing.setQuarantined(0);
        when(batchDAO.findByFileHash(anyString())).thenReturn(existing);

        IntakeService.IntakeSummary summary = intakeService.processFile(FILE_NAME, FILE_CONTENT);

        assertTrue(summary.isDuplicate());
        assertFalse(summary.shouldReject());
        assertEquals(3, summary.getSucceeded());
        verify(claimService, never()).submit(any(), anyInt());
        verify(batchDAO, never()).insert(any());
    }

    @Test
    public void staleProcessingRow_isReclaimedNotSkipped() throws Exception {
        // A PROCESSING row means a prior run crashed mid-file — re-process it.
        ClaimIntakeBatch stale = new ClaimIntakeBatch();
        stale.setId(7);
        stale.setStatus("PROCESSING");
        when(batchDAO.findByFileHash(anyString())).thenReturn(stale);
        when(claimFileParser.parse(anyString())).thenReturn(resultWithClaims(1));
        when(claimService.submit(any(), anyInt())).thenReturn(okClaim());

        IntakeService.IntakeSummary summary = intakeService.processFile(FILE_NAME, FILE_CONTENT);

        assertFalse(summary.isDuplicate());
        verify(batchDAO, never()).insert(any());          // reuses the stale row
        verify(claimService).submit(any(), anyInt());     // actually processes
        verify(batchDAO).updateCompletion(eq(7), eq("COMPLETED"), eq(1), eq(1), eq(0), eq(null));
    }

    // -------------------------------------------------------------------------
    // File-level parse failure → reject
    // -------------------------------------------------------------------------

    @Test
    public void fileLevelParseFailure_updatesLedgerFailed_andSignalsReject() throws Exception {
        when(claimFileParser.parse(anyString()))
            .thenThrow(new IntakeParseException("Not valid FHIR"));

        IntakeService.IntakeSummary summary = intakeService.processFile(FILE_NAME, FILE_CONTENT);

        assertFalse(summary.isDuplicate());
        assertTrue("file-level failure must route to rejected/", summary.shouldReject());
        assertEquals(0, summary.getSucceeded());
        verify(batchDAO).updateCompletion(eq(42), eq("FAILED"), eq(0), eq(0), eq(0), anyString());
        verify(claimService, never()).submit(any(), anyInt());
    }

    // -------------------------------------------------------------------------
    // Successful processing
    // -------------------------------------------------------------------------

    @Test
    public void allRecordsSucceed_ledgerCompleted_archive() throws Exception {
        when(claimFileParser.parse(anyString())).thenReturn(resultWithClaims(2));
        when(claimService.submit(any(), anyInt())).thenReturn(okClaim());

        IntakeService.IntakeSummary summary = intakeService.processFile(FILE_NAME, FILE_CONTENT);

        assertFalse(summary.isDuplicate());
        assertFalse(summary.shouldReject());
        assertEquals(2, summary.getSucceeded());
        assertEquals(0, summary.getQuarantined());
        verify(batchDAO).updateCompletion(eq(42), eq("COMPLETED"), eq(2), eq(2), eq(0), eq(null));
    }

    // -------------------------------------------------------------------------
    // Per-record quarantine isolation (submission failures)
    // -------------------------------------------------------------------------

    @Test
    public void oneSubmissionFails_othersStillProcessed() throws Exception {
        ClaimFileParseResult r = new ClaimFileParseResult();
        SubmitClaimRequest req1 = new SubmitClaimRequest();
        SubmitClaimRequest req2 = new SubmitClaimRequest();
        SubmitClaimRequest req3 = new SubmitClaimRequest();
        r.addClaim(req1); r.addClaim(req2); r.addClaim(req3);
        when(claimFileParser.parse(anyString())).thenReturn(r);

        when(claimService.submit(eq(req1), anyInt())).thenReturn(okClaim());
        when(claimService.submit(eq(req2), anyInt())).thenThrow(new ServiceException("Duplicate claim"));
        when(claimService.submit(eq(req3), anyInt())).thenReturn(okClaim());

        IntakeService.IntakeSummary summary = intakeService.processFile(FILE_NAME, FILE_CONTENT);

        assertEquals(2, summary.getSucceeded());
        assertEquals(1, summary.getQuarantined());
        assertFalse(summary.shouldReject());
        verify(batchDAO).updateCompletion(eq(42), eq("COMPLETED"), eq(3), eq(2), eq(1), anyString());
        verify(auditService).record(eq("INTAKE_RECORD_QUARANTINED"), eq("ClaimIntakeBatch"),
            eq(42L), anyString());
    }

    // -------------------------------------------------------------------------
    // Per-record PARSE failure isolation (CRITICAL fix: was aborting whole file)
    // -------------------------------------------------------------------------

    @Test
    public void parseRecordErrors_quarantinedNotAborting() throws Exception {
        ClaimFileParseResult r = new ClaimFileParseResult();
        r.addClaim(new SubmitClaimRequest());          // 1 good
        r.addRecordError(2, "bad CPT code");           // 1 bad (parse-time)
        when(claimFileParser.parse(anyString())).thenReturn(r);
        when(claimService.submit(any(), anyInt())).thenReturn(okClaim());

        IntakeService.IntakeSummary summary = intakeService.processFile(FILE_NAME, FILE_CONTENT);

        assertEquals(1, summary.getSucceeded());
        assertEquals(1, summary.getQuarantined());
        assertFalse(summary.shouldReject());
        verify(batchDAO).updateCompletion(eq(42), eq("COMPLETED"), eq(2), eq(1), eq(1), anyString());
    }

    @Test
    public void allRecordsFail_ledgerFailed_andReject() throws Exception {
        ClaimFileParseResult r = new ClaimFileParseResult();
        r.addClaim(new SubmitClaimRequest());
        when(claimFileParser.parse(anyString())).thenReturn(r);
        when(claimService.submit(any(), anyInt())).thenThrow(new ServiceException("Submission failed"));

        IntakeService.IntakeSummary summary = intakeService.processFile(FILE_NAME, FILE_CONTENT);

        assertEquals(0, summary.getSucceeded());
        assertEquals(1, summary.getQuarantined());
        assertTrue("zero accepted → route to rejected/", summary.shouldReject());
        verify(batchDAO).updateCompletion(eq(42), eq("FAILED"), eq(1), eq(0), eq(1), anyString());
    }

    // -------------------------------------------------------------------------
    // System user attribution
    // -------------------------------------------------------------------------

    @Test
    public void systemUserIdResolvedFromUserDao() throws Exception {
        when(claimFileParser.parse(anyString())).thenReturn(resultWithClaims(1));
        when(claimService.submit(any(), anyInt())).thenReturn(okClaim());

        intakeService.processFile(FILE_NAME, FILE_CONTENT);

        ArgumentCaptor<Integer> userIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(claimService).submit(any(), userIdCaptor.capture());
        assertEquals(99, (int) userIdCaptor.getValue());
    }

    @Test
    public void emptyFile_producesEmptyResults() throws Exception {
        when(claimFileParser.parse(anyString())).thenReturn(new ClaimFileParseResult());

        IntakeService.IntakeSummary summary = intakeService.processFile(FILE_NAME, FILE_CONTENT);

        assertEquals(0, summary.getSucceeded());
        assertEquals(0, summary.getQuarantined());
        assertFalse(summary.shouldReject()); // empty file (total=0) is not a failure
        verify(claimService, never()).submit(any(), anyInt());
        verify(batchDAO).updateCompletion(eq(42), eq("COMPLETED"), eq(0), eq(0), eq(0), eq(null));
    }
}
