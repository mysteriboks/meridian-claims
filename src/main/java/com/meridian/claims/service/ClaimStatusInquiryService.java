package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.EdiTransactionDAO;
import com.meridian.claims.intake.ClaimStatusInquiry;
import com.meridian.claims.intake.IntakeParseException;
import com.meridian.claims.intake.X12Edi276Parser;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.EdiTransaction;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.Provider;
import com.meridian.claims.model.User;
import com.meridian.claims.util.DateUtil;
import com.meridian.claims.util.LogMaskUtil;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Orchestrates inbound X12 276 claim status inquiry processing (Phase 14),
 * mirroring {@link IntakeService}'s shape: parse, resolve, generate a
 * response, log to {@code edi_transactions} — but a status inquiry is a
 * read-only query, not a claim submission, so it never touches
 * {@link ClaimService#submit}.
 *
 * Every inquiry gets a 277 response, even when the claim can't be found — an
 * "unknown claim" response (STC A4/1) is the correct answer, not a failure.
 */
@Service
public class ClaimStatusInquiryService {

    private static final Logger LOG = Logger.getLogger(ClaimStatusInquiryService.class);

    private final X12Edi276Parser parser;
    private final ClaimDAO claimDAO;
    private final MemberService memberService;
    private final ProviderService providerService;
    private final Edi277Generator edi277Generator;
    private final EdiTransactionDAO ediTransactionDAO;
    private final AuditService auditService;

    @Autowired
    public ClaimStatusInquiryService(X12Edi276Parser parser, ClaimDAO claimDAO,
                                      MemberService memberService, ProviderService providerService,
                                      Edi277Generator edi277Generator, EdiTransactionDAO ediTransactionDAO,
                                      AuditService auditService) {
        this.parser = parser;
        this.claimDAO = claimDAO;
        this.memberService = memberService;
        this.providerService = providerService;
        this.edi277Generator = edi277Generator;
        this.ediTransactionDAO = ediTransactionDAO;
        this.auditService = auditService;
    }

    /**
     * Processes one inbound 276 file: parses every inquiry transaction,
     * resolves each against {@code claims}, generates the 277 response, and
     * logs both the inbound 276 and the outbound 277 to {@code edi_transactions}
     * (the outbound row's {@code detail} carries the generated EDI text, which
     * the caller — either poller — retrieves via
     * {@link EdiTransactionDAO#findByFileReference} to deliver, exactly like
     * the 837 ack flow).
     *
     * @return true if the file was at least structurally parseable (a 277 was generated);
     *         false only on a file-level parse failure (no 277 possible)
     */
    public boolean processFile(String fileName, String fileContent, Integer tradingPartnerId) {
        List<ClaimStatusInquiry> inquiries;
        try {
            inquiries = parser.parse(fileContent);
        } catch (IntakeParseException e) {
            LOG.error("ClaimStatusInquiryService: file-level parse failure fileName=" + fileName
                + " reason=" + e.getMessage());
            auditService.record("STATUS_INQUIRY_FILE_FAILED", "EdiTransaction", null,
                "276 file " + fileName + " could not be parsed: " + e.getMessage());
            return false;
        }

        String isaControlNumber = inquiries.isEmpty() ? null : inquiries.get(0).getIsaControlNumber();
        String gsControlNumber = inquiries.isEmpty() ? null : inquiries.get(0).getGsControlNumber();

        List<Edi277Generator.ClaimStatusResult> results = new ArrayList<Edi277Generator.ClaimStatusResult>();
        for (ClaimStatusInquiry inquiry : inquiries) {
            results.add(resolve(inquiry));
        }

        String edi277 = edi277Generator.generate277(isaControlNumber, results);

        EdiTransaction inbound = new EdiTransaction();
        inbound.setDirection(EdiTransaction.DIRECTION_INBOUND);
        inbound.setTransactionType("276");
        inbound.setIsaControlNumber(isaControlNumber);
        inbound.setGsControlNumber(gsControlNumber);
        inbound.setStatus(EdiTransaction.STATUS_ACCEPTED);
        inbound.setFileReference(fileName);
        inbound.setTradingPartnerId(tradingPartnerId);
        int inboundId = ediTransactionDAO.insert(inbound);

        EdiTransaction outbound = new EdiTransaction();
        outbound.setDirection(EdiTransaction.DIRECTION_OUTBOUND);
        outbound.setTransactionType("277");
        outbound.setIsaControlNumber(isaControlNumber);
        outbound.setGsControlNumber(gsControlNumber);
        outbound.setStatus(EdiTransaction.STATUS_ACCEPTED);
        outbound.setRelatedTransactionId(inboundId);
        outbound.setFileReference(fileName);
        outbound.setDetail(edi277);
        outbound.setTradingPartnerId(tradingPartnerId);
        ediTransactionDAO.insert(outbound);

        LOG.info("ClaimStatusInquiryService: fileName=" + fileName + " inquiries=" + inquiries.size());
        return true;
    }

    private Edi277Generator.ClaimStatusResult resolve(ClaimStatusInquiry inquiry) {
        Claim claim = null;

        if (inquiry.getClaimControlNumber() != null && !inquiry.getClaimControlNumber().trim().isEmpty()) {
            claim = claimDAO.findByClaimNumber(inquiry.getClaimControlNumber().trim());
        }

        if (claim == null) {
            claim = resolveByMemberProviderDos(inquiry);
        }

        if (claim == null) {
            LOG.info("ClaimStatusInquiryService: no claim found for inquiry "
                + LogMaskUtil.maskMemberNumber("memberNumber=" + inquiry.getMemberNumber()));
            return Edi277Generator.ClaimStatusResult.notFound(inquiry.getStControlNumber());
        }
        return Edi277Generator.ClaimStatusResult.found(inquiry.getStControlNumber(), claim.getClaimNumber(), claim.getStatus());
    }

    private Claim resolveByMemberProviderDos(ClaimStatusInquiry inquiry) {
        if (inquiry.getMemberNumber() == null || inquiry.getMemberNumber().trim().isEmpty()
                || inquiry.getProviderNpi() == null || inquiry.getProviderNpi().trim().isEmpty()
                || inquiry.getDateOfServiceString() == null || inquiry.getDateOfServiceString().trim().isEmpty()) {
            return null;
        }
        Member member = memberService.findByMemberNumber(inquiry.getMemberNumber().trim());
        Provider provider = providerService.findByNpi(inquiry.getProviderNpi().trim());
        Date dos = DateUtil.parseFlexible(inquiry.getDateOfServiceString());
        if (member == null || provider == null || dos == null) {
            return null;
        }
        return claimDAO.findByMemberAndDOS(member.getId(), provider.getId(), dos);
    }

}
