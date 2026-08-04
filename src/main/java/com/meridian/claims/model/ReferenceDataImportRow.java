package com.meridian.claims.model;

import java.util.Date;

/**
 * One staged row within a {@link ReferenceDataImportBatch} — the reviewable
 * diff the Admin sees before approving an import (Phase 19).
 */
public class ReferenceDataImportRow {

    public static final String TYPE_ADD = "ADD";
    public static final String TYPE_CHANGE = "CHANGE";
    public static final String TYPE_FLAG = "FLAG";

    private int id;
    private int batchId;
    private String rowType;
    private String code;
    private String description;
    /** For CHANGE rows, the prior description; for NPPES FLAG rows, the NPPES status text. */
    private String extra;
    private boolean applied;
    private Date createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getBatchId() { return batchId; }
    public void setBatchId(int batchId) { this.batchId = batchId; }

    public String getRowType() { return rowType; }
    public void setRowType(String rowType) { this.rowType = rowType; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }

    public boolean isApplied() { return applied; }
    public void setApplied(boolean applied) { this.applied = applied; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
