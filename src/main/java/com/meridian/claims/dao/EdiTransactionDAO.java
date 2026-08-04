package com.meridian.claims.dao;

import com.meridian.claims.model.EdiTransaction;

import java.util.List;

/**
 * DAO for the integration transaction log ({@code edi_transactions}) — the
 * reconciliation backbone for the interoperability program (Phase 12+).
 */
public interface EdiTransactionDAO {

    /** Insert a new transaction row; returns the generated id. */
    int insert(EdiTransaction txn);

    /** Return the most recent N transaction rows ordered by created_at DESC. */
    List<EdiTransaction> findRecent(int limit);

    /**
     * Return all transaction rows (inbound and outbound acks) logged against a
     * given file reference, ordered by created_at. Used by TradingPartnerPollerJob
     * (Phase 13) to retrieve the acknowledgment(s) IntakeService just generated for
     * a file so they can be pushed back through the originating partner's transport.
     */
    List<EdiTransaction> findByFileReference(String fileReference);
}
