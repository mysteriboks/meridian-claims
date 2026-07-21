package com.meridian.claims.service;

import com.meridian.claims.dao.ScheduledJobLogDAO;
import com.meridian.claims.model.ScheduledJobLog;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Records job execution history. Each job calls start() at the top, then
 * either complete() or fail() at the end. Thin wrapper over ScheduledJobLogDAO —
 * never throws (logging must not abort the business operation).
 */
@Service
public class ScheduledJobLogService {

    private static final Logger LOG = Logger.getLogger(ScheduledJobLogService.class);

    @Autowired private ScheduledJobLogDAO scheduledJobLogDAO;

    public int start(String jobName) {
        try {
            return scheduledJobLogDAO.start(jobName);
        } catch (Exception e) {
            LOG.warn("Could not start job log for " + jobName, e);
            return -1;
        }
    }

    public void complete(int logId, int recordsProcessed) {
        if (logId < 0) return;
        try {
            scheduledJobLogDAO.complete(logId, recordsProcessed);
        } catch (Exception e) {
            LOG.warn("Could not complete job log id=" + logId, e);
        }
    }

    public void fail(int logId, String errorMessage) {
        if (logId < 0) return;
        try {
            scheduledJobLogDAO.fail(logId, errorMessage);
        } catch (Exception e) {
            LOG.warn("Could not fail job log id=" + logId, e);
        }
    }

    public List<ScheduledJobLog> findRecent(int limit) {
        return scheduledJobLogDAO.findRecent(limit);
    }

    public List<ScheduledJobLog> findByJobName(String jobName) {
        return scheduledJobLogDAO.findByJobName(jobName);
    }
}
