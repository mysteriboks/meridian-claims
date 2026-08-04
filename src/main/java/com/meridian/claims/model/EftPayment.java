package com.meridian.claims.model;

import java.math.BigDecimal;
import java.util.Date;

/**
 * One EFT/ACH file generation event for a payment batch (Phase 18) — the TRN
 * reassociation number recorded here is the same value stamped on the paired
 * 835's TRN segment ({@link Edi835Generator}), letting a provider match the
 * electronic deposit back to its remittance advice.
 */
public class EftPayment {

    public static final String STATUS_GENERATED = "GENERATED";
    public static final String STATUS_SETTLED = "SETTLED";
    public static final String STATUS_FAILED = "FAILED";

    private int id;
    private int paymentBatchId;
    private int remittanceBatchId;
    private String trnReassociationNumber;
    private BigDecimal amount;
    private String settlementStatus;
    private String achFileReference;
    private int entryCount;
    private int skippedProviderCount;
    private Date createdAt;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPaymentBatchId() { return paymentBatchId; }
    public void setPaymentBatchId(int paymentBatchId) { this.paymentBatchId = paymentBatchId; }

    public int getRemittanceBatchId() { return remittanceBatchId; }
    public void setRemittanceBatchId(int remittanceBatchId) { this.remittanceBatchId = remittanceBatchId; }

    public String getTrnReassociationNumber() { return trnReassociationNumber; }
    public void setTrnReassociationNumber(String trnReassociationNumber) { this.trnReassociationNumber = trnReassociationNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getSettlementStatus() { return settlementStatus; }
    public void setSettlementStatus(String settlementStatus) { this.settlementStatus = settlementStatus; }

    public String getAchFileReference() { return achFileReference; }
    public void setAchFileReference(String achFileReference) { this.achFileReference = achFileReference; }

    public int getEntryCount() { return entryCount; }
    public void setEntryCount(int entryCount) { this.entryCount = entryCount; }

    public int getSkippedProviderCount() { return skippedProviderCount; }
    public void setSkippedProviderCount(int skippedProviderCount) { this.skippedProviderCount = skippedProviderCount; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
