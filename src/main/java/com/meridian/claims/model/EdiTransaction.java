package com.meridian.claims.model;

import java.util.Date;

/**
 * One row in the integration transaction log ({@code edi_transactions}) — the
 * reconciliation backbone for the interoperability program (Phase 12+). Records
 * every inbound and outbound EDI interchange/transaction, keyed on its control
 * numbers. An acknowledgment row (999 / 277CA / TA1) links back to the inbound
 * transaction it responds to via {@code relatedTransactionId}.
 */
public class EdiTransaction {

    public static final String DIRECTION_INBOUND = "INBOUND";
    public static final String DIRECTION_OUTBOUND = "OUTBOUND";

    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_PARTIAL = "PARTIAL";

    private int id;
    private String direction;
    private String transactionType;
    private String isaControlNumber;
    private String gsControlNumber;
    private String stControlNumber;
    private String status;
    private Integer relatedTransactionId;
    private String fileReference;
    private String detail;
    private Integer tradingPartnerId;
    private Date createdAt;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getIsaControlNumber() { return isaControlNumber; }
    public void setIsaControlNumber(String isaControlNumber) { this.isaControlNumber = isaControlNumber; }

    public String getGsControlNumber() { return gsControlNumber; }
    public void setGsControlNumber(String gsControlNumber) { this.gsControlNumber = gsControlNumber; }

    public String getStControlNumber() { return stControlNumber; }
    public void setStControlNumber(String stControlNumber) { this.stControlNumber = stControlNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getRelatedTransactionId() { return relatedTransactionId; }
    public void setRelatedTransactionId(Integer relatedTransactionId) { this.relatedTransactionId = relatedTransactionId; }

    public String getFileReference() { return fileReference; }
    public void setFileReference(String fileReference) { this.fileReference = fileReference; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public Integer getTradingPartnerId() { return tradingPartnerId; }
    public void setTradingPartnerId(Integer tradingPartnerId) { this.tradingPartnerId = tradingPartnerId; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
