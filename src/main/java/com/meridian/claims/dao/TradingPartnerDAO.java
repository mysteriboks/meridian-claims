package com.meridian.claims.dao;

import com.meridian.claims.model.TradingPartner;

import java.util.List;

/** DAO for the trading-partner master ({@code trading_partners}, Phase 13). */
public interface TradingPartnerDAO {

    int insert(TradingPartner partner);

    void update(TradingPartner partner);

    TradingPartner findById(int id);

    List<TradingPartner> findAll();

    /** Active partners only — the set the poller job iterates. */
    List<TradingPartner> findAllActive();
}
