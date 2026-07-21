package com.meridian.claims.dao;

import com.meridian.claims.model.MemberCoverage;

import java.util.Date;
import java.util.List;

public interface MemberCoverageDAO {

    MemberCoverage findById(int id);

    List<MemberCoverage> findByMemberId(int memberId);

    List<MemberCoverage> findActiveByMemberId(int memberId);

    MemberCoverage findByMemberAndDate(int memberId, String coverageOrder, Date dateOfService);

    void insert(MemberCoverage coverage);

    void update(MemberCoverage coverage);

    void delete(int id);
}
