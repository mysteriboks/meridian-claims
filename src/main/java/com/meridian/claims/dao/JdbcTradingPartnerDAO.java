package com.meridian.claims.dao;

import com.meridian.claims.model.TradingPartner;
import org.apache.log4j.Logger;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

@Repository
public class JdbcTradingPartnerDAO extends BaseDAO implements TradingPartnerDAO {

    private static final Logger LOG = Logger.getLogger(JdbcTradingPartnerDAO.class);

    private static final String COLS = "id, partner_name, isa_qualifier, isa_id, gs_id, " +
        "enabled_transactions, transport_type, transport_host, transport_port, transport_username, " +
        "transport_credential_ref, inbound_path, outbound_path, active, created_at, updated_at";

    @Override
    public int insert(TradingPartner partner) {
        String sql = "INSERT INTO trading_partners " +
            "(partner_name, isa_qualifier, isa_id, gs_id, enabled_transactions, transport_type, " +
            "transport_host, transport_port, transport_username, transport_credential_ref, " +
            "inbound_path, outbound_path, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setString(1, partner.getPartnerName());
                ps.setString(2, partner.getIsaQualifier());
                ps.setString(3, partner.getIsaId());
                ps.setString(4, partner.getGsId());
                ps.setString(5, partner.getEnabledTransactions());
                ps.setString(6, partner.getTransportType());
                ps.setString(7, partner.getTransportHost());
                if (partner.getTransportPort() != null) {
                    ps.setInt(8, partner.getTransportPort());
                } else {
                    ps.setNull(8, Types.INTEGER);
                }
                ps.setString(9, partner.getTransportUsername());
                ps.setString(10, partner.getTransportCredentialRef());
                ps.setString(11, partner.getInboundPath());
                ps.setString(12, partner.getOutboundPath());
                ps.setBoolean(13, partner.isActive());
                return ps;
            }, keyHolder);
            int id = keyHolder.getKey().intValue();
            partner.setId(id);
            return id;
        } catch (Exception e) {
            LOG.error("insert trading partner failed name=" + partner.getPartnerName(), e);
            throw new DAOException("Could not insert trading partner", e);
        }
    }

    @Override
    public void update(TradingPartner partner) {
        String sql = "UPDATE trading_partners SET partner_name = ?, isa_qualifier = ?, isa_id = ?, " +
            "gs_id = ?, enabled_transactions = ?, transport_type = ?, transport_host = ?, " +
            "transport_port = ?, transport_username = ?, transport_credential_ref = ?, " +
            "inbound_path = ?, outbound_path = ?, active = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql,
                partner.getPartnerName(), partner.getIsaQualifier(), partner.getIsaId(),
                partner.getGsId(), partner.getEnabledTransactions(), partner.getTransportType(),
                partner.getTransportHost(), partner.getTransportPort(), partner.getTransportUsername(),
                partner.getTransportCredentialRef(), partner.getInboundPath(), partner.getOutboundPath(),
                partner.isActive(), partner.getId());
        } catch (Exception e) {
            LOG.error("update trading partner failed id=" + partner.getId(), e);
            throw new DAOException("Could not update trading partner id=" + partner.getId(), e);
        }
    }

    @Override
    public TradingPartner findById(int id) {
        String sql = "SELECT " + COLS + " FROM trading_partners WHERE id = ?";
        try {
            List<TradingPartner> rows = getJdbcTemplate().query(sql, new TradingPartnerRowMapper(), id);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            LOG.error("findById trading partner failed id=" + id, e);
            throw new DAOException("Could not load trading partner id=" + id, e);
        }
    }

    @Override
    public List<TradingPartner> findAll() {
        String sql = "SELECT " + COLS + " FROM trading_partners ORDER BY partner_name";
        try {
            return getJdbcTemplate().query(sql, new TradingPartnerRowMapper());
        } catch (Exception e) {
            LOG.error("findAll trading partners failed", e);
            throw new DAOException("Could not load trading partners", e);
        }
    }

    @Override
    public List<TradingPartner> findAllActive() {
        String sql = "SELECT " + COLS + " FROM trading_partners WHERE active = TRUE ORDER BY partner_name";
        try {
            return getJdbcTemplate().query(sql, new TradingPartnerRowMapper());
        } catch (Exception e) {
            LOG.error("findAllActive trading partners failed", e);
            throw new DAOException("Could not load active trading partners", e);
        }
    }

    private static final class TradingPartnerRowMapper implements RowMapper<TradingPartner> {
        @Override
        public TradingPartner mapRow(ResultSet rs, int rowNum) throws SQLException {
            TradingPartner p = new TradingPartner();
            p.setId(rs.getInt("id"));
            p.setPartnerName(rs.getString("partner_name"));
            p.setIsaQualifier(rs.getString("isa_qualifier"));
            p.setIsaId(rs.getString("isa_id"));
            p.setGsId(rs.getString("gs_id"));
            p.setEnabledTransactions(rs.getString("enabled_transactions"));
            p.setTransportType(rs.getString("transport_type"));
            p.setTransportHost(rs.getString("transport_host"));
            int port = rs.getInt("transport_port");
            if (!rs.wasNull()) { p.setTransportPort(port); }
            p.setTransportUsername(rs.getString("transport_username"));
            p.setTransportCredentialRef(rs.getString("transport_credential_ref"));
            p.setInboundPath(rs.getString("inbound_path"));
            p.setOutboundPath(rs.getString("outbound_path"));
            p.setActive(rs.getBoolean("active"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) { p.setCreatedAt(new java.util.Date(createdAt.getTime())); }
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) { p.setUpdatedAt(new java.util.Date(updatedAt.getTime())); }
            return p;
        }
    }
}
