package com.meridian.claims.model;

import java.math.BigDecimal;
import java.util.Date;

public class PaymentBatch {

    private int id;
    private Date batchDate;
    private BigDecimal totalAmount;
    private String status;          // PENDING / APPROVED / EXPORTED
    private Date exportedAt;
    private String fileReference;
    private Integer createdBy;
    private Date createdAt;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Date getBatchDate() { return batchDate; }
    public void setBatchDate(Date batchDate) { this.batchDate = batchDate; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getExportedAt() { return exportedAt; }
    public void setExportedAt(Date exportedAt) { this.exportedAt = exportedAt; }

    public String getFileReference() { return fileReference; }
    public void setFileReference(String fileReference) { this.fileReference = fileReference; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
