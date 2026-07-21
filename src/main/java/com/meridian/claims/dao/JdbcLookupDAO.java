package com.meridian.claims.dao;

import com.meridian.claims.model.DenialReasonCode;
import com.meridian.claims.model.DiagnosisCode;
import com.meridian.claims.model.ProcedureCode;
import com.meridian.claims.model.ServiceTypeCategory;
import org.apache.log4j.Logger;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class JdbcLookupDAO extends BaseDAO implements LookupDAO {

    private static final Logger LOG = Logger.getLogger(JdbcLookupDAO.class);

    // --- Denial Reason Codes ---

    @Override
    public List<DenialReasonCode> findAllDenialReasonCodes() {
        try {
            return getJdbcTemplate().query(
                "SELECT code, carc_code, description, active FROM denial_reason_codes ORDER BY code",
                new DenialCodeRowMapper());
        } catch (Exception e) {
            LOG.error("findAllDenialReasonCodes failed", e);
            throw new DAOException("Could not list denial reason codes", e);
        }
    }

    @Override
    public DenialReasonCode findDenialReasonCode(String code) {
        try {
            return getJdbcTemplate().queryForObject(
                "SELECT code, carc_code, description, active FROM denial_reason_codes WHERE code = ?",
                new DenialCodeRowMapper(), code);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findDenialReasonCode failed code=" + code, e);
            throw new DAOException("Could not load denial reason code " + code, e);
        }
    }

    @Override
    public void insertDenialReasonCode(DenialReasonCode c) {
        try {
            getJdbcTemplate().update(
                "INSERT INTO denial_reason_codes (code, carc_code, description, active) VALUES (?, ?, ?, ?)",
                c.getCode(), c.getCarcCode(), c.getDescription(), c.isActive());
        } catch (Exception e) {
            LOG.error("insertDenialReasonCode failed code=" + c.getCode(), e);
            throw new DAOException("Could not insert denial reason code", e);
        }
    }

    @Override
    public void updateDenialReasonCode(DenialReasonCode c) {
        try {
            getJdbcTemplate().update(
                "UPDATE denial_reason_codes SET carc_code = ?, description = ?, active = ? WHERE code = ?",
                c.getCarcCode(), c.getDescription(), c.isActive(), c.getCode());
        } catch (Exception e) {
            LOG.error("updateDenialReasonCode failed code=" + c.getCode(), e);
            throw new DAOException("Could not update denial reason code", e);
        }
    }

    // --- Procedure Codes ---

    @Override
    public List<ProcedureCode> findAllProcedureCodes() {
        try {
            return getJdbcTemplate().query(
                "SELECT code, description, service_type, active FROM procedure_codes ORDER BY code",
                new ProcedureCodeRowMapper());
        } catch (Exception e) {
            LOG.error("findAllProcedureCodes failed", e);
            throw new DAOException("Could not list procedure codes", e);
        }
    }

    @Override
    public ProcedureCode findProcedureCode(String code) {
        try {
            return getJdbcTemplate().queryForObject(
                "SELECT code, description, service_type, active FROM procedure_codes WHERE code = ?",
                new ProcedureCodeRowMapper(), code);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findProcedureCode failed code=" + code, e);
            throw new DAOException("Could not load procedure code " + code, e);
        }
    }

    @Override
    public void insertProcedureCode(ProcedureCode c) {
        try {
            getJdbcTemplate().update(
                "INSERT INTO procedure_codes (code, description, service_type, active) VALUES (?, ?, ?, ?)",
                c.getCode(), c.getDescription(), c.getServiceType(), c.isActive());
        } catch (Exception e) {
            LOG.error("insertProcedureCode failed code=" + c.getCode(), e);
            throw new DAOException("Could not insert procedure code", e);
        }
    }

    @Override
    public void updateProcedureCode(ProcedureCode c) {
        try {
            getJdbcTemplate().update(
                "UPDATE procedure_codes SET description = ?, service_type = ?, active = ? WHERE code = ?",
                c.getDescription(), c.getServiceType(), c.isActive(), c.getCode());
        } catch (Exception e) {
            LOG.error("updateProcedureCode failed code=" + c.getCode(), e);
            throw new DAOException("Could not update procedure code", e);
        }
    }

    // --- Diagnosis Codes ---

    @Override
    public List<DiagnosisCode> findAllDiagnosisCodes() {
        try {
            return getJdbcTemplate().query(
                "SELECT code, description, active FROM diagnosis_codes ORDER BY code",
                new DiagnosisCodeRowMapper());
        } catch (Exception e) {
            LOG.error("findAllDiagnosisCodes failed", e);
            throw new DAOException("Could not list diagnosis codes", e);
        }
    }

    @Override
    public DiagnosisCode findDiagnosisCode(String code) {
        try {
            return getJdbcTemplate().queryForObject(
                "SELECT code, description, active FROM diagnosis_codes WHERE code = ?",
                new DiagnosisCodeRowMapper(), code);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findDiagnosisCode failed code=" + code, e);
            throw new DAOException("Could not load diagnosis code " + code, e);
        }
    }

    @Override
    public void insertDiagnosisCode(DiagnosisCode c) {
        try {
            getJdbcTemplate().update(
                "INSERT INTO diagnosis_codes (code, description, active) VALUES (?, ?, ?)",
                c.getCode(), c.getDescription(), c.isActive());
        } catch (Exception e) {
            LOG.error("insertDiagnosisCode failed code=" + c.getCode(), e);
            throw new DAOException("Could not insert diagnosis code", e);
        }
    }

    @Override
    public void updateDiagnosisCode(DiagnosisCode c) {
        try {
            getJdbcTemplate().update(
                "UPDATE diagnosis_codes SET description = ?, active = ? WHERE code = ?",
                c.getDescription(), c.isActive(), c.getCode());
        } catch (Exception e) {
            LOG.error("updateDiagnosisCode failed code=" + c.getCode(), e);
            throw new DAOException("Could not update diagnosis code", e);
        }
    }

    // --- Service Type Categories ---

    @Override
    public List<ServiceTypeCategory> findAllServiceTypeCategories() {
        try {
            return getJdbcTemplate().query(
                "SELECT code, description, active FROM service_type_categories ORDER BY code",
                new ServiceTypeCategoryRowMapper());
        } catch (Exception e) {
            LOG.error("findAllServiceTypeCategories failed", e);
            throw new DAOException("Could not list service type categories", e);
        }
    }

    @Override
    public ServiceTypeCategory findServiceTypeCategory(String code) {
        try {
            return getJdbcTemplate().queryForObject(
                "SELECT code, description, active FROM service_type_categories WHERE code = ?",
                new ServiceTypeCategoryRowMapper(), code);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findServiceTypeCategory failed code=" + code, e);
            throw new DAOException("Could not load service type category " + code, e);
        }
    }

    @Override
    public void insertServiceTypeCategory(ServiceTypeCategory c) {
        try {
            getJdbcTemplate().update(
                "INSERT INTO service_type_categories (code, description, active) VALUES (?, ?, ?)",
                c.getCode(), c.getDescription(), c.isActive());
        } catch (Exception e) {
            LOG.error("insertServiceTypeCategory failed code=" + c.getCode(), e);
            throw new DAOException("Could not insert service type category", e);
        }
    }

    @Override
    public void updateServiceTypeCategory(ServiceTypeCategory c) {
        try {
            getJdbcTemplate().update(
                "UPDATE service_type_categories SET description = ?, active = ? WHERE code = ?",
                c.getDescription(), c.isActive(), c.getCode());
        } catch (Exception e) {
            LOG.error("updateServiceTypeCategory failed code=" + c.getCode(), e);
            throw new DAOException("Could not update service type category", e);
        }
    }

    // --- Row mappers ---

    private static final class DenialCodeRowMapper implements RowMapper<DenialReasonCode> {
        @Override
        public DenialReasonCode mapRow(ResultSet rs, int rowNum) throws SQLException {
            DenialReasonCode c = new DenialReasonCode();
            c.setCode(rs.getString("code"));
            c.setCarcCode(rs.getString("carc_code"));
            c.setDescription(rs.getString("description"));
            c.setActive(rs.getBoolean("active"));
            return c;
        }
    }

    private static final class ProcedureCodeRowMapper implements RowMapper<ProcedureCode> {
        @Override
        public ProcedureCode mapRow(ResultSet rs, int rowNum) throws SQLException {
            ProcedureCode c = new ProcedureCode();
            c.setCode(rs.getString("code"));
            c.setDescription(rs.getString("description"));
            c.setServiceType(rs.getString("service_type"));
            c.setActive(rs.getBoolean("active"));
            return c;
        }
    }

    private static final class DiagnosisCodeRowMapper implements RowMapper<DiagnosisCode> {
        @Override
        public DiagnosisCode mapRow(ResultSet rs, int rowNum) throws SQLException {
            DiagnosisCode c = new DiagnosisCode();
            c.setCode(rs.getString("code"));
            c.setDescription(rs.getString("description"));
            c.setActive(rs.getBoolean("active"));
            return c;
        }
    }

    private static final class ServiceTypeCategoryRowMapper implements RowMapper<ServiceTypeCategory> {
        @Override
        public ServiceTypeCategory mapRow(ResultSet rs, int rowNum) throws SQLException {
            ServiceTypeCategory c = new ServiceTypeCategory();
            c.setCode(rs.getString("code"));
            c.setDescription(rs.getString("description"));
            c.setActive(rs.getBoolean("active"));
            return c;
        }
    }
}
