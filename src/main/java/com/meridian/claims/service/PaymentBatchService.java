package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.PaymentBatchDAO;
import com.meridian.claims.dao.PaymentDAO;
import com.meridian.claims.dao.ProviderDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.Payment;
import com.meridian.claims.model.PaymentBatch;
import com.meridian.claims.model.Provider;
import com.meridian.claims.model.RemittanceBatch;
import com.meridian.claims.util.CsvWriter;
import com.meridian.claims.util.Money;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Service
public class PaymentBatchService {

    private static final Logger LOG = Logger.getLogger(PaymentBatchService.class);

    @Autowired private PaymentBatchDAO paymentBatchDAO;
    @Autowired private PaymentDAO paymentDAO;
    @Autowired private ClaimDAO claimDAO;
    @Autowired private ProviderDAO providerDAO;
    @Autowired private ClaimService claimService;
    @Autowired private SubrogationService subrogationService;
    @Autowired private RemittanceService remittanceService;
    @Autowired private EftPaymentService eftPaymentService;
    @Autowired private AuditService auditService;

    /**
     * Creates a payment batch grouping all PENDING payments, sets each payment's claim
     * to IN_BATCH, and computes the batch total.
     */
    @Transactional
    public PaymentBatch createBatch(Date batchDate, int createdByUserId) {
        List<Payment> pendingPayments = paymentDAO.findByStatus("PENDING");
        if (pendingPayments.isEmpty()) {
            throw new ServiceException("No pending payments available for batch");
        }

        PaymentBatch batch = new PaymentBatch();
        batch.setBatchDate(batchDate);
        batch.setCreatedBy(createdByUserId);
        paymentBatchDAO.insert(batch);

        BigDecimal total = Money.ZERO;
        for (Payment p : pendingPayments) {
            paymentDAO.assignToBatch(p.getId(), batch.getId());

            // PENDING_PAYMENT → IN_BATCH via the centralized state-machine guard
            Claim claim = claimDAO.findById(p.getClaimId());
            if (claim != null && claim.getStatus() == ClaimStatus.PENDING_PAYMENT) {
                claimService.assertLegalTransition(
                    ClaimStatus.PENDING_PAYMENT.name(), ClaimStatus.IN_BATCH.name());
                claimDAO.updateStatus(p.getClaimId(), ClaimStatus.IN_BATCH.name(), claim.getVersion());
            }
            total = Money.add(total, p.getPlanPaidTotal());
        }
        paymentBatchDAO.updateTotalAmount(batch.getId(), total);
        batch.setTotalAmount(total);

        auditService.record("BATCH_CREATED", "PAYMENT_BATCH", (long) batch.getId(),
            "Batch created; " + pendingPayments.size() + " payments; total=" + total);
        LOG.info("Payment batch created id=" + batch.getId() + " payments=" + pendingPayments.size());
        return batch;
    }

    /**
     * Exports a batch to CSV, marks it EXPORTED, and moves each payment's claim to PAID.
     * Returns the CSV content string.
     */
    @Transactional
    public String exportCsv(int batchId, int userId) {
        PaymentBatch batch = paymentBatchDAO.findById(batchId);
        if (batch == null) {
            throw new ServiceException("Payment batch not found: id=" + batchId);
        }
        if ("EXPORTED".equals(batch.getStatus())) {
            throw new ServiceException("Batch id=" + batchId + " is already exported");
        }

        List<Payment> payments = paymentDAO.findByBatchId(batchId);

        CsvWriter csv = new CsvWriter();
        csv.header("PaymentId", "ClaimNumber", "ProviderName", "ProviderNPI",
                   "BilledTotal", "AllowedTotal", "PlanPaidTotal", "MemberResponsibility",
                   "BatchDate");

        String batchDateStr = new SimpleDateFormat("yyyy-MM-dd").format(batch.getBatchDate());
        java.util.List<Integer> paymentIds = new java.util.ArrayList<Integer>();
        for (Payment p : payments) {
            paymentIds.add(p.getId());
            Claim claim = claimDAO.findById(p.getClaimId());
            Provider provider = claim != null ? providerDAO.findById(claim.getProviderId()) : null;
            csv.row(
                String.valueOf(p.getId()),
                claim != null ? claim.getClaimNumber() : "",
                provider != null ? provider.getName() : "",
                provider != null ? provider.getNpi() : "",
                p.getBilledTotal().toPlainString(),
                p.getAllowedTotal().toPlainString(),
                p.getPlanPaidTotal().toPlainString(),
                p.getMemberResponsibility().toPlainString(),
                batchDateStr
            );

            // IN_BATCH → PAID on export; auto-open subrogation for accident-flagged claims
            if (claim != null && claim.getStatus() == ClaimStatus.IN_BATCH) {
                claimDAO.updateStatus(claim.getId(), ClaimStatus.PAID.name(), claim.getVersion());
                subrogationService.openIfAccident(claim.getId());
            }
        }

        String fileRef = "BATCH-" + batchId + "-" + batchDateStr + ".csv";
        paymentBatchDAO.updateStatus(batchId, "EXPORTED", fileRef);

        // Generate provider remittance advice simultaneously (PHASES.md Phase 6 / reuses Phase 5)
        if (!paymentIds.isEmpty()) {
            RemittanceBatch remittanceBatch = remittanceService.generateBatch(paymentIds, batch.getBatchDate());
            // Phase 18: issue electronic payment (NACHA ACH) for any provider with banking info
            // configured; providers without it are simply skipped, so the batch stays partially
            // check-paid rather than blocking the export.
            eftPaymentService.generateForBatch(batch, remittanceBatch,
                remittanceService.findItemsByBatchId(remittanceBatch.getId()));
        }

        auditService.record("BATCH_EXPORTED", "PAYMENT_BATCH", (long) batchId,
            "Exported " + payments.size() + " payments; file=" + fileRef);
        LOG.info("Payment batch exported id=" + batchId + " file=" + fileRef);
        return csv.build();
    }

    public PaymentBatch findById(int id) {
        return paymentBatchDAO.findById(id);
    }

    public List<PaymentBatch> findAll() {
        return paymentBatchDAO.findAll();
    }
}
