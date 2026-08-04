package com.meridian.claims.transport;

import com.meridian.claims.model.TradingPartner;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Default transport: a local filesystem directory pair (inbound/outbound),
 * exactly like the single global directory {@code InboundClaimFilePollerJob}
 * has always polled — this adapter just makes that behavior partner-scoped and
 * reusable behind the {@link TransportAdapter} seam. Accepted/rejected inbound
 * files move to an {@code archive}/{@code rejected} subdirectory of the
 * partner's configured inbound path, mirroring the existing poller's convention.
 */
@Component
public class LocalDirectoryTransportAdapter implements TransportAdapter {

    private static final Logger LOG = Logger.getLogger(LocalDirectoryTransportAdapter.class);

    @Override
    public List<String> listInboundFiles(TradingPartner partner) throws TransportException {
        Path inbound = requireInboundDir(partner);
        List<String> names = new ArrayList<String>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inbound, "*")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    names.add(file.getFileName().toString());
                }
            }
        } catch (IOException e) {
            throw new TransportException("Could not list inbound directory for partner "
                + partner.getPartnerName() + ": " + e.getMessage(), e);
        }
        return names;
    }

    @Override
    public String readInboundFile(TradingPartner partner, String fileName) throws TransportException {
        Path inbound = requireInboundDir(partner);
        try {
            return new String(Files.readAllBytes(inbound.resolve(fileName)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TransportException("Could not read " + fileName + " for partner "
                + partner.getPartnerName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void archiveInboundFile(TradingPartner partner, String fileName, boolean accepted) throws TransportException {
        Path inbound = requireInboundDir(partner);
        Path dest = inbound.resolve(accepted ? "archive" : "rejected").resolve(fileName);
        try {
            Files.createDirectories(dest.getParent());
            Files.move(inbound.resolve(fileName), dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new TransportException("Could not move " + fileName + " to "
                + (accepted ? "archive/" : "rejected/") + " for partner " + partner.getPartnerName()
                + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void writeOutboundFile(TradingPartner partner, String fileName, String content) throws TransportException {
        if (partner.getOutboundPath() == null || partner.getOutboundPath().trim().isEmpty()) {
            LOG.warn("No outbound_path configured for partner " + partner.getPartnerName()
                + " — " + fileName + " not written");
            return;
        }
        Path outbound = Paths.get(partner.getOutboundPath().trim());
        try {
            Files.createDirectories(outbound);
            Files.write(outbound.resolve(fileName), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new TransportException("Could not write outbound file " + fileName + " for partner "
                + partner.getPartnerName() + ": " + e.getMessage(), e);
        }
    }

    private Path requireInboundDir(TradingPartner partner) throws TransportException {
        if (partner.getInboundPath() == null || partner.getInboundPath().trim().isEmpty()) {
            throw new TransportException("No inbound_path configured for partner " + partner.getPartnerName());
        }
        return Paths.get(partner.getInboundPath().trim());
    }
}
