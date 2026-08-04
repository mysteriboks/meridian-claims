package com.meridian.claims.service;

import com.meridian.claims.dao.EftPaymentDAO;
import com.meridian.claims.dao.ProviderDAO;
import com.meridian.claims.model.EftPayment;
import com.meridian.claims.model.PaymentBatch;
import com.meridian.claims.model.Provider;
import com.meridian.claims.model.RemittanceBatch;
import com.meridian.claims.model.RemittanceBatchItem;
import com.meridian.claims.util.AchFileWriter;
import com.meridian.claims.util.Money;
import com.meridian.claims.util.ValidationUtil;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates EFT/ACH payment issuance for a payment batch (Phase 18):
 * groups the batch's remittance items by provider, builds one NACHA credit
 * entry per provider with configured banking info (via {@link AchFileWriter}),
 * and records the result in {@code eft_payments}. Providers with no (or
 * invalid) banking info are skipped, not fatal — reported via the returned
 * {@link EftPayment}'s skipped-provider count, mirroring the per-record
 * quarantine philosophy used throughout the intake pollers.
 */
@Service
public class EftPaymentService {

    private static final Logger LOG = Logger.getLogger(EftPaymentService.class);

    private final EftPaymentDAO eftPaymentDAO;
    private final ProviderDAO providerDAO;

    @Value("${claims.eft.origin.routing-number:123456789}")
    private String originRoutingNumber;

    @Value("${claims.eft.origin.name:MERIDIAN CLAIMS}")
    private String originName;

    @Value("${claims.eft.company.id:1234567890}")
    private String companyId;

    @Value("${claims.eft.output.path:}")
    private String outputPath;

    @Autowired
    public EftPaymentService(EftPaymentDAO eftPaymentDAO, ProviderDAO providerDAO) {
        this.eftPaymentDAO = eftPaymentDAO;
        this.providerDAO = providerDAO;
    }

    /**
     * Builds the NACHA file for one payment batch's items and records the
     * {@code eft_payments} row. Returns null (and creates no row) if not a
     * single provider in the batch has usable banking info — that batch
     * stays check-paid, not ACH.
     */
    @Transactional
    public EftPayment generateForBatch(PaymentBatch paymentBatch, RemittanceBatch remittanceBatch,
                                        List<RemittanceBatchItem> items) {
        List<AchFileWriter.Entry> allEntries = buildEntries(items);
        int skipped = countSkipped(items) - allEntries.size();

        if (allEntries.isEmpty()) {
            LOG.info("EftPaymentService: no providers with banking info in payment batch id="
                + paymentBatch.getId() + " — staying check-paid (" + skipped + " skipped)");
            return null;
        }

        String trn = "EFT" + String.format("%09d", paymentBatch.getId());
        String achText = AchFileWriter.generate(originRoutingNumber, originName, companyId, originName,
            paymentBatch.getBatchDate(), trn, allEntries);

        String fileRef = persistToDisk(paymentBatch.getId(), achText);

        BigDecimal totalAchAmount = BigDecimal.ZERO;
        for (AchFileWriter.Entry entry : allEntries) {
            totalAchAmount = Money.add(totalAchAmount, entry.getAmount());
        }

        EftPayment eftPayment = new EftPayment();
        eftPayment.setPaymentBatchId(paymentBatch.getId());
        eftPayment.setRemittanceBatchId(remittanceBatch.getId());
        eftPayment.setTrnReassociationNumber(trn);
        eftPayment.setAmount(totalAchAmount);
        eftPayment.setSettlementStatus(EftPayment.STATUS_GENERATED);
        eftPayment.setAchFileReference(fileRef);
        eftPayment.setEntryCount(allEntries.size());
        eftPayment.setSkippedProviderCount(skipped);
        eftPaymentDAO.insert(eftPayment);

        LOG.info("EftPaymentService: generated ACH file paymentBatchId=" + paymentBatch.getId()
            + " entries=" + allEntries.size() + " skipped=" + skipped + " trn=" + trn);
        return eftPayment;
    }

    public EftPayment findByPaymentBatchId(int paymentBatchId) {
        return eftPaymentDAO.findByPaymentBatchId(paymentBatchId);
    }

    public EftPayment findByRemittanceBatchId(int remittanceBatchId) {
        return eftPaymentDAO.findByRemittanceBatchId(remittanceBatchId);
    }

    public List<EftPayment> findRecent(int limit) {
        return eftPaymentDAO.findRecent(limit);
    }

    @Transactional
    public void markSettled(int eftPaymentId) {
        EftPayment eftPayment = eftPaymentDAO.findById(eftPaymentId);
        if (eftPayment == null) {
            throw new ServiceException("EFT payment id=" + eftPaymentId + " not found");
        }
        eftPaymentDAO.updateSettlementStatus(eftPaymentId, EftPayment.STATUS_SETTLED);
    }

    /**
     * Regenerates the NACHA file text for a batch that already has an
     * {@code eft_payments} row — used for re-download, reusing the persisted
     * TRN reassociation number so it stays stable across regenerations.
     */
    public String regenerateAchText(PaymentBatch paymentBatch, List<RemittanceBatchItem> items, EftPayment eftPayment) {
        List<AchFileWriter.Entry> entries = buildEntries(items);
        return AchFileWriter.generate(originRoutingNumber, originName, companyId, originName,
            paymentBatch.getBatchDate(), eftPayment.getTrnReassociationNumber(), entries);
    }

    /** Groups items by provider, summing plan-paid amounts, and builds one ACH entry per provider with usable banking info. */
    private List<AchFileWriter.Entry> buildEntries(List<RemittanceBatchItem> items) {
        Map<Integer, BigDecimal> amountByProvider = new LinkedHashMap<Integer, BigDecimal>();
        for (RemittanceBatchItem item : items) {
            BigDecimal running = amountByProvider.get(item.getProviderId());
            amountByProvider.put(item.getProviderId(),
                running == null ? item.getPlanPaid() : Money.add(running, item.getPlanPaid()));
        }

        List<AchFileWriter.Entry> entries = new ArrayList<AchFileWriter.Entry>();
        for (Map.Entry<Integer, BigDecimal> providerAmount : amountByProvider.entrySet()) {
            Provider provider = providerDAO.findById(providerAmount.getKey());
            if (provider == null || !hasUsableBankingInfo(provider)) {
                continue;
            }
            entries.add(new AchFileWriter.Entry(
                provider.getAchRoutingNumber(), provider.getAchAccountNumber(), provider.getAchAccountType(),
                providerAmount.getValue(), provider.getNpi(), provider.getName()));
        }
        return entries;
    }

    /** Number of distinct providers represented in the batch's items (for the skipped-count calculation). */
    private int countSkipped(List<RemittanceBatchItem> items) {
        Map<Integer, Boolean> seen = new LinkedHashMap<Integer, Boolean>();
        for (RemittanceBatchItem item : items) {
            seen.put(item.getProviderId(), Boolean.TRUE);
        }
        return seen.size();
    }

    private boolean hasUsableBankingInfo(Provider provider) {
        return ValidationUtil.achRoutingNumber(provider.getAchRoutingNumber())
            && ValidationUtil.required(provider.getAchAccountNumber())
            && ("CHECKING".equals(provider.getAchAccountType()) || "SAVINGS".equals(provider.getAchAccountType()));
    }

    private String persistToDisk(int paymentBatchId, String achText) {
        if (outputPath == null || outputPath.trim().isEmpty()) {
            return null;
        }
        String filename = outputPath + "/eft-" + paymentBatchId + ".ach";
        try {
            Files.write(Paths.get(filename), achText.getBytes("UTF-8"));
            LOG.info("ACH file written to " + filename);
            return filename;
        } catch (IOException e) {
            LOG.error("Could not write ACH file for payment batch id=" + paymentBatchId, e);
            return null;
        }
    }
}
