package com.meridian.claims.model;

import java.util.Date;

/**
 * One staged (and, once approved, applied) bulk reference-data import —
 * ICD-10/CPT-HCPCS code updates, the X12 CARC/RARC external code list, or
 * an NPPES NPI-validation extract (Phase 19). Mirrors the file-level
 * idempotency ledger shape used throughout the intake pollers
 * ({@code claim_intake_batches}, {@code enrollment_batches}), but staging
 * never touches the live lookup tables or {@code providers} — an explicit
 * {@link com.meridian.claims.service.ReferenceDataImportService#applyImport}
 * call does, honoring the CPT AMA-license "admin-approved apply" rule.
 */
public class ReferenceDataImportBatch {

    public static final String FEED_DIAGNOSIS_CODES = "DIAGNOSIS_CODES";
    public static final String FEED_PROCEDURE_CODES = "PROCEDURE_CODES";
    public static final String FEED_CARC_RARC = "CARC_RARC";
    public static final String FEED_NPPES = "NPPES";

    public static final String STATUS_STAGED = "STAGED";
    public static final String STATUS_APPLIED = "APPLIED";
    public static final String STATUS_FAILED = "FAILED";

    private int id;
    private String feedType;
    private String fileName;
    private String fileHash;
    private String status;
    private int totalRecords;
    private int addedCount;
    private int changedCount;
    private int flaggedCount;
    private String errorMessage;
    private Integer appliedByUserId;
    private Date appliedAt;
    private Date createdAt;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFeedType() { return feedType; }
    public void setFeedType(String feedType) { this.feedType = feedType; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }

    public int getAddedCount() { return addedCount; }
    public void setAddedCount(int addedCount) { this.addedCount = addedCount; }

    public int getChangedCount() { return changedCount; }
    public void setChangedCount(int changedCount) { this.changedCount = changedCount; }

    public int getFlaggedCount() { return flaggedCount; }
    public void setFlaggedCount(int flaggedCount) { this.flaggedCount = flaggedCount; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getAppliedByUserId() { return appliedByUserId; }
    public void setAppliedByUserId(Integer appliedByUserId) { this.appliedByUserId = appliedByUserId; }

    public Date getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Date appliedAt) { this.appliedAt = appliedAt; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
