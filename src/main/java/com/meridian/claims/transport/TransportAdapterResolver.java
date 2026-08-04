package com.meridian.claims.transport;

import com.meridian.claims.model.TradingPartner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link TransportAdapter} implementation for a trading partner
 * based on {@link TradingPartner#getTransportType()}. The single place callers
 * (the poller job, the service layer) go to get the right transport — they never
 * branch on transport type themselves.
 */
@Component
public class TransportAdapterResolver {

    private final LocalDirectoryTransportAdapter localAdapter;
    private final SftpTransportAdapter sftpAdapter;

    @Autowired
    public TransportAdapterResolver(LocalDirectoryTransportAdapter localAdapter, SftpTransportAdapter sftpAdapter) {
        this.localAdapter = localAdapter;
        this.sftpAdapter = sftpAdapter;
    }

    public TransportAdapter resolve(TradingPartner partner) {
        if (TradingPartner.TRANSPORT_SFTP.equals(partner.getTransportType())) {
            return sftpAdapter;
        }
        return localAdapter;
    }
}
