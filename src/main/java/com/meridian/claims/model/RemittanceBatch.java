package com.meridian.claims.model;

import java.math.BigDecimal;
import java.util.Date;

public class RemittanceBatch {

    private int id;
    private Date paymentDate;
    private BigDecimal totalPaid;
    private String status;      // GENERATED / SENT
    private Date generatedAt;
    private Date createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Date getPaymentDate() { return paymentDate; }
    public void setPaymentDate(Date paymentDate) { this.paymentDate = paymentDate; }

    public BigDecimal getTotalPaid() { return totalPaid; }
    public void setTotalPaid(BigDecimal totalPaid) { this.totalPaid = totalPaid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Date generatedAt) { this.generatedAt = generatedAt; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
