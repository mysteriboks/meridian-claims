package com.meridian.claims.dao;

import com.meridian.claims.model.ScheduledJobLog;
import java.util.List;

public interface ScheduledJobLogDAO {
    int start(String jobName);
    void complete(int id, int recordsProcessed);
    void fail(int id, String errorMessage);
    List<ScheduledJobLog> findByJobName(String jobName);
    List<ScheduledJobLog> findRecent(int limit);
}
