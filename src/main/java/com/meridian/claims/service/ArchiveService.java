package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimArchiveDAO;
import com.meridian.claims.model.Claim;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read access to archived claims for the Admin archive-search screen. The archiving
 * itself runs in ClaimArchiveJob (nightly); this service is the query side.
 */
@Service
public class ArchiveService {

    @Autowired private ClaimArchiveDAO claimArchiveDAO;

    public List<Claim> search(String query) {
        return claimArchiveDAO.search(query);
    }

    public Claim findById(int id) {
        return claimArchiveDAO.findById(id);
    }
}
