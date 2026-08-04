package com.meridian.claims.transport;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.meridian.claims.model.TradingPartner;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * SFTP transport (JSch) for a live trading partner (Phase 13).
 *
 * The partner's password/passphrase is never stored in {@code trading_partners}
 * — {@link TradingPartner#getTransportCredentialRef()} is a key name resolved
 * from the Spring {@link Environment} at connect time (i.e. from the external
 * prod property overlay in production; unset in dev, where this adapter is
 * simply not exercised against a real host). Connects fresh per operation —
 * trading-partner poll volume is low (batch, not per-request), so a pooled
 * connection is not worth the added complexity.
 */
@Component
public class SftpTransportAdapter implements TransportAdapter {

    private static final Logger LOG = Logger.getLogger(SftpTransportAdapter.class);
    private static final int DEFAULT_PORT = 22;
    private static final int CONNECT_TIMEOUT_MS = 15000;

    private final Environment environment;

    @Autowired
    public SftpTransportAdapter(Environment environment) {
        this.environment = environment;
    }

    @Override
    public List<String> listInboundFiles(TradingPartner partner) throws TransportException {
        ChannelSftp channel = null;
        Session session = null;
        try {
            session = openSession(partner);
            channel = openChannel(session);
            String remoteDir = requireInboundPath(partner);
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = channel.ls(remoteDir);
            List<String> names = new ArrayList<String>();
            for (ChannelSftp.LsEntry entry : entries) {
                if (!entry.getAttrs().isDir()) {
                    names.add(entry.getFilename());
                }
            }
            return names;
        } catch (JSchException | SftpException e) {
            throw new TransportException("SFTP list failed for partner " + partner.getPartnerName()
                + ": " + e.getMessage(), e);
        } finally {
            close(channel, session);
        }
    }

    @Override
    public String readInboundFile(TradingPartner partner, String fileName) throws TransportException {
        ChannelSftp channel = null;
        Session session = null;
        try {
            session = openSession(partner);
            channel = openChannel(session);
            String remotePath = requireInboundPath(partner) + "/" + fileName;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            channel.get(remotePath, out);
            return out.toString("UTF-8");
        } catch (JSchException | SftpException | java.io.IOException e) {
            throw new TransportException("SFTP read failed for " + fileName + " (partner "
                + partner.getPartnerName() + "): " + e.getMessage(), e);
        } finally {
            close(channel, session);
        }
    }

    @Override
    public void archiveInboundFile(TradingPartner partner, String fileName, boolean accepted) throws TransportException {
        ChannelSftp channel = null;
        Session session = null;
        try {
            session = openSession(partner);
            channel = openChannel(session);
            String remoteDir = requireInboundPath(partner);
            String subdir = remoteDir + "/" + (accepted ? "archive" : "rejected");
            mkdirIfAbsent(channel, subdir);
            channel.rename(remoteDir + "/" + fileName, subdir + "/" + fileName);
        } catch (JSchException | SftpException e) {
            throw new TransportException("SFTP archive failed for " + fileName + " (partner "
                + partner.getPartnerName() + "): " + e.getMessage(), e);
        } finally {
            close(channel, session);
        }
    }

    @Override
    public void writeOutboundFile(TradingPartner partner, String fileName, String content) throws TransportException {
        if (partner.getOutboundPath() == null || partner.getOutboundPath().trim().isEmpty()) {
            LOG.warn("No outbound_path configured for partner " + partner.getPartnerName()
                + " — " + fileName + " not written");
            return;
        }
        ChannelSftp channel = null;
        Session session = null;
        try {
            session = openSession(partner);
            channel = openChannel(session);
            String remoteDir = partner.getOutboundPath().trim();
            mkdirIfAbsent(channel, remoteDir);
            InputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            channel.put(in, remoteDir + "/" + fileName);
        } catch (JSchException | SftpException e) {
            throw new TransportException("SFTP write failed for " + fileName + " (partner "
                + partner.getPartnerName() + "): " + e.getMessage(), e);
        } finally {
            close(channel, session);
        }
    }

    // -------------------------------------------------------------------------

    private Session openSession(TradingPartner partner) throws TransportException, JSchException {
        if (partner.getTransportHost() == null || partner.getTransportHost().trim().isEmpty()) {
            throw new TransportException("No transport_host configured for partner " + partner.getPartnerName());
        }
        if (partner.getTransportUsername() == null || partner.getTransportUsername().trim().isEmpty()) {
            throw new TransportException("No transport_username configured for partner " + partner.getPartnerName());
        }
        String password = resolveCredential(partner);
        int port = partner.getTransportPort() != null ? partner.getTransportPort() : DEFAULT_PORT;

        JSch jsch = new JSch();
        Session session = jsch.getSession(partner.getTransportUsername().trim(),
            partner.getTransportHost().trim(), port);
        session.setPassword(password);
        // Internal trading-partner endpoints are not on the public host-key trust store this app
        // ships with; host-key pinning is an operational setup step (documented for Phase 22),
        // not something the app can assume at code level.
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(CONNECT_TIMEOUT_MS);
        session.connect(CONNECT_TIMEOUT_MS);
        return session;
    }

    private ChannelSftp openChannel(Session session) throws JSchException {
        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect(CONNECT_TIMEOUT_MS);
        return channel;
    }

    private String resolveCredential(TradingPartner partner) throws TransportException {
        String ref = partner.getTransportCredentialRef();
        if (ref == null || ref.trim().isEmpty()) {
            throw new TransportException("No transport_credential_ref configured for partner "
                + partner.getPartnerName());
        }
        String password = environment.getProperty("claims.transport.credential." + ref.trim());
        if (password == null || password.isEmpty()) {
            throw new TransportException("Credential '" + ref + "' for partner " + partner.getPartnerName()
                + " is not configured (expected claims.transport.credential." + ref
                + " in the external prod property overlay)");
        }
        return password;
    }

    private String requireInboundPath(TradingPartner partner) throws TransportException {
        if (partner.getInboundPath() == null || partner.getInboundPath().trim().isEmpty()) {
            throw new TransportException("No inbound_path configured for partner " + partner.getPartnerName());
        }
        return partner.getInboundPath().trim();
    }

    private void mkdirIfAbsent(ChannelSftp channel, String remoteDir) throws SftpException {
        try {
            channel.stat(remoteDir);
        } catch (SftpException e) {
            channel.mkdir(remoteDir);
        }
    }

    private void close(ChannelSftp channel, Session session) {
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }
}
