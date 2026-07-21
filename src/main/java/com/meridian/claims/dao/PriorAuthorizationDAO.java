package com.meridian.claims.dao;

import com.meridian.claims.model.PriorAuthorization;
import com.meridian.claims.util.Page;

import java.util.Date;

public interface PriorAuthorizationDAO {

    PriorAuthorization findById(int id);

    PriorAuthorization findByAuthNumber(String authNumber);

    PriorAuthorization findValid(int memberId, String procedureCode, Date serviceDate);

    Page<PriorAuthorization> findByMemberId(int memberId, int pageNumber, int pageSize);

    Page<PriorAuthorization> findAll(int pageNumber, int pageSize);

    void insert(PriorAuthorization auth);

    void update(PriorAuthorization auth);

    void expire(int id);
}
