package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.ClaimLineItemDAO;
import com.meridian.claims.dao.EobDocumentDAO;
import com.meridian.claims.dao.MemberDAO;
import com.meridian.claims.dao.ProviderDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.model.EobDocument;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.Provider;
import com.meridian.claims.util.HtmlUtil;
import com.meridian.claims.util.Money;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Generates Explanation of Benefit documents on claim APPROVED or DENIED.
 * Content is HTML stored in eob_documents. Email is attempted if member has an email address.
 */
@Service
public class EobService {

    private static final Logger LOG = Logger.getLogger(EobService.class);

    @Autowired private ClaimDAO claimDAO;
    @Autowired private ClaimLineItemDAO lineItemDAO;
    @Autowired private MemberDAO memberDAO;
    @Autowired private ProviderDAO providerDAO;
    @Autowired private EobDocumentDAO eobDocumentDAO;
    @Autowired private MailService mailService;

    public EobDocument findById(int id) {
        return eobDocumentDAO.findById(id);
    }

    public List<EobDocument> findByMemberId(int memberId) {
        return eobDocumentDAO.findByMemberId(memberId);
    }

    public void markMailed(int id, int mailedByUserId) {
        eobDocumentDAO.markMailed(id, mailedByUserId);
    }

    @Transactional
    public EobDocument generate(int claimId) {
        Claim claim = claimDAO.findById(claimId);
        if (claim == null) {
            throw new ServiceException("Cannot generate EOB — claim not found: id=" + claimId);
        }
        // Idempotency guard: never generate a second EOB for the same claim.
        EobDocument existing = eobDocumentDAO.findByClaimId(claimId);
        if (existing != null) {
            LOG.warn("EOB already exists for claimId=" + claimId + " eobId=" + existing.getId());
            return existing;
        }
        Member member = memberDAO.findById(claim.getMemberId());
        Provider provider = providerDAO.findById(claim.getProviderId());
        List<ClaimLineItem> lineItems = lineItemDAO.findByClaimId(claimId);

        String content = buildHtml(claim, member, provider, lineItems);

        EobDocument doc = new EobDocument();
        doc.setClaimId(claimId);
        doc.setMemberId(claim.getMemberId());
        doc.setContent(content);
        eobDocumentDAO.insert(doc);

        // Email if member has an address on record
        if (member != null && member.getEmail() != null && !member.getEmail().trim().isEmpty()) {
            try {
                String subject = "Explanation of Benefits — Claim " + claim.getClaimNumber();
                mailService.send(member.getEmail().trim(), subject, content);
                LOG.info("EOB emailed to member id=" + claim.getMemberId() + " claimId=" + claimId);
            } catch (Exception e) {
                LOG.warn("EOB email failed claimId=" + claimId, e);
            }
        }

        LOG.info("EOB generated claimId=" + claimId + " eobId=" + doc.getId());
        return doc;
    }

    private String buildHtml(Claim claim, Member member, Provider provider,
                              List<ClaimLineItem> lineItems) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><body style='font-family:sans-serif;max-width:700px;margin:auto'>");
        sb.append("<h2>Explanation of Benefits</h2>");
        sb.append("<p><strong>Claim #:</strong> ").append(esc(claim.getClaimNumber())).append("</p>");
        sb.append("<p><strong>Status:</strong> ").append(esc(claim.getStatus().name())).append("</p>");

        if (member != null) {
            sb.append("<p><strong>Member:</strong> ")
              .append(esc(member.getFirstName())).append(" ").append(esc(member.getLastName()))
              .append(" (").append(esc(member.getMemberNumber())).append(")</p>");
        }
        if (provider != null) {
            sb.append("<p><strong>Provider:</strong> ").append(esc(provider.getName()))
              .append(" NPI: ").append(esc(provider.getNpi())).append("</p>");
        }
        sb.append("<p><strong>Date of Service:</strong> ").append(claim.getDateOfService()).append("</p>");

        if (claim.getDenialReasonCode() != null) {
            sb.append("<p style='color:red'><strong>Denial Reason:</strong> ")
              .append(esc(claim.getDenialReasonCode())).append("</p>");
        }

        // Line items table
        sb.append("<table border='1' cellpadding='4' style='border-collapse:collapse;width:100%'>");
        sb.append("<thead><tr><th>Procedure</th><th>Billed</th><th>Allowed</th><th>Plan Paid</th><th>Member Resp.</th></tr></thead>");
        sb.append("<tbody>");

        BigDecimal totalBilled = Money.ZERO;
        BigDecimal totalAllowed = Money.ZERO;
        BigDecimal totalPlan = Money.ZERO;
        BigDecimal totalMember = Money.ZERO;

        for (ClaimLineItem li : lineItems) {
            BigDecimal billed   = li.getBilledAmount() != null ? li.getBilledAmount() : Money.ZERO;
            BigDecimal allowed  = li.getAllowedAmount() != null ? li.getAllowedAmount() : Money.ZERO;
            BigDecimal plan     = li.getPlanPaidAmount() != null ? li.getPlanPaidAmount() : Money.ZERO;
            BigDecimal memberResp = li.getMemberResponsibility() != null ? li.getMemberResponsibility() : Money.ZERO;
            totalBilled  = Money.add(totalBilled, billed);
            totalAllowed = Money.add(totalAllowed, allowed);
            totalPlan    = Money.add(totalPlan, plan);
            totalMember  = Money.add(totalMember, memberResp);
            sb.append("<tr>")
              .append("<td>").append(esc(li.getProcedureCode())).append("</td>")
              .append("<td>$").append(billed).append("</td>")
              .append("<td>$").append(allowed).append("</td>")
              .append("<td>$").append(plan).append("</td>")
              .append("<td>$").append(memberResp).append("</td>")
              .append("</tr>");
        }
        sb.append("<tr style='font-weight:bold'>")
          .append("<td>TOTAL</td>")
          .append("<td>$").append(totalBilled).append("</td>")
          .append("<td>$").append(totalAllowed).append("</td>")
          .append("<td>$").append(totalPlan).append("</td>")
          .append("<td>$").append(totalMember).append("</td>")
          .append("</tr>");
        sb.append("</tbody></table>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private String esc(String s) {
        return HtmlUtil.escape(s);
    }
}
