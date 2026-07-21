package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.ClaimLineItemDAO;
import com.meridian.claims.dao.PaymentDAO;
import com.meridian.claims.dao.RemittanceBatchDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.model.Payment;
import com.meridian.claims.model.RemittanceBatch;
import com.meridian.claims.model.RemittanceBatchItem;
import com.meridian.claims.util.HtmlUtil;
import com.meridian.claims.util.Money;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RemittanceService {

    private static final Logger LOG = Logger.getLogger(RemittanceService.class);

    @Autowired private PaymentDAO paymentDAO;
    @Autowired private ClaimDAO claimDAO;
    @Autowired private ClaimLineItemDAO lineItemDAO;
    @Autowired private RemittanceBatchDAO remittanceBatchDAO;

    /**
     * Creates a RemittanceBatch containing one item per paid claim, grouped by provider.
     * Returns the batch with all items persisted.
     */
    @Transactional
    public RemittanceBatch generateBatch(List<Integer> paymentIds, Date paymentDate) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            throw new ServiceException("Payment list must not be empty");
        }

        BigDecimal totalPaid = Money.ZERO;

        RemittanceBatch batch = new RemittanceBatch();
        batch.setPaymentDate(paymentDate);
        batch.setTotalPaid(Money.ZERO); // updated below
        remittanceBatchDAO.insertBatch(batch);

        for (int paymentId : paymentIds) {
            Payment payment = paymentDAO.findById(paymentId);
            if (payment == null) {
                LOG.warn("generateBatch: payment id=" + paymentId + " not found — skipped");
                continue;
            }
            Claim claim = claimDAO.findById(payment.getClaimId());
            if (claim == null) {
                LOG.warn("generateBatch: claim not found for payment id=" + paymentId + " — skipped");
                continue;
            }
            List<ClaimLineItem> lineItems = lineItemDAO.findByClaimId(claim.getId());

            // One batch item per line item (per spec: procedure codes + CARC per line)
            for (ClaimLineItem li : lineItems) {
                BigDecimal billed  = li.getBilledAmount() != null ? li.getBilledAmount() : Money.ZERO;
                BigDecimal allowed = li.getAllowedAmount() != null ? li.getAllowedAmount() : Money.ZERO;
                BigDecimal planPaid = li.getPlanPaidAmount() != null ? li.getPlanPaidAmount() : Money.ZERO;

                RemittanceBatchItem item = new RemittanceBatchItem();
                item.setBatchId(batch.getId());
                item.setClaimId(claim.getId());
                item.setProviderId(claim.getProviderId());
                item.setBilled(billed);
                item.setAllowed(allowed);
                item.setPlanPaid(planPaid);
                item.setAdjustmentReasonCode(li.getAdjustmentReasonCode());
                item.setProcedureCode(li.getProcedureCode());
                remittanceBatchDAO.insertItem(item);

                totalPaid = Money.add(totalPaid, planPaid);
            }
        }

        // Persist the summed total so reloads from the DB show the correct figure.
        remittanceBatchDAO.updateTotalPaid(batch.getId(), totalPaid);
        batch.setTotalPaid(totalPaid);

        LOG.info("Remittance batch generated batchId=" + batch.getId() +
            " items=" + paymentIds.size() + " totalPaid=" + totalPaid);
        return batch;
    }

    /**
     * Generates printable HTML for all items in a batch grouped by provider.
     */
    public String buildHtml(int batchId) {
        RemittanceBatch batch = remittanceBatchDAO.findById(batchId);
        if (batch == null) {
            throw new ServiceException("Remittance batch not found: id=" + batchId);
        }
        List<RemittanceBatchItem> items = remittanceBatchDAO.findItemsByBatchId(batchId);

        // Group by provider
        Map<Integer, List<RemittanceBatchItem>> byProvider = new HashMap<Integer, List<RemittanceBatchItem>>();
        for (RemittanceBatchItem it : items) {
            if (!byProvider.containsKey(it.getProviderId())) {
                byProvider.put(it.getProviderId(), new java.util.ArrayList<RemittanceBatchItem>());
            }
            byProvider.get(it.getProviderId()).add(it);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><body style='font-family:sans-serif'>");
        sb.append("<h2>Remittance Advice — Batch #").append(batchId).append("</h2>");
        sb.append("<p>Payment Date: ").append(batch.getPaymentDate()).append("</p>");

        for (Map.Entry<Integer, List<RemittanceBatchItem>> entry : byProvider.entrySet()) {
            sb.append("<h3>Provider ID: ").append(entry.getKey()).append("</h3>");
            sb.append("<table border='1' cellpadding='4' style='border-collapse:collapse;width:100%;margin-bottom:16px'>");
            sb.append("<thead><tr><th>Claim</th><th>Billed</th><th>Allowed</th><th>Plan Paid</th><th>CARC</th></tr></thead><tbody>");
            BigDecimal providerTotal = Money.ZERO;
            for (RemittanceBatchItem it : entry.getValue()) {
                sb.append("<tr>")
                  .append("<td>").append(it.getClaimId()).append("</td>")
                  .append("<td>$").append(it.getBilled()).append("</td>")
                  .append("<td>$").append(it.getAllowed()).append("</td>")
                  .append("<td>$").append(it.getPlanPaid()).append("</td>")
                  .append("<td>").append(HtmlUtil.escape(it.getAdjustmentReasonCode())).append("</td>")
                  .append("</tr>");
                providerTotal = Money.add(providerTotal, it.getPlanPaid());
            }
            sb.append("<tr style='font-weight:bold'><td>Provider Total</td><td></td><td></td><td>$")
              .append(providerTotal).append("</td><td></td></tr>");
            sb.append("</tbody></table>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    public RemittanceBatch findBatchById(int id) {
        return remittanceBatchDAO.findById(id);
    }

    public List<RemittanceBatch> findAllBatches() {
        return remittanceBatchDAO.findAll();
    }

    public void markBatchSent(int batchId) {
        remittanceBatchDAO.markSent(batchId);
    }

    public List<RemittanceBatchItem> findItemsByBatchId(int batchId) {
        return remittanceBatchDAO.findItemsByBatchId(batchId);
    }
}
