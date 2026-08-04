package com.meridian.claims.service;

import com.meridian.claims.dao.ProviderDAO;
import com.meridian.claims.dao.ReferenceDataImportDAO;
import com.meridian.claims.model.CarcRarcCode;
import com.meridian.claims.model.DiagnosisCode;
import com.meridian.claims.model.Provider;
import com.meridian.claims.model.ReferenceDataImportBatch;
import com.meridian.claims.model.ReferenceDataImportRow;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ReferenceDataImportServiceTest {

    private ReferenceDataImportService service;
    private ReferenceDataImportDAO importDAO;
    private LookupService lookupService;
    private ProviderDAO providerDAO;
    private AuditService auditService;

    @Before
    public void setUp() {
        importDAO = mock(ReferenceDataImportDAO.class);
        lookupService = mock(LookupService.class);
        providerDAO = mock(ProviderDAO.class);
        auditService = mock(AuditService.class);
        service = new ReferenceDataImportService(importDAO, lookupService, providerDAO, auditService);

        when(importDAO.insertBatch(any(ReferenceDataImportBatch.class))).thenAnswer(inv -> {
            ReferenceDataImportBatch b = (ReferenceDataImportBatch) inv.getArguments()[0];
            b.setId(1);
            return 1;
        });
    }

    // --- Diagnosis codes ---

    @Test
    public void stageImport_diagnosisCodes_newCode_stagesAddRow() {
        when(lookupService.getDiagnosisCode("E11.9")).thenReturn(null);
        String csv = "code,description\nE11.9,Type 2 diabetes mellitus";

        ReferenceDataImportBatch batch = service.stageImport(ReferenceDataImportBatch.FEED_DIAGNOSIS_CODES, "icd10.csv", csv);

        assertEquals(1, batch.getAddedCount());
        assertEquals(0, batch.getChangedCount());
        ArgumentCaptor<ReferenceDataImportRow> rowCaptor = ArgumentCaptor.forClass(ReferenceDataImportRow.class);
        verify(importDAO).insertRow(rowCaptor.capture());
        assertEquals(ReferenceDataImportRow.TYPE_ADD, rowCaptor.getValue().getRowType());
        assertEquals("E11.9", rowCaptor.getValue().getCode());
    }

    @Test
    public void stageImport_diagnosisCodes_changedDescription_stagesChangeRow() {
        DiagnosisCode existing = new DiagnosisCode();
        existing.setCode("E11.9");
        existing.setDescription("Old description");
        when(lookupService.getDiagnosisCode("E11.9")).thenReturn(existing);
        String csv = "code,description\nE11.9,New description";

        ReferenceDataImportBatch batch = service.stageImport(ReferenceDataImportBatch.FEED_DIAGNOSIS_CODES, "icd10.csv", csv);

        assertEquals(1, batch.getChangedCount());
        ArgumentCaptor<ReferenceDataImportRow> rowCaptor = ArgumentCaptor.forClass(ReferenceDataImportRow.class);
        verify(importDAO).insertRow(rowCaptor.capture());
        assertEquals(ReferenceDataImportRow.TYPE_CHANGE, rowCaptor.getValue().getRowType());
        assertEquals("Old description", rowCaptor.getValue().getExtra());
    }

    @Test
    public void stageImport_diagnosisCodes_unchangedDescription_stagesNoRow() {
        DiagnosisCode existing = new DiagnosisCode();
        existing.setCode("E11.9");
        existing.setDescription("Same description");
        when(lookupService.getDiagnosisCode("E11.9")).thenReturn(existing);
        String csv = "code,description\nE11.9,Same description";

        ReferenceDataImportBatch batch = service.stageImport(ReferenceDataImportBatch.FEED_DIAGNOSIS_CODES, "icd10.csv", csv);

        assertEquals(0, batch.getAddedCount());
        assertEquals(0, batch.getChangedCount());
        verify(importDAO, never()).insertRow(any(ReferenceDataImportRow.class));
    }

    // --- Procedure codes ---

    @Test
    public void stageImport_procedureCodes_newCode_stagesAddRow() {
        when(lookupService.getProcedureCode("99213")).thenReturn(null);
        String csv = "code,description\n99213,Office visit";

        ReferenceDataImportBatch batch = service.stageImport(ReferenceDataImportBatch.FEED_PROCEDURE_CODES, "cpt.csv", csv);

        assertEquals(1, batch.getAddedCount());
    }

    // --- CARC/RARC ---

    @Test
    public void stageImport_carcRarc_newCode_stagesAddRow() {
        when(lookupService.getCarcRarcCode("45")).thenReturn(null);
        String csv = "code,type,description\n45,CARC,Charge exceeds fee schedule";

        ReferenceDataImportBatch batch = service.stageImport(ReferenceDataImportBatch.FEED_CARC_RARC, "carc.csv", csv);

        assertEquals(1, batch.getAddedCount());
        ArgumentCaptor<ReferenceDataImportRow> rowCaptor = ArgumentCaptor.forClass(ReferenceDataImportRow.class);
        verify(importDAO).insertRow(rowCaptor.capture());
        assertEquals("CARC", rowCaptor.getValue().getExtra());
    }

    @Test
    public void stageImport_carcRarc_changedDescription_stagesChangeRow() {
        CarcRarcCode existing = new CarcRarcCode();
        existing.setCode("45");
        existing.setCodeType("CARC");
        existing.setDescription("Old text");
        when(lookupService.getCarcRarcCode("45")).thenReturn(existing);
        String csv = "code,type,description\n45,CARC,New text";

        ReferenceDataImportBatch batch = service.stageImport(ReferenceDataImportBatch.FEED_CARC_RARC, "carc.csv", csv);

        assertEquals(1, batch.getChangedCount());
    }

    // --- NPPES ---

    @Test
    public void stageImport_nppes_activeProvider_stagesFlagRowToValid() {
        Provider provider = new Provider();
        provider.setId(5);
        provider.setNpiValidationStatus("UNVERIFIED");
        when(providerDAO.findByNpi("1234567893")).thenReturn(provider);
        String csv = "npi,status\n1234567893,ACTIVE";

        ReferenceDataImportBatch batch = service.stageImport(ReferenceDataImportBatch.FEED_NPPES, "nppes.csv", csv);

        assertEquals(1, batch.getFlaggedCount());
        ArgumentCaptor<ReferenceDataImportRow> rowCaptor = ArgumentCaptor.forClass(ReferenceDataImportRow.class);
        verify(importDAO).insertRow(rowCaptor.capture());
        assertEquals(ReferenceDataImportRow.TYPE_FLAG, rowCaptor.getValue().getRowType());
        assertEquals("VALID", rowCaptor.getValue().getExtra());
    }

    @Test
    public void stageImport_nppes_inactiveProvider_stagesFlagRowToInvalid() {
        Provider provider = new Provider();
        provider.setId(5);
        provider.setNpiValidationStatus("UNVERIFIED");
        when(providerDAO.findByNpi("1234567893")).thenReturn(provider);
        String csv = "npi,status\n1234567893,INACTIVE";

        ReferenceDataImportBatch batch = service.stageImport(ReferenceDataImportBatch.FEED_NPPES, "nppes.csv", csv);

        ArgumentCaptor<ReferenceDataImportRow> rowCaptor = ArgumentCaptor.forClass(ReferenceDataImportRow.class);
        verify(importDAO).insertRow(rowCaptor.capture());
        assertEquals("INVALID", rowCaptor.getValue().getExtra());
    }

    @Test
    public void stageImport_nppes_unknownProvider_stagesNoRow() {
        when(providerDAO.findByNpi("1234567893")).thenReturn(null);
        String csv = "npi,status\n1234567893,ACTIVE";

        ReferenceDataImportBatch batch = service.stageImport(ReferenceDataImportBatch.FEED_NPPES, "nppes.csv", csv);

        assertEquals(0, batch.getFlaggedCount());
        verify(importDAO, never()).insertRow(any(ReferenceDataImportRow.class));
    }

    @Test
    public void stageImport_nppes_alreadyValid_stagesNoRow() {
        Provider provider = new Provider();
        provider.setId(5);
        provider.setNpiValidationStatus("VALID");
        when(providerDAO.findByNpi("1234567893")).thenReturn(provider);
        String csv = "npi,status\n1234567893,ACTIVE";

        ReferenceDataImportBatch batch = service.stageImport(ReferenceDataImportBatch.FEED_NPPES, "nppes.csv", csv);

        assertEquals(0, batch.getFlaggedCount());
    }

    // --- Idempotency ---

    @Test
    public void stageImport_duplicateFileContent_returnsExistingBatchWithoutReParsing() {
        ReferenceDataImportBatch existing = new ReferenceDataImportBatch();
        existing.setId(99);
        existing.setStatus(ReferenceDataImportBatch.STATUS_STAGED);
        when(importDAO.findBatchByFileHash(anyString())).thenReturn(existing);

        ReferenceDataImportBatch result = service.stageImport(
            ReferenceDataImportBatch.FEED_DIAGNOSIS_CODES, "icd10.csv", "code,description\nE11.9,Diabetes");

        assertEquals(99, result.getId());
        verify(importDAO, never()).insertBatch(any(ReferenceDataImportBatch.class));
    }

    // --- Apply ---

    @Test
    public void applyImport_diagnosisAddRow_savesNewDiagnosisCode() {
        ReferenceDataImportBatch batch = new ReferenceDataImportBatch();
        batch.setId(1);
        batch.setFeedType(ReferenceDataImportBatch.FEED_DIAGNOSIS_CODES);
        batch.setStatus(ReferenceDataImportBatch.STATUS_STAGED);
        when(importDAO.findBatchById(1)).thenReturn(batch);

        ReferenceDataImportRow row = new ReferenceDataImportRow();
        row.setId(10);
        row.setRowType(ReferenceDataImportRow.TYPE_ADD);
        row.setCode("E11.9");
        row.setDescription("Type 2 diabetes");
        when(importDAO.findRowsByBatchId(1)).thenReturn(Arrays.asList(row));
        when(lookupService.getDiagnosisCode("E11.9")).thenReturn(null);

        service.applyImport(1, 7);

        ArgumentCaptor<DiagnosisCode> dcCaptor = ArgumentCaptor.forClass(DiagnosisCode.class);
        verify(lookupService).saveDiagnosisCode(dcCaptor.capture());
        assertEquals("E11.9", dcCaptor.getValue().getCode());
        assertEquals("Type 2 diabetes", dcCaptor.getValue().getDescription());
        verify(importDAO).markRowApplied(10);
        verify(importDAO).markBatchApplied(eq(1), eq(7), any(java.util.Date.class));
    }

    @Test
    public void applyImport_nppesFlagRow_updatesProviderValidation() {
        ReferenceDataImportBatch batch = new ReferenceDataImportBatch();
        batch.setId(2);
        batch.setFeedType(ReferenceDataImportBatch.FEED_NPPES);
        batch.setStatus(ReferenceDataImportBatch.STATUS_STAGED);
        when(importDAO.findBatchById(2)).thenReturn(batch);

        ReferenceDataImportRow row = new ReferenceDataImportRow();
        row.setId(20);
        row.setRowType(ReferenceDataImportRow.TYPE_FLAG);
        row.setCode("1234567893");
        row.setExtra("VALID");
        when(importDAO.findRowsByBatchId(2)).thenReturn(Arrays.asList(row));
        Provider provider = new Provider();
        provider.setId(5);
        when(providerDAO.findByNpi("1234567893")).thenReturn(provider);

        service.applyImport(2, 7);

        verify(providerDAO).updateNpiValidation(eq(5), eq("VALID"), any());
    }

    @Test(expected = ServiceException.class)
    public void applyImport_unknownBatch_throws() {
        when(importDAO.findBatchById(999)).thenReturn(null);
        service.applyImport(999, 7);
    }

    @Test(expected = ServiceException.class)
    public void applyImport_alreadyApplied_throws() {
        ReferenceDataImportBatch batch = new ReferenceDataImportBatch();
        batch.setId(1);
        batch.setStatus(ReferenceDataImportBatch.STATUS_APPLIED);
        when(importDAO.findBatchById(1)).thenReturn(batch);

        service.applyImport(1, 7);
    }

    // --- Read delegation ---

    @Test
    public void findRecentBatches_delegatesToDao() {
        service.findRecentBatches(10);
        verify(importDAO).findRecentBatches(10);
    }

    @Test
    public void findBatchById_delegatesToDao() {
        service.findBatchById(1);
        verify(importDAO).findBatchById(1);
    }

    @Test
    public void findRowsByBatchId_delegatesToDao() {
        service.findRowsByBatchId(1);
        verify(importDAO).findRowsByBatchId(1);
    }
}
