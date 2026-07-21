package com.meridian.claims.model;

import java.util.Date;

/**
 * Represents one inbound claim file that has been processed (or is being
 * processed) by the batch intake poller job. Keyed on SHA-256 file hash
 * for idempotency — re-submitting the same file is a no-op.
 */
public class ClaimIntakeBatch {

    private int id;
    private String fileName;
    private String fileHash;
    private String status;
    private int totalRecords;
    private int succeeded;
    private int quarantined;
    private String errorMessage;
    private Date createdAt;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }

    public int getSucceeded() { return succeeded; }
    public void setSucceeded(int succeeded) { this.succeeded = succeeded; }

    public int getQuarantined() { return quarantined; }
    public void setQuarantined(int quarantined) { this.quarantined = quarantined; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
