package com.meridian.claims.dao;

import com.meridian.claims.model.ScheduledJobLog;
import org.apache.log4j.Logger;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcScheduledJobLogDAO extends BaseDAO implements ScheduledJobLogDAO {

    private static final Logger LOG = Logger.getLogger(JdbcScheduledJobLogDAO.class);

    @Override
    public int start(String jobName) {
        String sql = "INSERT INTO scheduled_job_log (job_name, status, records_processed) VALUES (?,'RUNNING',0)";
        KeyHolder key = new GeneratedKeyHolder();
        try {
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setString(1, jobName);
                return ps;
            }, key);
            return key.getKey().intValue();
        } catch (Exception e) {
            LOG.error("start job log failed jobName=" + jobName, e);
            throw new DAOException("Could not start job log for " + jobName, e);
        }
    }

    @Override
    public void complete(int id, int recordsProcessed) {
        String sql = "UPDATE scheduled_job_log SET status = 'SUCCESS', completed_at = NOW(), " +
            "records_processed = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql, recordsProcessed, id);
        } catch (Exception e) {
            LOG.error("complete job log failed id=" + id, e);
            throw new DAOException("Could not complete job log id=" + id, e);
        }
    }

    @Override
    public void fail(int id, String errorMessage) {
        String sql = "UPDATE scheduled_job_log SET status = 'FAILED', completed_at = NOW(), " +
            "error_message = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql, errorMessage, id);
        } catch (Exception e) {
            LOG.error("fail job log failed id=" + id, e);
            throw new DAOException("Could not fail job log id=" + id, e);
        }
    }

    @Override
    public List<ScheduledJobLog> findByJobName(String jobName) {
        String sql = "SELECT id, job_name, started_at, completed_at, status, records_processed, error_message " +
            "FROM scheduled_job_log WHERE job_name = ? ORDER BY started_at DESC LIMIT 20";
        try {
            return getJdbcTemplate().query(sql, new JobLogRowMapper(), jobName);
        } catch (Exception e) {
            LOG.error("findByJobName failed jobName=" + jobName, e);
            throw new DAOException("Could not load job log for " + jobName, e);
        }
    }

    @Override
    public List<ScheduledJobLog> findRecent(int limit) {
        String sql = "SELECT id, job_name, started_at, completed_at, status, records_processed, error_message " +
            "FROM scheduled_job_log ORDER BY started_at DESC LIMIT ?";
        try {
            return getJdbcTemplate().query(sql, new JobLogRowMapper(), limit);
        } catch (Exception e) {
            LOG.error("findRecent failed", e);
            throw new DAOException("Could not load recent job log", e);
        }
    }

    private static final class JobLogRowMapper implements RowMapper<ScheduledJobLog> {
        @Override
        public ScheduledJobLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            ScheduledJobLog l = new ScheduledJobLog();
            l.setId(rs.getInt("id"));
            l.setJobName(rs.getString("job_name"));
            Timestamp sa = rs.getTimestamp("started_at");
            if (sa != null) { l.setStartedAt(new java.util.Date(sa.getTime())); }
            Timestamp ca = rs.getTimestamp("completed_at");
            if (ca != null) { l.setCompletedAt(new java.util.Date(ca.getTime())); }
            l.setStatus(rs.getString("status"));
            l.setRecordsProcessed(rs.getInt("records_processed"));
            l.setErrorMessage(rs.getString("error_message"));
            return l;
        }
    }
}
