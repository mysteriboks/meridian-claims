package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.ClaimLineItemDAO;
import com.meridian.claims.dao.MemberDAO;
import com.meridian.claims.dao.PaymentDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.Payment;
import com.meridian.claims.util.CsvWriter;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Exports all claims and payments for a given member as CSV.
 * Uses ClaimDAO.findByMemberId for an efficient single-query scan.
 */
@Service
public class MemberExportService {

    private static final Logger LOG = Logger.getLogger(MemberExportService.class);

    @Autowired private MemberDAO memberDAO;
    @Autowired private ClaimDAO claimDAO;
    @Autowired private ClaimLineItemDAO claimLineItemDAO;
    @Autowired private PaymentDAO paymentDAO;

    /** Returns a CSV of all claims + line items for the member. */
    public String exportClaimsCsv(int memberId) {
        requireMember(memberId);
        CsvWriter csv = new CsvWriter();
        csv.header("ClaimNumber", "ClaimType", "DateOfService", "Status",
                   "ProcedureCode", "BilledAmount", "AllowedAmount", "PlanPaid", "MemberResp");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        List<Claim> claims = claimDAO.findByMemberId(memberId);
        for (Claim claim : claims) {
            List<ClaimLineItem> items = claimLineItemDAO.findByClaimId(claim.getId());
            if (items.isEmpty()) {
                csv.row(claim.getClaimNumber(), claim.getClaimType().name(),
                    sdf.format(claim.getDateOfService()), claim.getStatus().name(),
                    "", "", "", "", "");
            } else {
                for (ClaimLineItem li : items) {
                    csv.row(
                        claim.getClaimNumber(),
                        claim.getClaimType().name(),
                        sdf.format(claim.getDateOfService()),
                        claim.getStatus().name(),
                        li.getProcedureCode(),
                        li.getBilledAmount() != null ? li.getBilledAmount().toPlainString() : "",
                        li.getAllowedAmount() != null ? li.getAllowedAmount().toPlainString() : "",
                        li.getPlanPaidAmount() != null ? li.getPlanPaidAmount().toPlainString() : "",
                        li.getMemberResponsibility() != null ? li.getMemberResponsibility().toPlainString() : ""
                    );
                }
            }
        }
        LOG.info("Member claims export generated memberId=" + memberId + " claims=" + claims.size());
        return csv.build();
    }

    /** Returns a CSV of all payments for the member. */
    public String exportPaymentsCsv(int memberId) {
        requireMember(memberId);
        CsvWriter csv = new CsvWriter();
        csv.header("PaymentId", "ClaimNumber", "Status", "BilledTotal",
                   "AllowedTotal", "PlanPaidTotal", "MemberResponsibility", "PaymentDate");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        List<Claim> claims = claimDAO.findByMemberId(memberId);
        for (Claim claim : claims) {
            Payment p = paymentDAO.findByClaimId(claim.getId());
            if (p != null) {
                csv.row(
                    String.valueOf(p.getId()),
                    claim.getClaimNumber(),
                    p.getStatus(),
                    p.getBilledTotal().toPlainString(),
                    p.getAllowedTotal().toPlainString(),
                    p.getPlanPaidTotal().toPlainString(),
                    p.getMemberResponsibility().toPlainString(),
                    p.getPaymentDate() != null ? sdf.format(p.getPaymentDate()) : ""
                );
            }
        }
        return csv.build();
    }

    private void requireMember(int memberId) {
        Member member = memberDAO.findById(memberId);
        if (member == null) {
            throw new ServiceException("Member not found: id=" + memberId);
        }
    }
}
