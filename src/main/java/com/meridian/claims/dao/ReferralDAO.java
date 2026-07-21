package com.meridian.claims.dao;

import com.meridian.claims.model.Referral;
import com.meridian.claims.util.Page;

import java.util.Date;

public interface ReferralDAO {

    Referral findById(int id);

    Referral findByReferralNumber(String referralNumber);

    Referral findValid(int memberId, String serviceType, Date serviceDate);

    Page<Referral> findByMemberId(int memberId, int pageNumber, int pageSize);

    Page<Referral> findAll(int pageNumber, int pageSize);

    void insert(Referral referral);

    void update(Referral referral);

    void expire(int id);
}
