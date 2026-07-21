package com.meridian.claims.dao;

import com.meridian.claims.model.Appeal;
import java.util.List;

public interface AppealDAO {
    void insert(Appeal appeal);
    Appeal findById(int id);
    List<Appeal> findByClaimId(int claimId);
    List<Appeal> findByStatus(String status);
    void updateStatus(int id, String status, String outcome, String outcomeNotes);
    void updateAssignment(int id, Integer assignedTo);
}
