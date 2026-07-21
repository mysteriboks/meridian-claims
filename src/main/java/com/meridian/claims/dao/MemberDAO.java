package com.meridian.claims.dao;

import com.meridian.claims.model.Member;
import com.meridian.claims.util.Page;

import java.util.List;

public interface MemberDAO {

    Member findById(int id);

    Member findByMemberNumber(String memberNumber);

    Page<Member> search(String query, int pageNumber, int pageSize);

    List<Member> findAll();

    List<Member> findAllActive();

    void insert(Member member);

    void update(Member member);

    void softDelete(int id);

    void setStatus(int id, String status);
}
