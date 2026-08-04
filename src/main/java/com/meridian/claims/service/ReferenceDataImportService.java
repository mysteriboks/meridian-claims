package com.meridian.claims.service;

import com.meridian.claims.dao.ProviderDAO;
import com.meridian.claims.dao.ReferenceDataImportDAO;
import com.meridian.claims.model.CarcRarcCode;
import com.meridian.claims.model.DiagnosisCode;
import com.meridian.claims.model.ProcedureCode;
import com.meridian.claims.model.Provider;
import com.meridian.claims.model.ReferenceDataImportBatch;
import com.meridian.claims.model.ReferenceDataImportRow;
import com.meridian.claims.util.CsvReader;
import com.meridian.claims.util.LedgerUtil;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Orchestrates bulk reference-data imports (Phase 19): ICD-10/CPT-HCPCS code
 * updates, the X12 CARC/RARC external code list, and NPPES NPI validation.
 * Two-phase by design — {@link #stageImport} parses the file and computes a
 * reviewable diff without touching any live table; {@link #applyImport} is a
 * separate, explicit Admin action that actually writes the changes. This
 * honors the CPT AMA-license "admin-approved apply, not silent auto-load"
 * rule from the start, applied uniformly to every feed type for consistency.
 *
 * File-level idempotency mirrors every other intake ledger in this codebase
 * (SHA-256 in the batch row) — reuses {@link LedgerUtil}, the shared home
 * for that pattern extracted during the Phase 17 duplication sweep.
 */
@Service
public class ReferenceDataImportService {

    private static final Logger LOG = Logger.getLogger(ReferenceDataImportService.class);

    private final ReferenceDataImportDAO importDAO;
    private final LookupService lookupService;
    private final ProviderDAO providerDAO;
    private final AuditService auditService;

    @Autowired
    public ReferenceDataImportService(ReferenceDataImportDAO importDAO, LookupService lookupService,
                                       ProviderDAO providerDAO, AuditService auditService) {
        this.importDAO = importDAO;
        this.lookupService = lookupService;
        this.providerDAO = providerDAO;
        this.auditService = auditService;
    }

    public List<ReferenceDataImportBatch> findRecentBatches(int limit) {
        return importDAO.findRecentBatches(limit);
    }

    public ReferenceDataImportBatch findBatchById(int id) {
        return importDAO.findBatchById(id);
    }

    public List<ReferenceDataImportRow> findRowsByBatchId(int batchId) {
        return importDAO.findRowsByBatchId(batchId);
    }

    /**
     * Parses the file and computes a diff against current data — inserts a
     * batch + its staged rows, but never touches {@code diagnosis_codes},
     * {@code procedure_codes}, {@code carc_rarc_codes}, or {@code providers}.
     * Idempotent by file content: re-staging the same file returns the
     * existing batch rather than creating a duplicate.
     */
    @Transactional
    public ReferenceDataImportBatch stageImport(String feedType, String fileName, String fileContent) {
        String hash = LedgerUtil.sha256(fileContent);
        ReferenceDataImportBatch existing = importDAO.findBatchByFileHash(hash);
        if (existing != null) {
            LOG.info("ReferenceDataImportService: file already staged (hash=" + hash
                + " status=" + existing.getStatus() + ") — skipping fileName=" + fileName);
            return existing;
        }

        List<String[]> csvRows = CsvReader.parseAll(fileContent);
        List<String[]> dataRows = csvRows.isEmpty() ? csvRows : csvRows.subList(1, csvRows.size()); // skip header

        List<ReferenceDataImportRow> pendingRows = new ArrayList<ReferenceDataImportRow>();
        int added = 0, changed = 0, flagged = 0;

        for (String[] cells : dataRows) {
            if (ReferenceDataImportBatch.FEED_NPPES.equals(feedType)) {
                ReferenceDataImportRow row = diffNppesRow(cells);
                if (row != null) { pendingRows.add(row); flagged++; }
            } else if (ReferenceDataImportBatch.FEED_CARC_RARC.equals(feedType)) {
                ReferenceDataImportRow row = diffCarcRarcRow(cells);
                if (row != null) {
                    pendingRows.add(row);
                    if (ReferenceDataImportRow.TYPE_ADD.equals(row.getRowType())) added++; else changed++;
                }
            } else if (ReferenceDataImportBatch.FEED_DIAGNOSIS_CODES.equals(feedType)) {
                DiagnosisCode current = lookupService.getDiagnosisCode(safeCell(cells, 0));
                ReferenceDataImportRow row = diffCodeRow(cells, current != null,
                    current != null ? current.getDescription() : null);
                if (row != null) {
                    pendingRows.add(row);
                    if (ReferenceDataImportRow.TYPE_ADD.equals(row.getRowType())) added++; else changed++;
                }
            } else if (ReferenceDataImportBatch.FEED_PROCEDURE_CODES.equals(feedType)) {
                ProcedureCode current = lookupService.getProcedureCode(safeCell(cells, 0));
                ReferenceDataImportRow row = diffCodeRow(cells, current != null,
                    current != null ? current.getDescription() : null);
                if (row != null) {
                    pendingRows.add(row);
                    if (ReferenceDataImportRow.TYPE_ADD.equals(row.getRowType())) added++; else changed++;
                }
            } else {
                throw new ServiceException("Unknown reference data feed type: " + feedType);
            }
        }

        ReferenceDataImportBatch batch = new ReferenceDataImportBatch();
        batch.setFeedType(feedType);
        batch.setFileName(fileName);
        batch.setFileHash(hash);
        batch.setStatus(ReferenceDataImportBatch.STATUS_STAGED);
        batch.setTotalRecords(dataRows.size());
        batch.setAddedCount(added);
        batch.setChangedCount(changed);
        batch.setFlaggedCount(flagged);
        int batchId = importDAO.insertBatch(batch);

        for (ReferenceDataImportRow row : pendingRows) {
            row.setBatchId(batchId);
            importDAO.insertRow(row);
        }

        auditService.record("REFDATA_IMPORT_STAGED", "ReferenceDataImportBatch", (long) batchId,
            feedType + " staged from " + fileName + ": " + added + " added, " + changed + " changed, "
                + flagged + " flagged, out of " + dataRows.size() + " records");
        LOG.info("ReferenceDataImportService: staged fileName=" + fileName + " feedType=" + feedType
            + " batchId=" + batchId + " total=" + dataRows.size() + " added=" + added
            + " changed=" + changed + " flagged=" + flagged);
        return batch;
    }

    /**
     * Applies every staged row in a batch to the live table it targets —
     * an explicit Admin action, never automatic. A batch can only be
     * applied once (guarded by its {@code STAGED} status).
     */
    @Transactional
    public ReferenceDataImportBatch applyImport(int batchId, int userId) {
        ReferenceDataImportBatch batch = importDAO.findBatchById(batchId);
        if (batch == null) {
            throw new ServiceException("Reference data import batch id=" + batchId + " not found");
        }
        if (!ReferenceDataImportBatch.STATUS_STAGED.equals(batch.getStatus())) {
            throw new ServiceException("Batch id=" + batchId + " is not staged (status=" + batch.getStatus() + ")");
        }

        List<ReferenceDataImportRow> rows = importDAO.findRowsByBatchId(batchId);
        for (ReferenceDataImportRow row : rows) {
            applyRow(batch.getFeedType(), row);
            importDAO.markRowApplied(row.getId());
        }

        Date now = new Date();
        importDAO.markBatchApplied(batchId, userId, now);
        auditService.record("REFDATA_IMPORT_APPLIED", "ReferenceDataImportBatch", (long) batchId,
            batch.getFeedType() + " applied: " + rows.size() + " rows");
        LOG.info("ReferenceDataImportService: applied batchId=" + batchId + " rows=" + rows.size());

        batch.setStatus(ReferenceDataImportBatch.STATUS_APPLIED);
        batch.setAppliedByUserId(userId);
        batch.setAppliedAt(now);
        return batch;
    }

    private void applyRow(String feedType, ReferenceDataImportRow row) {
        if (ReferenceDataImportBatch.FEED_DIAGNOSIS_CODES.equals(feedType)) {
            DiagnosisCode dc = lookupService.getDiagnosisCode(row.getCode());
            if (dc == null) {
                dc = new DiagnosisCode();
                dc.setCode(row.getCode());
                dc.setActive(true);
            }
            dc.setDescription(row.getDescription());
            lookupService.saveDiagnosisCode(dc);
        } else if (ReferenceDataImportBatch.FEED_PROCEDURE_CODES.equals(feedType)) {
            ProcedureCode pc = lookupService.getProcedureCode(row.getCode());
            if (pc == null) {
                pc = new ProcedureCode();
                pc.setCode(row.getCode());
                pc.setActive(true);
            }
            pc.setDescription(row.getDescription());
            lookupService.saveProcedureCode(pc);
        } else if (ReferenceDataImportBatch.FEED_CARC_RARC.equals(feedType)) {
            CarcRarcCode cc = lookupService.getCarcRarcCode(row.getCode());
            if (cc == null) {
                cc = new CarcRarcCode();
                cc.setCode(row.getCode());
                cc.setActive(true);
            }
            cc.setDescription(row.getDescription());
            cc.setCodeType(row.getExtra());
            lookupService.saveCarcRarcCode(cc);
        } else if (ReferenceDataImportBatch.FEED_NPPES.equals(feedType)) {
            Provider provider = providerDAO.findByNpi(row.getCode());
            if (provider != null) {
                providerDAO.updateNpiValidation(provider.getId(), row.getExtra(), new Date());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Diffing helpers — one per feed shape. Return null when the row is a
    // no-op (value already matches current data) so it's never staged.
    // -------------------------------------------------------------------------

    private ReferenceDataImportRow diffCodeRow(String[] cells, boolean exists, String currentDescription) {
        String code = safeCell(cells, 0);
        String description = safeCell(cells, 1);
        if (code.isEmpty()) {
            return null;
        }
        if (!exists) {
            return newRow(ReferenceDataImportRow.TYPE_ADD, code, description, null);
        }
        if (!description.equals(currentDescription)) {
            return newRow(ReferenceDataImportRow.TYPE_CHANGE, code, description, currentDescription);
        }
        return null;
    }

    private ReferenceDataImportRow diffCarcRarcRow(String[] cells) {
        String code = safeCell(cells, 0);
        String codeType = safeCell(cells, 1).toUpperCase();
        String description = safeCell(cells, 2);
        if (code.isEmpty()) {
            return null;
        }
        CarcRarcCode existing = lookupService.getCarcRarcCode(code);
        if (existing == null) {
            ReferenceDataImportRow row = newRow(ReferenceDataImportRow.TYPE_ADD, code, description, codeType);
            return row;
        }
        if (!description.equals(existing.getDescription()) || !codeType.equals(existing.getCodeType())) {
            ReferenceDataImportRow row = newRow(ReferenceDataImportRow.TYPE_CHANGE, code, description, codeType);
            return row;
        }
        return null;
    }

    private ReferenceDataImportRow diffNppesRow(String[] cells) {
        String npi = safeCell(cells, 0);
        String rawStatus = safeCell(cells, 1);
        if (npi.isEmpty()) {
            return null;
        }
        Provider provider = providerDAO.findByNpi(npi);
        if (provider == null) {
            return null; // not one of our providers — nothing to flag
        }
        String targetStatus = "ACTIVE".equalsIgnoreCase(rawStatus) ? "VALID" : "INVALID";
        if (targetStatus.equals(provider.getNpiValidationStatus())) {
            return null; // already up to date
        }
        ReferenceDataImportRow row = new ReferenceDataImportRow();
        row.setRowType(ReferenceDataImportRow.TYPE_FLAG);
        row.setCode(npi);
        row.setDescription(rawStatus);
        row.setExtra(targetStatus);
        return row;
    }

    private ReferenceDataImportRow newRow(String rowType, String code, String description, String extra) {
        ReferenceDataImportRow row = new ReferenceDataImportRow();
        row.setRowType(rowType);
        row.setCode(code);
        row.setDescription(description);
        row.setExtra(extra);
        return row;
    }

    private String safeCell(String[] cells, int index) {
        return index < cells.length && cells[index] != null ? cells[index].trim() : "";
    }
}
