package com.meridian.claims.dao;

import com.meridian.claims.model.RemittanceBatch;
import com.meridian.claims.model.RemittanceBatchItem;
import java.math.BigDecimal;
import java.util.List;

public interface RemittanceBatchDAO {
    void insertBatch(RemittanceBatch batch);
    void insertItem(RemittanceBatchItem item);
    void updateTotalPaid(int batchId, BigDecimal totalPaid);
    RemittanceBatch findById(int id);
    List<RemittanceBatch> findAll();
    List<RemittanceBatchItem> findItemsByBatchId(int batchId);
    void markSent(int batchId);
}
