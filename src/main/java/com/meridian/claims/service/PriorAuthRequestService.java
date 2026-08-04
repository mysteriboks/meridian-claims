package com.meridian.claims.service;

import com.meridian.claims.dao.EdiTransactionDAO;
import com.meridian.claims.intake.IntakeParseException;
import com.meridian.claims.intake.PriorAuthRequest;
import com.meridian.claims.intake.X12Edi278Parser;
import com.meridian.claims.model.EdiTransaction;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.PriorAuthorization;
import com.meridian.claims.model.Provider;
import com.meridian.claims.util.DateUtil;
import com.meridian.claims.util.ValidationUtil;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Orchestrates inbound X12 278 prior-authorization request processing
 * (Phase 15), mirroring {@link ClaimStatusInquiryService}'s shape: parse,
 * resolve/validate, act, respond, log — but here "act" means creating a real
 * {@code prior_authorizations} row via the existing
 * {@link PriorAuthorizationService#createAuthorization}, not just answering a
 * read-only query.
 *
 * The domain's {@code PriorAuthStatus} (ACTIVE/EXPIRED/VOIDED) has no pended
 * state, so a request is either certified (creates an ACTIVE authorization) or
 * not certified (no row is created) — straight-through, matching the
 * auto-adjudication spirit of claim intake, with no third "pended for review"
 * outcome to route anywhere.
 */
@Service
public class PriorAuthRequestService {

    private static final Logger LOG = Logger.getLogger(PriorAuthRequestService.class);

    private final X12Edi278Parser parser;
    private final MemberService memberService;
    private final ProviderService providerService;
    private final PriorAuthorizationService priorAuthorizationService;
    private final Edi278ResponseGenerator edi278ResponseGenerator;
    private final EdiTransactionDAO ediTransactionDAO;
    private final AuditService auditService;

    @Autowired
    public PriorAuthRequestService(X12Edi278Parser parser, MemberService memberService,
                                    ProviderService providerService,
                                    PriorAuthorizationService priorAuthorizationService,
                                    Edi278ResponseGenerator edi278ResponseGenerator,
                                    EdiTransactionDAO ediTransactionDAO, AuditService auditService) {
        this.parser = parser;
        this.memberService = memberService;
        this.providerService = providerService;
        this.priorAuthorizationService = priorAuthorizationService;
        this.edi278ResponseGenerator = edi278ResponseGenerator;
        this.ediTransactionDAO = ediTransactionDAO;
        this.auditService = auditService;
    }

    /**
     * Processes one inbound 278 file: parses every request transaction, validates
     * and resolves each against members/providers, creates a real prior
     * authorization for anything that certifies, generates the 278 response, and
     * logs both the inbound 278 and outbound response to {@code edi_transactions}
     * (retrievable via {@link EdiTransactionDAO#findByFileReference} for delivery,
     * exactly like the 837 ack flow and the 276/277 flow).
     *
     * @return true if the file was at least structurally parseable; false only on
     *         a file-level parse failure (no response possible)
     */
    public boolean processFile(String fileName, String fileContent, Integer tradingPartnerId) {
        List<PriorAuthRequest> parsedRequests;
        try {
            parsedRequests = parser.parse(fileContent);
        } catch (IntakeParseException e) {
            LOG.error("PriorAuthRequestService: file-level parse failure fileName=" + fileName
                + " reason=" + e.getMessage());
            auditService.record("PRIOR_AUTH_REQUEST_FILE_FAILED", "EdiTransaction", null,
                "278 file " + fileName + " could not be parsed: " + e.getMessage());
            return false;
        }

        String isaControlNumber = parsedRequests.isEmpty() ? null : parsedRequests.get(0).getIsaControlNumber();
        String gsControlNumber = parsedRequests.isEmpty() ? null : parsedRequests.get(0).getGsControlNumber();

        List<Edi278ResponseGenerator.PriorAuthResult> results = new ArrayList<Edi278ResponseGenerator.PriorAuthResult>();
        for (PriorAuthRequest request : parsedRequests) {
            results.add(resolve(request));
        }

        String edi278Response = edi278ResponseGenerator.generate278Response(isaControlNumber, results);

        EdiTransaction inbound = new EdiTransaction();
        inbound.setDirection(EdiTransaction.DIRECTION_INBOUND);
        inbound.setTransactionType("278");
        inbound.setIsaControlNumber(isaControlNumber);
        inbound.setGsControlNumber(gsControlNumber);
        inbound.setStatus(EdiTransaction.STATUS_ACCEPTED);
        inbound.setFileReference(fileName);
        inbound.setTradingPartnerId(tradingPartnerId);
        int inboundId = ediTransactionDAO.insert(inbound);

        EdiTransaction outbound = new EdiTransaction();
        outbound.setDirection(EdiTransaction.DIRECTION_OUTBOUND);
        outbound.setTransactionType("278");
        outbound.setIsaControlNumber(isaControlNumber);
        outbound.setGsControlNumber(gsControlNumber);
        outbound.setStatus(EdiTransaction.STATUS_ACCEPTED);
        outbound.setRelatedTransactionId(inboundId);
        outbound.setFileReference(fileName);
        outbound.setDetail(edi278Response);
        outbound.setTradingPartnerId(tradingPartnerId);
        ediTransactionDAO.insert(outbound);

        LOG.info("PriorAuthRequestService: fileName=" + fileName + " requests=" + parsedRequests.size());
        return true;
    }

    private Edi278ResponseGenerator.PriorAuthResult resolve(PriorAuthRequest request) {
        String validationError = validate(request);
        if (validationError != null) {
            return Edi278ResponseGenerator.PriorAuthResult.denied(request.getStControlNumber(), validationError);
        }

        Member member = memberService.findByMemberNumber(request.getMemberNumber().trim());
        if (member == null) {
            return Edi278ResponseGenerator.PriorAuthResult.denied(request.getStControlNumber(), "member not found");
        }
        Provider provider = providerService.findByNpi(request.getProviderNpi().trim());
        if (provider == null) {
            return Edi278ResponseGenerator.PriorAuthResult.denied(request.getStControlNumber(), "provider not found");
        }
        Date authorizedFrom = DateUtil.parseFlexible(request.getAuthorizedFromString());
        Date authorizedTo = DateUtil.parseFlexible(request.getAuthorizedToString());
        if (authorizedFrom == null || authorizedTo == null) {
            return Edi278ResponseGenerator.PriorAuthResult.denied(request.getStControlNumber(),
                "invalid or missing requested certification period");
        }

        try {
            PriorAuthorization auth = priorAuthorizationService.createAuthorization(
                member.getId(), provider.getId(), request.getProcedureCode().trim(),
                request.getServiceType(), authorizedFrom, authorizedTo,
                request.getRequestedUnits() != null ? request.getRequestedUnits() : 1,
                "Created from inbound X12 278 request");
            return Edi278ResponseGenerator.PriorAuthResult.approved(request.getStControlNumber(), auth.getAuthNumber());
        } catch (ServiceException e) {
            return Edi278ResponseGenerator.PriorAuthResult.denied(request.getStControlNumber(), e.getMessage());
        }
    }

    private String validate(PriorAuthRequest request) {
        if (request.getMemberNumber() == null || request.getMemberNumber().trim().isEmpty()) {
            return "member number is missing (no NM1*IL segment)";
        }
        if (request.getProviderNpi() == null || request.getProviderNpi().trim().isEmpty()) {
            return "provider NPI is missing (no NM1*1P segment)";
        }
        String npiError = ValidationUtil.validateNpi(request.getProviderNpi().trim());
        if (npiError != null) {
            return npiError;
        }
        if (request.getProcedureCode() == null || request.getProcedureCode().trim().isEmpty()) {
            return "procedure code is missing (no SV1 segment)";
        }
        String cptError = ValidationUtil.validateProcedureCode(request.getProcedureCode().trim());
        if (cptError != null) {
            return cptError;
        }
        return null;
    }

}
