package com.meridian.claims.service;

import com.meridian.claims.dao.MemberCoverageDAO;
import com.meridian.claims.dao.MemberDAO;
import com.meridian.claims.model.CoverageOrder;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberCoverage;
import com.meridian.claims.model.MemberStatus;
import com.meridian.claims.util.LogMaskUtil;
import com.meridian.claims.util.Page;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class MemberService {

    private static final Logger LOG = Logger.getLogger(MemberService.class);

    private final MemberDAO memberDAO;
    private final MemberCoverageDAO memberCoverageDAO;
    private final AuditService auditService;

    @Autowired
    public MemberService(MemberDAO memberDAO, MemberCoverageDAO memberCoverageDAO,
                          AuditService auditService) {
        this.memberDAO = memberDAO;
        this.memberCoverageDAO = memberCoverageDAO;
        this.auditService = auditService;
    }

    public Member findById(int id) {
        Member m = memberDAO.findById(id);
        if (m == null) {
            throw new ServiceException("Member id=" + id + " not found");
        }
        return m;
    }

    public Member findByMemberNumber(String memberNumber) {
        return memberDAO.findByMemberNumber(memberNumber);
    }

    public Page<Member> search(String query, int pageNumber, int pageSize) {
        int page = pageNumber < 1 ? 1 : pageNumber;
        int size = pageSize < 1 ? 20 : pageSize;
        return memberDAO.search(query, page, size);
    }

    public List<Member> listAllActive() {
        return memberDAO.findAllActive();
    }

    @Transactional
    public Member createMember(String memberNumber, String firstName, String lastName,
                                Date dob, String address, String phone, String email) {
        if (memberNumber == null || memberNumber.trim().isEmpty()) {
            throw new ServiceException("Member number is required");
        }
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new ServiceException("First name is required");
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            throw new ServiceException("Last name is required");
        }
        if (dob == null) {
            throw new ServiceException("Date of birth is required");
        }
        if (memberDAO.findByMemberNumber(memberNumber.trim()) != null) {
            throw new ServiceException("Member number " + memberNumber + " is already in use");
        }
        Member m = new Member();
        m.setMemberNumber(memberNumber.trim());
        m.setFirstName(firstName.trim());
        m.setLastName(lastName.trim());
        m.setDob(dob);
        m.setAddress(address);
        m.setPhone(phone);
        m.setEmail(email);
        m.setStatus(MemberStatus.ACTIVE);
        memberDAO.insert(m);
        LOG.info("Created member id=" + m.getId() + " memberNumber=" + LogMaskUtil.maskMemberNumber(m.getMemberNumber()));
        auditService.record("MEMBER_CREATED", "MEMBER", (long) m.getId(),
            "Created member " + m.getMemberNumber());
        return m;
    }

    @Transactional
    public void updateMember(int id, String firstName, String lastName,
                              Date dob, String address, String phone, String email, String status) {
        Member m = findById(id);
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new ServiceException("First name is required");
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            throw new ServiceException("Last name is required");
        }
        m.setFirstName(firstName.trim());
        m.setLastName(lastName.trim());
        m.setDob(dob);
        m.setAddress(address);
        m.setPhone(phone);
        m.setEmail(email);
        if (status != null) {
            m.setStatus(MemberStatus.valueOf(status));
        }
        memberDAO.update(m);
    }

    @Transactional
    public void deactivateMember(int id) {
        Member m = findById(id);
        memberDAO.softDelete(id);
        LOG.info("Deactivated (soft-deleted) member id=" + id);
        auditService.record("MEMBER_DEACTIVATED", "MEMBER", (long) id,
            "Deactivated member " + m.getMemberNumber());
    }

    // --- Coverage ---

    public List<MemberCoverage> getCoverageRecords(int memberId) {
        return memberCoverageDAO.findByMemberId(memberId);
    }

    public List<MemberCoverage> getActiveCoverage(int memberId) {
        return memberCoverageDAO.findActiveByMemberId(memberId);
    }

    @Transactional
    public void addCoverage(int memberId, int planId, String coverageOrder,
                             Date effectiveDate, Date terminationDate) {
        findById(memberId);
        CoverageOrder order = parseCoverageOrder(coverageOrder);
        validateCoverage(memberId, 0, order, effectiveDate, terminationDate);
        MemberCoverage c = new MemberCoverage();
        c.setMemberId(memberId);
        c.setPlanId(planId);
        c.setCoverageOrder(order);
        c.setEffectiveDate(effectiveDate);
        c.setTerminationDate(terminationDate);
        memberCoverageDAO.insert(c);
        auditService.record("COVERAGE_ADDED", "MEMBER", (long) memberId,
            "Added " + order + " coverage (plan id=" + planId + ")");
    }

    @Transactional
    public void updateCoverage(int coverageId, int planId, String coverageOrder,
                                Date effectiveDate, Date terminationDate) {
        MemberCoverage c = memberCoverageDAO.findById(coverageId);
        if (c == null) {
            throw new ServiceException("Coverage record id=" + coverageId + " not found");
        }
        CoverageOrder order = parseCoverageOrder(coverageOrder);
        validateCoverage(c.getMemberId(), coverageId, order, effectiveDate, terminationDate);
        c.setPlanId(planId);
        c.setCoverageOrder(order);
        c.setEffectiveDate(effectiveDate);
        c.setTerminationDate(terminationDate);
        memberCoverageDAO.update(c);
    }

    private CoverageOrder parseCoverageOrder(String coverageOrder) {
        if (coverageOrder == null || coverageOrder.trim().isEmpty()) {
            throw new ServiceException("Coverage order is required");
        }
        try {
            return CoverageOrder.valueOf(coverageOrder.trim());
        } catch (IllegalArgumentException e) {
            throw new ServiceException("Invalid coverage order: " + coverageOrder);
        }
    }

    /**
     * Enforces COB rules for a coverage record being added or edited:
     *   - effective date required; termination (if present) must be >= effective
     *   - at most one PRIMARY whose date window overlaps another PRIMARY
     *   - a SECONDARY must have a PRIMARY whose window overlaps it
     * The record identified by excludeCoverageId (0 = none) is skipped so an
     * edit does not conflict with itself.
     */
    private void validateCoverage(int memberId, int excludeCoverageId, CoverageOrder order,
                                   Date effectiveDate, Date terminationDate) {
        if (effectiveDate == null) {
            throw new ServiceException("Effective date is required");
        }
        if (terminationDate != null && terminationDate.before(effectiveDate)) {
            throw new ServiceException("Termination date must be on or after the effective date");
        }

        List<MemberCoverage> existing = memberCoverageDAO.findByMemberId(memberId);

        if (order == CoverageOrder.PRIMARY) {
            for (int i = 0; i < existing.size(); i++) {
                MemberCoverage other = existing.get(i);
                if (other.getId() == excludeCoverageId) {
                    continue;
                }
                if (other.getCoverageOrder() == CoverageOrder.PRIMARY
                        && windowsOverlap(effectiveDate, terminationDate,
                                          other.getEffectiveDate(), other.getTerminationDate())) {
                    throw new ServiceException(
                        "This member already has a PRIMARY coverage that overlaps the given dates");
                }
            }
        } else { // SECONDARY
            boolean hasOverlappingPrimary = false;
            for (int i = 0; i < existing.size(); i++) {
                MemberCoverage other = existing.get(i);
                if (other.getId() == excludeCoverageId) {
                    continue;
                }
                if (other.getCoverageOrder() == CoverageOrder.PRIMARY
                        && windowsOverlap(effectiveDate, terminationDate,
                                          other.getEffectiveDate(), other.getTerminationDate())) {
                    hasOverlappingPrimary = true;
                    break;
                }
            }
            if (!hasOverlappingPrimary) {
                throw new ServiceException(
                    "A SECONDARY coverage requires an active PRIMARY coverage for the same period");
            }
        }
    }

    /**
     * True if two date windows overlap. A null termination date means open-ended.
     */
    private boolean windowsOverlap(Date startA, Date endA, Date startB, Date endB) {
        // A starts after B ends -> no overlap
        if (endB != null && startA.after(endB)) {
            return false;
        }
        // B starts after A ends -> no overlap
        if (endA != null && startB.after(endA)) {
            return false;
        }
        return true;
    }

    @Transactional
    public void removeCoverage(int coverageId) {
        MemberCoverage c = memberCoverageDAO.findById(coverageId);
        memberCoverageDAO.delete(coverageId);
        if (c != null) {
            auditService.record("COVERAGE_REMOVED", "MEMBER", (long) c.getMemberId(),
                "Removed " + c.getCoverageOrder() + " coverage id=" + coverageId);
        }
    }

    public MemberCoverage findEligibleCoverage(int memberId, String coverageOrder, Date dateOfService) {
        return memberCoverageDAO.findByMemberAndDate(memberId, coverageOrder, dateOfService);
    }
}
