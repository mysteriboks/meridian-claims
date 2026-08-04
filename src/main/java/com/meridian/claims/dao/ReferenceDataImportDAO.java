package com.meridian.claims.dao;

import com.meridian.claims.model.ReferenceDataImportBatch;
import com.meridian.claims.model.ReferenceDataImportRow;

import java.util.Date;
import java.util.List;

/** DAO for the reference-data stage-then-apply import ledger (Phase 19). */
public interface ReferenceDataImportDAO {

    int insertBatch(ReferenceDataImportBatch batch);

    ReferenceDataImportBatch findBatchById(int id);

    ReferenceDataImportBatch findBatchByFileHash(String fileHash);

    List<ReferenceDataImportBatch> findRecentBatches(int limit);

    void markBatchApplied(int id, int appliedByUserId, Date appliedAt);

    void markBatchFailed(int id, String errorMessage);

    int insertRow(ReferenceDataImportRow row);

    List<ReferenceDataImportRow> findRowsByBatchId(int batchId);

    void markRowApplied(int id);
}
