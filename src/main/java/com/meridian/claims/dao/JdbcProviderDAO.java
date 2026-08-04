package com.meridian.claims.dao;

import com.meridian.claims.model.NetworkStatus;
import com.meridian.claims.model.Provider;
import com.meridian.claims.model.ProviderType;
import com.meridian.claims.util.Page;
import org.apache.log4j.Logger;
import org.springframework.dao.EmptyResultDataAccessException;
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
public class JdbcProviderDAO extends BaseDAO implements ProviderDAO {

    private static final Logger LOG = Logger.getLogger(JdbcProviderDAO.class);

    private static final String SELECT_COLS =
        "id, npi, name, provider_type, specialty, network_status, phone, address, " +
        "ach_routing_number, ach_account_number, ach_account_type, " +
        "npi_validation_status, npi_validated_at, " +
        "deleted_at, created_at, updated_at";

    @Override
    public Provider findById(int id) {
        String sql = "SELECT " + SELECT_COLS + " FROM providers WHERE id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new ProviderRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findById failed id=" + id, e);
            throw new DAOException("Could not load provider id=" + id, e);
        }
    }

    @Override
    public Provider findByNpi(String npi) {
        String sql = "SELECT " + SELECT_COLS + " FROM providers WHERE npi = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new ProviderRowMapper(), npi);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findByNpi failed npi=" + npi, e);
            throw new DAOException("Could not load provider npi=" + npi, e);
        }
    }

    @Override
    public Page<Provider> search(String query, int pageNumber, int pageSize) {
        String like = "%" + (query == null ? "" : query.trim()) + "%";
        String countSql = "SELECT COUNT(*) FROM providers WHERE deleted_at IS NULL " +
            "AND (name ILIKE ? OR npi ILIKE ? OR specialty ILIKE ?)";
        String dataSql = "SELECT " + SELECT_COLS + " FROM providers WHERE deleted_at IS NULL " +
            "AND (name ILIKE ? OR npi ILIKE ? OR specialty ILIKE ?) " +
            "ORDER BY name LIMIT ? OFFSET ?";
        try {
            int total = getJdbcTemplate().queryForObject(countSql, Integer.class, like, like, like);
            int offset = (pageNumber - 1) * pageSize;
            List<Provider> items = getJdbcTemplate().query(dataSql, new ProviderRowMapper(),
                like, like, like, pageSize, offset);
            return new Page<Provider>(items, pageNumber, pageSize, total);
        } catch (Exception e) {
            LOG.error("search failed query=" + query, e);
            throw new DAOException("Could not search providers", e);
        }
    }

    @Override
    public List<Provider> findAll() {
        String sql = "SELECT " + SELECT_COLS + " FROM providers ORDER BY name";
        try {
            return getJdbcTemplate().query(sql, new ProviderRowMapper());
        } catch (Exception e) {
            LOG.error("findAll failed", e);
            throw new DAOException("Could not list providers", e);
        }
    }

    @Override
    public List<Provider> findAllActive() {
        String sql = "SELECT " + SELECT_COLS + " FROM providers WHERE deleted_at IS NULL ORDER BY name";
        try {
            return getJdbcTemplate().query(sql, new ProviderRowMapper());
        } catch (Exception e) {
            LOG.error("findAllActive failed", e);
            throw new DAOException("Could not list active providers", e);
        }
    }

    @Override
    public void insert(Provider provider) {
        String sql = "INSERT INTO providers " +
            "(npi, name, provider_type, specialty, network_status, phone, address) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            final Provider p = provider;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setString(1, p.getNpi());
                ps.setString(2, p.getName());
                ps.setString(3, p.getProviderType() == null ? "INDIVIDUAL" : p.getProviderType().name());
                ps.setString(4, p.getSpecialty());
                ps.setString(5, p.getNetworkStatus() == null ? "IN_NETWORK" : p.getNetworkStatus().name());
                ps.setString(6, p.getPhone());
                ps.setString(7, p.getAddress());
                return ps;
            }, keyHolder);
            if (keyHolder.getKey() != null) {
                provider.setId(keyHolder.getKey().intValue());
            }
        } catch (Exception e) {
            LOG.error("insert failed npi=" + provider.getNpi(), e);
            throw new DAOException("Could not insert provider", e);
        }
    }

    @Override
    public void update(Provider provider) {
        String sql = "UPDATE providers SET npi = ?, name = ?, provider_type = ?, " +
            "specialty = ?, network_status = ?, phone = ?, address = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql,
                provider.getNpi(),
                provider.getName(),
                provider.getProviderType().name(),
                provider.getSpecialty(),
                provider.getNetworkStatus().name(),
                provider.getPhone(),
                provider.getAddress(),
                provider.getId());
        } catch (Exception e) {
            LOG.error("update failed id=" + provider.getId(), e);
            throw new DAOException("Could not update provider id=" + provider.getId(), e);
        }
    }

    @Override
    public void updateBankingInfo(int id, String routingNumber, String accountNumber, String accountType) {
        String sql = "UPDATE providers SET ach_routing_number = ?, ach_account_number = ?, ach_account_type = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql, routingNumber, accountNumber, accountType, id);
        } catch (Exception e) {
            LOG.error("updateBankingInfo failed id=" + id, e);
            throw new DAOException("Could not update banking info for provider id=" + id, e);
        }
    }

    @Override
    public void updateNpiValidation(int id, String status, java.util.Date validatedAt) {
        String sql = "UPDATE providers SET npi_validation_status = ?, npi_validated_at = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql, status,
                validatedAt == null ? null : new java.sql.Timestamp(validatedAt.getTime()), id);
        } catch (Exception e) {
            LOG.error("updateNpiValidation failed id=" + id, e);
            throw new DAOException("Could not update NPI validation status for provider id=" + id, e);
        }
    }

    @Override
    public void softDelete(int id) {
        try {
            getJdbcTemplate().update("UPDATE providers SET deleted_at = NOW() WHERE id = ?", id);
        } catch (Exception e) {
            LOG.error("softDelete failed id=" + id, e);
            throw new DAOException("Could not delete provider id=" + id, e);
        }
    }

    private static final class ProviderRowMapper implements RowMapper<Provider> {
        @Override
        public Provider mapRow(ResultSet rs, int rowNum) throws SQLException {
            Provider p = new Provider();
            p.setId(rs.getInt("id"));
            p.setNpi(rs.getString("npi"));
            p.setName(rs.getString("name"));
            p.setProviderType(ProviderType.valueOf(rs.getString("provider_type")));
            p.setSpecialty(rs.getString("specialty"));
            p.setNetworkStatus(NetworkStatus.valueOf(rs.getString("network_status")));
            p.setPhone(rs.getString("phone"));
            p.setAddress(rs.getString("address"));
            p.setAchRoutingNumber(rs.getString("ach_routing_number"));
            p.setAchAccountNumber(rs.getString("ach_account_number"));
            p.setAchAccountType(rs.getString("ach_account_type"));
            p.setNpiValidationStatus(rs.getString("npi_validation_status"));
            Timestamp npiValidatedAt = rs.getTimestamp("npi_validated_at");
            if (npiValidatedAt != null) p.setNpiValidatedAt(new java.util.Date(npiValidatedAt.getTime()));
            Timestamp deletedAt = rs.getTimestamp("deleted_at");
            if (deletedAt != null) p.setDeletedAt(new java.util.Date(deletedAt.getTime()));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) p.setCreatedAt(new java.util.Date(createdAt.getTime()));
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) p.setUpdatedAt(new java.util.Date(updatedAt.getTime()));
            return p;
        }
    }
}
