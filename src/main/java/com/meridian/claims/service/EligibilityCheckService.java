package com.meridian.claims.service;

import com.meridian.claims.dao.EligibilityCheckDAO;
import com.meridian.claims.intake.EligibilityResponse;
import com.meridian.claims.intake.IntakeParseException;
import com.meridian.claims.intake.X12Edi271Parser;
import com.meridian.claims.model.EligibilityCheck;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.Provider;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * Orchestrates a real-time eligibility check (Phase 16): builds the outbound
 * 270 request, round-trips it through {@link EligibilityClient}, parses the
 * 271 response, and persists the result to {@code eligibility_checks} —
 * triggered by the "Check Eligibility" action on the member screen, not by
 * an inbound file poller (Meridian is the requester here, the reverse of
 * the 276/278 flows).
 */
@Service
public class EligibilityCheckService {

    private static final Logger LOG = Logger.getLogger(EligibilityCheckService.class);

    private final Edi270Generator edi270Generator;
    private final X12Edi271Parser edi271Parser;
    private final EligibilityClient eligibilityClient;
    private final EligibilityCheckDAO eligibilityCheckDAO;
    private final MemberService memberService;
    private final ProviderService providerService;
    private final AuditService auditService;

    @Autowired
    public EligibilityCheckService(Edi270Generator edi270Generator, X12Edi271Parser edi271Parser,
                                    EligibilityClient eligibilityClient, EligibilityCheckDAO eligibilityCheckDAO,
                                    MemberService memberService, ProviderService providerService,
                                    AuditService auditService) {
        this.edi270Generator = edi270Generator;
        this.edi271Parser = edi271Parser;
        this.eligibilityClient = eligibilityClient;
        this.eligibilityCheckDAO = eligibilityCheckDAO;
        this.memberService = memberService;
        this.providerService = providerService;
        this.auditService = auditService;
    }

    public EligibilityCheck checkEligibility(int memberId, Integer providerId, String serviceType, Integer currentUserId) {
        Member member = memberService.findById(memberId);
        if (member == null) {
            throw new ServiceException("Member not found: " + memberId);
        }
        Provider provider = providerId != null ? providerService.findById(providerId) : null;

        EligibilityCheck check = new EligibilityCheck();
        check.setMemberId(memberId);
        check.setProviderId(providerId);
        check.setServiceType(serviceType);
        check.setInquiryAt(new Date());
        check.setCheckedByUserId(currentUserId);

        try {
            String request270 = edi270Generator.generate270(member, provider, serviceType);
            String response271 = eligibilityClient.checkEligibility(member, provider, serviceType, request270);
            List<EligibilityResponse> parsed = edi271Parser.parse(response271);
            if (parsed.isEmpty()) {
                check.setResultStatus(EligibilityCheck.STATUS_ERROR);
                check.setCoverageSnapshot("Eligibility response contained no transaction");
            } else {
                EligibilityResponse resp = parsed.get(0);
                check.setResultStatus(mapStatus(resp.getEb01Code()));
                check.setCoverageSnapshot(resp.getPlanDescription());
            }
        } catch (EligibilityClientException e) {
            LOG.error("Eligibility check failed memberId=" + memberId, e);
            check.setResultStatus(EligibilityCheck.STATUS_ERROR);
            check.setCoverageSnapshot("Eligibility check failed: " + e.getMessage());
        } catch (IntakeParseException e) {
            LOG.error("Eligibility response could not be parsed memberId=" + memberId, e);
            check.setResultStatus(EligibilityCheck.STATUS_ERROR);
            check.setCoverageSnapshot("Eligibility response could not be parsed: " + e.getMessage());
        }

        check.setResponseAt(new Date());
        eligibilityCheckDAO.insert(check);
        auditService.record("ELIGIBILITY_CHECK", "Member", (long) memberId,
            "Eligibility check result=" + check.getResultStatus());
        return check;
    }

    /** Most recent eligibility checks for a member, newest first — for the member-detail history panel. */
    public List<EligibilityCheck> findRecentByMember(int memberId, int limit) {
        return eligibilityCheckDAO.findByMemberId(memberId, limit);
    }

    private String mapStatus(String eb01Code) {
        if ("1".equals(eb01Code)) {
            return EligibilityCheck.STATUS_ACTIVE;
        }
        if ("6".equals(eb01Code) || "8".equals(eb01Code)) {
            return EligibilityCheck.STATUS_INACTIVE;
        }
        return EligibilityCheck.STATUS_ERROR;
    }
}
