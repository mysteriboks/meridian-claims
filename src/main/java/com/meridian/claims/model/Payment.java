package com.meridian.claims.model;

import java.math.BigDecimal;
import java.util.Date;

public class Payment {

    private int id;
    private int claimId;
    private BigDecimal billedTotal;
    private BigDecimal allowedTotal;
    private BigDecimal planPaidTotal;
    private BigDecimal memberResponsibility;
    private boolean partialPaymentFlag;
    private BigDecimal amountPaid;
    private BigDecimal remainingBalance;
    private String referenceNumber;
    private String status;          // PENDING / PAID / VOIDED
    private Date paymentDate;
    private Date createdAt;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getClaimId() { return claimId; }
    public void setClaimId(int claimId) { this.claimId = claimId; }

    public BigDecimal getBilledTotal() { return billedTotal; }
    public void setBilledTotal(BigDecimal billedTotal) { this.billedTotal = billedTotal; }

    public BigDecimal getAllowedTotal() { return allowedTotal; }
    public void setAllowedTotal(BigDecimal allowedTotal) { this.allowedTotal = allowedTotal; }

    public BigDecimal getPlanPaidTotal() { return planPaidTotal; }
    public void setPlanPaidTotal(BigDecimal planPaidTotal) { this.planPaidTotal = planPaidTotal; }

    public BigDecimal getMemberResponsibility() { return memberResponsibility; }
    public void setMemberResponsibility(BigDecimal memberResponsibility) { this.memberResponsibility = memberResponsibility; }

    public boolean isPartialPaymentFlag() { return partialPaymentFlag; }
    public void setPartialPaymentFlag(boolean partialPaymentFlag) { this.partialPaymentFlag = partialPaymentFlag; }

    public BigDecimal getAmountPaid() { return amountPaid; }
    public void setAmountPaid(BigDecimal amountPaid) { this.amountPaid = amountPaid; }

    public BigDecimal getRemainingBalance() { return remainingBalance; }
    public void setRemainingBalance(BigDecimal remainingBalance) { this.remainingBalance = remainingBalance; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getPaymentDate() { return paymentDate; }
    public void setPaymentDate(Date paymentDate) { this.paymentDate = paymentDate; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
